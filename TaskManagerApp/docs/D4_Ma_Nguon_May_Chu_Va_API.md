# D4. Mã nguồn máy chủ và API

- **Dự án:** Android Auto Sync – TaskManagerApp.
- **Người biên soạn:** PeterKhang3012.
- **Ngày đối chiếu và kiểm thử:** 30/10/2026.
- **Phiên bản mã nguồn được mô tả:** nhánh `feature/my-sync`, commit `5e4e828`.
- **Phạm vi:** tài liệu API, mã nguồn tham chiếu, dữ liệu mẫu và hướng dẫn triển khai server demo. Tài liệu mô tả hành vi đang có, không bổ sung hoặc sửa mã nguồn.

## 1. Mục tiêu và minh chứng bàn giao

Máy chủ cung cấp dữ liệu công việc dùng chung cho các thiết bị Android. Thiết bị có thể chỉnh sửa dữ liệu cục bộ khi mất mạng, sau đó gửi thay đổi lên máy chủ và lấy các bản ghi thay đổi về khi đồng bộ.

| Nội dung D4 | Cách thực hiện hiện tại | Minh chứng |
| --- | --- | --- |
| API CRUD | GET truy vấn; PUT tạo/cập nhật và xóa mềm | [Server.js](../server/Server.js), mục 4–5 |
| Dữ liệu dùng chung | Một `Map` trong tiến trình server, các thiết bị gọi cùng server | Mục 2 và 8 |
| Định danh bản ghi | UUID do Android tạo, dùng làm khóa `id` | [TaskEntity.kt](../app/src/main/java/com/example/taskmanagerapp/data/local/TaskEntity.kt), mục 3 |
| Thông tin cập nhật | `version`, `updatedAt`, `seq` | Mục 3 và 6 |
| Xử lý yêu cầu đồng bộ | PUT kiểm tra version; GET lọc delta; HTTP 409 trả bản server | Mục 4 và 6 |
| Dữ liệu mẫu | JSON và các lệnh PowerShell tạo/sửa/xóa task | Mục 5 |
| Hướng dẫn triển khai | Node.js, script chạy, cấu hình địa chỉ và kiểm tra kết nối | Mục 7–8 |

Không có endpoint DELETE riêng: xóa công việc được thực hiện bằng PUT với `deleted: true`. Không có API batch `/tasks/sync` trong phiên bản này.

## 2. Kiến trúc và tổ chức mã nguồn

```text
Android A: Room → Retrofit ──┐
                            ├── HTTP/JSON → Express → Map<id, task> trong RAM
Android B: Room → Retrofit ──┘
```

Hai thiết bị chỉ dùng chung dữ liệu khi kết nối tới cùng một tiến trình server. Nếu mỗi người chạy một server trên máy riêng thì đó là hai kho dữ liệu độc lập.

| File | Vai trò |
| --- | --- |
| [server/Server.js](../server/Server.js) | Express, kho dữ liệu RAM, API task và API giả lập |
| [server/package.json](../server/package.json) | Dependency Express và các lệnh `start`, `test` |
| [server/package-lock.json](../server/package-lock.json) | Khóa phiên bản dependency và thông tin integrity dùng bởi npm |
| [server/test/sync.test.js](../server/test/sync.test.js) | Kiểm thử HTTP tích hợp của API |
| [run.bat](../run.bat), [run.sh](../run.sh) | Cài dependency và chạy server |
| [TaskApiService.kt](../app/src/main/java/com/example/taskmanagerapp/data/remote/TaskApiService.kt) | Khai báo API Retrofit phía Android |
| [TaskDto.kt](../app/src/main/java/com/example/taskmanagerapp/data/remote/TaskDto.kt) | DTO và ánh xạ dữ liệu local/remote |
| [SyncEngine.kt](../app/src/main/java/com/example/taskmanagerapp/sync/SyncEngine.kt) | Client push/pull và xử lý phản hồi xung đột |

Server dùng Node.js, CommonJS và Express. Khi chạy `node Server.js`, server lắng nghe cổng **3000**. Khi được import bởi test, module xuất Express app để test tự chọn cổng tạm.

## 3. Mô hình dữ liệu và quy ước

### 3.1. Địa chỉ và định dạng

