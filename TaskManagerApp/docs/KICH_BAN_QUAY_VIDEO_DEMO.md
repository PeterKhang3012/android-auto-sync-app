# KỊCH BẢN QUAY VIDEO DEMO (PHONG CÁCH TỰ NHIÊN, SINH VIÊN BÁO CÁO)
**Đề tài**: Xây dựng Ứng dụng Android Hỗ trợ Hoạt động Ngoại tuyến và Tự động Đồng bộ (Offline-First Task Manager)  
**Thời lượng**: khoảng 3 - 4 phút  
**Bố cục màn hình**:
- Nửa màn hình bên trái: **Máy ảo Pixel 6a** (đang mở sẵn app).
- Nửa màn hình bên phải: **Trình duyệt Chrome** vào sẵn link `http://localhost:3000/tasks` (tích ô `Pretty-print`).

---

## ⏱️ ĐOẠN 1: MỞ ĐẦU & KHỞI ĐỘNG SERVER (khoảng 30 giây)

* **Thao tác tay**: 
  - Chỉ chuột vào màn hình đen của Server đang chạy ở cổng 3000.
  - Chỉ chuột sang màn hình máy ảo bên trái và trang web bên phải.
* **Lời nói tự nhiên**:
  > *"Dạ em chào thầy cô! Sau đây em xin đại diện nhóm demo sản phẩm ứng dụng quản lý công việc Offline-First và đồng bộ dữ liệu tự động.*  
  > *Về phần chuẩn bị, app tụi em đã build sẵn ra file `app-debug.apk` để có thể cài lên máy thật dùng luôn. Còn phía server thì tụi em viết bằng Node.js, chỉ cần gõ `npm ci` rồi `npm start`, hoặc bấm file `run.bat` là server chạy lên ở cổng 3000 như màn hình bên này.*  
  > *Bây giờ em sẽ test lần lượt các trường hợp từ TC01 đến TC09 theo yêu cầu của đề tài ạ."*

---

## ⏱️ ĐOẠN 2: TEST TC01 & TC06 - TẠO VIỆC ONLINE & BẤM SYNC NHIỀU LẦN (khoảng 40 giây)

* **Thao tác tay**:
  1. Máy ảo đang có mạng bình thường. Bấm nút **"+ Thêm việc"**.
  2. Gõ:
     - Tiêu đề: `TC01 Tao viec online`
     - Mô tả: `Kiem thu co mang day du`
     - Bấm **Lưu**.
  3. Bấm nút **"Đồng bộ ngay"**: Thấy badge đổi sang xanh lá `✓ Đã đồng bộ`.
  4. Bấm liên tiếp nút **"Đồng bộ ngay" thêm 3 lần nữa** (Click: 1, 2, 3).
  5. Sang tab Chrome bên phải, bấm phím **F5**.
* **Lời nói tự nhiên**:
  > *"Đầu tiên là trường hợp có mạng bình thường (**TC01**). Em thêm một việc mới với tiêu đề là 'TC01 Tao viec online', mô tả là 'Kiem thu co mang day du' rồi bấm Lưu. Xong em bấm 'Đồng bộ ngay'. Thầy cô thấy badge chuyển ngay sang màu xanh lá 'Đã đồng bộ'. Em qua web F5 lại thì task này đã xuất hiện trên server rồi ạ.*  
  > *Tiện đây em test luôn trường hợp bấm đồng bộ nhiều lần (**TC06**). Em bấm nút Đồng bộ liên tục 3 lần nữa. Qua bên server F5 lại thì vẫn chỉ có duy nhất 1 bản ghi này thôi, do tụi em dùng mã UUID cố định và quản lý version nên hoàn toàn không bị trùng lặp dữ liệu."*

---

## ⏱️ ĐOẠN 3: TEST TC02 & TC08 - TẮT MẠNG, TẠO SỬA & XÓA KHI OFFLINE (khoảng 50 giây)

* **Thao tác tay**:
  1. Kéo thanh thông báo trên máy ảo xuống, bấm **Chế độ máy bay ✈️** để ngắt mạng.
  2. Bấm **"+ Thêm việc"**, gõ: `TC02 Cong viec offline`, bấm **Lưu**.
  3. Bấm biểu tượng cây bút sửa tiêu đề thành: `TC02 Cong viec offline da sua`, bấm **Cập nhật**.
  4. Bấm biểu tượng thùng rác ở task `TC01`, bấm **Xóa**.
  5. Chỉ chuột vào badge màu cam `⏳ Chờ đồng bộ` và bấm thử "Đồng bộ ngay".
* **Lời nói tự nhiên**:
  > *"Bây giờ em sẽ giả lập tình huống điện thoại bị mất mạng (**TC02**). Em kéo xuống bật Chế độ máy bay lên.*  
  > *Lúc này đang offline, em tạo một task mới là 'TC02 Cong viec offline'. Thầy cô thấy bấm Lưu một cái là việc hiện lên ngay lập tức, không có độ trễ gì hết vì nó lưu thẳng vào Room SQLite dưới máy. Em bấm sửa việc lại thành 'da sua' nó cũng cập nhật liền, và badge hiện màu cam là 'Chờ đồng bộ'. Em bấm nút đồng bộ thử thì app báo đang chờ mạng chứ không bị đơ app.*  
  > *Tiếp theo em test xóa việc khi đang mất mạng (**TC08**). Em bấm xóa task TC01 đi. Task biến mất trên màn hình nhưng thực chất trong database Room tụi em chỉ đánh dấu cờ `isDeleted = true` chứ chưa xóa hẳn để tí nữa còn đồng bộ lên server."*

---

## ⏱️ ĐOẠN 4: TEST TC04 & TC03 - TẮT HẲN APP RỒI BẬT LẠI MẠNG (khoảng 40 giây)

