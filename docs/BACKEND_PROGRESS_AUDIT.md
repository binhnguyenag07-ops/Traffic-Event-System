# Báo Cáo Audit Tiến Độ Backend

Ngày: 2026-06-25

## Mục Đích

Tài liệu này kiểm chứng nhận định của Student C:

> Student A, B, C đã vô tình làm nhanh tiến độ nên gần như đã xong backend của các tuần 4, 5, 6, 7, trong khi frontend mới ở mức tuần 3.

Phạm vi audit:

- Dùng ảnh roadmap của nhóm để so tiến độ theo tuần.
- Dùng file `OOP - Group Project.pdf` để chấm yêu cầu chức năng và description của project.
- Dùng code và test hiện tại trong repo làm bằng chứng triển khai.

Lưu ý: roadmap trong PDF không được dùng để kết luận tiến độ theo thời gian. Roadmap dùng để so tiến độ là roadmap trong ảnh của nhóm.

## Kết Luận Nhanh

Student C đúng một phần. Backend của A, B, C có nhiều module lõi đã đi trước frontend: road density, blocked/congested road states, BFS/Dijkstra/A*, congestion-aware routing, vehicle domain movement, traffic event domain classes, observer/factory/strategy patterns, và nhiều unit tests.

Nhưng nhận định "backend tuần 4-7 gần như đã xong" là quá lạc quan nếu xét ở cấp ứng dụng hoàn chỉnh. Nhiều class quan trọng ở tầng service, simulation, API, DTO, và statistics hiện vẫn rỗng hoặc chưa được nối lại với nhau.

Kết luận nên dùng cho team:

> Backend có nền tảng tốt và một số phần đã đi trước, nhưng chưa thể coi là xong basic project. Team nên đóng gói một demo end-to-end tối thiểu trước, sau đó mới đẩy nhanh frontend và nâng cấp project cho "xịn".

## Kết Quả Kiểm Thử

Đã chạy full Maven test suite:

```text
mvn test
tests=201 failures=0 errors=0 skipped=0
```

Đây là điểm rất tốt. Nó chứng minh các phần domain, persistence, routing đang được test hiện tại chạy ổn định.

Tuy nhiên, test pass không có nghĩa là backend đã hoàn chỉnh, vì nhiều module chưa có implementation nên cũng chưa có test thật sự.

## Đối Chiếu Theo Roadmap Của Nhóm

### Student A

| Tuần | Hạng mục trong roadmap | Trạng thái hiện tại | Bằng chứng |
|---|---|---|---|
| 4 | Road density | Đạt | `RoadNetwork.recordVehicleEnteringRoad`, `recordVehicleLeavingRoad`, `updateVehicleDensity`, `getAverageVehicleDensity`, `findRoadsAtOrAboveDensity` |
| 5 | Blocked/congested states | Đạt | `RoadStatus`, `RoadCondition`, `Road.isAvailable`, routing bỏ qua road bị blocked/closed |
| 6 | Scenario maps | Một phần | Có `density_demo.json` và `small_city.json`; `congestion_demo.json` và `accident_demo.json` đang rỗng |
| 7 | Unit tests | Khá tốt cho phần sở hữu | Network, persistence, density, routing liên quan đều có test pass |

Nhận xét: Student A thật sự đã đi trước ở phần nền tảng backend. Phần cần bổ sung là scenario maps cho event/congestion/accident để demo simulation.

### Student B

| Tuần | Hạng mục trong roadmap | Trạng thái hiện tại | Bằng chứng |
|---|---|---|---|
| 4 | Dynamic routing | Một phần | Vehicle có thể gọi recompute route, nhưng simulation service chưa tự động trigger rerouting |
| 5 | Avoid bad roads | Đạt ở cấp algorithm | `RouteBuilder.isRoadUsable` lọc road không khả dụng; BFS, Dijkstra, A* đều dùng hàm này |
| 6 | Algorithm metrics | Một phần | `RouteResult` có computation time và visited nodes; `AlgorithmStatistics` đang rỗng |
| 7 | Algorithm comparison table | Chưa đạt | Chưa có statistics module hoặc bảng/report so sánh algorithm |

Nhận xét: Student B có phần algorithm mạnh và test tốt. Tuy nhiên các mục metrics và comparison mới dừng ở mức dữ liệu thấp, chưa thành chức năng ứng dụng.

### Student C

| Tuần | Hạng mục trong roadmap | Trạng thái hiện tại | Bằng chứng |
|---|---|---|---|
| 4 | Vehicle movement | Một phần | `Vehicle.moveOneStep` có và được test; `VehicleService`/`VehicleServiceImpl` chưa implement |
| 5 | Accident/congestion/closure | Đạt ở cấp domain, chưa đạt ở cấp app | Có `AccidentEvent`, `CongestionEvent`, `RoadClosureEvent`, `TrafficLightDelayEvent`; service và API cho event đang rỗng |
| 6 | Multi-vehicle demo | Chưa đạt ở backend | Chưa có simulation engine hoặc vehicle service quản lý nhiều xe |
| 7 | Event tests | Đạt ở unit level | `TrafficEventTest` test apply/resolve của event |