- Trên máy chạy server: `http://localhost:3000`.
- Android Emulator trên cùng máy: `http://10.0.2.2:3000/`.
- Thiết bị khác trong LAN: `http://<IP-LAN-may-chu>:3000/`.
- PUT gửi `Content-Type: application/json`.
- API task thành công trả JSON; các lỗi kiểm tra dữ liệu dùng `sendStatus` có thể trả văn bản, không phải JSON.
- Phiên bản demo chưa có xác thực, phân quyền hoặc HTTPS.

### 3.2. Cấu trúc task

| Trường | Kiểu | Nguồn và ý nghĩa |
| --- | --- | --- |
| `id` | string | Lấy từ đường dẫn `/tasks/:id`; Android tạo UUID. Server không kiểm tra định dạng UUID. ID trong body không quyết định khóa lưu trữ. |
| `title` | string | Bắt buộc khi PUT; hiện server chấp nhận cả chuỗi rỗng. |
| `description` | string hoặc null | Tùy chọn; nếu bỏ qua thì response có thể không chứa trường này. |
| `completed` | boolean | Bắt buộc; trạng thái hoàn thành. |
| `deleted` | boolean | Bắt buộc; dấu xóa mềm. |
| `version` | số nguyên an toàn, không âm | Bắt buộc trong request; phiên bản server mà bản sửa dựa vào. Server cấp phiên bản mới khi chấp nhận PUT. |
| `updatedAt` | số nguyên an toàn, không âm | Bắt buộc; quy ước timestamp Unix theo mili giây. PUT giữ giá trị client gửi; không tự đổi thành thời gian server. |
| `seq` | số nguyên | Server cấp sau mỗi PUT được chấp nhận hoặc thao tác simulate-edit; client không được tự quyết định giá trị này. |

Tên JSON là **`completed`** và **`deleted`**, khác tên thuộc tính Kotlin `isCompleted`, `isDeleted`. DTO Retrofit thực hiện ánh xạ này.

Với ID chưa tồn tại, client nên gửi `version: 0`; server tạo bản ghi với version 1. Hiện server chưa bắt buộc version của bản tạo mới phải bằng 0. Với ID đã tồn tại, request phải gửi đúng version hiện tại, nếu khác thì trả 409.

### 3.3. Response lấy thay đổi

```json
{
  "tasks": [],
  "serverTime": 0
}
```

`serverTime` là **cursor số thứ tự**, không phải timestamp và không phải giờ hệ thống. `seq` tăng chung trong tiến trình, còn `version` tăng riêng cho từng task. Kho RAM chỉ giữ bản mới nhất của mỗi ID; đây không phải nhật ký chứa mọi lần chỉnh sửa.

## 4. Đặc tả API

### 4.1. Danh sách endpoint

| Method | Đường dẫn | Chức năng | Response chính |
| --- | --- | --- | --- |
| GET | `/tasks?updatedAfter=<cursor>` | Lấy các task có `seq > cursor`, gồm cả task đã xóa mềm | 200, `{tasks, serverTime}` |
| PUT | `/tasks/:id` | Tạo mới hoặc thay thế nội dung task; xóa mềm bằng `deleted: true` | 200, task mới; 400; 409 |
| POST | `/admin/simulate-edit/:id` | Giả lập thiết bị khác sửa task trên server | 200, task mới; 404 |
| POST | `/admin/chaos?failRate=<n>&delayMs=<n>` | Cấu hình lỗi và độ trễ giả lập | 200, `{failRate, delayMs}`; 400 |
| POST | `/admin/reset` | Xóa toàn bộ Map để chuẩn bị lại demo | 204, không có body |

Các endpoint `/tasks` còn có thể nhận HTTP 500 do chaos hoặc bị client timeout khi server phản hồi chậm. Các endpoint `/admin/*` được khai báo trước middleware chaos nên vẫn dùng được để tắt lỗi giả lập.

### 4.2. GET /tasks

- `updatedAfter`: cursor của lần pull trước; bỏ qua hoặc gửi 0 để lấy toàn bộ bản ghi hiện có, kể cả tombstone.
- Server hiện chuyển tham số thành số qua `+req.query.updatedAfter || 0`, chưa kiểm tra cursor không âm hoặc số nguyên. Nên chỉ gửi cursor hợp lệ đã nhận từ server.
- Không có bản thay đổi vẫn trả 200 với `tasks: []` và cursor hiện tại.
- Không lọc bỏ `deleted: true`: client cần dấu này để ẩn task trên các thiết bị khác.
- Không bảo đảm thứ tự sắp xếp theo `seq`; client sử dụng `serverTime` làm cursor, không lấy phần tử cuối để suy ra cursor.

