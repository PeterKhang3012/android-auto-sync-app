# BÁO CÁO LÝ THUYẾT (DELIVERABLE D1 - 50% PHẦN NGƯỜI A)
**Đề tài**: Xây dựng Ứng dụng Quản lý Công việc Hỗ trợ Đồng bộ Tự động (Offline-First Task Manager)  
**Người phụ trách**: Người A (Android Client Lead)  

---

## 1. NGUYÊN LÝ VÀ KIẾN TRÚC OFFLINE-FIRST TRÊN THIẾT BỊ DI ĐỘNG

### 1.1. Khái niệm và Động lực
Trong môi trường mạng di động, kết nối Internet thường không ổn định, chập chờn hoặc thậm chí mất mạng hoàn toàn khi di chuyển (thang máy, tầng hầm, vùng sâu vùng xa). Mô hình truyền thống **Online-First** giả định thiết bị luôn kết nối mạng, mọi thao tác người dùng (tạo, sửa, xóa) đều phải gửi yêu cầu HTTP lên máy chủ và chờ phản hồi thành công mới cập nhật giao diện. Khi mất mạng, ứng dụng bị treo, báo lỗi hoặc hoàn toàn tê liệt, dẫn đến trải nghiệm người dùng (UX) rất kém.

Ngược lại, mô hình **Offline-First** đặt việc lưu trữ cục bộ lên ưu tiên hàng đầu:
- Mọi thao tác tương tác đều được ghi nhận ngay lập tức vào Cơ sở dữ liệu cục bộ (Local Database) trên máy.
- Ứng dụng phản hồi ngay lập tức với người dùng với độ trễ bằng 0 (Zero Latency UI).
- Một tiến trình đồng bộ chạy ngầm (Sync Engine / Background Worker) sẽ tự động phát hiện khi nào có kết nối mạng để đẩy các thay đổi chưa đồng bộ lên máy chủ và kéo dữ liệu mới nhất về.

### 1.2. Mô hình Nguồn chân lý duy nhất (Single Source of Truth - SSOT)
Trong kiến trúc Offline-First, **Room Database đóng vai trò là Nguồn chân lý duy nhất**. 
- Giao diện người dùng (UI) **chỉ lắng nghe (observe)** duy nhất một nguồn dữ liệu phát ra từ Room thông qua reactive stream (`Flow<List<TaskEntity>>`).
- Khi có dữ liệu mới từ Server tải về, dữ liệu đó cũng phải được ghi vào Room DB trước, sau đó Room DB tự động kích hoạt phát (emit) dữ liệu mới ra UI.
- Điều này đảm bảo tính nhất quán tuyệt đối của dữ liệu, loại bỏ hiện tượng UI hiển thị không đồng bộ với CSDL.

---

## 2. MẪU KIẾN TRÚC MVVM VÀ REPOSITORY PATTERN

### 2.1. Kiến trúc MVVM (Model - View - ViewModel)
MVVM là kiến trúc chuẩn được Google khuyến nghị trong phát triển Android hiện đại:
- **View (Activity/Fragment/Composable)**: Chỉ đảm nhận việc hiển thị dữ liệu và tiếp nhận sự kiện từ người dùng (click, nhập liệu). View không chứa bất kỳ logic nghiệp vụ nào.
- **ViewModel**: Là cầu nối giữa View và Model, duy trì và quản lý trạng thái giao diện (UI State). ViewModel sống sót qua các sự kiện cấu hình thay đổi (Configuration Changes) như xoay màn hình, giúp dữ liệu không bị mất và tránh rò rỉ bộ nhớ (Memory Leak).
- **Model**: Đại diện cho tầng dữ liệu và logic nghiệp vụ của ứng dụng (CSDL cục bộ, mạng).

### 2.2. Mẫu Repository (Repository Pattern)
Repository đóng vai trò là một lớp trung gian trừu tượng (Abstraction Layer) che giấu nguồn gốc thực tế của dữ liệu đối với ViewModel:
- ViewModel chỉ gọi các hàm của `TaskRepository` (ví dụ: `allTasks`, `insertTask()`, `deleteTask()`).
- ViewModel hoàn toàn không cần biết dữ liệu được lấy từ SQLite cục bộ hay từ API mạng Retrofit.
- `TaskRepository` chịu trách nhiệm điều phối việc ghi vào Room DB, gán trạng thái `syncStatus = PENDING`, và kích hoạt WorkManager đồng bộ.

---

## 3. LƯU TRỮ DỮ LIỆU CỤC BỘ VỚI ROOM DATABASE & SQLITE

### 3.1. Các thành phần chính của Room
Room là một thư viện Object Mapping (ORM) trừu tượng hóa SQLite trên Android:
1. **Entity (`TaskEntity`)**: Đại diện cho một bảng trong CSDL SQLite. Mỗi trường dữ liệu ánh xạ tương ứng với một cột trong bảng.
2. **DAO - Data Access Object (`TaskDao`)**: Giao diện chứa các hàm truy vấn CSDL. Room tự động sinh mã thực thi lúc biên dịch (Compile-time verification), giúp bắt lỗi cú pháp SQL ngay từ khi build dự án.
3. **Database (`TaskDatabase`)**: Điểm truy cập chính, quản lý kết nối và phiên bản CSDL.

