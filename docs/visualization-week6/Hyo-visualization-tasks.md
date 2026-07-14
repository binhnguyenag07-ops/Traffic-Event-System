# Hyo - Week 6 Visualization Tasks

## Mục tiêu

Đồng bộ vehicle và traffic event với simulation, sau đó hiển thị chúng trực tiếp trên bản đồ theo từng tick.

> File này không lặp lại các vehicle/event domain class, factory, event API và unit test domain đã có.

## Checklist công việc

### 1. Hợp nhất nguồn dữ liệu vehicle

- [ ] Loại bỏ tình trạng `VehicleServiceImpl` và `TrafficSimulator` giữ hai danh sách vehicle độc lập.
- [ ] Vehicle tạo bằng `POST /api/vehicles` phải xuất hiện trong simulation snapshot.
- [ ] Vehicle được tạo khi initialize simulation phải xuất hiện trong `GET /api/vehicles`.
- [ ] Reset simulation phải cập nhật cùng một danh sách vehicle cho cả service và simulator.
- [ ] Bảo đảm vehicle response có route hiện tại, next step và travel history cần cho visualization.
- [ ] Viết test xác nhận vehicle API và simulation snapshot luôn đồng bộ.

### 2. Hợp nhất nguồn dữ liệu traffic event

- [ ] Loại bỏ tình trạng `TrafficEventServiceImpl` và `TrafficSimulator` giữ hai danh sách event không đồng bộ.
- [ ] Event từ simulation initialize phải xuất hiện trong `GET /api/events`.
- [ ] Event tạo qua API phải xuất hiện trong snapshot.
- [ ] Event bị xóa qua API phải được xóa khỏi simulator.
- [ ] Event apply/resolve trong `TickProcessor` phải publish qua `TrafficEventBus`.
- [ ] Bảo đảm event resolve phục hồi đúng road condition trước đó.
- [ ] Viết test cho create, initialize, delete, apply và resolve trong luồng runtime.

### 3. Hiển thị vehicle trên bản đồ

- [ ] Truyền danh sách vehicle trong snapshot vào `MapViewport`.
- [ ] Vẽ marker riêng cho `CAR`, `BUS`, `EMERGENCY`.
- [ ] Cập nhật marker sau mỗi tick.
- [ ] Hiển thị khác nhau cho `WAITING`, `MOVING`, `ARRIVED`, `BLOCKED`.
- [ ] Click marker để xem ID, type, current location, destination, status, route và travel history.
- [ ] Xử lý nhiều vehicle cùng đứng tại một intersection để marker không chồng hoàn toàn.
- [ ] Hiển thị route của vehicle đang được chọn nếu có.

### 4. Hiển thị traffic event trên bản đồ

- [ ] Vẽ icon event trên road bị ảnh hưởng.
- [ ] Dùng icon/màu riêng cho congestion, accident, closure và traffic-light delay.
- [ ] Chỉ làm nổi bật event đang `ACTIVE`; phân biệt `SCHEDULED` và `RESOLVED`.
- [ ] Hiển thị start tick, duration và số tick còn lại.
- [ ] Cập nhật màu road ngay khi event apply.
- [ ] Phục hồi màu road khi event resolve.
- [ ] Đồng bộ danh sách/event marker sau thao tác create, activate, resolve và delete.

## Bàn giao cho thành viên khác

- Cung cấp vehicle/event DTO contract cuối cùng cho An.
- Thống nhất với Binh cách event thay đổi road color và condition.
- Bảo đảm `TrafficEventBus` cung cấp event cho dynamic rerouting của Nguyie.
- Cung cấp dữ liệu đếm vehicle/event cho statistics.

## Tiêu chí hoàn thành

- [ ] Vehicle API và simulator dùng cùng một nguồn dữ liệu.
- [ ] Event API và simulator dùng cùng một nguồn dữ liệu.
- [ ] Marker vehicle di chuyển đúng sau mỗi tick.
- [ ] Event marker xuất hiện trên đúng road.
- [ ] Road đổi trạng thái khi event active và phục hồi khi resolved.
- [ ] Không còn vehicle/event đã xóa xuất hiện trong snapshot.
- [ ] Integration test vehicle/event runtime pass.

## Ghi chú theo dõi

| Ngày | Công việc | Trạng thái | Vướng mắc |
|---|---|---|---|
| | | Chưa làm | |