Ví dụ sau khi task đầu tiên được tạo:

```json
{
  "tasks": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Hoan thanh bao cao D4",
      "description": "Viet tai lieu API",
      "completed": false,
      "deleted": false,
      "version": 1,
      "updatedAt": 1793318400000,
      "seq": 1
    }
  ],
  "serverTime": 1
}
```

Ví dụ này giả định server mới chạy và chưa có thay đổi nào khác. Version, seq và cursor trong thực tế phụ thuộc các thao tác đã thực hiện.

### 4.3. PUT /tasks/:id

Đây là upsert toàn bộ nội dung task, không phải PATCH. Khi sửa/xóa, gửi đủ các trường bắt buộc và giữ các trường nội dung muốn bảo toàn.

Ví dụ body tạo task:

```json
{
  "title": "Hoan thanh bao cao D4",
  "description": "Viet tai lieu API",
  "completed": false,
  "deleted": false,
  "version": 0,
  "updatedAt": 1793318400000
}
```

Quy trình xử lý:

1. Kiểm tra kiểu các trường bắt buộc; không hợp lệ trả **400 Bad Request**.
2. Tìm task theo ID trong đường dẫn.
3. Nếu đã tồn tại và version gửi lên khác version server, trả **409 Conflict**, không áp dụng PUT này.
4. Nếu được chấp nhận: ghi nội dung mới, đặt version bằng version cũ + 1 (hoặc 1 nếu tạo mới), tăng seq và trả **200 OK** cùng task mới.

Response xung đột ví dụ:

```json
{
  "server": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Ban sua tren thiet bi B",
    "description": "Viet tai lieu API",
    "completed": false,
    "deleted": false,
    "version": 2,
    "updatedAt": 1793318401000,
    "seq": 2
  }
}
```

Server trả bản hiện tại, không tự quyết định client/server thắng. Chính sách đó nằm ở client, mô tả trong mục 6.

### 4.4. POST /admin/simulate-edit/:id

Nếu tồn tại, server nối ` [máy B]` vào title, tăng version và seq, đặt `updatedAt = Date.now()`, rồi trả task mới. Nếu không tồn tại, trả 404. Gọi lặp endpoint này sẽ sửa title nhiều lần; đây là công cụ kiểm thử có tác dụng phụ, không phải API đồng bộ có tính gửi lại an toàn.

### 4.5. POST /admin/chaos

| Tham số query | Miền giá trị | Mặc định | Ý nghĩa |
| --- | --- | --- | --- |
| `failRate` | Số hữu hạn từ 0 đến 1 | 0 | Xác suất trả HTTP 500 cho request qua middleware |
| `delayMs` | Số hữu hạn từ 0 đến 60000 | 0 | Độ trễ trước khi xử lý request, đơn vị ms |

Bỏ qua tham số sẽ đặt tham số đó về mặc định, không giữ cấu hình cũ. Ví dụ `failRate=1` gây lỗi mọi request task; lỗi trả JSON `{"error":"chaos"}`. `delayMs=7000` vượt read timeout 5 giây của client Android hiện tại và phục vụ thử timeout. Server trì hoãn trước rồi mới xét failRate.

### 4.6. POST /admin/reset

Trả 204; xóa toàn bộ task trong RAM. **Không reset seq và không tắt chaos.** Đây là thao tác chuẩn bị demo, không phải API xóa dữ liệu để đồng bộ: không tạo tombstone nên client khác không biết các task cũ đã bị xóa. Chỉ dùng với dữ liệu thử có thể bỏ; nếu cần, xóa dữ liệu app trên các emulator để chuẩn bị lại, và tắt chaos riêng.

### 4.7. Mã trạng thái

| Mã | Ý nghĩa trong server này |
| --- | --- |
| 200 | GET/PUT hoặc thao tác admin thành công |
| 204 | Reset thành công, không có nội dung |
| 400 | Body PUT không hợp lệ, query chaos không hợp lệ; JSON sai cú pháp cũng có thể bị Express từ chối |
| 404 | Simulate-edit không tìm thấy ID, hoặc route không tồn tại |
| 409 | Version PUT không bằng phiên bản hiện có; response chứa `server` |
| 500 | Lỗi được cấu hình bởi chaos, response `{"error":"chaos"}` |

Timeout là lỗi chờ phản hồi ở client, không phải một mã HTTP mà server này chủ động trả.

## 5. Dữ liệu mẫu và lệnh gọi thử

