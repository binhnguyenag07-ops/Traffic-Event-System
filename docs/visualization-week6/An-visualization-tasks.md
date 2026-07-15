# An - Week 6 Visualization Tasks

## Mục tiêu

Tích hợp các component đã có thành một visualization dashboard end-to-end, hoàn thiện simulation snapshot và statistics để toàn bộ giao diện dùng chung một trạng thái nhất quán.

> Frontend scaffold, API client và các component cơ bản đã tồn tại. Checklist dưới đây chỉ tập trung vào phần chưa tích hợp hoặc chưa có backend hỗ trợ.

## Checklist công việc

### 1. Tích hợp dashboard thật vào `App.jsx`

- [ ] Refactor `App.jsx` để sử dụng `MapViewport`, `ControlPanel`, `AlgorithmPanel`, `EventPanel`, `VehiclePanel` và `StatisticsPanel`.
- [ ] Loại bỏ hoặc hợp nhất các panel trùng lặp đang viết trực tiếp trong `App.jsx`.
- [ ] Đặt bản đồ làm khu vực chính của dashboard.
- [ ] Tạo state dùng chung cho network, route, vehicles, events, tick và selected object.
- [ ] Truyền route của Nguyie, road của Binh và vehicle/event của Hyo vào cùng `MapViewport`.
- [ ] Không hard-code intersections `I1-I6` và default vehicles trong component chính.
- [ ] Lấy toàn bộ dữ liệu lựa chọn từ API/scenario hiện tại.

### 2. Hoàn thiện simulation snapshot

- [ ] Implement `SimulationSnapshot.java`.
- [ ] Implement `SimulationSnapshotDto.java`.
- [ ] Thay `Map<String, Object>` trong `SimulationController` bằng DTO rõ ràng.
- [ ] Bổ sung `status` vào snapshot.
- [ ] Bổ sung dynamic road state vào snapshot hoặc bảo đảm road API được refresh cùng tick.
- [ ] Bổ sung route/progress cần thiết của từng vehicle.
- [ ] Bổ sung statistics vào snapshot hoặc cung cấp API statistics riêng.
- [ ] Viết test contract cho snapshot.

### 3. Hoàn thiện statistics frontend và API wiring

- [ ] Phối hợp Nguyie implement `TrafficStatistics`, `StatisticsServiceImpl` và `StatisticsController`.
- [ ] Đăng ký `StatisticsController` trong `ApiRouteRegistry` và `App`.
- [ ] Implement `frontend/src/hooks/useStatistics.js` đang rỗng.
- [ ] Kết nối `StatisticsPanel` với API thật.
- [ ] Hiển thị total/moving/arrived/blocked vehicles.
- [ ] Hiển thị average congestion/density và số road theo status.
- [ ] Hiển thị active event count và algorithm metrics.
- [ ] Làm statistics refresh theo tick hoặc polling interval.

### 4. Đồng bộ API frontend với backend

- [ ] Sửa import `../api/trafficApi` thành đúng tên `trafficAPI.js` trong các hook.
- [ ] Kiểm tra từng method trong `trafficAPI.js`; bỏ khỏi UI hoặc implement backend cho endpoint chưa tồn tại.
- [ ] Thống nhất request/response của routing preference và algorithm selection.
- [ ] Đọc base URL từ biến môi trường thay vì chỉ hard-code port 7070.
- [ ] Chuẩn hóa loading, empty state và error state.
- [ ] Sau mỗi tick, cập nhật đồng bộ snapshot, road state và statistics.
- [ ] Dừng interval/polling khi pause, reset hoặc component unmount.

### 5. Hoàn thiện trải nghiệm và kiểm thử

- [ ] Tạo layout desktop rõ ràng: map, controls, object details và statistics.
- [ ] Bổ sung responsive tối thiểu cho màn hình laptop nhỏ.
- [ ] Xử lý text/icon đang lỗi encoding trong giao diện.
- [ ] Thêm frontend test setup và script test.
- [ ] Test render map từ API data.
- [ ] Test route highlight.
- [ ] Test vehicle marker cập nhật theo tick.
- [ ] Test event làm road đổi màu.
- [ ] Test statistics refresh.
- [ ] Viết một integration/E2E scenario: initialize, start, tick, event, reroute, resolve và reset.
- [ ] Cập nhật README với lệnh chạy backend và frontend.

## Bàn giao và tích hợp

- Nhận road/intersection contract và scenario từ Binh.
- Nhận route overlay và algorithm metrics từ Nguyie.
- Nhận vehicle/event state và marker data từ Hyo.
- Chốt props/state contract của `MapViewport` trước khi merge các nhánh.

## Tiêu chí hoàn thành

- [ ] `App.jsx` thực sự render bản đồ và các component visualization.
- [ ] Road, route, vehicle và event xuất hiện đồng thời trên một bản đồ.
- [ ] Start, pause, resume, reset và step tick cập nhật giao diện chính xác.
- [ ] Snapshot có contract ổn định và test.
- [ ] Statistics hiển thị dữ liệu thật.
- [ ] Frontend build và test thành công.
- [ ] Luồng demo end-to-end chạy mà không cần sửa dữ liệu thủ công trong code.

## Ghi chú theo dõi

| Ngày | Công việc | Trạng thái | Vướng mắc |
|---|---|---|---|
| | | Chưa làm | |
