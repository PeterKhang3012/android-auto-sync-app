# D7. Báo cáo thực nghiệm và đánh giá

- **Dự án:** Android Auto Sync – TaskManagerApp.
- **Người biên soạn:** PeterKhang3012.
- **Mã kiểm thử:** [TC09SyncBenchmarkTest.kt](../app/src/androidTest/java/com/example/taskmanagerapp/TC09SyncBenchmarkTest.kt).
## 1. Mục tiêu thực nghiệm

Đánh giá khả năng đồng bộ nhiều bản ghi từ cơ sở dữ liệu Room lên server qua repository và WorkManager thực tế. Các chỉ tiêu gồm thời gian hoàn tất, tỷ lệ task được xác nhận đúng, số ID trùng và số bản ghi thiếu/thừa hoặc lệch dữ liệu.

Thực nghiệm sử dụng luồng:

```text
Instrumentation test → TaskRepository → Room và WorkManager
                                      → SyncEngine → Retrofit → server
Đối chiếu độc lập: đọc Room + GET dữ liệu server + trạng thái WorkManager
```

Thời gian đo phản ánh cả quá trình tạo dữ liệu và xử lý đồng bộ, không chỉ tốc độ HTTP. Kết quả chức năng online/offline, đóng/mở app, timeout và xung đột được trình bày riêng trong [D6](D6_Bo_Kiem_Thu_Va_Ket_Qua_Kiem_Thu.md).

## 2. Môi trường thử nghiệm

| Thành phần | Thông tin ghi nhận |
| --- | --- |
| Máy thực nghiệm | Windows, chạy Android Studio và server Node.js |
| Gradle | 9.6.0 theo báo cáo test |
| Loại kiểm thử | Android instrumented test, AndroidJUnit4 |
| Ứng dụng | Package com.example.taskmanagerapp, bản debug |
| Thiết bị lượt 10 task | Small Phone, AVD API 37.0 |
| Thiết bị lượt 50/100 task | Android emulator; chưa có MODEL/API cụ thể trong các dòng SUMMARY |
| Database client | Room thật của ứng dụng, không dùng database in-memory thay thế |
| Bộ lập lịch | WorkManager thật, yêu cầu mạng CONNECTED, manual unique work nối chuỗi |
| Giao tiếp | Retrofit, JSON; BASE_URL hiện tại http://10.0.2.2:3000/ |
| Server | Node.js/Express, cổng 3000, Map trong RAM |
| Đồng hồ đo | SystemClock.elapsedRealtime(), đơn vị mili giây |
| Polling kiểm chứng | Nghỉ khoảng 1 giây giữa các vòng; thời lượng vòng còn gồm đọc Room/work và GET |
| Điều kiện trước đo | Demo Mock tắt, server truy cập được, không có task pending/failed/syncing hoặc manual work chưa kết thúc |

## 3. Dữ liệu kiểm thử và phạm vi từng lượt

| Mức dữ liệu | Số lượt có kết quả | Nội dung nhóm dữ liệu |
| --- | --- | --- |
| 10 task | 1 | 10 ID riêng thuộc runId của lượt |
| 50 task | 1 | 50 ID riêng thuộc runId của lượt |
| 100 task | 1 | 100 ID riêng thuộc runId của lượt |

Mỗi ID có dạng `<runId>-<index>`. Title chứa runId và chỉ số; description chứa N và index. Trạng thái completed xen kẽ theo chỉ số, deleted=false và version khởi tạo 0. Repository cấp updatedAt khi ghi dữ liệu và đặt trạng thái PENDING.

Trước mỗi lượt đo, dữ liệu/cache được xóa thủ công để chuẩn bị lượt mới. Bản thân hàm kiểm thử không thực hiện reset server hoặc xóa dữ liệu. Ba lượt lần lượt tạo 10, 50 và 100 task, tổng cộng 160 task đã tạo trong quá trình thực nghiệm; không có nghĩa cả 160 task cùng tồn tại sau lượt cuối.
## 4. Cách thực hiện và đo lường

### 4.1. Chạy bài kiểm thử

Từ `TaskManagerApp`, khi một emulator đã khởi động và server đang chạy:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.example.taskmanagerapp.TC09SyncBenchmarkTest" `
  "-Pandroid.testInstrumentationRunnerArguments.tc09Enabled=true" `
  "-Pandroid.testInstrumentationRunnerArguments.tc09N=10" `
  "-Pandroid.testInstrumentationRunnerArguments.tc09TimeoutSeconds=180" `
  --rerun-tasks --console=plain
```

