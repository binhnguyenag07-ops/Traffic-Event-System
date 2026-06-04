# Hướng dẫn setup từ zero - Traffic Event System

Tài liệu này dành cho người mới hoàn toàn: chưa cài Java, Maven, Node.js, Git hoặc IDE. Đi hết tài liệu này, bạn sẽ có môi trường đủ để mở project, chạy backend Java, chạy frontend React và bắt đầu code theo workflow của nhóm.

Ngày kiểm tra link cài đặt: 2026-06-04.

## 1. Project này cần những gì?

Project có 2 phần chính:

| Phần | Công nghệ | Dùng để làm gì |
| --- | --- | --- |
| Backend | Java 25, Maven, JDK HttpServer, Jackson, JUnit 5 | Xử lý graph đường đi, vehicle, routing, traffic events, simulation, API |
| Frontend | React 19, Vite 7, CSS thuần | Hiển thị map, controls, events, statistics và gọi backend API |
| Version control | Git | Clone project, tạo branch, commit, pull/push code |
| Editor đề xuất | Visual Studio Code hoặc IntelliJ IDEA | Viết code Java/React và quản lý project |

Bạn cần cài tối thiểu:

1. JDK 25.
2. Maven.
3. Node.js LTS, kèm npm.
4. Git.
5. Một editor/IDE.

## 2. Link tải chính thức

Ưu tiên tải từ link chính thức để tránh bản cài đặt lạ.