* **Thao tác tay**:
  1. Vuốt tắt hẳn app TaskManager đi (kill app).
  2. Bấm vào icon TaskManager mở app lên lại ➔ Task `TC02` vẫn còn nguyên.
  3. Kéo thanh thông báo xuống, **Tắt Chế độ máy bay** (Bật lại mạng Wi-Fi 📶).
  4. Bấm **"Đồng bộ ngay"** (hoặc đợi vài giây WorkManager tự chạy).
  5. Badge cam đổi sang màu xanh lá `✓ Đã đồng bộ`.
  6. Sang web Chrome bấm **F5**: Thấy task `TC02` xuất hiện, còn task `TC01` thì có chữ `"deleted": true`.
* **Lời nói tự nhiên**:
  > *"Để chứng minh dữ liệu không bị mất khi thoát app (**TC04**), em tắt hẳn ứng dụng đi rồi mở lại. Thầy cô thấy task offline hồi nãy vẫn nằm nguyên ở đây.*  
  > *Giờ em tắt Chế độ máy bay để máy có mạng trở lại (**TC03**). Em bấm 'Đồng bộ ngay' thì WorkManager sẽ chạy ngầm và đẩy dữ liệu lên. Badge cam đã tự động đổi sang màu xanh lá.*  
  > *Em qua web F5 lại: Task TC02 tạo lúc mất mạng giờ đã lên server rồi. Còn task TC01 em xóa lúc nãy thì server nhận được cờ `deleted: true`. Đây là kỹ thuật Tombstone giúp server biết task đã xóa để sau này không bị tải ngược về máy lại."*

---

## ⏱️ ĐOẠN 5: TEST TC05 - TEST LỖI SERVER 500 & TỰ THỬ LẠI (khoảng 40 giây)

* **Thao tác tay**:
  1. Bấm menu **3 chấm** góc trên bên phải app.
  2. Chọn: **"Giả lập lỗi server / timeout"** ➔ Chọn: **"Lỗi HTTP 500"**.
  3. Bấm **"+ Thêm việc"**, gõ: `TC05 Thu loi server`, bấm **Lưu**.
  4. Bấm **"Đồng bộ ngay"**: Thấy badge chuyển sang màu đỏ `⚠ Lỗi đồng bộ`.
  5. Bấm menu 3 chấm ➔ Chọn lại mục lỗi ➔ Đổi lại thành **"Bình thường"**.
  6. Bấm lại "Đồng bộ ngay": Badge đỏ tự đổi sang màu xanh lá `✓ Đã đồng bộ`.
* **Lời nói tự nhiên**:
  > *"Tiếp theo là **TC05**: Kiểm tra khi server bị lỗi hoặc chập chờn.*  
  > *Ở menu 3 chấm này tụi em có làm sẵn nút giả lập lỗi HTTP 500 trên server. Em bật lỗi lên, xong em thêm một task là 'TC05 Thu loi server' rồi bấm đồng bộ. Server báo lỗi nhưng app không hề bị văng hay mất dữ liệu, mà badge chuyển sang màu đỏ báo lỗi. WorkManager sẽ tự động xếp lịch để thử lại sau.*  
  > *Giờ em chỉnh server về lại trạng thái Bình thường và bấm đồng bộ lại, thì task lập tức được gửi thành công và chuyển sang màu xanh liền ạ."*

---

## ⏱️ ĐOẠN 6: TEST TC07 & TC09 - XỬ LÝ XUNG ĐỘT & XEM THỐNG KÊ (khoảng 45 giây)

* **Thao tác tay**:
  1. Nhấn và **giữ lì (Long click)** vào task `TC02 Cong viec offline da sua` khoảng 1.5 giây.
  2. Menu hiện ra ➔ Chọn: **"Mô phỏng xung đột dữ liệu (Conflict Demo)"**.
  3. Hộp thoại bật lên: Chỉ chuột vào khung cam (bản máy này) và khung xanh (bản máy B trên server).
  4. Bấm nút: **"Lấy bản Máy Chủ"**.
  5. Tiêu đề trên app đổi thành có đuôi `[máy B]`.
  6. Bấm menu **3 chấm** góc phải ➔ Chọn: **"Thống kê đồng bộ thật"**. Giữ hộp thoại khoảng 3 giây.
* **Lời nói tự nhiên**:
  > *"Kế tiếp là phần quan trọng nhất: Xử lý xung đột (**TC07**).*  
  > *Em nhấn giữ vào task TC02 để giả lập tình huống có một bạn khác dùng máy B cũng vừa sửa task này trên server. Lúc này app phát hiện xung đột và hiện hộp thoại cho người dùng tự chọn: Khung cam là bản trên máy em, còn khung xanh là bản từ server có thêm chữ `[máy B]`. Em bấm 'Lấy bản Máy Chủ' thì app lập tức cập nhật theo bản server mà không bị lỗi ghi đè mất dữ liệu.*  
  > *Cuối cùng là **TC09**: Em mở phần 'Thống kê đồng bộ thật' lên. Ở đây app ghi nhận chi tiết: tổng số lần đồng bộ, số lần thành công, tốc độ mạng trung bình bao nhiêu mili-giây và số lần bị xung đột để tụi em làm báo cáo thực nghiệm D7 ạ."*

---

## ⏱️ ĐOẠN 7: KẾT THÚC (10 giây)

* **Thao tác tay**: Bấm Đóng hộp thoại, quay lại màn hình chính của app.
* **Lời nói tự nhiên**:
  > *"Dạ phần demo của nhóm em đã đi qua đầy đủ 9 trường hợp kiểm thử của đề tài. Em cảm ơn thầy cô đã theo dõi ạ!"*  
*(Bấm dừng quay video)*