Thay tc09N bằng 50 hoặc 100 cho mức tương ứng. Cần tc09Enabled=true để không bị SKIPPED. 

### 4.2. Mốc đo

1. Kiểm tra điều kiện trước đo và gọi GET server; bước này nằm ngoài completedMs.
2. Ghi mốc t0 ngay trước khi tạo task đầu tiên qua repository.
3. Tạo N task. Repository ghi Room và xếp work trong quá trình tạo; WorkManager có thể bắt đầu xử lý trước khi tạo xong toàn bộ nhóm.
4. Ghi insertionMs sau khi tạo đủ N task, rồi xếp thêm một lượt sync.
5. Poll Room, các manual work mới và GET server. Chỉ kết thúc thành công khi toàn bộ tiêu chí ở mục 4.3 đạt.
6. Ghi completedMs, log chi tiết và SUMMARY. Test vẫn giữ lại dữ liệu để đối chiếu.

### 4.3. Điều kiện xác nhận thành công

Một task được verified khi:

- Có ID tương ứng trong dữ liệu kỳ vọng, Room và server.
- Title, description, completed và deleted khớp dữ liệu đã tạo.
- Local mang trạng thái SYNCED.
- Version server lớn hơn 0 và bằng version local.
- updatedAt của local và server bằng nhau.

Toàn lượt PASS khi verified=N, không có ID trùng, không có bản thiếu/thừa hoặc chưa khớp, và các manual work mới quan sát được đều SUCCEEDED.

### 4.4. Công thức và ý nghĩa chỉ số

```text
completedMs = mốc xác nhận đủ điều kiện thành công - t0
insertionMs = mốc tạo xong N task - t0
Tỷ lệ task xác nhận đúng = verified / requested × 100%
Tỷ lệ lượt PASS trong mẫu đo = số lượt PASS / số lượt đã thực sự chạy đo × 100%
ID trùng = số bản server trong nhóm - số ID khác nhau trong nhóm
```

`mismatchedIds` đếm ID thiếu/thừa hoặc task chưa đạt điều kiện đối chiếu trong hợp tập ID của nhóm kỳ vọng, local và server. Không dùng số liệu menu kiểm tra dữ liệu toàn app thay cho chỉ số nhóm test.

`completedMs` gồm tạo Room, thời gian xếp hàng và xử lý WorkManager, đồng bộ HTTP, cùng polling xác nhận. Không tách được thời gian từng thành phần từ SUMMARY. `insertionMs` là một phần của vùng đo; không cộng nó lần nữa vào completedMs hoặc coi phép trừ là thời gian HTTP thuần vì việc tạo và sync có thể chồng lấn.

`observedElapsedMs` được ghi gần cuối bài đo, gồm thêm thời gian xử lý sau mốc thành công. Thời lượng test HTML và thời gian BUILD SUCCESSFUL của Gradle là các phạm vi khác, không thay thế completedMs.

## 5. Bảng kết quả đo

### 5.1. Thời gian và tỷ lệ xác nhận

| Số task | Số lượt | Tạo dữ liệu insertionMs | Hoàn tất completedMs | Hoàn tất (giây) | observedElapsedMs | Task xác nhận đúng | Tỷ lệ task đúng | Kết quả |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 10 | 1 | 131 ms | 2493 ms | 2.493 s | 2508 ms | 10/10 | 100% | PASS |
| 50 | 1 | 108 ms | 2553 ms | 2.553 s | 2570 ms | 50/50 | 100% | PASS |
| 100 | 1 | 1150 ms | 22964 ms | 22.964 s | 23120 ms | 100/100 | 100% | PASS |

Các giá trị là thời gian của từng lượt, **không phải trung bình nhiều lượt**. Tổng task xác nhận đúng là 160/160; tỷ lệ lượt PASS trong ba lượt đo đã chạy là 3/3. Đây không phải tỷ lệ HTTP request thành công hoặc xác suất thành công dài hạn.

### 5.2. Tính nhất quán và trạng thái work

| Số task | Local / server của nhóm | ID trùng | ID/nội dung lệch | Manual work | Lỗi cuối lượt |
| --- | --- | --- | --- | --- | --- |
| 10 | 10 / 10 | 0 | 0 | 11 SUCCEEDED | null |
| 50 | 50 / 50 | 0 | 0 | 51 SUCCEEDED | null |
| 100 | 100 / 100 | 0 | 0 | 101 SUCCEEDED | null |

`error=null` là không có lỗi kết thúc được ghi trong SUMMARY; không dùng để kết luận không từng có retry hoặc lỗi trung gian. Không có số đếm request trung gian trong log này.

