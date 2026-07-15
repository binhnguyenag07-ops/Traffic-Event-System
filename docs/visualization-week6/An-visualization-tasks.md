# An - Week 6 Visualization Tasks

## Mục tiêu

Tích hợp các component đã có thành một visualization dashboard end-to-end, hoàn thiện simulation snapshot và statistics để toàn bộ giao diện dùng chung một trạng thái nhất quán.

> Frontend scaffold, API client và các component cơ bản đã tồn tại. Checklist dưới đây chỉ tập trung vào phần chưa tích hợp hoặc chưa có backend hỗ trợ.

## Checklist công việc

### 1. Tích hợp dashboard thật vào `App.jsx`

- [x] Refactor `App.jsx` để sử dụng `MapViewport`, `ControlPanel`, `AlgorithmPanel`, `EventPanel`, `VehiclePanel` và `StatisticsPanel`.
- [x] Loại bỏ hoặc hợp nhất các panel trùng lặp đang viết trực tiếp trong `App.jsx`.
- [x] Đặt bản đồ làm khu vực chính của dashboard.
- [x] Tạo state dùng chung cho network, route, vehicles, events, tick và selected object.
- [x] Truyền route của Nguyie, road của Binh và vehicle/event của Hyo vào cùng `MapViewport`.
- [x] Không hard-code intersections `I1-I6` và default vehicles trong component chính.
- [x] Lấy toàn bộ dữ liệu lựa chọn từ API/scenario hiện tại.

### 2. Hoàn thiện simulation snapshot

- [x] Implement `SimulationSnapshot.java`.
- [x] Implement `SimulationSnapshotDto.java`.
- [x] Thay `Map<String, Object>` trong `SimulationController` bằng DTO rõ ràng.
- [x] Bổ sung `status` vào snapshot.
- [x] Bổ sung dynamic road state vào snapshot hoặc bảo đảm road API được refresh cùng tick. *(snapshot chứa `roads` — `RoadStateDto`, cùng shape với `/api/network/roads`)*
- [x] Bổ sung route/progress cần thiết của từng vehicle. *(VehicleDto có `routeSteps`, `nextRouteStepIndex`, `occupiedRoadId`, `travelHistory`)*
- [x] Bổ sung statistics vào snapshot hoặc cung cấp API statistics riêng. *(cả hai: snapshot chứa `statistics`, API `/api/statistics` vẫn hoạt động)*
- [x] Viết test contract cho snapshot. *(`SimulationSnapshotContractTest.java`)*

### 3. Hoàn thiện statistics frontend và API wiring

- [x] Phối hợp Nguyie implement `TrafficStatistics`, `StatisticsServiceImpl` và `StatisticsController`. *(đã có từ tuần trước)*
- [x] Đăng ký `StatisticsController` trong `ApiRouteRegistry` và `App`.
- [x] Implement `frontend/src/hooks/useStatistics.js` đang rỗng.
- [x] Kết nối `StatisticsPanel` với API thật.
- [x] Hiển thị total/moving/arrived/blocked vehicles.
- [x] Hiển thị average congestion/density và số road theo status.
- [x] Hiển thị active event count và algorithm metrics.
- [x] Làm statistics refresh theo tick hoặc polling interval. *(refresh theo tick — không cần polling riêng)*

### 4. Đồng bộ API frontend với backend

- [x] Sửa import `../api/trafficApi` thành đúng tên `trafficAPI.js` trong các hook.
- [x] Kiểm tra từng method trong `trafficAPI.js`; bỏ khỏi UI hoặc implement backend cho endpoint chưa tồn tại. *(UI chỉ gọi endpoint có thật; các method chưa có backend vẫn ở client như contract dự kiến nhưng không được UI gọi)*
- [x] Thống nhất request/response của routing preference và algorithm selection. *(UI gửi `preference`, hiển thị `algorithm` từ response; danh sách algorithm lấy từ `/api/statistics`)*
- [x] Đọc base URL từ biến môi trường thay vì chỉ hard-code port 7070. *(`VITE_API_BASE_URL`)*
- [x] Chuẩn hóa loading, empty state và error state.
- [x] Sau mỗi tick, cập nhật đồng bộ snapshot, road state và statistics. *(một payload snapshot duy nhất)*
- [x] Dừng interval/polling khi pause, reset hoặc component unmount.

### 5. Hoàn thiện trải nghiệm và kiểm thử

- [x] Tạo layout desktop rõ ràng: map, controls, object details và statistics.
- [x] Bổ sung responsive tối thiểu cho màn hình laptop nhỏ. *(breakpoint 1100px/600px)*
- [x] Xử lý text/icon đang lỗi encoding trong giao diện. *(bỏ glyph ▶/⏸/↺, dùng text button)*
- [x] Thêm frontend test setup và script test. *(Vitest + Testing Library, `npm test`)*
- [x] Test render map từ API data.
- [x] Test route highlight.
- [x] Test vehicle marker cập nhật theo tick.
- [x] Test event làm road đổi màu.
- [x] Test statistics refresh.
- [x] Viết một integration/E2E scenario: initialize, start, tick, event, reroute, resolve và reset. *(`App.test.jsx`)*
- [x] Cập nhật README với lệnh chạy backend và frontend.

## Bàn giao và tích hợp

- Nhận road/intersection contract và scenario từ Binh.
- Nhận route overlay và algorithm metrics từ Nguyie.
- Nhận vehicle/event state và marker data từ Hyo.
- Chốt props/state contract của `MapViewport` trước khi merge các nhánh. *(contract ghi trong JSDoc đầu file `MapViewport.jsx`)*

## Tiêu chí hoàn thành

- [x] `App.jsx` thực sự render bản đồ và các component visualization.
- [x] Road, route, vehicle và event xuất hiện đồng thời trên một bản đồ.
- [x] Start, pause, resume, reset và step tick cập nhật giao diện chính xác.
- [x] Snapshot có contract ổn định và test.
- [x] Statistics hiển thị dữ liệu thật.
- [x] Frontend build và test thành công.
- [x] Luồng demo end-to-end chạy mà không cần sửa dữ liệu thủ công trong code.

## Ghi chú theo dõi

| Ngày | Công việc | Trạng thái | Vướng mắc |
|---|---|---|---|
| 2026-07-15 | Snapshot mở rộng (roads, route/progress, statistics) + contract test | Xong | |
| 2026-07-15 | Dashboard: MapViewport SVG, ControlPanel, VehiclePanel, EventPanel, AlgorithmPanel, StatisticsPanel + shared state trong App.jsx | Xong | |
| 2026-07-15 | Hooks: useRoadNetwork/useSimulation/useStatistics/useAlgorithms viết lại theo API thật; env base URL | Xong | |
| 2026-07-15 | Frontend tests: 35 test (map, route highlight, vehicle tick, event color, stats refresh, E2E scenario) | Xong | |
