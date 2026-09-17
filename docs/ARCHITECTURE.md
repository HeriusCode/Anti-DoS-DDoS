# Thiết kế Máy 2 - DoS/DDoS Protection Server

## 1. Kiến trúc tổng thể

Máy 2 dùng kiến trúc phân lớp kết hợp MVC:

- **View:** JavaFX Dashboard chỉ hiển thị trạng thái và phát sinh thao tác người dùng.
- **Controller/Application:** điều phối Start/Stop/Reset, chuyển snapshot từ service lên JavaFX thread.
- **Service:** Web Server, Traffic Monitor, rule-based Detector, Anti-DoS và Logger.
- **Model/Configuration:** trạng thái, event, thống kê và toàn bộ threshold dùng chung.

Mỗi HTTP request đi qua kiểm tra block, connection limit và rate limit trước khi được xử lý. TrafficMonitor ghi nhận mọi kết quả, DoSDetector đọc snapshot theo chu kỳ và AntiDoSManager phản ứng với DetectionEvent khi Auto Defense bật.

## 2. Sơ đồ module

```text
Machine 1 (HTTP client / attack simulator in controlled LAN)
                         |
                         v
+-------------------- Machine 2 -------------------------+
| web.WebServer -> protection gates -> HttpRequestHandler|
|       |                         |                       |
|       +-------> monitor.TrafficMonitor                  |
|                         |                               |
|                         v                               |
|                 detection.DoSDetector                   |
|                         | DetectionEvent                |
|                         v                               |
|                protection.AntiDoSManager                |
|                         |                               |
|       +-----------------+-------------------+           |
|       v                 v                   v           |
|  RateLimiter    ConnectionLimiter     ClientBlocker     |
|                                                         |
| All modules -> logging.SecurityLogger                   |
| Snapshots/events -> controller -> JavaFX Dashboard      |
+---------------------------------------------------------+
```

## 3. Class diagram dạng text

```text
Main --creates--> ApplicationController
Main --shows----> Dashboard

ApplicationController
  - config: ServerConfig
  - webServer: WebServer
  - monitor: TrafficMonitor
  - detector: DoSDetector
  - antiDoS: AntiDoSManager
  - logger: SecurityLogger
  + startServer(port)
  + stopServer()
  + resetStatistics()

WebServer 1 o-- * ClientConnection
ClientConnection --> HttpRequestHandler
ClientConnection --> TrafficMonitor
ClientConnection --> AntiDoSManager

TrafficMonitor 1 *-- 1 TrafficStatistics
TrafficMonitor --> ClientInfo
DoSDetector --> TrafficStatistics
DoSDetector --> DetectionRule
DoSDetector --> DetectionEvent
AntiDoSManager *-- RateLimiter
AntiDoSManager *-- ConnectionLimiter
AntiDoSManager *-- ClientBlocker

Dashboard *-- ServerPanel
Dashboard *-- MonitorPanel
Dashboard *-- SecurityPanel
Dashboard --> DashboardController
DashboardController --> observable UI model
```

## 4. Activity Diagram

```text
[Start]
   |
Validate port --invalid--> Show validation error --> [End]
   |
Create ServerSocket + worker pool
   |
Start monitor + detector schedulers
   |
[Wait for connection]
   |
Connection limit exceeded? --yes--> Reject + count + log --+
   | no                                                   |
Blocked IP? --yes--> 403/drop + count + log --------------+
   | no                                                   |
Rate allowed? --no--> 429 + limited count + log ----------+
   | yes                                                  |
Parse GET -> create response -> record latency/result -----+
   |
Detector evaluates latest window
   |
NORMAL / SUSPICIOUS / ATTACK_DETECTED
   |
ATTACK_DETECTED and Auto Defense? --yes--> activate defenses
   |
Publish snapshot to Dashboard
   |
Stop requested? --no--> [Wait for connection]
   | yes
Close socket -> shutdown executors -> log -> [End]
```

## 5. Sequence Diagram - HTTP request

```text
Machine1 -> WebServer: TCP connect + HTTP GET /
WebServer -> ConnectionLimiter: tryAcquire(ip)
ConnectionLimiter --> WebServer: accepted
WebServer -> ClientConnection: execute(socket)
ClientConnection -> ClientBlocker: isBlocked(ip)
ClientConnection -> RateLimiter: allow(ip)
RateLimiter --> ClientConnection: allowed
ClientConnection -> TrafficMonitor: requestReceived(ip)
ClientConnection -> HttpRequestHandler: handle(GET /)
HttpRequestHandler --> ClientConnection: 200 + HTML
ClientConnection --> Machine1: HTTP/1.1 200 OK
ClientConnection -> TrafficMonitor: requestCompleted(ip, latency, 200)
ClientConnection -> ConnectionLimiter: release(ip)
TrafficMonitor --> Dashboard: immutable snapshot
```

