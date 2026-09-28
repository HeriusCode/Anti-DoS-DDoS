package server.web;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Handles the small set of HTTP GET endpoints used by the controlled lab. */
public final class HttpRequestHandler {
    // Each page request performs real CPU work. Concurrent requests contend for
    // the machine's resources without an artificial single-request gate.
    private static final int ANALYTICS_SAMPLES = 600_000;
    private final String serverName;
    private final int[] trafficSamples = createTrafficSamples();
    private final AtomicLong reportsGenerated = new AtomicLong();

    public HttpRequestHandler(String serverName) {
        this.serverName = serverName;
    }

    public HttpResponse handle(String method, String target) {
        if (!"GET".equalsIgnoreCase(method)) {
            return text(405, "Method Not Allowed", "Only HTTP GET is supported.");
        }
        String path = target == null ? "/" : target.split("\\?", 2)[0];
        return switch (path) {
            case "/", "/index.html" -> htmlPage();
            case "/health" -> text(200, "OK", "OK");
            case "/favicon.ico" -> new HttpResponse(204, "No Content", "image/x-icon", new byte[0]);
            default -> text(404, "Not Found", "The requested resource was not found.");
        };
    }

    private HttpResponse htmlPage() {
        long reportNumber = reportsGenerated.incrementAndGet();
        int[] report = trafficSamples.clone();
        report[(int) (reportNumber % report.length)] = (int) (reportNumber % 1_000);
        Arrays.sort(report);
        int median = report[report.length / 2];
        int p95 = report[(int) (report.length * .95)];
        int p99 = report[(int) (report.length * .99)];
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>DoS/DDoS Protection Lab Server</title>
                  <style>
                    *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;
                    background:radial-gradient(circle at top,#08344b,#020b14 58%);color:#c9efff;
                    font-family:Segoe UI,Arial,sans-serif}.card{width:min(680px,90vw);padding:48px;border:1px solid #00cbe8;
                    border-radius:14px;background:rgba(3,25,42,.94);box-shadow:0 0 35px rgba(0,209,232,.2)}
                    h1{margin:0 0 14px;color:#10e4ea;font-size:32px}.status{display:inline-block;padding:7px 14px;
                    border:1px solid #00d0a8;border-radius:20px;color:#00efc4;background:#003d38;font-weight:700}
                    p{line-height:1.6;color:#9ccce8}.meta{margin-top:26px;padding-top:18px;border-top:1px solid #12617e}
                    .metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:22px}
                    .metric{padding:14px;border:1px solid #12617e;border-radius:8px;background:#062338}
                    .metric b{display:block;color:#10e4ea;font-size:22px}.metric span{font-size:12px;color:#9ccce8}
                  </style>
                </head>
                <body><main class="card"><span class="status">SERVER RUNNING</span>
                  <h1>DoS/DDoS Protection Lab Server</h1>
                  <p>The Java TCP/HTTP server on Machine 2 is running and accepting controlled LAN requests.</p>
                  <div class="metrics">
                    <div class="metric"><b>${MEDIAN}</b><span>Median sample</span></div>
                    <div class="metric"><b>${P95}</b><span>95th percentile</span></div>
                    <div class="metric"><b>${P99}</b><span>99th percentile</span></div>
                  </div>
                  <p>Lab data analysis #${REPORT}: ${SAMPLES} sample records processed for this request.</p>
                  <div class="meta"><strong>Service:</strong> ${SERVER_NAME}<br><strong>Protocol:</strong> TCP/IP + HTTP/1.1<br>
                  <strong>Scope:</strong> Localhost / private LAN lab only</div>
                </main></body></html>
                """.replace("${SERVER_NAME}", serverName)
                .replace("${MEDIAN}", Integer.toString(median))
                .replace("${P95}", Integer.toString(p95))
                .replace("${P99}", Integer.toString(p99))
                .replace("${REPORT}", Long.toString(reportNumber))
                .replace("${SAMPLES}", Integer.toString(ANALYTICS_SAMPLES));
        return new HttpResponse(200, "OK", "text/html; charset=UTF-8",
                html.getBytes(StandardCharsets.UTF_8));
    }

    private static int[] createTrafficSamples() {
        Random random = new Random(0x5EED_2026L);
        int[] samples = new int[ANALYTICS_SAMPLES];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = random.nextInt(1_000);
        }
        return samples;
    }

    private static HttpResponse text(int code, String reason, String message) {
        return new HttpResponse(code, reason, "text/plain; charset=UTF-8",
                message.getBytes(StandardCharsets.UTF_8));
    }

    public record HttpResponse(int statusCode, String reason, String contentType, byte[] body) {
    }
}