### 5.3. Định danh minh chứng

| Số task | Thời điểm SUMMARY theo Logcat ngày 31/10/2026 | RunId |
| --- | --- | --- |
| 10 | 01:14:21.779 | TC09-1793384058344-3f575532-3bc5-4ee4-848c-93251ed42727 |
| 50 | 17:15:20.638 | TC09-1793441717386-d1cba48e-35d8-4840-b7d8-915bb1589510 |
| 100 | 17:22:55.394 | TC09-1793442151194-c8d05352-d7ff-4c61-8f21-49d217201232 |

Đây là thời điểm ghi log, không phải mốc t0. Các timestamp JSON được giữ nguyên trong phụ lục; thời gian đo dùng đồng hồ monotonic, không lấy chênh lệch giờ hiển thị Logcat.

## 6. Phân tích kết quả

### 6.1. Thời gian đồng bộ

Lượt 10 task hoàn tất trong 2.493 giây, lượt 50 task trong 2.553 giây, chênh 60 ms. Hai giá trị gần nhau chưa chứng minh chi phí đồng bộ 10 và 50 task tương đương. Với polling khoảng một giây và thời gian chờ WorkManager, khác biệt xử lý bên trong có thể không thể hiện đầy đủ ở mốc xác nhận.

Lượt 100 task hoàn tất trong 22.964 giây, cao hơn rõ rệt so với hai lượt còn lại. Thời gian tạo dữ liệu cũng tăng lên 1150 ms, so với 108 ms của lượt 50 task. Việc 50 task được tạo nhanh hơn 10 task cho thấy số đo còn chịu ảnh hưởng của trạng thái chạy và lịch xử lý, không chỉ số lượng task.

### 6.2. Tỷ lệ thành công và nhất quán

Ba lượt đều đạt tiêu chí cuối: đủ task, đúng dữ liệu, local SYNCED, version/updatedAt khớp server và work hoàn tất. Không ghi nhận ID trùng hoặc bản lệch trong ba nhóm được đo. Kết quả thể hiện luồng Room → repository → WorkManager → server hoạt động trong các lượt thực nghiệm này.

Tỷ lệ 100% chỉ mô tả mẫu đã đo. Không đại diện cho mọi điều kiện mất mạng, timeout, retry, nhiều thiết bị hoặc server restart. TC09 cũng không thay thế ca TC07 về hai thiết bị sửa cùng một task.

### 6.3. Đối chiếu D4–D6

- D4 mô tả server dùng Map RAM: các lượt đo này không kiểm chứng dữ liệu tồn tại sau khi restart.
- D5 mô tả push tối đa hai lượt trong một phiên và retry qua WorkManager: SUMMARY cho biết trạng thái cuối, không cho biết số phiên hoặc request đã dùng để hoàn tất.
- D6 ghi TC09 10 task với completedMs=2493 và toàn bài test 3.572 giây; hai số không mâu thuẫn vì khác mốc đo. D7 bổ sung hai lượt 50 và 100 task, không thay đổi kết quả lượt 10 task.
- Các phép đo API localhost trên Linux trước đây có phạm vi và môi trường khác. Không gộp chúng vào bảng Android này hoặc dùng để tính tốc độ chênh lệch trực tiếp.

## 7. Hạn chế của thực nghiệm

- Mỗi mức dữ liệu mới có một lượt; chưa có trung bình, độ lệch chuẩn hoặc đánh giá độ ổn định qua nhiều lần lặp.
- Chưa xác nhận cùng model/cấu hình emulator và trạng thái tài nguyên cho cả ba lượt. Thiếu thông tin phần cứng và phiên bản công cụ đầy đủ.
- Thời gian gồm tạo dữ liệu, hàng đợi, HTTP và polling; chưa tách được đóng góp của từng thành phần. GET kiểm chứng tạo thêm tải và có thể đọc cả dữ liệu server ngoài nhóm trước khi lọc.
- Dữ liệu các lượt trước được giữ lại; quy mô kho server và Room nền có thể khác nhau giữa các lượt, nhưng chưa có log PREFLIGHT_OK đầy đủ để định lượng.
- Periodic work có sẵn không bị test hủy; hoạt động nền có thể ảnh hưởng lịch xử lý. SUMMARY chỉ tổng hợp manual work mới quan sát được.
- Test đối chiếu một nhóm dữ liệu local/server; không đo toàn bộ dữ liệu app, độ trễ cập nhật UI, hai thiết bị đồng thời hoặc hội tụ xung đột.
- Chưa đo CPU/RAM tiêu thụ, thời gian reboot/force-stop, độ bền dữ liệu server hoặc hiệu năng LAN/WAN.
- Không có số đếm HTTP lỗi/retry trung gian nên không suy ra tỷ lệ thành công trên mỗi request.
- Chỉ mức lớn nhất 100 task và không có tải đồng thời; chưa đủ kết luận khả năng mở rộng hoặc giới hạn tải.

