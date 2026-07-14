# Nguyie - Week 6 Visualization Tasks

## Mục tiêu

Hoàn thiện route overlay, so sánh thuật toán và dynamic rerouting để kết quả BFS, Dijkstra và A* có thể quan sát trực tiếp trên bản đồ.

> File này không lặp lại phần routing domain, strategy, route result và các unit test thuật toán đã hoàn thành.

## Checklist công việc

### 1. Vẽ route trên bản đồ

- [ ] Truyền `steps` và danh sách `roadId` của route từ `App`/`AlgorithmPanel` sang `MapViewport`.
- [ ] Highlight route bằng màu và độ dày khác road bình thường.
- [ ] Vẽ mũi tên hoặc dấu chỉ hướng đi của route.
- [ ] Đánh dấu rõ start intersection và destination intersection.
- [ ] Cho phép xóa route đang hiển thị.
- [ ] Cho phép chọn start/destination bằng click trên intersection.
- [ ] Cập nhật overlay khi người dùng tìm route mới.
- [ ] Cập nhật overlay khi vehicle bị reroute.

### 2. Hoàn thiện API và contract thuật toán

- [ ] Thống nhất rõ `RoutePreference` và tên thuật toán mà frontend gửi lên.
- [ ] Không để frontend gửi `BFS`, `DIJKSTRA`, `ASTAR` vào trường chỉ nhận route preference.
- [ ] Tạo endpoint liệt kê lựa chọn routing nếu `useAlgorithms` tiếp tục gọi `/api/routing/algorithms`.
- [ ] Tạo API so sánh các thuật toán trên cùng start/destination, hoặc hoàn thiện logic gọi tuần tự ở frontend.
- [ ] Chuẩn hóa response so sánh gồm algorithm, distance, travel time, cost, visited nodes và computation time.
- [ ] Xử lý riêng trường hợp một thuật toán không tìm được đường.

### 3. Hoàn thiện algorithm statistics

- [ ] Implement `AlgorithmStatistics`.
- [ ] Ghi nhận computation time sau mỗi lần tìm route.
- [ ] Ghi nhận visited nodes, route cost và kết quả thành công/thất bại.
- [ ] Ghi nhận số lần reroute.
- [ ] Implement phần algorithm statistics trong `StatisticsService`.
- [ ] Cung cấp dữ liệu cho bảng so sánh thuật toán.
- [ ] Viết test cho việc thu thập và tổng hợp metrics.

### 4. Nối dynamic rerouting vào runtime

- [ ] Đăng ký `DynamicReroutingListener` với `TrafficEventBus` trong application wiring.
- [ ] Sửa listener để dùng danh sách vehicle hiện tại, không giữ bản sao cũ sau initialize/reset.
- [ ] Chỉ reroute vehicle thực sự bị ảnh hưởng bởi road của event.
- [ ] Cập nhật reroute count khi reroute thành công.
- [ ] Bảo đảm route mới được đưa vào simulation snapshot để frontend vẽ lại.
- [ ] Viết integration test: route chứa road bị block, event active, vehicle nhận route mới.

## Bàn giao cho thành viên khác

- Cung cấp contract route overlay cho An: `roadId`, `from`, `to`, `cost`.
- Thống nhất với Binh cách map `roadId` sang đường được vẽ.
- Phối hợp Hyo để event publish đúng lúc và vehicle list luôn là danh sách hiện tại.
- Cung cấp fields algorithm statistics cho `StatisticsPanel`.

## Tiêu chí hoàn thành

- [ ] Route được highlight đúng trên bản đồ.
- [ ] Start, destination và hướng đi nhìn thấy rõ.
- [ ] Có thể so sánh ít nhất BFS, Dijkstra và A* bằng dữ liệu thực.
- [ ] Event block road làm vehicle reroute trong runtime.
- [ ] Overlay đổi sang route mới sau reroute.
- [ ] Algorithm metrics được hiển thị và có test.

## Ghi chú theo dõi

| Ngày | Công việc | Trạng thái | Vướng mắc |
|---|---|---|---|
| | | Chưa làm | |