Các lệnh dưới đây dùng **PowerShell**, không cần tạo file JSON riêng. Chạy sau khi đã khởi động server. Mẫu sử dụng ID cố định để dễ theo dõi; với một lượt thử mới nên dùng server mới hoặc ID mới để tránh 409 từ dữ liệu thử trước.

### 5.1. Khai báo và tạo task

```powershell
$base = 'http://localhost:3000'
$id = '550e8400-e29b-41d4-a716-446655440000'
$sample = @{
    title = 'Hoan thanh bao cao D4'
    description = 'Viet tai lieu API'
    completed = $false
    deleted = $false
    version = 0
    updatedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
}
$created = Invoke-RestMethod -Method Put -Uri "$base/tasks/$id" `
    -ContentType 'application/json' -Body ($sample | ConvertTo-Json)
$created | ConvertTo-Json
```

Kỳ vọng: HTTP 200, ID đúng đường dẫn, version 1 nếu ID chưa tồn tại.

### 5.2. Đọc và lấy cursor

```powershell
$initial = Invoke-RestMethod -Uri "$base/tasks?updatedAfter=0"
$initial | ConvertTo-Json -Depth 5
$cursor = $initial.serverTime
```

### 5.3. Cập nhật hoàn thành

```powershell
$created.completed = $true
$created.updatedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$updated = Invoke-RestMethod -Method Put -Uri "$base/tasks/$id" `
    -ContentType 'application/json' -Body ($created | ConvertTo-Json)
$updated | ConvertTo-Json
```

Giữ version của `$created` làm phiên bản cơ sở. Server trả version mới trong `$updated`; không tự tăng version trước khi gửi.

### 5.4. Xóa mềm và lấy thay đổi

```powershell
$updated.deleted = $true
$updated.updatedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$deleted = Invoke-RestMethod -Method Put -Uri "$base/tasks/$id" `
    -ContentType 'application/json' -Body ($updated | ConvertTo-Json)