## 8. Kết luận

Trong ba lượt thực nghiệm Android với 10, 50 và 100 task, cả ba lượt đều PASS, xác nhận đúng 160/160 task, không ghi nhận ID trùng hoặc dữ liệu lệch trong nhóm thử. Thời gian hoàn tất lần lượt là **2.493, 2.553 và 22.964 giây** theo cùng định nghĩa đo bao gồm tạo dữ liệu, WorkManager và kiểm chứng server.

Kết quả cho thấy mô-đun đồng bộ có thể xử lý nhiều bản ghi qua luồng ứng dụng thật trong điều kiện thực nghiệm đã ghi nhận. Lượt 100 task có thời gian cao hơn đáng kể nhưng chưa đủ dữ liệu để xác định nguyên nhân hoặc dự đoán hiệu năng dài hạn. Kết luận chỉ áp dụng ba lượt đo này; không mở rộng thành bảo đảm nhất quán giữa hai thiết bị, tự phục hồi mọi lỗi mạng hoặc lưu dữ liệu bền vững sau restart.

## Phụ lục A. Log SUMMARY minh chứng

Các JSON sau giữ nguyên giá trị của ba dòng SUMMARY. Chúng là căn cứ cho bảng kết quả, không phải dữ liệu mẫu giả định.

### A.1. Lượt 10 task

```json
{
  "event": "SUMMARY",
  "runId": "TC09-1793384058344-3f575532-3bc5-4ee4-848c-93251ed42727",
  "timestampMs": 1793384061779,
  "status": "PASS",
  "requested": 10,
  "created": 10,
  "verified": 10,
  "successRatePercent": 100,
  "completedMs": 2493,
  "observedElapsedMs": 2508,
  "insertionMs": 131,
  "duplicateIds": 0,
  "mismatchedIds": 0,
  "workStates": "{SUCCEEDED=11}",
  "lastObservation": "GET OK; expected=10 local=10 server=10",
  "error": null,
  "scope": "Only this runId group; no two-device or UI measurement"
}
```

### A.2. Lượt 50 task

```json
{
  "event": "SUMMARY",
  "runId": "TC09-1793441717386-d1cba48e-35d8-4840-b7d8-915bb1589510",
  "timestampMs": 1793441720638,
  "status": "PASS",
  "requested": 50,
  "created": 50,
  "verified": 50,
  "successRatePercent": 100,
  "completedMs": 2553,
  "observedElapsedMs": 2570,
  "insertionMs": 108,
  "duplicateIds": 0,
  "mismatchedIds": 0,
  "workStates": "{SUCCEEDED=51}",
  "lastObservation": "GET OK; expected=50 local=50 server=50",
  "error": null,
  "scope": "Only this runId group; no two-device or UI measurement"
}
```

### A.3. Lượt 100 task

```json
{
  "event": "SUMMARY",
  "runId": "TC09-1793442151194-c8d05352-d7ff-4c61-8f21-49d217201232",
  "timestampMs": 1793442175394,
  "status": "PASS",
  "requested": 100,
  "created": 100,
  "verified": 100,
  "successRatePercent": 100,
  "completedMs": 22964,
  "observedElapsedMs": 23120,
  "insertionMs": 1150,
  "duplicateIds": 0,
  "mismatchedIds": 0,
  "workStates": "{SUCCEEDED=101}",
  "lastObservation": "GET OK; expected=100 local=100 server=100",
  "error": null,
  "scope": "Only this runId group; no two-device or UI measurement"
}
```

### A.4. Báo cáo test lượt 10 task

```text
Class: com.example.taskmanagerapp.TC09SyncBenchmarkTest
Tests: 1
Failures: 0
Skipped: 0
Duration: 3.572s
Successful: 100%
Generated by Gradle 9.6.0 at Oct 31, 2026, 1:14:23 AM
```

Lượt 50/100 task có SUMMARY PASS nhưng chưa có HTML riêng đính kèm trong bộ minh chứng này. Logcat có thể lọc bằng `tag:TC09`; log đầy đủ nằm tại `files/tc09_logs/<runId>.jsonl` trong vùng dữ liệu private của app. Chi tiết cách chạy và lấy log nằm trong TC09 của D6.
