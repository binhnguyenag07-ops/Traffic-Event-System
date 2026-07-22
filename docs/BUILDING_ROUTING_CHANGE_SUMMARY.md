# Tổng hợp thay đổi hỗ trợ hành trình giữa các tòa nhà

Trong thay đổi vừa rồi, hệ thống được bổ sung chức năng hỗ trợ hành trình **từ tòa nhà đến tòa nhà**. Một tòa nhà có thể có nhiều cổng kết nối với mạng đường; hệ thống sẽ chọn cặp cổng có tuyến đường tốt nhất và có thể đổi cổng đích khi đường bị chặn hoặc ùn tắc.

Tổng cộng có **7 file mới và 13 file được sửa**. Các thay đổi xuyên sang phần Routing của thành viên B và Vehicle của thành viên C là để nối đầy đủ luồng chức năng, không phải để thay thế kiến trúc hoặc các thuật toán hiện có.

## Các file mới

- `src/main/java/team/traffic/domain/network/Building.java`: mô hình tòa nhà, gồm ID, tên, loại, tọa độ và danh sách cổng. File có validation để tránh dữ liệu thiếu hoặc trùng ID cổng.

- `src/main/java/team/traffic/domain/network/BuildingAccessPoint.java`: mô hình một cổng ra/vào, liên kết tòa nhà với một intersection và lưu khoảng cách driveway.

- `src/main/java/team/traffic/domain/network/BuildingType.java`: định nghĩa các loại tòa nhà như residential, office, school, hospital, parking và các loại khác.

- `src/main/java/team/traffic/domain/routing/BuildingRoutePlan.java`: đóng gói kết quả routing cùng cổng xuất phát, cổng đích và tổng chi phí của phương án.

- `src/main/java/team/traffic/routing/BuildingRoutePlanner.java` — phần Routing của thành viên B: thử các cổng có thể sử dụng rồi chọn tuyến có chi phí thấp nhất theo `RoutePreference`. File này chỉ tái sử dụng `RoutingService.findRoute()`, không sửa Dijkstra, A*, BFS hoặc các thuật toán routing hiện có.

- `src/test/java/team/traffic/domain/network/BuildingTest.java`: kiểm tra tòa nhà lưu được nhiều cổng và từ chối ID cổng bị trùng.

- `src/test/java/team/traffic/routing/BuildingRoutePlannerTest.java`: kiểm tra hệ thống chọn đúng cổng có chi phí thấp nhất và đổi sang cổng khác khi tuyến ban đầu bị chặn.

## Các file Network và Persistence được sửa

- `src/main/java/team/traffic/domain/network/RoadNetwork.java`: thêm collection và các hàm quản lý Building; kiểm tra cổng phải tham chiếu đến intersection tồn tại; ngăn xóa intersection đang được một cổng của tòa nhà sử dụng.

- `src/main/java/team/traffic/persistence/JsonMapLoader.java`: đọc `buildings` và `accessPoints` từ JSON. Trường `buildings` vẫn là tùy chọn để các map cũ tiếp tục hoạt động.

- `src/main/java/team/traffic/persistence/MapSaver.java`: ghi Building và các cổng trở lại JSON; sắp xếp theo ID để kết quả lưu có thứ tự ổn định.

- `src/main/java/team/traffic/api/controller/NetworkController.java`: bổ sung `buildingCount`, API lấy danh sách tòa nhà và API lấy một tòa nhà theo ID.

- `src/test/java/team/traffic/persistence/JsonMapLoaderTest.java`: kiểm tra việc load tòa nhà có nhiều cổng và báo lỗi nếu cổng tham chiếu đến intersection không tồn tại.

- `src/test/java/team/traffic/persistence/MapSaverTest.java`: kiểm tra Building cùng các access point không bị mất sau chu trình save–load.

## Các file Vehicle của thành viên C được sửa

- `src/main/java/team/traffic/domain/vehicle/Vehicle.java`: lưu `originBuildingId` và `destinationBuildingId`. Khi reroute, xe có thể tính lại và chọn một cổng đích khác. `destinationIntersectionId` cần cho phép cập nhật vì cổng được chọn có thể thay đổi. Luồng xe chạy trực tiếp từ intersection đến intersection cũ vẫn được giữ nguyên.

- `src/main/java/team/traffic/service/VehicleService.java`: thêm API tạo xe giữa hai tòa nhà dưới dạng default method để không phá các implementation cũ.

- `src/main/java/team/traffic/service/impl/VehicleServiceImpl.java`: dùng `RoutingService` chọn cặp cổng, tạo xe tại access point xuất phát, gán route và thông tin hai tòa nhà.

- `src/main/java/team/traffic/api/controller/VehicleController.java`: chấp nhận thêm `startBuildingId` và `destinationBuildingId`, kiểm tra hai trường phải được gửi cùng nhau, đồng thời vẫn hỗ trợ request dùng intersection như trước.

- `src/main/java/team/traffic/api/dto/VehicleDto.java`: trả thêm ID tòa nhà xuất phát và đích để frontend hoặc API client biết đây là chuyến đi giữa các tòa nhà.

- `src/main/java/team/traffic/App.java`: truyền `RoutingService` vào `VehicleServiceImpl`; việc này cần thiết để service tạo xe có thể tính tuyến giữa các tòa nhà.

## File Routing service được sửa

- `src/main/java/team/traffic/service/RoutingService.java`: thêm hai default method `findRouteToBuilding` và `findRouteBetweenBuildings`. Hai method này ủy quyền cho `BuildingRoutePlanner`, nhờ đó API của routing hỗ trợ Building mà không buộc sửa các implementation và thuật toán routing hiện tại.

## Lý do thay đổi xuyên qua phần việc của các thành viên

Chức năng này là một luồng xuyên tầng:

1. Building và các cổng phải tồn tại trong domain model.
2. Map loader/saver phải đọc và ghi được dữ liệu Building.
3. Routing phải chọn được cổng ra/vào phù hợp.
4. Vehicle phải lưu điểm đầu–cuối theo Building và có khả năng chọn lại cổng khi reroute.
5. Service và controller phải cho phép tạo hành trình mới qua API.

Vì vậy, file `BuildingRoutePlanner.java` được thêm vào phần Routing của thành viên B vì việc so sánh và lựa chọn tuyến thuộc trách nhiệm routing. Các file Vehicle của thành viên C được sửa vì Vehicle là đối tượng trực tiếp sử dụng tuyến và phải biết điểm đến Building để reroute đúng. Những thay đổi này giữ nguyên các API và hành vi cũ dành cho hành trình intersection-to-intersection.

## Kết quả kiểm thử

Toàn bộ thay đổi đã được kiểm tra bằng lệnh:

```bash
mvn test
```

Kết quả: **456 test đã chạy thành công, không có failure, error hoặc test bị skip**.
