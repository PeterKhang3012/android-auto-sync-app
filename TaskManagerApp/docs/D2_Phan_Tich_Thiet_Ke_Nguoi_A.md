# TÀI LIỆU PHÂN TÍCH THIẾT KẾ (DELIVERABLE D2 - PHẦN NGƯỜI A)
**Đề tài**: Xây dựng Ứng dụng Quản lý Công việc Hỗ trợ Đồng bộ Tự động (Offline-First Task Manager)  
**Người phụ trách**: Người A (Android Client Lead)  

---

## 1. SƠ ĐỒ CA SỬ DỤNG PHÍA CLIENT (USE CASE DIAGRAM)

```mermaid
flowchart LR
    User(["👤 Người dùng"])

    subgraph App ["Ứng dụng TaskManager (Android Client)"]
        UC1(["UC01: Xem danh sách công việc & Trạng thái"])
        UC2(["UC02: Thêm mới công việc"])
        UC3(["UC03: Chỉnh sửa công việc"])
        UC4(["UC04: Đánh dấu hoàn thành / chưa xong"])
        UC5(["UC05: Xóa công việc (Xóa logic)"])
        UC6(["UC06: Kích hoạt đồng bộ thủ công"])
        UC7(["UC07: Giải quyết xung đột dữ liệu"])
    end

    User --> UC1
    User --> UC2
    User --> UC3
    User --> UC4
    User --> UC5
    User --> UC6
    User --> UC7
```

### Bảng đặc tả các ca sử dụng chính:
- **UC01: Xem danh sách**: Người dùng mở ứng dụng, danh sách công việc hiển thị ngay từ Room DB kèm badge trạng thái đồng bộ (`SYNCED`, `PENDING`, `SYNCING`, `FAILED`).
- **UC02: Thêm mới công việc**: Nhập tiêu đề, mô tả. Dữ liệu được gán UUID, lưu vào Room với `syncStatus = PENDING`, kích hoạt WorkManager.
- **UC03: Sửa công việc**: Cho phép cập nhật tiêu đề, mô tả. Trạng thái chuyển thành `PENDING`, cập nhật timestamp `updatedAt`.
- **UC04: Hoàn thành / Chưa xong**: Tích chọn checkbox, cập nhật trạng thái `isCompleted` trong Room.
- **UC05: Xóa công việc**: Đánh dấu `isDeleted = 1`, ẩn khỏi UI, giữ lại để gửi Tombstone lên server.
- **UC06: Đồng bộ thủ công**: Bấm nút "Đồng bộ ngay" để gọi `enqueueUniqueWork` chạy ngay lập tức.
- **UC07: Giải quyết xung đột**: Khi phát hiện dữ liệu trên server có phiên bản mới hơn phiên bản cơ sở (Base Version), hiển thị dialog so sánh hai phiên bản để người dùng chọn.

---

## 2. SƠ ĐỒ THÀNH PHẦN CLIENT (COMPONENT DIAGRAM)

```mermaid
flowchart TD
    subgraph ViewLayer ["Tầng Giao diện (UI Layer)"]
        MainActivity["MainActivity"]
        TaskAdapter["TaskAdapter (RecyclerView)"]
        Dialogs["Add/Edit & Conflict Dialogs"]
    end

    subgraph ViewModelLayer ["Tầng Trạng thái (ViewModel Layer)"]
        TaskViewModel["TaskViewModel\n(StateFlow / LiveData)"]
    end

    subgraph DomainDataLayer ["Tầng Dữ liệu & Nghiệp vụ (Data Layer)"]
        TaskRepository["TaskRepository\n(Single Source of Truth)"]
    end

    subgraph LocalStorage ["Tầng Lưu trữ Cục bộ (Local Storage)"]
        TaskDatabase["TaskDatabase (Room)"]
        TaskDao["TaskDao (CRUD & Flow queries)"]
        TaskEntity["TaskEntity (SQLite Table: tasks)"]
    end

    subgraph BackgroundSync ["Tầng Đồng bộ Nền (Sync Layer)"]
        WorkManager["Jetpack WorkManager\n(Constraints & Backoff)"]
        SyncWorker["SyncWorker (CoroutineWorker)"]
    end

    subgraph RemoteNetwork ["Tầng Mạng (Network Layer)"]
        RetrofitClient["RetrofitClient"]
        TaskApiService["TaskApiService (REST API)"]
    end

    MainActivity --> TaskViewModel
    TaskAdapter --> MainActivity
    Dialogs --> MainActivity
    TaskViewModel --> TaskRepository
    TaskRepository --> TaskDao
    TaskDao --> TaskDatabase
    TaskDatabase --> TaskEntity
    TaskRepository --> WorkManager
    WorkManager --> SyncWorker
    SyncWorker --> TaskDao
    SyncWorker --> TaskApiService
    TaskApiService --> RetrofitClient
```

