package server.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Multi-client TCP server with basic HTTP/1.1 GET handling. */
public final class WebServer implements AutoCloseable {
    private static final int BACKLOG = 128;
    private static final int WORKER_COUNT = Math.max(8,
            Math.min(32, Runtime.getRuntime().availableProcessors() * 2));

    private final Observer observer;
    private final RequestGuard requestGuard;
    private final HttpRequestHandler requestHandler =
            new HttpRequestHandler("Machine 2 Protection Server");
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService acceptExecutor;
    private volatile ExecutorService workerExecutor;
    private volatile ExecutorService rejectExecutor;

    public WebServer(Observer observer, RequestGuard requestGuard) {
        this.observer = observer;
        this.requestGuard = requestGuard;
    }

    public synchronized void start(int port) throws IOException {
        if (running.get()) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", port), BACKLOG);
        serverSocket = socket;
        workerExecutor = new ThreadPoolExecutor(WORKER_COUNT, WORKER_COUNT, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                Thread.ofPlatform().daemon(true).name("http-worker-", 0).factory());
        rejectExecutor = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(128),
                Thread.ofPlatform().daemon(true).name("http-reject-", 0).factory());
        acceptExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon(true).name("http-accept-").factory());
        running.set(true);
        acceptExecutor.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                // This intentionally resource-constrained service is for the
                // local/private-network lab, never for public Internet peers.
                if (!client.getInetAddress().isLoopbackAddress()
                        && !client.getInetAddress().isSiteLocalAddress()
                        && !client.getInetAddress().isLinkLocalAddress()) {
                    client.close();
                    continue;
                }
                client.setTcpNoDelay(true);
                String ip = client.getInetAddress().getHostAddress();
                observer.onConnectionOpened(ip);
                long started = System.nanoTime();
                RequestGuard.Decision decision = requestGuard.enter(ip);
                if (decision != RequestGuard.Decision.ALLOW) {
                    try {
                        rejectExecutor.execute(() -> reject(client, ip, decision, started));
                    } catch (RejectedExecutionException exception) {
                        observer.onRequestRejected(ip, RequestGuard.Decision.CONNECTION_LIMITED);
                        observer.onRequestCompleted(ip, "REJECTED", "-", 503, System.nanoTime() - started);
                        observer.onConnectionClosed(ip);
                        client.close();
                    }
                    continue;
                }
                try {
                    workerExecutor.execute(new ClientConnection(client, requestHandler, observer,
                            requestGuard, requestGuard::leave));
                } catch (RejectedExecutionException exception) {
                    requestGuard.leave();
                    try {
                        rejectExecutor.execute(() -> reject(client, ip,
                                RequestGuard.Decision.CONNECTION_LIMITED, started));
                    } catch (RejectedExecutionException ignored) {
                        observer.onRequestRejected(ip, RequestGuard.Decision.CONNECTION_LIMITED);
                        observer.onRequestCompleted(ip, "REJECTED", "-", 503, System.nanoTime() - started);
                        observer.onConnectionClosed(ip);
                        client.close();
                    }
                }
            } catch (SocketException exception) {
                if (running.get()) {
                    observer.onServerError(exception);
                }
            } catch (IOException exception) {
                if (running.get()) {
                    observer.onServerError(exception);
                }
            }
        }
    }

    private void reject(Socket client, String ip, RequestGuard.Decision decision, long started) {
        int status = decision == RequestGuard.Decision.CONNECTION_LIMITED ? 503 : 429;
        String reason = status == 503 ? "Service Unavailable" : "Too Many Requests";
        byte[] body = (reason + "\n").getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        try (client) {
            client.setSoTimeout(250);
            consumeRequestHeaders(client.getInputStream());
            client.getOutputStream().write(headers.getBytes(StandardCharsets.ISO_8859_1));
            client.getOutputStream().write(body);
            client.getOutputStream().flush();
        } catch (IOException ignored) {
            // The admission decision is still counted if the peer disconnects.
        } finally {
            observer.onRequestRejected(ip, decision);
            observer.onRequestCompleted(ip, "REJECTED", "-", status, System.nanoTime() - started);
            observer.onConnectionClosed(ip);
        }
    }

    private static void consumeRequestHeaders(InputStream input) throws IOException {
        int matched = 0;
        for (int count = 0; count < 8_192; count++) {
            int value = input.read();
            if (value < 0) return;
            matched = switch (matched) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : value == '\r' ? 1 : 0;
                case 2 -> value == '\r' ? 3 : 0;
                default -> value == '\n' ? 4 : 0;
            };
            if (matched == 4) return;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        closeSocket();
        shutdown(acceptExecutor);
        shutdown(workerExecutor);
        shutdown(rejectExecutor);
        requestGuard.reset();
        requestGuard.resetConnections();
        acceptExecutor = null;
        workerExecutor = null;
        rejectExecutor = null;
    }

    private void closeSocket() {
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing an already-closed server socket is harmless during shutdown.
            }
        }
    }

    private static void shutdown(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }

    public interface Observer {
        void onConnectionOpened(String ip);

        void onConnectionClosed(String ip);

        void onRequestCompleted(String ip, String method, String target, int statusCode, long responseNanos);

        void onRequestRejected(String ip, RequestGuard.Decision decision);

        void onServerError(Exception exception);
    }
}