### 3.2. Kỹ thuật Xóa mềm (Soft Delete / Tombstone)
Trong hệ thống đồng bộ dữ liệu hai chiều, nếu xóa cứng bản ghi khỏi SQLite (`DELETE FROM tasks`), thiết bị sẽ mất hoàn toàn thông tin về bản ghi đó và không thể thông báo cho máy chủ biết rằng bản ghi đã bị xóa.
- Giải pháp: Sử dụng cờ `isDeleted = Boolean` (hoặc cờ `is_deleted = 1`).
- Khi xóa: Cập nhật `isDeleted = true`, `updatedAt = currentTime`, `syncStatus = "PENDING"`.
- UI truy vấn: `SELECT * FROM tasks WHERE isDeleted = 0` (bản ghi bị ẩn khỏi giao diện nhưng vẫn nằm trong DB).
- Khi đồng bộ: Đẩy bản ghi có `isDeleted = true` lên server để server đánh dấu tombstone, sau khi server xác nhận thành công mới có thể dọn dẹp (Hard Delete hoặc lưu trữ).

---

## 4. BẤT ĐỒNG BỘ VỚI KOTLIN COROUTINES VÀ STATEFLOW

### 4.1. Kotlin Coroutines
Coroutines là giải pháp bất đồng bộ nhẹ (lightweight threads) trên Android:
- Cho phép viết mã bất đồng bộ theo phong cách tuần tự (sequential style), không bị hiện tượng callback-hell.
- **Dispatchers**:
  - `Dispatchers.Main`: Chạy trên Main Thread để cập nhật giao diện.
  - `Dispatchers.IO`: Tối ưu cho các tác vụ I/O chặn (truy vấn CSDL Room, gọi API mạng Retrofit).
- **Structured Concurrency**: Quản lý vòng đời coroutine thông qua `viewModelScope` và `lifecycleScope`. Khi ViewModel hoặc Activity bị hủy, mọi tác vụ nền liên quan đều tự động bị hủy theo, ngăn ngừa rò rỉ tài nguyên.

### 4.2. Kotlin Flow & StateFlow
- **Flow**: Là một luồng dữ liệu phản ứng bất đồng bộ (Reactive Stream) phát ra nhiều giá trị theo thời gian. Room DAO hỗ trợ trả về `Flow<List<TaskEntity>>`, mỗi khi CSDL thay đổi, Room tự động emit danh sách mới nhất.
- **StateFlow**: Là một Hot Flow lưu giữ trạng thái hiện tại (State-holder), luôn có một giá trị ban đầu và phát ra giá trị mới nhất cho các Collector mới kết nối. Trong ViewModel, ta chuyển đổi Flow từ Room thành StateFlow bằng toán tử `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`, giúp tiết kiệm tài nguyên khi app rơi vào trạng thái nền (Background).

---

## 5. LẬP LỊCH TÁC VỤ NỀN TIN CẬY VỚI ANDROID JETPACK WORKMANAGER

### 5.1. Vai trò của WorkManager
WorkManager là giải pháp chuẩn của Android để lập lịch các tác vụ nền có tính chất **Deferrable** (có thể hoãn lại) và **Guaranteed Execution** (được đảm bảo thực thi ngay cả khi ứng dụng bị tắt hoặc thiết bị khởi động lại).

### 5.2. Các cơ chế nâng cao áp dụng trong dự án
1. **Network Constraints**:
   - Sử dụng `.setRequiredNetworkType(NetworkType.CONNECTED)` để hệ điều hành chỉ kích hoạt `SyncWorker` khi thiết bị có kết nối Internet khả dụng.
2. **Chính sách Thử lại Cấp số nhân (Exponential Backoff Policy)**:
   - Khi `SyncWorker` thất bại do mất mạng đột ngột hoặc máy chủ quá tải (`Result.retry()`), WorkManager sẽ thử lại với thời gian chờ tăng theo cấp số nhân (ví dụ: 10s, 20s, 40s,...), tránh việc spam liên tục làm cạn kiệt pin và quá tải máy chủ.
3. **Đơn nhất hóa Tác vụ (Unique Work Policy)**:
   - Dùng `WorkManager.enqueueUniqueWork("TaskSyncUniqueWork", ExistingWorkPolicy.REPLACE, request)` để đảm bảo tại một thời điểm chỉ có một tiến trình đồng bộ duy nhất được xử lý, loại bỏ tình trạng race condition khi người dùng bấm đồng bộ liên tục.
4. **Tự động phục hồi sau Reboot**:
   - WorkManager tự động lưu trữ thông tin tác vụ vào CSDL nội bộ của hệ điều hành, tự động đăng ký với hệ thống để chạy lại khi điện thoại khởi động lại xong mà không cần viết thêm receiver thủ công.
