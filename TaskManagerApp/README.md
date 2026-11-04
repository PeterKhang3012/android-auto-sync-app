# TaskManagerApp - Offline-First Task Manager & Auto-Sync App

> **BÁO CÁO TIỂU LUẬN GIỮA KỲ**  
> **Môn học**: Lập trình Ứng dụng Di động (Android)  
> **Đề tài 01**: Xây dựng ứng dụng Android hỗ trợ hoạt động ngoại tuyến và đồng bộ dữ liệu tự động (Offline-First Task Manager)  
>
> **Thành viên thực hiện**:
> - **Người A (Android Client Lead)**: **Nguyễn Hữu Tân** - MSSV: **524H0028** 
> - **Người B (Backend & Sync Lead)**: **Nguyễn Ngọc Đức Khang** - MSSV: **524H0099** 
>
> 

---

## 📚 Danh mục tài liệu báo cáo (Deliverables)

| Deliverable | Tên tài liệu | Người phụ trách |
| :---: | :--- | :---: | :--- |
| **D1** | Báo cáo nghiên cứu lý thuyết Offline-First & Sync | Nguyễn Hữu Tân (A) & Nguyễn Ngọc Đức Khang (B) |
| **D2** | Phân tích & Thiết kế hệ thống (Use Case, Component, Sequence, ERD) | Nguyễn Hữu Tân (Người A) |
| **D3** | Mã nguồn ứng dụng Android & Hướng dẫn Build/Run | Nguyễn Hữu Tân (Người A) |
| **D4** | Mã nguồn máy chủ Node.js & Đặc tả API RESTful | Nguyễn Ngọc Đức Khang (Người B) |
| **D5** | Thiết kế mô-đun đồng bộ & Thuật toán xử lý xung đột | Nguyễn Ngọc Đức Khang (Người B) |
| **D6** | Bộ kiểm thử tự động (Unit Test & Room DAO Test) | Nguyễn Ngọc Đức Khang (Người B) |
| **D7** | Báo cáo thực nghiệm, đo đạc hiệu năng trên 2 thiết bị | Nguyễn Ngọc Đức Khang (Người B) |
| **D8** | Sản phẩm trình diễn, Video Demo & Hướng dẫn sử dụng | Nguyễn Hữu Tân (Người A) |

---

## 🏗️ 1. Kiến trúc hệ thống Client (Clean Architecture & MVVM)

Ứng dụng tuân thủ nghiêm ngặt mô hình chuẩn của Android:
```
[ UI Layer (Activity / Adapter) ]
          │  ▲
          │  │ StateFlow (tasks) / LiveData (syncWorkInfo)
          ▼  │
   [ TaskViewModel ]
          │  ▲
          │  │ Suspend Functions / Flow
          ▼  │
   [ TaskRepository ]  <─── (Single Source of Truth)
     │            │
     ▼            ▼
[ Room DB ]   [ WorkManager & Retrofit ]
(Local Cache) (Sync Engine & Remote API)
```

- **Single Source of Truth (SSOT)**: Mọi thao tác đọc dữ liệu của giao diện đều lấy từ CSDL cục bộ **Room Database**. Giao diện không bao giờ đợi kết quả từ mạng mới hiển thị (Zero Latency UI).
- **Soft Delete (Xóa logic)**: Khi người dùng xóa công việc, ứng dụng không xóa cứng khỏi CSDL mà cập nhật cờ `isDeleted = 1`, `syncStatus = "PENDING"`, và `updatedAt = currentTime`. Điều này giúp hệ thống lưu trữ vết để gửi yêu cầu xóa (Tombstone) lên máy chủ khi có mạng.

---

## 📱 2. Các chức năng chính (Yêu cầu 1, 2, 5)

1. **Quản lý công việc (CRUD)**:
   - Thêm công việc mới kèm mô tả chi tiết.
   - Sửa tiêu đề và nội dung công việc (Edit Task Dialog).
   - Đánh dấu hoàn thành / chưa hoàn thành (Checkbox với hiệu ứng gạch ngang tiêu đề).
   - Xóa công việc với hộp thoại xác nhận (Soft-delete).
2. **Badge trạng thái đồng bộ trực quan**:
   - `✓ Đã đồng bộ` (Xanh lục): Dữ liệu cục bộ khớp hoàn toàn với máy chủ.
   - `⏳ Chờ đồng bộ` (Cam): Dữ liệu vừa được thêm/sửa/xóa ở chế độ Offline hoặc đang chờ mạng.
   - `⟳ Đang đồng bộ` (Xanh dương): Đang trong quá trình đẩy/kéo dữ liệu với máy chủ.
   - `⚠ Lỗi đồng bộ` (Đỏ): Gặp lỗi mạng hoặc máy chủ (sẽ tự động thử lại).
3. **Thanh tiến trình đồng bộ (Sync Indicator)**:
   - Theo dõi trạng thái của `WorkManager` (`RUNNING`, `SUCCEEDED`, `FAILED`, `ENQUEUED`).
