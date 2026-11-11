# DoS/DDoS Protection Server - Machine 2

Đồ án LAB môn Mạng máy tính. Repository hiện có Dashboard JavaFX, Web Server TCP/HTTP thật và Traffic Monitor cơ bản cho Máy 2. Số liệu trên Dashboard được lấy từ request thật; project không tạo traffic tấn công và không nhắm tới hệ thống bên ngoài.

## Yêu cầu

- JDK 25
- Apache Maven 3.9+

## Chạy Dashboard

```powershell
mvn clean javafx:run
```

## Đăng ký và đăng nhập bằng PostgreSQL

Ứng dụng mở màn hình tài khoản trước Dashboard. Nhập tên đăng nhập, email,
mật khẩu (8–128 ký tự) và xác nhận mật khẩu. Sau khi đăng ký thành công,
đăng nhập bằng tên đăng nhập hoặc email để vào Dashboard.

Chạy PostgreSQL trước khi mở ứng dụng. Cấu hình kết nối trong
`config/database.properties` (file cục bộ được Git bỏ qua); xem mẫu ở
`config/database.example.properties`. Mặc định kết nối `localhost:5432`,
database `dos_protection_lab`, user `postgres`. Có thể ghi đè qua các biến
`LAB_DB_HOST`, `LAB_DB_PORT`, `LAB_DB_NAME`, `LAB_DB_USER`, `LAB_DB_PASSWORD`.
Nếu database chưa tồn tại, ứng dụng tạo database riêng và bảng `lab_accounts`;
user PostgreSQL cần có quyền tạo database trong lần đầu.

Mật khẩu tài khoản ứng dụng được băm PBKDF2-HMAC-SHA256 với salt riêng;
không lưu mật khẩu gốc. Tên đăng nhập và email là duy nhất (không phân biệt
hoa/thường). Các truy vấn tài khoản dùng PreparedStatement. Công việc JDBC
và băm mật khẩu chạy trên luồng nền để giao diện không bị treo.
Google/GitHub hiện là nút mẫu bị vô hiệu hóa, cần cấu hình OAuth riêng.
Tài khoản này dùng để vào Dashboard JavaFX; endpoint HTTP LAB không yêu cầu
đăng nhập để vẫn tương thích với Attack Simulator.

Dashboard khởi động ở trạng thái `SERVER STOPPED`. Nút Start Server mở listener HTTP và trang lab thật trong trình duyệt; Test Connection chỉ gọi `/health` ở chế độ nền; Stop Server đóng socket và worker pool. Các thống kê request, client IP, response time và biểu đồ được cập nhật từ traffic thật.

Khi bật Protection, server kiểm tra giới hạn kết nối lúc nhận socket và giới hạn tốc độ sau khi đọc HTTP headers. Request vượt `Rate Limit (req/sec)` nhận HTTP 429; vượt `Max connections` nhận HTTP 503. Connection Limit chỉnh được từ 1 đến 1.024 trong Settings hoặc trang Protection; số kết nối bị từ chối được hiển thị trên thẻ Connection Limiting. Auto Defense hiện là cơ chế tự leo thang sang Temporary Blocking: khi cả hai bật, sau ba lần vượt rate limit, IP bị chặn trong `Block Duration`. Tắt một trong hai vẫn giữ rate limiting, nhưng không tạo block mới. Tắt Protection bỏ qua các giới hạn phòng thủ. Bộ đếm Limited/Dropped và biểu đồ Protection dùng số request bị từ chối thực tế, không phải giá trị ước lượng. Trên cùng máy server, trình duyệt và simulator dùng quota riêng; các client từ máy khác chia sẻ quota theo IP nguồn.

Trang `/` trả về HTML nhẹ để request hợp lệ vẫn phản hồi nhanh trong lúc kiểm thử. Với client từ máy khác trong LAN, rate limiting được thực hiện ngay sau khi accept socket và trước hàng đợi HTTP worker; request dư nhận `429` hoặc bị đóng nếu hàng đợi từ chối đã đầy. `/health` là endpoint kiểm tra kết nối. Mặc định Rate Limit là 20 req/sec; có thể chỉnh trong Settings để phù hợp máy lab. Server chỉ nhận kết nối từ localhost hoặc mạng riêng/link-local.

Xem thiết kế tổng thể tại [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Phạm vi an toàn

Chỉ sử dụng trong localhost/private LAN và với server do người dùng kiểm soát. Project không có IP spoofing, amplification/reflection, botnet, malware hay cơ chế vượt firewall.
