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

    private final Socket socket;
    private final HttpRequestHandler handler;
    private final WebServer.Observer observer;
    private final String clientIp;

    public ClientConnection(Socket socket, HttpRequestHandler handler, WebServer.Observer observer) {
        this.socket = socket;
        this.handler = handler;
        this.observer = observer;
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
            String requestLine = reader.readLine();
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
            consumeHeaders(reader);
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
            observer.onConnectionClosed(clientIp);
        }
    }

    private static void consumeHeaders(BufferedReader reader) throws IOException {
        for (int count = 0; count < MAX_HEADER_LINES; count++) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) {
                return;
            }
        }
        throw new IOException("Too many HTTP headers");
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