4. **Nút "Đồng bộ ngay" (Manual Sync)**:
   - Cho phép người dùng chủ động kích hoạt đồng bộ bất kỳ lúc nào.
5. **Cơ chế WorkManager thông minh**:
   - **Network Constraints**: Chỉ chạy ngầm khi thiết bị có kết nối Internet (`NetworkType.CONNECTED`).
   - **Exponential Backoff**: Tự động thử lại theo cấp số nhân nếu gặp sự cố mạng (bắt đầu từ 10 giây).
   - **Unique Work Policy**: Sử dụng `enqueueUniqueWork` tránh việc tạo nhiều tiến trình chạy đè lên nhau.
6. **Xử lý xung đột (Conflict Resolution Dialog)**:
   - Cung cấp giao diện trực quan so sánh giữa **Bản Cục Bộ (Local)** và **Bản Máy Chủ (Server)** để người dùng tự quyết định giữ bản nào.

---

## 🗄️ 3. Lược đồ CSDL Room (TaskEntity)

| Tên trường | Kiểu dữ liệu | Ý nghĩa |
| :--- | :--- | :--- |
| `id` | `String` (UUID, Primary Key) | Định danh duy nhất toàn cầu của bản ghi, sinh ngay tại client |
| `title` | `String` | Tiêu đề công việc |
| `description` | `String?` | Mô tả chi tiết (tùy chọn) |
| `isCompleted` | `Boolean` | Trạng thái hoàn thành |
| `syncStatus` | `String` | Trạng thái đồng bộ (`PENDING`, `SYNCING`, `SYNCED`, `FAILED`) |
| `updatedAt` | `Long` (Timestamp ms) | Mốc thời gian cập nhật gần nhất |
| `isDeleted` | `Boolean` | Cờ xóa mềm (0: Hoạt động, 1: Đã xóa) |
| `version` | `Long` | Phiên bản bản ghi phục vụ phát hiện xung đột |

---

## 🚀 4. Hướng dẫn Build & Chạy Ứng dụng Android (Deliverable D3)


- **Repository Git**: `https://github.com/PeterKhang3012/android-auto-sync-app.git`
- **Nhánh chính**: `main`
- **Thư mục source code**: `TaskManagerApp/`

### 4.1. Yêu cầu môi trường phát triển
- **Android Studio**: Bản Hedgehog (2023.1.1) trở lên (khuyên dùng Ladybug / Koala).
- **JDK**: JDK 17 hoặc JDK 21 (được tích hợp sẵn trong Android Studio).
- **Android SDK**: Compile SDK 34, Min SDK 26.
- **Thiết bị**: Máy ảo Android Emulator (khuyên dùng Pixel 6a, Android 14) hoặc điện thoại thật Android 8.0+.

### 4.2. Chạy ứng dụng qua Android Studio (Giao diện trực quan)
1. Khởi động **Android Studio**, chọn **Open** và dẫn tới thư mục `TaskManagerApp`.
2. Chờ ứng dụng tự động tải thư viện và đồng bộ Gradle (Gradle Sync) thành công.
3. Bật máy ảo trong **Device Manager** (hoặc cắm điện thoại đã bật USB Debugging).
4. Chọn cấu hình chạy là `app`, bấm nút **Run (▶)** hoặc phím tắt `Shift + F10`.

### 4.3. Biên dịch và cài đặt bằng dòng lệnh (Gradle CLI)
Mở cửa sổ Terminal tại thư mục `TaskManagerApp`:
- **Biên dịch file APK Debug:**
  - *Trên Windows (PowerShell / Android Studio Terminal)*:
    ```powershell
    .\gradlew.bat assembleDebug
    ```
  - *Trên Windows (CMD cũ)*:
    ```cmd
    gradlew.bat assembleDebug
    ```
  - *Trên macOS / Linux*:
    ```bash
    ./gradlew assembleDebug
    ```
  - File APK sau khi build nằm tại: `app/build/outputs/apk/debug/app-debug.apk`

- **Cài đặt APK vào thiết bị qua ADB:**
  ```powershell
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

- **Chạy kiểm thử tự động (Unit Test / Room DAO Test):**
  ```powershell
  .\gradlew.bat testDebugUnitTest
  .\gradlew.bat connectedAndroidTest
  ```

## Server

### Chạy trên máy tính

Cài Node.js LTS (kèm npm) và Android Studio với Android SDK 34 trước.
Sau khi pull code, chạy server bằng một trong hai cách dưới đây.

**Windows:** mở Terminal tại `TaskManagerApp` rồi chạy:

```powershell
.\run.bat
```

Hoặc nhấp đúp `run.bat` trong File Explorer.

**macOS/Linux:** mở Terminal tại `TaskManagerApp` rồi chạy:

```sh
bash run.sh
```

Hai script tự tìm thư mục `server` theo vị trí của script, chạy `npm ci` để cài
đúng phiên bản thư viện trong `package-lock.json`, rồi chạy `npm start` nếu cài
thành công. Có thể gọi script bằng đường dẫn từ thư mục khác. Mỗi lần chạy sẽ
cài lại dependency nên cần kết nối mạng. Script không tự cài Node.js hoặc npm.

Khi thấy `TaskSync server: http://localhost:3000`, giữ cửa sổ Terminal mở và chạy
app Android. Nhấn `Ctrl+C` để dừng server. Nếu được hỏi `Terminate batch job` trên
Windows, chọn `Y`. Nếu cổng 3000 đang được dùng, dừng server cũ trước khi chạy lại.
Trên Windows, cửa sổ sẽ được giữ lại khi có lỗi để đọc thông báo.