## 6. Sequence Diagram - phát hiện DoS

```text
Scheduler -> TrafficMonitor: snapshot(last 5 seconds)
TrafficMonitor --> DoSDetector: TrafficStatistics + per-IP rates
DoSDetector -> DetectionRule: evaluate metrics
DetectionRule --> DoSDetector: ATTACK_DETECTED + reasons
DoSDetector -> SecurityLogger: log ATTACK_DETECTED
DoSDetector -> AntiDoSManager: onDetection(event)
AntiDoSManager -> RateLimiter: activate/tighten
AntiDoSManager -> ConnectionLimiter: activate
AntiDoSManager -> ClientBlocker: block abusive IP temporarily
AntiDoSManager -> SecurityLogger: log defense actions
DoSDetector --> Dashboard: status/event
AntiDoSManager --> Dashboard: protection snapshot
```

## 7. Cấu trúc thư mục

```text
src/main/java/server/
|-- Main.java
|-- config/ServerConfig.java
|-- controller/DashboardController.java
|-- web/{WebServer,ClientConnection,HttpRequestHandler}.java
|-- monitor/{TrafficMonitor,TrafficStatistics}.java
|-- detection/{DoSDetector,DetectionRule,AttackStatus}.java
|-- protection/{AntiDoSManager,RateLimiter,ConnectionLimiter,ClientBlocker}.java
|-- model/{ClientInfo,ServerStatus,DetectionEvent,ProtectionStatus}.java
|-- logging/SecurityLogger.java
`-- view/{Dashboard,ServerPanel,MonitorPanel,SecurityPanel}.java

src/main/resources/server/view/dashboard.css
security.log
```

Giai đoạn hiện tại đã triển khai `Main`, `config`, `controller`, model, bốn class `view`, `TrafficMonitor` và bộ ba `WebServer`/`ClientConnection`/`HttpRequestHandler`. Detection và Protection backend chuyên biệt sẽ được bổ sung lần lượt, không tạo class rỗng chỉ để đủ tên.

## 8. Chức năng từng class

| Class | Trách nhiệm duy nhất |
|---|---|
| `Main` | Bootstrap JavaFX và đóng tài nguyên khi thoát |
| `ServerConfig` | Nguồn cấu hình tập trung, validate port/threshold |
| `WebServer` | Vòng lặp accept, quản lý lifecycle và worker pool |
| `ClientConnection` | Xử lý một TCP connection, áp dụng protection gates |
| `HttpRequestHandler` | Parse GET cơ bản và tạo HTTP response |
| `TrafficMonitor` | Ghi nhận event concurrent, rolling window, tạo snapshot |
| `TrafficStatistics` | Snapshot/thread-safe counters của traffic |
| `DetectionRule` | Đánh giá nhiều metric và trả mức/rationale |
| `DoSDetector` | Chạy rule định kỳ, quản lý chuyển trạng thái |
| `RateLimiter` | Token/fixed-window limit độc lập theo IP |
| `ConnectionLimiter` | Giới hạn active connection toàn cục và theo IP |
| `ClientBlocker` | Blacklist tạm thời với thời điểm hết hạn |
| `AntiDoSManager` | Điều phối protection và Auto Defense |
| `SecurityLogger` | Ghi log thread-safe đúng format |
| `ClientInfo` | Snapshot thống kê và trạng thái một IP |
| `ServerStatus` | Running, port, start time/uptime |
| `DetectionEvent` | Bằng chứng bất biến của một lần detection |
| `ProtectionStatus` | Trạng thái các cơ chế bảo vệ |
| `DashboardController` | Adapter giữa UI và backend, không chứa detection rule |
| `Dashboard` | Bố cục cửa sổ tổng thể |
| `ServerPanel` | Card Web Server và các thao tác kết nối |
| `MonitorPanel` | KPI, chart và bảng client |
| `SecurityPanel` | Detection/protection, blocked clients và log |

## 9. Luồng dữ liệu

```text
TCP/HTTP bytes
 -> request metadata (IP, method, path, timestamp)
 -> concurrent counters + rolling timestamp queues
 -> immutable statistics snapshot
 -> rule result + DetectionEvent
 -> protection commands/state
 -> HTTP 200/400/403/405/429/503
 -> UI snapshot and append-only security.log
