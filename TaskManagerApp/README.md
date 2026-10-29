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
