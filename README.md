# DoS/DDoS Protection Server - Machine 2

Đồ án LAB môn Mạng máy tính. Repository hiện có Dashboard JavaFX, Web Server TCP/HTTP thật và Traffic Monitor cơ bản cho Máy 2. Số liệu trên Dashboard được lấy từ request thật; project không tạo traffic tấn công và không nhắm tới hệ thống bên ngoài.

## Yêu cầu

- JDK 23
- Apache Maven 3.9+

## Chạy Dashboard

```powershell
mvn clean javafx:run
```

Dashboard khởi động ở trạng thái `SERVER STOPPED`. Nút Start Server mở listener HTTP và trang lab thật trong trình duyệt; Test Connection chỉ gọi `/health` ở chế độ nền; Stop Server đóng socket và worker pool. Các thống kê request, client IP, response time và biểu đồ được cập nhật từ traffic thật.

Xem thiết kế tổng thể tại [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Phạm vi an toàn

Chỉ sử dụng trong localhost/private LAN và với server do người dùng kiểm soát. Project không có IP spoofing, amplification/reflection, botnet, malware hay cơ chế vượt firewall.
