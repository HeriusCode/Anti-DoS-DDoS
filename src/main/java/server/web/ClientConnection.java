package server.web;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import server.web.HttpRequestHandler.HttpResponse;

/** Parses and responds to one HTTP request on one TCP connection. */
public final class ClientConnection implements Runnable {
    private static final int MAX_HEADER_LINES = 100;
    private static final int MAX_HEADER_CHARS = 8_192;

    private final Socket socket;
    private final HttpRequestHandler handler;
    private final WebServer.Observer observer;
    private final RequestGuard requestGuard;
    private final Runnable onClosed;
    private final String clientIp;

    public ClientConnection(Socket socket, HttpRequestHandler handler, WebServer.Observer observer,
                            RequestGuard requestGuard,
                            Runnable onClosed) {
        this.socket = socket;
        this.handler = handler;
        this.observer = observer;
        this.requestGuard = requestGuard;
        this.onClosed = onClosed;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        long started = System.nanoTime();
        String method = "UNKNOWN";
        String target = "/";
        int statusCode = 400;
        boolean requestReceived = false;
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.ISO_8859_1));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {
            socket.setSoTimeout(5_000);
            String requestLine = readLine(reader, MAX_HEADER_CHARS);
            if (requestLine == null || requestLine.length() > 8_192) {
                write(writer, new HttpResponse(400, "Bad Request", "text/plain; charset=UTF-8",
                        "Invalid HTTP request".getBytes(StandardCharsets.UTF_8)));
                return;
            }
            requestReceived = true;
            String[] parts = requestLine.trim().split("\\s+");
            if (parts.length < 3 || !parts[2].startsWith("HTTP/")) {
                write(writer, new HttpResponse(400, "Bad Request", "text/plain; charset=UTF-8",
                        "Malformed request line".getBytes(StandardCharsets.UTF_8)));
                return;
            }
            method = parts[0];
            target = parts[1];
            String userAgent = consumeHeaders(reader);
            RequestGuard.Decision decision = requestGuard.checkRequest(clientIp, userAgent);
            if (decision != RequestGuard.Decision.ALLOW) {
                statusCode = 429;
                observer.onRequestRejected(clientIp, decision);
                write(writer, new HttpResponse(429, "Too Many Requests", "text/plain; charset=UTF-8",
                        "Too Many Requests\n".getBytes(StandardCharsets.UTF_8)));
                return;
            }
            HttpResponse response = handler.handle(method, target);
            statusCode = response.statusCode();
            write(writer, response);
        } catch (IOException exception) {
            statusCode = 500;
        } finally {
            long elapsed = System.nanoTime() - started;
            if (requestReceived) {
                observer.onRequestCompleted(clientIp, method, target, statusCode, elapsed);
            }
            onClosed.run();
            observer.onConnectionClosed(clientIp);
        }
    }

    private static String consumeHeaders(BufferedReader reader) throws IOException {
        int remaining = MAX_HEADER_CHARS;
        String userAgent = "";
        for (int count = 0; count < MAX_HEADER_LINES; count++) {
            String line = readLine(reader, remaining);
            if (line == null || line.isEmpty()) {
                return userAgent;
            }
            remaining -= line.length() + 2;
            if (remaining <= 0) throw new IOException("HTTP headers are too large");
            if (line.regionMatches(true, 0, "User-Agent:", 0, 11)) {
                userAgent = line.substring(11).trim();
            }
        }
        throw new IOException("Too many HTTP headers");
    }

    private static String readLine(BufferedReader reader, int maxChars) throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int value = reader.read();
            if (value < 0) return line.isEmpty() ? null : line.toString();
            if (value == '\n') {
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                    line.setLength(line.length() - 1);
                }
                return line.toString();
            }
            if (line.length() >= maxChars) throw new IOException("HTTP header line is too long");
            line.append((char) value);
        }
    }

    private static void write(BufferedWriter writer, HttpResponse response) throws IOException {
        writer.write("HTTP/1.1 " + response.statusCode() + " " + response.reason() + "\r\n");
        writer.write("Content-Type: " + response.contentType() + "\r\n");
        writer.write("Content-Length: " + response.body().length + "\r\n");
        writer.write("Connection: close\r\n");
        writer.write("Server: DoS-Protection-Lab/1.0\r\n\r\n");
        writer.flush();
        socketOutput(writer, response.body());
    }

    private static void socketOutput(BufferedWriter writer, byte[] body) throws IOException {
        if (body.length > 0) {
            writer.write(new String(body, StandardCharsets.UTF_8));
            writer.flush();
        }
    }
}