$deleted | ConvertTo-Json
Invoke-RestMethod -Uri "$base/tasks?updatedAfter=$cursor" | ConvertTo-Json -Depth 5
```

Kỳ vọng: task vẫn có trên server với `deleted: true`; GET từ cursor trước cập nhật trả bản mới nhất của task. App ẩn bản ghi đã xóa khỏi danh sách, không xóa dấu vết cần đồng bộ.

### 5.5. Tái hiện xung đột phiên bản

Tiếp tục dùng dữ liệu trên; task đã xóa mềm vẫn tồn tại trong Map nên có thể dùng để thử version:

```powershell
Invoke-RestMethod -Method Post -Uri "$base/admin/simulate-edit/$id" | ConvertTo-Json
try {
    Invoke-RestMethod -Method Put -Uri "$base/tasks/$id" `
        -ContentType 'application/json' -Body ($deleted | ConvertTo-Json)
} catch {
    Write-Host 'Kiem tra HTTP status va response; ky vong 409 voi ban server.'
    Write-Host $_.Exception.Message
    Write-Host $_.ErrorDetails.Message
}
```

Simulate-edit đã tăng version, nên PUT dùng `$deleted.version` cũ phải bị từ chối. Không coi mọi exception là minh chứng 409: kiểm tra mã lỗi và body thực tế để phân biệt lỗi mạng.

### 5.6. Giả lập lỗi và phục hồi

```powershell
# Moi request task tra HTTP 500.
Invoke-RestMethod -Method Post -Uri "$base/admin/chaos?failRate=1&delayMs=0"

# Chuyen sang che do cham 7 giay de thu timeout Android.
Invoke-RestMethod -Method Post -Uri "$base/admin/chaos?failRate=0&delayMs=7000"

# Luon tat gia lap sau khi thu.
Invoke-RestMethod -Method Post -Uri "$base/admin/chaos?failRate=0&delayMs=0"
```

Các lệnh trên cấu hình server; cần gọi API task hoặc chạy đồng bộ Android sau mỗi cấu hình để quan sát lỗi tương ứng.

## 6. Xử lý yêu cầu đồng bộ và gửi lại

### 6.1. Đẩy dữ liệu và nhận thay đổi

1. Android tạo UUID khi thêm offline; các lần gửi lại giữ nguyên UUID đó.
2. Client gửi PUT với nội dung task, dấu xóa mềm và version cơ sở.
3. Server chấp nhận hoặc trả 409 theo version.
4. Khi PUT thành công, client lấy version mới; chỉ đánh dấu SYNCED nếu bản local không bị sửa thêm trong lúc chờ request.
5. Sau push, client GET từ `last_server_seq`, lưu delta vào Room và chỉ cập nhật cursor sau khi áp dụng pull thành công.

### 6.2. Tránh bản ghi trùng và bảo vệ phiên bản

`Map` lưu một bản ghi trên mỗi ID. Gửi lại cùng ID không sinh thêm một bản ghi mới có ID khác. Nếu PUT đã áp dụng nhưng phản hồi bị mất, request gửi lại với version cũ sẽ nhận 409 thay vì được áp dụng ngay lần nữa.

Không diễn giải cơ chế này là “mọi lần gửi lại đều trả 200 giống nhau” hoặc “exactly-once”. Server không có bảng lưu request ID/response để nhận diện chính xác từng thao tác. Client phải xử lý 409, có thể cần gửi lại sau khi giải quyết xung đột. Hai lần tạo cùng nội dung nhưng dùng hai UUID khác vẫn tạo hai task riêng.

### 6.3. Chính sách xung đột phía Android

- **LAST_WRITE_WINS (mặc định):** local thắng khi `local.updatedAt > server.updatedAt`; bằng nhau thì server thắng.
- **SERVER_WINS:** nhận toàn bộ bản server.
- **CLIENT_WINS:** giữ nội dung local, cập nhật version cơ sở theo server rồi gửi lại.

LWW thuận tiện để chọn bản sửa mới hơn, nhưng phụ thuộc đồng hồ thiết bị. Cả ba chính sách chọn toàn bộ bản ghi nên có thể làm mất chỉnh sửa của bên thua; chưa có gộp theo từng trường. Nếu local tiếp tục thay đổi trong lúc chờ phản hồi 409, engine hiện giữ bản sửa mới đó để xử lý tiếp, thay vì ghi đè bằng snapshot cũ.

Phần này mô tả giao tiếp với API; thuật toán và minh chứng đầy đủ của mô-đun đồng bộ thuộc D5.

## 7. Hướng dẫn cài đặt và triển khai demo

### 7.1. Điều kiện

- Node.js LTS kèm npm; Express 5 yêu cầu Node.js từ 18 trở lên.
- Mạng để tải dependency theo lockfile; cổng 3000 chưa bị tiến trình khác chiếm.
- Windows dùng `run.bat`; macOS/Linux dùng Bash cho `run.sh`.
- Để chạy client: Android Studio, SDK và toolchain tương thích cấu hình Gradle của project.

Kiểm tra công cụ:

```text
node --version
npm --version
```

### 7.2. Chạy server bằng một lệnh

Sau khi clone/pull nhánh `feature/my-sync`, mở Terminal tại thư mục **TaskManagerApp**.

Windows PowerShell:

```powershell
.\run.bat
```

macOS/Linux:

```bash
bash run.sh
```

Script chạy `npm ci` trong `server` rồi `npm start`; chỉ khởi động server nếu cài thành công. Mỗi lần chạy sẽ cài lại dependency. Hai script tự xác định đường dẫn dựa trên vị trí file nên có thể gọi bằng đường dẫn từ thư mục khác.

Thông báo sẵn sàng:

```text
TaskSync server: http://localhost:3000
```

Giữ Terminal mở. Nhấn Ctrl+C để dừng; Windows có thể hỏi `Terminate batch job`, chọn Y. Server chưa có cơ chế lưu dữ liệu khi dừng.

Cách chạy thủ công nếu cần chẩn đoán, từ `TaskManagerApp/server`:

```sh
npm ci
npm start
```

### 7.3. Kiểm tra sẵn sàng

Mở `http://localhost:3000/tasks?updatedAfter=0` trong trình duyệt hoặc dùng:

```powershell
Invoke-RestMethod -Uri 'http://localhost:3000/tasks?updatedAfter=0' | ConvertTo-Json -Depth 5
```

Server mới phải trả `{"tasks":[],"serverTime":0}`. Server đã có dữ liệu có thể trả danh sách/cursor khác. Truy cập `/` có thể trả 404 vì server không có trang chủ; đây không phải endpoint kiểm tra sức khỏe.

## 8. Kết nối Android và nhiều thiết bị

Địa chỉ hiện nằm trong [RetrofitClient.kt](../app/src/main/java/com/example/taskmanagerapp/data/remote/RetrofitClient.kt): `http://10.0.2.2:3000/`. App có quyền Internet và cho phép HTTP cleartext trong manifest.

- Hai emulator chạy trên cùng máy có thể cùng gọi server qua `10.0.2.2`.
- Nếu điện thoại thật hoặc emulator trên máy khác muốn dùng chung server, cần địa chỉ LAN của máy chủ, kết nối mạng phù hợp và firewall cho phép cổng 3000.
- Đổi BASE_URL cho trường hợp LAN là **thay đổi code riêng**, chưa được thực hiện trong tài liệu này; cần thống nhất trước khi sửa theo nguyên tắc của project.
- Mở `TaskManagerApp` trong Android Studio, Sync Gradle, chọn emulator và Run app; tắt Demo Mock để đồng bộ thật.

Quy trình minh họa: A tạo task và đồng bộ → B đồng bộ để nhận → A sửa/xóa mềm rồi đồng bộ → B đồng bộ lại để nhận bản mới hoặc dấu xóa. Khi thử xung đột, cho A sửa offline, B sửa và gửi trước, rồi A kết nối lại và gửi version cũ.

Đây là hướng dẫn thực hiện; không phải khẳng định đã chạy thành công hai thiết bị trong đợt kiểm chứng D4 này.

## 9. Kiểm chứng và giới hạn minh chứng

Ngày 30/10/2026 chạy từ `TaskManagerApp/server`:

```sh
npm test
```

Kết quả: **1 test tích hợp pass, 0 fail**, chạy HTTP thật qua loopback trên cổng tạm. Test gồm nhiều bước xác nhận:

- PUT tạo task, version mới và GET danh sách.
- GET delta rỗng khi cursor đã cập nhật.
- Simulate-edit và PUT version cũ trả 409 đúng bản server.
- Gửi bản đã giải quyết xung đột và gửi lại version cũ.
- Xóa mềm và lấy tombstone bằng delta.
- Request không hợp lệ trả 400.
- Chaos trả 500, delay 25 ms và phục hồi.
- Reset và tạo task mới vẫn tăng seq trong cùng tiến trình.

Test này không phải kiểm thử Android end-to-end. Độ trễ 25 ms trong test cũng không chứng minh timeout 5 giây của Android. Chưa xác nhận trong đợt này: chạy hai Android, phục hồi sau đóng app/reboot, độ bền lưu trữ qua restart server hoặc hiệu năng nhiều thiết bị. Không đưa các tình huống đó vào kết quả “đã pass” của D4.

## 10. Hạn chế hiện tại và giải pháp đề xuất

Các đề xuất dưới đây **chưa được thực hiện**; cần thống nhất trước mọi chỉnh sửa mã nguồn.

| Vấn đề đã xác định | Ảnh hưởng | Giải pháp đề xuất |
| --- | --- | --- |
| Task và seq chỉ nằm trong RAM | Restart mất dữ liệu; client có cursor/version cũ có thể bỏ sót hoặc không nhận đúng dữ liệu sau restart | Lưu task, tombstone và cursor trong database/file bền vững; hoặc bổ sung cơ chế phát hiện server reset và full resync |
| Admin reset xóa cứng Map mà không tạo tombstone | Client không tự xóa task cũ sau reset | Chỉ dùng reset cho demo; nếu cần xóa dùng chung, thiết kế thao tác phát tombstone hoặc cơ chế reset đồng bộ |
| Chưa có xác thực/HTTPS, admin API mở | Bên truy cập được server có thể sửa dữ liệu hoặc bật chaos | Giới hạn demo trong môi trường thử; trước triển khai thật cần xác thực, phân quyền và TLS |
| Validation còn tối thiểu | Title rỗng, cursor bất hợp lệ và version khởi tạo chưa được ràng buộc chặt | Bổ sung schema validation và hợp đồng lỗi thống nhất |
| Chưa có request ID và kho lưu kết quả thao tác | Gửi lại bảo vệ bằng ID/version nhưng có thể nhận 409; không có bảo đảm exactly-once | Nếu cần, thiết kế idempotency key và lưu kết quả xử lý cùng transaction |
| BASE_URL Android cố định | Thiết bị ở máy khác không tự trỏ tới server chung | Thống nhất cách cấu hình địa chỉ server trước khi chỉnh client |

D4 hiện chứng minh API và mã nguồn server demo với kho dữ liệu dùng chung trong thời gian tiến trình hoạt động. Không coi đây là server production hoặc hệ thống đã lưu trữ bền vững.