Nhận xét: Student C đã làm được các class domain quan trọng, nhưng feature chưa thành workflow backend chạy được. Event chưa được trigger theo tick, vehicle chưa tự động nhận thông báo và reroute.

## Các Khoảng Trống Backend

Những file sau đang rỗng hoặc gần như chưa implement:

```text
src/main/java/team/traffic/api/controller/SimulationController.java
src/main/java/team/traffic/api/controller/StatisticsController.java
src/main/java/team/traffic/api/controller/TrafficEventController.java
src/main/java/team/traffic/api/controller/VehicleController.java
src/main/java/team/traffic/api/dto/SimulationSnapshotDto.java
src/main/java/team/traffic/api/dto/TrafficEventDto.java
src/main/java/team/traffic/api/dto/VehicleDto.java
src/main/java/team/traffic/domain/statistics/AlgorithmStatistics.java
src/main/java/team/traffic/domain/statistics/TrafficStatistics.java
src/main/java/team/traffic/service/SimulationService.java
src/main/java/team/traffic/service/StatisticsService.java
src/main/java/team/traffic/service/TrafficEventService.java
src/main/java/team/traffic/service/impl/SimulationServiceImpl.java
src/main/java/team/traffic/service/impl/StatisticsServiceImpl.java
src/main/java/team/traffic/service/impl/TrafficEventServiceImpl.java
src/main/java/team/traffic/service/impl/VehicleServiceImpl.java
src/main/java/team/traffic/simulation/SimulationClock.java
src/main/java/team/traffic/simulation/SimulationCommand.java
src/main/java/team/traffic/simulation/SimulationSnapshot.java
src/main/java/team/traffic/simulation/SimulationStatus.java
src/main/java/team/traffic/simulation/TickProcessor.java
src/main/java/team/traffic/simulation/TrafficSimulator.java
```

Riêng `VehicleService.java` hiện mới là class rỗng. Nên cân nhắc chuyển nó thành interface để đồng bộ với pattern hiện có của `RoadNetworkService` và `RoutingService`.

## Vấn Đề Chức Năng Đang Tồn Tại

### 1. Simulation engine chưa có

Theo PDF, ứng dụng cuối cùng phải có simulation engine để update state, advance time, process vehicle movement, handle traffic events, và collect statistics. Hiện các class trong package `simulation` mới là placeholder.

Tác động: chưa thể demo một traffic simulator thật sự.

### 2. Traffic event chưa nối vào runtime

Event domain có thể `apply` và `resolve` road condition, nhưng chưa có `TrafficEventService` hoặc `TickProcessor` để kích hoạt event đúng tick.

Tác động: accident/congestion/closure đã tồn tại như object, nhưng chưa thành kịch bản chạy trong simulation.

### 3. Dynamic rerouting chưa tự động

`Vehicle.recomputeRoute(...)` đã có, nhưng không có simulation/service nào tự động gọi nó khi đường bị blocked, congested, closed, hoặc accident.

Tác động: có khả năng recompute route, nhưng chưa thể gọi là dynamic rerouting trong ứng dụng.

### 4. Multi-vehicle chưa được quản lý bởi backend

Vehicle domain có, nhưng `VehicleServiceImpl` rỗng. Chưa có registry quản lý vehicles, API tạo vehicle, API lấy danh sách vehicle, hoặc simulation loop di chuyển nhiều vehicle.

Tác động: Week 6 "multi-vehicle demo" chưa đạt.

### 5. Statistics chưa có

`TrafficStatistics` và `AlgorithmStatistics` đang rỗng. PDF yêu cầu các metric như average travel time, total vehicles, congestion metrics, algorithm computation time, route recalculation count, blocked roads, và bảng/chart so sánh algorithm.

Tác động: statistics panel và experiment summary chưa có backend thật để chạy.

### 6. Frontend và backend đang lệch API contract

Frontend API client đang gọi nhiều endpoint như:

```text
/api/vehicles
/api/events
/api/simulation/start
/api/simulation/tick
/api/statistics
/api/network/roads
/api/network/generate
/api/routing/algorithms
```

Trong khi backend hiện mới đang register các endpoint:

```text
/api/health
/api/network
/api/network/intersections
/api/network/intersections/{id}
/api/routes
```

Tác động: nhiều component frontend không thể hoạt động với backend hiện tại.

## Điểm Mạnh Hiện Có

Project hiện có nhiều nền tảng tốt:

