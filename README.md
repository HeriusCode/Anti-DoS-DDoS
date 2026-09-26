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

Khi bật Protection, server kiểm tra giới hạn kết nối lúc nhận socket và giới hạn tốc độ sau khi đọc HTTP headers. Request vượt `Rate Limit (req/sec)` nhận HTTP 429; vượt `Max connections` nhận HTTP 503. Nếu Auto Defense và Temporary Blocking cùng bật, các lần vượt ngưỡng liên tiếp sẽ chặn IP trong `Block Duration`. Tắt Protection bỏ qua các giới hạn này. Bộ đếm Limited/Dropped và biểu đồ Protection dùng số request bị từ chối thực tế, không phải giá trị ước lượng. Trên cùng máy server, trình duyệt và simulator dùng quota riêng; các client từ máy khác chia sẻ quota theo IP nguồn.

Trang `/` tính lại báo cáo từ 600.000 mẫu dữ liệu lab trên mỗi request bằng một worker phân tích, trong khi số luồng HTTP cũng bị giới hạn. Không có `sleep` hay độ trễ cố định: khi tắt phòng thủ, lưu lượng vượt năng lực xử lý sẽ xếp hàng, khiến trang chậm hoặc có request bị từ chối; khi bật phòng thủ, request bị chặn không chạy tác vụ phân tích. `/health` vẫn là endpoint nhẹ để kiểm tra kết nối. Mặc định Rate Limit là 20 req/sec; có thể chỉnh trong Settings để phù hợp máy lab. Server chỉ nhận kết nối từ localhost hoặc mạng riêng/link-local.

Xem thiết kế tổng thể tại [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Phạm vi an toàn

Chỉ sử dụng trong localhost/private LAN và với server do người dùng kiểm soát. Project không có IP spoofing, amplification/reflection, botnet, malware hay cơ chế vượt firewall.