```

UI không đọc trực tiếp các collection đang bị worker sửa. Controller lấy snapshot bất biến và cập nhật control qua `Platform.runLater`/JavaFX timeline.

## 10. Cơ chế Detection

Window mặc định 5 giây. Rule engine tính global RPS và per-client RPS, sau đó chấm các dấu hiệu:

- global RPS >= threshold: dấu hiệu mạnh;
- per-IP RPS >= per-client threshold: xác định nguồn nghi vấn;
- active connections >= max: dấu hiệu mạnh;
- average response >= response threshold: dấu hiệu suy giảm;
- tỉ lệ limited/dropped tăng: xác nhận áp lực kéo dài.

`NORMAL`: không có hoặc chỉ có nhiễu nhỏ. `SUSPICIOUS`: một rule cảnh báo hoặc metric đạt khoảng 70-99% threshold. `ATTACK_DETECTED`: một rule nghiêm trọng kèm rule xác nhận, hoặc global rate vượt xa threshold. Dùng hysteresis/cooldown để trạng thái không nhấp nháy khi traffic ở sát ngưỡng.

## 11. Cơ chế Anti-DoS

- `RateLimiter`: bucket riêng cho từng IP; chỉ request vượt quota bị trả 429.
- `ConnectionLimiter`: semaphore/counter giới hạn số connection đang xử lý; vượt ngưỡng trả 503 rồi đóng.
- `ClientBlocker`: IP vượt ngưỡng liên tiếp bị block đến `now + blockDuration`; tự hết hạn, không block vĩnh viễn.
- `AntiDoSManager`: ở `MONITORING` chỉ quan sát; `ACTIVE` áp dụng các gate. Auto Defense chuyển sang active khi nhận event tấn công. Khi bình thường trở lại, protection vẫn ở monitoring/armed thay vì tắt hoàn toàn.

## 12. Thiết kế Dashboard

- Dark cyber theme tương tự ảnh tham chiếu, accent cyan/teal; đỏ chỉ dùng cho attack/error.
- Header: tên hệ thống, navigation, server state, đồng hồ.
- Sidebar: nhóm chức năng; giai đoạn đầu một màn hình tổng quan duy nhất.
- Center: Web Server card, 8 KPI, line chart RPS, client traffic table, detection/protection cards.
- Right: trạng thái nhanh, security log, quick actions và thông tin endpoint.
- Responsive ở mức desktop LAB; kích thước khuyến nghị 1440x900 trở lên.
- Chart giữ tối đa 60 điểm để tránh tăng bộ nhớ vô hạn.

## 13. Giao tiếp Máy 1 - Máy 2

Máy 2 bind vào địa chỉ LAN/`0.0.0.0` với port đã cấu hình. Dashboard hiển thị IPv4 private và URL, ví dụ `http://192.168.1.20:8080/`. Máy 1 chỉ gửi HTTP GET tới URL đó. Không có agent cài trên Máy 1, không spoof IP; server lấy địa chỉ thật từ `Socket.getInetAddress()`.

## 14. Cách chạy project

1. Cài JDK 23 và Maven 3.9+.
2. Kiểm tra `java -version` và `mvn -version`.
3. Tại thư mục project chạy `mvn clean javafx:run`.
4. Chọn port hợp lệ rồi Start Server (sau khi module WebServer được tích hợp).
5. Mở URL hiển thị trên Dashboard từ trình duyệt/Máy 1 cùng LAN.

## 15. Cách test

- **Unit:** rolling window, reset counters, từng DetectionRule, rate limiter theo IP, block expiry, connection acquire/release.
- **Integration:** socket GET trả 200; method sai trả 405; malformed request trả 400; limit trả 429/503.
- **Concurrency:** nhiều worker cập nhật counter không mất số liệu; stop server khi đang có connection.
- **UI smoke:** Start/Stop/Reset, threshold validation, chart tối đa 60 điểm, table/log cập nhật trên JavaFX thread.
- **LAN demo:** một client tốc độ thấp rồi tăng tải có giới hạn/thời lượng; quan sát NORMAL -> SUSPICIOUS -> ATTACK_DETECTED -> NORMAL.

## 16. Thứ tự triển khai

1. Dashboard + UI model/controller (giai đoạn hiện tại).
2. TrafficStatistics + TrafficMonitor.
3. SecurityLogger.
4. RateLimiter + ConnectionLimiter + ClientBlocker + AntiDoSManager.
5. DetectionRule + DoSDetector.
6. WebServer + ClientConnection + HttpRequestHandler.
7. Tích hợp, unit/integration test và LAN demo.

Thứ tự này cho phép kiểm tra từng module độc lập và thay dữ liệu demo trên Dashboard bằng snapshot thật mà không viết lại giao diện.
