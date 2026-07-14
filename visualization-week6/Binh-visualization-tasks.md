# Binh - Week 6 Visualization Tasks

## Mục tiêu

Hoàn thiện dữ liệu road network và phần hiển thị nền bản đồ để các trạng thái đường có thể được quan sát chính xác theo thời gian thực.

> File này chỉ liệt kê những phần còn thiếu. Các network domain, persistence, density calculation và API đọc intersection/road đã có không nằm trong checklist.

## Checklist công việc

### 1. Hoàn thiện dữ liệu demo

- [ ] Hoàn thiện `data/congestion_demo.json` đang rỗng.
- [ ] Hoàn thiện `data/accident_demo.json` đang rỗng.
- [ ] Bảo đảm mỗi scenario có đủ tọa độ để các intersection không bị chồng lên nhau.
- [ ] Bảo đảm scenario có road `OPEN`, `CONGESTED`, `BLOCKED` hoặc `CLOSED` để kiểm tra màu.
- [ ] Bổ sung vehicle count và density hợp lệ cho các road cần minh họa.
- [ ] Viết test load và validate hai scenario mới.

### 2. Hoàn thiện dữ liệu road dành cho visualization

- [ ] Bổ sung `vehicleDensity` vào response của `GET /api/network/roads`.
- [ ] Bổ sung `travelCost` vào response road.
- [ ] Bổ sung `available` vào response road.
- [ ] Bổ sung `capacityUsage` nếu frontend cần hiển thị phần trăm tải.
- [ ] Bảo đảm endpoint trả road condition mới nhất sau tick hoặc traffic event.
- [ ] Viết controller/API test kiểm tra các trường mới.

### 3. Hoàn thiện lớp bản đồ nền

- [ ] Làm độ dày road thay đổi theo `vehicleDensity`.
- [ ] Dùng màu khác nhau cho `OPEN`, `CONGESTED`, `BLOCKED`, `CLOSED`.
- [ ] Xử lý hai road ngược chiều để chúng không vẽ chồng hoàn toàn lên nhau.
- [ ] Tự động tính scale và offset để toàn bộ network nằm trong canvas.
- [ ] Hiển thị intersection khác nhau theo `locationType`.
- [ ] Thêm legend cho road status, density và location type.
- [ ] Thêm tooltip/selection cho intersection.
- [ ] Thêm tooltip/selection cho road gồm ID, endpoints, speed, vehicle count, capacity, density, congestion, status và travel cost.

## Bàn giao cho thành viên khác

- Cung cấp JSON schema cuối cùng của intersection và road cho An.
- Cung cấp danh sách màu/status hoặc helper thống nhất cho `MapViewport`.
- Gửi ID road phù hợp trong hai scenario để Hyo dùng tạo event.
- Xác nhận với Nguyie rằng mọi `roadId` trong route response đều tồn tại trên bản đồ.

## Tiêu chí hoàn thành

- [ ] Hai scenario mới load thành công.
- [ ] Road API trả đủ dữ liệu cần để vẽ mà frontend không phải tự tính lại domain rule.
- [ ] Tất cả intersection và road nhìn thấy được trên canvas.
- [ ] Road đổi đúng màu và độ dày khi condition/density thay đổi.
- [ ] Tooltip và legend hiển thị đúng.
- [ ] Test của module network/persistence và test API mới đều pass.

## Ghi chú theo dõi

| Ngày | Công việc | Trạng thái | Vướng mắc |
|---|---|---|---|
| | | Chưa làm | |