- Road graph, intersections, roads, và graph operations đã có.
- JSON/CSV map loading đã có và được test.
- Synthetic map generator đã có.
- Road density và capacity behavior đã có.
- Blocked, closed, congested, open road states đã có.
- BFS, Dijkstra, A* đã có và được test.
- Congestion-aware route cost đã có.
- Vehicle movement và route recomputation methods đã có.
- Traffic event domain classes đã có.
- Observer pattern có nền tảng qua `TrafficEventBus`.
- Factory pattern có nền tảng qua `VehicleFactory`.
- Strategy pattern có nền tảng qua routing strategies.
- Full test suite hiện tại pass.

Điều này có nghĩa là team không phải làm lại từ đầu. Việc còn lại chủ yếu là nối các module thành ứng dụng chạy được.

## Kế Hoạch Đề Xuất

### Phase 1: Hoàn thiện backend cơ bản chạy được

Mục tiêu: đảm bảo app có thể demo các yêu cầu chính trong PDF ở mức basic.

Việc cần làm:

1. Implement `SimulationStatus`, `SimulationClock`, `SimulationSnapshot`, `TrafficSimulator`, và `TickProcessor`.
2. Implement `VehicleService` và `VehicleServiceImpl`.
3. Implement `TrafficEventService` và `TrafficEventServiceImpl`.
4. Implement `SimulationService` và `SimulationServiceImpl`.
5. Implement `TrafficStatistics`, `AlgorithmStatistics`, `StatisticsService`, và `StatisticsServiceImpl`.
6. Thêm DTO cho vehicles, events, snapshots, và statistics.
7. Thêm controller cho vehicles, events, simulation, và statistics.
8. Register các controller mới vào `ApiRouteRegistry`.
9. Thêm tests cho service và simulation behavior.

Demo tối thiểu cần chạy được:

```text
load map
create multiple vehicles
create traffic events
start simulation
advance one tick
pause/resume/reset
apply event effects
reroute affected vehicles
show statistics
```

### Phase 2: Đồng bộ frontend với backend thật

Mục tiêu: frontend chỉ gọi các endpoint có thật, hoặc backend phải implement các endpoint frontend đang gọi.

Việc cần làm:

1. Thêm backend endpoint cho `/api/network/roads`.
2. Quyết định `/api/network/load`, `/api/network/generate`, và `/api/network/save` có nằm trong basic demo không.
3. Nối vehicle panel với vehicle endpoints thật.
4. Nối event panel với event endpoints thật.
5. Nối control panel với simulation endpoints thật.
6. Nối statistics panel với statistics endpoints thật.
7. Ẩn hoặc xóa tạm thời các frontend controls cho feature chưa có backend.

### Phase 3: Sau đó mới làm cho project "xịn" hơn

Chỉ nên sang phase này sau khi Phase 1 và Phase 2 đã có demo end-to-end.

Các hướng nâng cấp:

- map visualization đẹp hơn;
- vehicle animation;
- congestion heatmap;
- scenario maps phong phú hơn;
- algorithm comparison table;
- charts cho experiment;
- large-scale synthetic networks;
- presentation demo gọn và ổn định;
- README và setup instructions rõ ràng hơn.

## Ý Kiến Về Đề Xuất Của Student C

Đề xuất của Student C có hướng suy nghĩ đúng: nếu basic project đã ổn thì team nên ngừng loay hoay với việc "làm cho đúng" và chuyển sang "làm cho xịn".

Nhưng điều kiện "basic project đã ổn" hiện chưa đúng. Backend có nhiều module lõi đi trước, nhưng full backend application chưa xong. Nếu kết thúc basic project ngay bây giờ thì rủi ro cao, vì project vẫn thiếu simulation, statistics, event runtime flow, vehicle services, và API integration.

Quyết định nên đưa ra:

> Chưa nên tuyên bố basic project đã xong. Nên dành sprint tiếp theo để đóng các backend integration gaps và nối frontend vào endpoint thật. Khi đã có một demo end-to-end hoàn chỉnh, lúc đó mới hợp lý để đẩy nhanh frontend polish và nâng cấp project.

## Kết Luận Cuối

Student C đúng một phần, nhưng nói quá mức.

Phần đúng:

- A, B, C đã làm được nhiều backend domain foundations trước frontend.
- Nền tảng backend đang được test hiện tại khá ổn.
- Project có đủ cơ sở để tăng tốc frontend trong thời gian gần.

Phần chưa đúng:

- Backend tuần 4-7 chưa gần như xong ở cấp ứng dụng.
- Simulation, multi-vehicle runtime, traffic event orchestration, statistics, và nhiều API vẫn còn thiếu.

Kết luận hành động:

> Hoàn thiện minimal end-to-end backend trước, sau đó tăng tốc frontend và nâng cấp project.
