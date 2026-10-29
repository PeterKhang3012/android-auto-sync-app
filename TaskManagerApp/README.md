# TaskManagerApp - Offline-First Android Client 

Ứng dụng quản lý công việc (Task Manager) trên nền tảng Android được xây dựng theo kiến trúc **Offline-First**, đảm bảo người dùng luôn có thể tạo, chỉnh sửa, xóa và theo dõi công việc ngay cả khi không có kết nối Internet. Dữ liệu sẽ tự động được xếp hàng và đồng bộ hai chiều với máy chủ ngay khi có mạng thông qua **Android Jetpack WorkManager**.

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

## 🧪 4. Kiểm thử tự động (Deliverable D6)

Bộ kiểm thử Room DAO đã được hiện thực trong `app/src/androidTest/java/com/example/taskmanagerapp/TaskDaoTest.kt`:
- `insertAndGetTaskById`: Kiểm tra tính toàn vẹn khi thêm và đọc bản ghi.
- `softDeleteTask_shouldHideFromGetAllTasks`: Kiểm tra xóa logic (ẩn khỏi UI nhưng vẫn còn trong DB để đồng bộ).
- `getPendingTasks_shouldReturnPendingAndFailedOnly`: Kiểm tra bộ lọc các tác vụ cần đồng bộ.
- `updateSyncStatus_shouldUpdateCorrectly`: Kiểm tra chuyển đổi trạng thái đồng bộ.

Lệnh chạy kiểm thử:
```bash
./gradlew connectedAndroidTest
```

---

## 🚀 5. Hướng dẫn Build & Chạy

1. Mở dự án trong **Android Studio**.
2. Đồng bộ Gradle (Sync Project with Gradle Files).
3. Biên dịch dự án:
   ```bash
   ./gradlew assembleDebug
   ```
4. File APK sau khi build nằm tại:
   `app/build/outputs/apk/debug/app-debug.apk`

## Tích hợp server và đồng bộ từ project Midterm

Giữ nguyên package `com.example.taskmanagerapp`, giao diện XML, Room và MVVM của
teammate. Các file Compose/Room của Midterm không được chép đè vào app; chức năng
đồng bộ được chuyển sang `sync/SyncEngine.kt` và dùng chung TaskEntity hiện tại.

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