| Công cụ | Link |
| --- | --- |
| JDK 25 Oracle | [oracle.com/java/technologies/downloads](https://www.oracle.com/java/technologies/downloads/) |
| Apache Maven | [maven.apache.org/install.html](https://maven.apache.org/install.html) |
| Node.js LTS | [nodejs.org/en/download](https://nodejs.org/en/download) |
| Git | [git-scm.com/downloads](https://git-scm.com/downloads) |
| Visual Studio Code | [code.visualstudio.com/download](https://code.visualstudio.com/download) |

Ghi chú:

- Cài **JDK**, không chỉ cài JRE. Maven cần compiler `javac`.
- Project hiện thống nhất backend dùng Java 25, nên mọi thành viên cần dùng JDK 25 để build đúng release target.
- Node.js LTS hiện tại trên trang Node.js là v24 LTS tại thời điểm kiểm tra. Nếu nhóm muốn ổn định lâu dài, chỉ cần dùng bản LTS mới nhất từ trang chính thức.

## 3. Cài đặt nhanh trên Windows bằng winget

Nếu máy Windows có `winget`, mở PowerShell và chạy:

```powershell
winget install Oracle.JDK.25
winget install Apache.Maven
winget install OpenJS.NodeJS.LTS
winget install Git.Git
winget install Microsoft.VisualStudioCode
```

Sau khi cài xong, đóng toàn bộ terminal rồi mở PowerShell mới.

Kiểm tra:

```powershell
java -version
javac -version
mvn -version
node --version
npm --version
git --version
code --version
```

Nếu tất cả đều in ra version, môi trường cơ bản đã ổn.

## 4. Cài đặt thủ công trên Windows

### 4.1 Cài JDK 25

1. Vào [Oracle JDK 25](https://www.oracle.com/java/technologies/downloads/).
2. Chọn:
   - Operating System: Windows.
   - Architecture: x64.
   - Package Type: JDK.
   - Version: 25.
3. Tải installer `.msi`.
4. Khi cài, bật tùy chọn thêm `JAVA_HOME` và thêm Java vào `PATH` nếu installer có hỏi.
5. Mở PowerShell mới và kiểm tra:

```powershell
java -version
javac -version
```

Kết quả đúng cần có Java 25 và có lệnh `javac`.

Nếu `java` chạy nhưng `javac` không chạy, thường là bạn đã cài JRE hoặc PATH chưa trỏ tới JDK.

### 4.2 Kiểm tra hoặc set `JAVA_HOME`

Kiểm tra:

```powershell
echo $env:JAVA_HOME
```

Nếu trống, set bằng Windows UI:

1. Mở Start Menu.
2. Tìm `Edit the system environment variables`.
3. Chọn `Environment Variables`.
4. Trong `User variables` hoặc `System variables`, tạo biến:
   - Name: `JAVA_HOME`
   - Value: đường dẫn JDK, ví dụ `C:\Program Files\Java\jdk-25.0.3`
5. Trong biến `Path`, thêm:
   - `%JAVA_HOME%\bin`
6. Mở PowerShell mới.
7. Chạy lại:

```powershell
java -version
javac -version
echo $env:JAVA_HOME
```

### 4.3 Cài Maven

Cách 1, dùng installer/package manager:

```powershell
winget install Apache.Maven
```

Cách 2, cài thủ công:

1. Vào [Apache Maven Installation](https://maven.apache.org/install.html).
2. Tải binary zip từ trang download Maven.
3. Giải nén vào ví dụ:

```text
C:\Tools\apache-maven-3.9.x
```

4. Thêm vào `Path`:

```text
C:\Tools\apache-maven-3.9.x\bin
```

5. Mở PowerShell mới và kiểm tra:

```powershell
mvn -version
```

Kết quả đúng cần hiện Maven version và Java version.

### 4.4 Cài Node.js và npm

1. Vào [Node.js Download](https://nodejs.org/en/download).
2. Tải bản LTS cho Windows.
3. Cài bằng installer mặc định.
4. Mở PowerShell mới và kiểm tra:

```powershell
node --version
npm --version
```

Node installer thường cài sẵn npm.

### 4.5 Cài Git

1. Vào [Git Downloads](https://git-scm.com/downloads).
2. Tải Git for Windows.
3. Khi cài, có thể giữ hầu hết tùy chọn mặc định.
4. Khuyến nghị chọn:
   - Default editor: VS Code nếu bạn dùng VS Code.
   - PATH option: Git from the command line and also from 3rd-party software.
5. Mở PowerShell mới và kiểm tra:

```powershell
git --version
```

Thiết lập tên và email commit:

```powershell
git config --global user.name "Ten Cua Ban"
git config --global user.email "email-cua-ban@example.com"
git config --global init.defaultBranch main
```

Kiểm tra lại:

```powershell
git config --global --list
```

### 4.6 Cài VS Code và extension

Tải VS Code tại [code.visualstudio.com/download](https://code.visualstudio.com/download).

Extension khuyến nghị:

- Extension Pack for Java.
- Maven for Java.
- Debugger for Java.
- ESLint.
- Prettier.
- GitLens, tùy chọn.

Mở project bằng VS Code:

```powershell
code .
```

## 5. Ghi chú nhanh cho macOS và Linux

macOS với Homebrew:

```bash
brew install openjdk
brew install maven
brew install node
brew install git
brew install --cask visual-studio-code
```

Ubuntu/Debian:

```bash
sudo apt update
sudo apt install openjdk-25-jdk maven nodejs npm git
```

Nếu distro chưa có gói `openjdk-25-jdk`, tải JDK 25 từ link Oracle ở phần trên rồi set `JAVA_HOME`/`PATH` trỏ tới thư mục JDK đó.

Sau đó kiểm tra:

```bash
java -version
javac -version
mvn -version
node --version
npm --version
git --version
```

## 6. Lấy source code về máy

Nếu project nằm trên GitHub/GitLab, dùng:

```powershell
git clone <repo-url> Traffic-Event-System
cd Traffic-Event-System
```

Ví dụ:

```powershell
git clone https://github.com/<owner>/Traffic-Event-System.git Traffic-Event-System
cd Traffic-Event-System
```

Nếu bạn nhận project bằng file `.zip`:

1. Giải nén vào một thư mục dễ nhớ, ví dụ:

```text
D:\Projects\Traffic-Event-System
```

2. Mở PowerShell tại thư mục đó:

```powershell
cd D:\Projects\Traffic-Event-System
```

3. Nếu chưa phải Git repo, hỏi nhóm để lấy repo chính thức. Làm nhóm bằng `.zip` rất dễ mất lịch sử commit.

## 7. Mở project lần đầu

Tại thư mục root của project:

```powershell
code .
```

Root project là thư mục chứa:

```text
pom.xml
README.md
docs/
frontend/
src/
data/
```

Không mở riêng thư mục `frontend` nếu bạn còn cần code Java backend. Mở root giúp VS Code nhìn thấy cả backend và frontend.

## 8. Sanity check project trước khi chạy

Chạy các lệnh này ở root project:

```powershell
Get-ChildItem
Get-Item pom.xml
Get-Item src\main\java\team\traffic\App.java
Get-Content README.md -TotalCount 20
Get-Content docs\ARCHITECTURE.md -TotalCount 20 -Encoding UTF8
```

Bạn cần thấy:

- `pom.xml` có nội dung XML Maven.
- `src/main/java/team/traffic/App.java` có class `team.traffic.App`.
- `frontend/package.json` có script `dev`, `build`, `preview`.
- `data/sample_map.csv` và `data/sample_map.json` tồn tại.

Nếu `pom.xml` hoặc `App.java` có size `0` byte:

```powershell
Get-Item pom.xml, src\main\java\team\traffic\App.java | Select-Object FullName,Length
```

Khi một trong hai file này rỗng, backend chưa thể build/run bằng Maven. Cách xử lý:

1. Chạy `git status --short` để xem file đang bị sửa thế nào.
2. Nếu file bị rỗng do thao tác nhầm, lấy lại từ commit gần nhất hoặc hỏi người quản lý repo.
3. Không tự viết lại `pom.xml` từ trí nhớ nếu nhóm đã thống nhất dependency, vì dễ lệch Jackson/JUnit/plugin fat JAR.
4. Sau khi khôi phục, chạy lại `mvn -version`, `mvn compile`.

## 9. Cấu trúc project cần nhớ

```text
Traffic-Event-System/
├── pom.xml
├── README.md
├── data/
│   ├── sample_map.csv
│   └── sample_map.json
├── docs/
│   └── ARCHITECTURE.md
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   └── src/
└── src/
    ├── main/java/team/traffic/
    └── test/java/team/traffic/
```

Backend Java:

| Package | Vai trò |
| --- | --- |
| `team.traffic.api` | HTTP server, route registry, controllers, DTOs |
| `team.traffic.config` | Runtime config |
| `team.traffic.domain.*` | Object nghiệp vụ: network, vehicle, event, route, statistics |
| `team.traffic.exception` | Exception nghiệp vụ |
| `team.traffic.observer` | Event bus và listener |
| `team.traffic.persistence` | Load/generate map |
| `team.traffic.routing` | BFS, Dijkstra, A*, strategy registry |
| `team.traffic.service` | Service interfaces |
| `team.traffic.service.impl` | Service implementations |
| `team.traffic.simulation` | Tick engine, snapshot, status |

Frontend React:

| Thư mục/file | Vai trò |
| --- | --- |
| `frontend/src/App.jsx` | Layout chính |
| `frontend/src/components/` | UI panels |
| `frontend/src/hooks/` | State và command client-side |
| `frontend/src/api/trafficApi.js` | API client |
| `frontend/src/styles.css` | CSS thuần |
| `frontend/vite.config.js` | Vite config, proxy `/api` sang backend `localhost:8080` |

## 10. Chạy backend

Từ root project:

```powershell
mvn compile
```

Chạy test:

```powershell
mvn test
```

Package:

```powershell
mvn package
```

Theo README/docs, project dự kiến tạo fat JAR:

```powershell
java -jar target\traffic-urban-simulator-1.0-SNAPSHOT.jar
```

Nếu chưa có fat JAR plugin hoặc `pom.xml` chưa khôi phục đầy đủ, lệnh trên có thể chưa chạy được. Khi đó kiểm tra:

```powershell
Get-ChildItem target
```

Nếu chỉ có class files, có thể chạy tạm bằng classpath sau khi compile:

```powershell
java -cp target\classes team.traffic.App
```

Backend dự kiến chạy ở:

```text
http://localhost:8080
```

Health check dự kiến:

```powershell
Invoke-WebRequest http://localhost:8080/api/health
```

## 11. Chạy frontend

Mở terminal thứ hai tại root project:

```powershell
cd frontend
npm install
npm run dev
```

Mở browser:

```text
http://localhost:5173
```

Vite config đang proxy:

```text
/api -> http://localhost:8080
```

Điều này nghĩa là frontend có thể gọi `/api/health`, còn Vite tự chuyển sang backend port `8080`.

Nếu backend chưa chạy, giao diện React vẫn có thể mở nhưng các API call sẽ fail.

## 12. Chạy full project bằng 2 terminal

Terminal 1, backend:

```powershell
cd D:\Projects\Traffic-Event-System
mvn compile
mvn test
mvn package
java -jar target\traffic-urban-simulator-1.0-SNAPSHOT.jar
```

Terminal 2, frontend:

```powershell
cd D:\Projects\Traffic-Event-System\frontend
npm install
npm run dev
```

Browser:

```text
http://localhost:5173
```

Backend:

```text
http://localhost:8080
```

## 13. Workflow làm việc hằng ngày

Mỗi ngày trước khi code:

```powershell
git status
git pull
```

Tạo branch mới:

```powershell
git checkout -b feature/<module>/<short-description>
```

Ví dụ:

```powershell
git checkout -b feature/routing/dijkstra
```

Sau khi code:

```powershell
mvn test
cd frontend
npm run build
cd ..
git status
git add .
git commit -m "feat: implement dijkstra routing"
git push origin feature/routing/dijkstra
```

Sau đó tạo Pull Request trên GitHub/GitLab.

## 14. Nên code module nào trước?

Theo `docs/ARCHITECTURE.md`, thứ tự hợp lý là:

1. `domain.network`, `persistence`, `RoadNetworkServiceImpl`.
2. `routing`, `RoutingStrategyRegistry`, `RoutingServiceImpl`.
3. `domain.vehicle`, `domain.event`, `observer`, `VehicleServiceImpl`, `TrafficEventServiceImpl`.
4. `simulation`, `statistics`, `api`, `SimulationServiceImpl`, `StatisticsServiceImpl`.
5. Frontend gọi API thật trong `frontend/src/api/trafficApi.js`.

Không nên bắt đầu bằng UI quá sâu khi backend chưa có API ổn định.

## 15. Cách đọc docs của project

Nên đọc theo thứ tự:

1. `README.md`: biết tech stack và lệnh build/run ngắn.
2. `docs/ARCHITECTURE.md`: hiểu kiến trúc, module, design patterns, API contract.
3. `docs/SETUP_FROM_ZERO_VI.md`: file này, dùng để setup máy và workflow.
4. Code trong `src/main/java/team/traffic`.
5. Code frontend trong `frontend/src`.

Nếu terminal hiển thị tiếng Việt bị lỗi, mở file bằng VS Code hoặc dùng:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
Get-Content docs\ARCHITECTURE.md -Encoding UTF8
```

## 16. Các lệnh kiểm tra nhanh

Root project:

```powershell
git status --short
mvn -version
mvn test
```

Frontend:

```powershell
cd frontend
npm install
npm run build
```

Kiểm tra port:

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
```

Tìm process theo port:

```powershell
Get-NetTCPConnection -LocalPort 5173 | Select-Object OwningProcess
```

Dừng process nếu chắc chắn đó là dev server cũ:

```powershell
Stop-Process -Id <PID>
```

## 17. Troubleshooting thường gặp

### `java` is not recognized

Nguyên nhân:

- Chưa cài JDK.
- PATH chưa có `%JAVA_HOME%\bin`.
- Terminal mở trước khi cài Java.

Cách xử lý:

1. Cài JDK 25.
2. Set `JAVA_HOME`.
3. Thêm `%JAVA_HOME%\bin` vào `Path`.
4. Mở terminal mới.
5. Chạy `java -version`.

### `javac` is not recognized

Nguyên nhân thường là cài JRE thay vì JDK.

Cách xử lý:

- Cài JDK 25.
- Kiểm tra `javac -version`.

### `mvn` is not recognized

Nguyên nhân:

- Chưa cài Maven.
- Maven `bin` chưa nằm trong `Path`.

Cách xử lý:

```powershell
winget install Apache.Maven
```

Hoặc thêm thư mục Maven `bin` vào `Path`, mở terminal mới, rồi chạy:

```powershell
mvn -version
```

### `npm.ps1 cannot be loaded because running scripts is disabled`

PowerShell có thể chặn script `.ps1`.

Cách nhanh:

```powershell
npm.cmd --version
npm.cmd install
npm.cmd run dev
```

Hoặc cho phép script ở user hiện tại:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

Sau đó mở PowerShell mới.

### `npm install` lỗi

Thử:

```powershell
cd frontend
Remove-Item -Recurse -Force node_modules -ErrorAction SilentlyContinue
Remove-Item package-lock.json -ErrorAction SilentlyContinue
npm install
```

Chỉ xóa `package-lock.json` khi nhóm đồng ý, vì file này giúp đồng bộ dependency.

### Port `5173` đã được dùng

Vite có thể tự chọn port khác, hoặc bạn kiểm tra process:

```powershell
Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
```

Nếu cần chạy port khác:

```powershell
npm run dev -- --port 5174
```

### Port `8080` đã được dùng

Backend dự kiến dùng `8080`. Kiểm tra:

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
```

Nếu bị chiếm bởi app khác, tắt app đó hoặc đổi port trong `ApplicationConfig` khi backend đã implement.

### Frontend mở được nhưng API lỗi

Kiểm tra:

1. Backend đã chạy chưa.
2. Backend có ở `localhost:8080` không.
3. Vite proxy trong `frontend/vite.config.js` có đúng target không.
4. Endpoint backend đã implement chưa.

### Maven build lỗi vì `pom.xml` rỗng

Kiểm tra:

```powershell
Get-Item pom.xml | Select-Object Length
```

Nếu length là `0`, Maven không có cấu hình để build. Cần khôi phục `pom.xml` từ Git hoặc hỏi người phụ trách backend.

### Chạy JAR lỗi `no main manifest attribute`

Nguyên nhân:

- JAR chưa được cấu hình main class.
- Fat JAR plugin chưa setup đúng.

Cách xử lý:

1. Kiểm tra `pom.xml` có plugin tạo executable JAR không.
2. Chạy tạm:

```powershell
java -cp target\classes team.traffic.App
```

3. Sau đó thêm hoặc sửa Maven plugin theo thống nhất nhóm.

## 18. Checklist cho người mới

Trước khi nhận task, bạn nên tick hết:

- [ ] Cài JDK 25.
- [ ] `java -version` chạy được.
- [ ] `javac -version` chạy được.
- [ ] Cài Maven.
- [ ] `mvn -version` chạy được.
- [ ] Cài Node.js LTS.
- [ ] `node --version` chạy được.
- [ ] `npm --version` chạy được.
- [ ] Cài Git.
- [ ] `git --version` chạy được.
- [ ] Set `git config --global user.name`.
- [ ] Set `git config --global user.email`.
- [ ] Clone project.
- [ ] Mở root project bằng VS Code/IDE.
- [ ] Đọc `README.md`.
- [ ] Đọc `docs/ARCHITECTURE.md`.
- [ ] Kiểm tra `pom.xml` không rỗng.
- [ ] Kiểm tra `App.java` không rỗng.
- [ ] Chạy được `mvn test` khi backend config đã đầy đủ.
- [ ] Chạy được `npm install`.
- [ ] Chạy được `npm run dev`.
- [ ] Tạo branch feature đầu tiên.

## 19. Checklist trước khi push code

Trước khi push:

```powershell
git status
mvn test
cd frontend
npm run build
cd ..
```

Sau đó:

```powershell
git add .
git commit -m "<type>: <short description>"
git push origin <branch-name>
```

Không commit:

- `target/`
- `frontend/node_modules/`
- `frontend/dist/`, trừ khi nhóm yêu cầu deploy static build.
- File IDE cá nhân nếu không thống nhất.

## 20. Gợi ý cấu hình `.gitignore`

Project nên ignore:

```gitignore
target/
frontend/node_modules/
frontend/dist/
*.log
.idea/
.vscode/
```

Nếu nhóm muốn share VS Code settings chung, có thể bỏ `.vscode/` khỏi ignore và chỉ commit các file cấu hình thật sự cần.

## 21. Tóm tắt cực ngắn

Người mới chỉ cần nhớ luồng này:

```powershell
# 1. Cài tool
java -version
javac -version
mvn -version
node --version
npm --version
git --version

# 2. Clone và mở project
git clone <repo-url> Traffic-Event-System
cd Traffic-Event-System
code .

# 3. Chạy backend
mvn test
mvn package
java -jar target\traffic-urban-simulator-1.0-SNAPSHOT.jar

# 4. Chạy frontend ở terminal khác
cd frontend
npm install
npm run dev

# 5. Mở app
# http://localhost:5173
```

Nếu backend chưa chạy được, kiểm tra ngay `pom.xml` và `src/main/java/team/traffic/App.java` có bị rỗng không.