---

## 3. SƠ ĐỒ TUẦN TỰ: LUỒNG THÊM CÔNG VIỆC VÀ TỰ ĐỘNG ĐỒNG BỘ (SEQUENCE DIAGRAM)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Người dùng
    participant UI as MainActivity / View
    participant VM as TaskViewModel
    participant Repo as TaskRepository
    participant DB as Room DB (TaskDao)
    participant WM as WorkManager
    participant Worker as SyncWorker
    participant Server as ☁️ Backend Server

    User->>UI: Nhập tiêu đề & Bấm "Lưu"
    UI->>VM: addTask(title, desc)
    VM->>Repo: insertTask(TaskEntity)
    Note over Repo: Gán syncStatus = PENDING<br/>updatedAt = CurrentTime
    Repo->>DB: insertTask(newTask)
    DB-->>UI: Flow phát ra danh sách mới (Hiển thị ngay lập tức - Zero Latency!)
    
    Repo->>WM: enqueueUniqueWork(SyncWorker)
    Note over WM: Kiểm tra Constraints:<br/>Đã có kết nối Internet?
    
    alt Có mạng Internet
        WM->>Worker: doWork()
        Worker->>DB: getPendingTasks()
        DB-->>Worker: Danh sách công việc PENDING
        Worker->>Server: POST /tasks/sync (batch payload)
        Server-->>Worker: HTTP 200 OK (appliedIds)
        Worker->>DB: updateSyncStatus(appliedIds, "SYNCED")
        DB-->>UI: Flow phát ra danh sách cập nhật Badge "Đã đồng bộ" (Màu xanh)
    else Mất mạng Internet (Offline)
        Note over WM: Lưu vào hàng đợi.<br/>Tự động chạy lại ngay khi có kết nối mạng!
    end
```

---

## 4. THIẾT KẾ CƠ SỞ DỮ LIỆU CỤC BỘ ROOM (ERD)

### Bảng: `tasks`

```mermaid
erDiagram
    TASKS {
        string id PK "UUID định danh toàn cầu (Primary Key)"
        string title "Tiêu đề công việc"
        string description "Mô tả chi tiết công việc"
        boolean isCompleted "Trạng thái hoàn thành (0 / 1)"
        string syncStatus "Trạng thái đồng bộ: PENDING, SYNCING, SYNCED, FAILED"
        long updatedAt "Thời điểm cập nhật gần nhất (Unix Epoch ms)"
        boolean isDeleted "Cờ xóa logic: 0 = Bình thường, 1 = Đã xóa"
        long version "Số hiệu phiên bản để phát hiện xung đột"
    }
```

### Mô tả chi tiết các ràng buộc và chỉ mục:
1. **Khóa chính (`id`)**: Dùng kiểu chuỗi `UUID.randomUUID().toString()`, đảm bảo tính duy nhất trên nhiều thiết bị mà không cần chờ Server cấp phát ID tự tăng.
2. **Cờ `isDeleted`**: 
   - Mặc định là `false` (`0`).
   - Khi xóa: Chuyển sang `true` (`1`).
   - Truy vấn hiển thị: `WHERE isDeleted = 0`.
3. **Cột `syncStatus`**:
   - `PENDING`: Cần đẩy lên server khi có mạng.
   - `SYNCING`: Đang trong tiến trình đẩy dữ liệu.
   - `SYNCED`: Đã đồng bộ thành công và trùng khớp với server.
   - `FAILED`: Lỗi mạng hoặc từ chối từ server.
4. **Cột `version`**:
   - Khởi tạo ban đầu là `1L`. Mỗi lần người dùng cập nhật hoặc giải quyết xung đột, giá trị này được tăng lên để hỗ trợ kỹ thuật Optimistic Concurrency Control.