Để chạy kiểm thử server riêng, mở Terminal tại `TaskManagerApp`:

```sh
cd server
npm test
```

Lệnh kiểm thử cần dependency đã được cài bởi script hoặc `npm ci`.

Mở thư mục `TaskManagerApp` bằng Android Studio, chạy app trên Android Emulator.
Retrofit gọi `http://10.0.2.2:3000/`. Máy Android thật cần thay BASE_URL trong
`data/remote/RetrofitClient.kt` bằng IP LAN của máy chạy server và cho phép cổng 3000
qua firewall. Không dùng địa chỉ emulator cho điện thoại thật.

### Kiểm tra hai thiết bị

1. Tắt Demo Mock, tạo task trên emulator A và bấm **Đồng bộ ngay**.
2. Trên emulator B bấm đồng bộ; task từ A phải xuất hiện.
3. Ngắt mạng A, chỉnh sửa task; đồng thời sửa task trên B và đồng bộ B.
4. Bật mạng A rồi đồng bộ. Menu **Chính sách xung đột** cho phép chọn bản sửa
   mới nhất thắng, server thắng hoặc client thắng. Nếu bằng thời gian, server thắng.
5. Nhấn giữ task đã có trên server để mô phỏng máy B sửa và mở dialog so sánh
   với bản server thật; chọn giữ local hoặc server.
6. Menu có thống kê tổng số lần/thành công/thời gian trung bình/xung đột,
   kiểm tra dữ liệu local-server và giả lập HTTP 500/timeout. Sau thử lỗi,
   chọn **Bình thường** để server hoạt động lại.
7. Xóa task, đồng bộ, rồi kéo trên B: task phải biến mất trên cả hai máy.

WorkManager chạy khi có mạng, thử lại theo exponential backoff và đồng bộ định kỳ
15 phút (Android có thể trì hoãn). Nút đồng bộ và thay đổi local xếp việc nối tiếp;
không hủy request đang gửi. Kéo lỗi không được báo thành công. Bản sửa mới trong
lúc request đang chạy được giữ PENDING để gửi tiếp. Demo Mock không đánh dấu bản
local là đã được server xác nhận.

API dùng `GET /tasks?updatedAfter=<seq>`, `PUT /tasks/:id`, trường JSON `completed`,
`deleted`, `version`; HTTP 409 trả `{server: ...}`. Cursor là số thứ tự server,
không phải timestamp thiết bị. Không còn gọi API batch `/tasks/sync`.

Server này phục vụ demo: dữ liệu nằm trong RAM, mất khi restart; các endpoint
`/admin/*` không có xác thực. Không đưa server này lên Internet để dùng production.
Khi restart server, nên xóa dữ liệu của cả hai app/emulator rồi thử với dữ liệu mới
để tránh cursor và trạng thái cũ. Thống kê được lưu tổng hợp trong SharedPreferences,
không dùng bảng SyncLog của bản Midterm.

### Trạng thái kiểm thử trong cloud

- `npm ci`, `npm test`: thành công; kiểm thử HTTP thật bao phủ push/pull,
  delta rỗng, xung đột 409, retry, xóa mềm, validation và giả lập lỗi/phục hồi.
- Android chưa xác nhận build/test: cấu hình sẵn có yêu cầu Gradle 9.6/JDK 25;
  Gradle tải và xác minh checksum thành công nhưng tải JDK qua `api.foojay.io`
  bị proxy trả HTTP 403. Máy cloud cũng chưa có Android SDK.
- Có thêm unit test cho chính sách xung đột; chưa chạy được vì chặn toolchain trên.
  Cần chạy `gradlew.bat testDebugUnitTest assembleDebug` và kiểm tra hai emulator
  trên máy có toolchain trước khi merge.

---

## 5. Mã nguồn máy chủ và API (Deliverable D4)

- **Tài liệu chi tiết**: [`docs/D4_Ma_Nguon_May_Chu_Va_API.md`](docs/D4_Ma_Nguon_May_Chu_Va_API.md)

## 🎥 6. Sản phẩm trình diễn & Video Demo (Deliverable D8)
- **File APK cài đặt**: [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)
- **Link Video Demo (YouTube)**: [https://youtu.be/1YPjmJF52CQ]



