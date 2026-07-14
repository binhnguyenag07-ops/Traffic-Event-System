# Binh - Week 6 Visualization Data Tasks

## Phạm vi sở hữu

Bình chỉ phụ trách:

- `src/main/java/team/traffic/domain/network/`
- `src/main/java/team/traffic/persistence/`
- `data/`
- Unit test và tài liệu trực tiếp của các module trên.

Không thuộc phạm vi của Bình:

- `frontend/`
- `api/`, controller và DTO.
- Simulation, service, routing, vehicle và traffic event.

## Mục tiêu

Cung cấp network model và scenario data đầy đủ, ổn định để thành viên phụ trách API/frontend có thể xây visualization mà không phải tự suy đoán hoặc tính lại quy tắc domain.

## Checklist

### 1. Scenario data

- [x] Hoàn thiện `data/congestion_demo.json`.
- [x] Hoàn thiện `data/accident_demo.json`.
- [x] Mỗi intersection có tọa độ hữu hạn và không trùng nhau.
- [x] Mỗi road tham chiếu endpoint tồn tại.
- [x] Vehicle count nằm trong capacity.
- [x] Vehicle density khớp `currentVehicleCount / capacity`.
- [x] Có road congested/blocked và alternative route để demo.

### 2. Network queries bàn giao cho visualization

- [x] `getAverageVehicleDensity()` trả density trung bình.
- [x] `findRoadsAtOrAboveDensity(double)` trả road cho heatmap.
- [x] `findRoadsByStatus(RoadStatus)` trả road theo trạng thái.
- [x] `getRoadStatusCounts()` trả số road của mọi trạng thái, kể cả trạng thái có giá trị 0.
- [x] `getAverageCongestionLevel()` trả congestion trung bình.
- [x] Các collection query trả về dạng read-only.
- [x] Validation rõ ràng khi status hoặc density threshold không hợp lệ.

### 3. Persistence và validation

- [x] JSON loader đọc được hai scenario Week 6.
- [x] Loader kiểm tra density consistency.
- [x] Map saver giữ lại tọa độ, density, congestion, vehicle count và road status.
- [x] Có test save/load round-trip cho congestion và accident scenario.
- [x] Có test coordinate uniqueness và endpoint consistency.

### 4. Tài liệu bàn giao

- [x] Cập nhật `docs/planning/Binh-network-persistence.md`.
- [x] Ghi rõ query contract cho heatmap, filter và statistics.
- [x] Ghi rõ ranh giới: Bình cung cấp domain/data; An phụ trách API và frontend.

## Dữ liệu An có thể sử dụng

### Intersection

- `id`, `name`
- `x`, `y`
- `locationType`

### Road

- `id`, `startIntersectionId`, `endIntersectionId`
- `distance`, `speedLimit`, `capacity`, `currentVehicleCount`
- `getCapacityUsage()`
- `RoadCondition`: congestion, density và status
- `calculateTravelCost()`
- `isAvailable()`

## Tiêu chí hoàn thành

- [x] Scenario congestion và accident load thành công.
- [x] Network cung cấp đủ query read-only cho visualization consumer.
- [x] Scenario data không có tọa độ trùng hoặc road endpoint lỗi.
- [x] Density và road condition giữ nguyên qua save/load.
- [x] Network và persistence tests pass.
- [x] Không chỉnh sửa file thuộc frontend/API/thành viên khác.

## Ghi chú theo dõi

| Ngày | Công việc | Trạng thái | Vướng mắc |
|---|---|---|---|
| 2026-07-14 | Network queries, scenario validation, round-trip tests và tài liệu bàn giao | Hoàn thành | Không |
