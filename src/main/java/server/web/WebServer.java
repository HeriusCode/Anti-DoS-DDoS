package server.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Multi-client TCP server with basic HTTP/1.1 GET handling. */
public final class WebServer implements AutoCloseable {
    private static final int BACKLOG = 128;
    private static final int WORKER_COUNT = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);

    private final Observer observer;
    private final HttpRequestHandler requestHandler =
            new HttpRequestHandler("Machine 2 Protection Server");
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService acceptExecutor;
    private volatile ExecutorService workerExecutor;

    public WebServer(Observer observer) {
        this.observer = observer;
    }

    public synchronized void start(int port) throws IOException {
        if (running.get()) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", port), BACKLOG);
        serverSocket = socket;
        workerExecutor = Executors.newFixedThreadPool(WORKER_COUNT,
                Thread.ofPlatform().daemon(true).name("http-worker-", 0).factory());
        acceptExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon(true).name("http-accept-").factory());
        running.set(true);
        acceptExecutor.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                client.setTcpNoDelay(true);
                String ip = client.getInetAddress().getHostAddress();
                observer.onConnectionOpened(ip);
                try {
                    workerExecutor.execute(new ClientConnection(client, requestHandler, observer));
                } catch (RejectedExecutionException exception) {
                    observer.onConnectionClosed(ip);
                    client.close();
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
        acceptExecutor = null;
        workerExecutor = null;
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

        void onServerError(Exception exception);
    }
}
