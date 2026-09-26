# DoS/DDoS Protection Server - Machine 2

Đồ án LAB môn Mạng máy tính. Repository hiện có Dashboard JavaFX, Web Server TCP/HTTP thật và Traffic Monitor cơ bản cho Máy 2. Số liệu trên Dashboard được lấy từ request thật; project không tạo traffic tấn công và không nhắm tới hệ thống bên ngoài.

## Yêu cầu

- JDK 25
- Apache Maven 3.9+

## Chạy Dashboard

```powershell
mvn clean javafx:run
```

Dashboard khởi động ở trạng thái `SERVER STOPPED`. Nút Start Server mở listener HTTP và trang lab thật trong trình duyệt; Test Connection chỉ gọi `/health` ở chế độ nền; Stop Server đóng socket và worker pool. Các thống kê request, client IP, response time và biểu đồ được cập nhật từ traffic thật.

Khi bật Protection, server kiểm tra lưu lượng theo IP trước khi đưa kết nối vào worker pool. Request vượt `Rate Limit (req/sec)` nhận HTTP 429; vượt `Max connections` nhận HTTP 503. Nếu Auto Defense và Temporary Blocking cùng bật, các lần vượt ngưỡng liên tiếp sẽ chặn IP trong `Block Duration`. Tắt Protection bỏ qua các giới hạn này. Bộ đếm Limited/Dropped và biểu đồ Protection dùng số request bị từ chối thực tế, không phải giá trị ước lượng. Nếu trình duyệt và máy tạo tải dùng cùng một IP, cả hai sẽ chịu chung giới hạn theo IP.

Xem thiết kế tổng thể tại [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Phạm vi an toàn

Chỉ sử dụng trong localhost/private LAN và với server do người dùng kiểm soát. Project không có IP spoofing, amplification/reflection, botnet, malware hay cơ chế vượt firewall.
