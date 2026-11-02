# DELIVERABLE D3: MÃ NGUỒN ỨNG DỤNG ANDROID

- **Đề tài**: Xây dựng Ứng dụng Android Hỗ trợ Hoạt động Ngoại tuyến và Tự động Đồng bộ (Offline-First Task Manager)
- **Người phụ trách**: Người A (Android Client Lead)
- **Yêu cầu đề bài**: Ứng dụng Kotlin có Room, ViewModel, Repository, Retrofit, WorkManager; giao diện hiển thị dữ liệu và trạng thái đồng bộ.
- **Minh chứng nộp**: Repository Git, hướng dẫn build và chạy.

---

## 1. THÔNG TIN REPOSITORY GIT
- **URL Repository GitHub**: `https://github.com/PeterKhang3012/android-auto-sync-app.git`
- **Nhánh triển khai chính**: `feature/my-sync` (hoặc `task-setup`)
- **Lệnh Clone dự án**:
  ```bash
  git clone https://github.com/PeterKhang3012/android-auto-sync-app.git
  ```
- **Thư mục chứa mã nguồn ứng dụng**: `TaskManagerApp/`

---

## 2. HƯỚNG DẪN BUILD VÀ CHẠY ỨNG DỤNG (BUILD & RUN GUIDE)

### Bước 1: Yêu cầu môi trường phát triển
- **Android Studio**: Bản Hedgehog (2023.1.1) trở lên (khuyên dùng Ladybug / Koala / Jellyfish).
- **JDK**: JDK 17 hoặc 21 (mặc định tích hợp sẵn trong Android Studio).
- **Android SDK**: Compile SDK 34, Min SDK 26.
- **Thiết bị**: Máy ảo Android (Emulator) hoặc điện thoại thật Android 8.0 trở lên.

### Bước 2: Mở dự án trong Android Studio
1. Khởi động **Android Studio**.
2. Chọn **Open** và dẫn tới thư mục: `android-auto-sync-app/TaskManagerApp`.
3. Chờ phần mềm tự động tải thư viện và đồng bộ Gradle (Gradle Sync) cho đến khi hiển thị thông báo thành công.

### Bước 3: Chuẩn bị thiết bị và Chạy ứng dụng
1. **Khởi động thiết bị**:
   - *Cách 1 (Máy ảo)*: Mở **Device Manager** ở góc phải Android Studio ➔ Bấm nút **Play (▶)** ở dòng máy ảo (ví dụ: `Pixel 6a`).
   - *Cách 2 (Điện thoại thật)*: Cắm cáp USB vào máy tính và bật **Gỡ lỗi USB (USB Debugging)** trên điện thoại.
2. **Chạy ứng dụng**:
   - Chọn cấu hình chạy là `app` và chọn thiết bị đích trên thanh công cụ trên cùng.
   - Bấm nút **Run 'app' (biểu tượng tam giác xanh ▶)** hoặc nhấn tổ hợp phím **`Shift + F10`**.
   - Ứng dụng sẽ tự động được biên dịch, cài đặt và mở lên trên màn hình.

---

## 3. HƯỚNG DẪN BIÊN DỊCH VÀ CÀI ĐẶT BẰNG DÒNG LỆNH (GRADLE CLI)

Mở cửa sổ Terminal (hoặc CMD / PowerShell) tại thư mục `TaskManagerApp`:

### 3.1. Lệnh biên dịch tạo file APK cài đặt (Debug APK)
- **Trên Windows (PowerShell / Terminal Android Studio)**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **Trên Windows (CMD cũ)**:
  ```cmd
  gradlew.bat assembleDebug
  ```
- **Trên macOS / Linux**:
  ```bash
  ./gradlew assembleDebug
  ```
- File APK cài đặt sau khi build thành công nằm tại:
  ```text
  app/build/outputs/apk/debug/app-debug.apk
  ```

### 3.2. Cài đặt file APK trực tiếp vào thiết bị qua ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3.3. Chạy bộ kiểm thử tự động (Automated Tests)
- Chạy Unit Test trên máy tính:
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```
- Chạy kiểm thử tự động Room Database trên thiết bị/máy ảo:
  ```powershell
  .\gradlew.bat connectedAndroidTest
  ```
