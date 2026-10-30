# D7. Báo cáo thực nghiệm và đánh giá

- **Dự án:** Android Auto Sync – TaskManagerApp.
- **Người biên soạn:** PeterKhang3012.
- **Ngày thực nghiệm:** 30/10/2026; thời điểm ghi nhận UTC: `2026-10-30T05:49:25.935Z`.

## 1. Mục tiêu thực nghiệm

Đo thời gian gửi tuần tự N task và lấy lại toàn bộ task qua HTTP; xác định tỷ lệ request/lượt đo thành công và kiểm tra tập ID/nội dung sau truyền. Đồng thời phân biệt kết quả API đã đo với các đặc tính hệ thống Android chưa đo.

Kết quả lỗi, timeout và xung đột theo ca được trình bày trong [D6](D6_Bo_Kiem_Thu_Va_Ket_Qua_Kiem_Thu.md). Không dùng bảng hiệu năng không có lỗi bên dưới để suy ra tỷ lệ thành công khi mạng thực tế bất ổn.

## 2. Môi trường

| Thành phần | Giá trị thực tế |
| --- | --- |
| Hệ điều hành | Linux x86_64, kernel 6.18.55 |
| Node.js / npm | v24.19.0 / 11.9.0 |
| CPU được hệ điều hành báo cáo | AMD EPYC 9V74 80-Core Processor |
| CPU logic được báo cáo | 5 |
| RAM được báo cáo | 33.29 GiB |
| Mã server | [Server.js](../server/Server.js), Express theo lockfile |
| Kho dữ liệu | Map trong RAM của một tiến trình |
| Đường truyền | HTTP `127.0.0.1`, cổng tạm; không qua mạng LAN/WAN |
| Client đo | Node.js fetch, cùng tiến trình chạy harness với Express app |
| Công cụ đo | Đồng hồ monotonic `performance.now()`, đơn vị ms |
| Mức đồng thời | Một PUT tại một thời điểm, sau đó một GET |
| Android emulator/Room/WorkManager | Không tham gia phép đo |

CPU/RAM là thông tin hệ điều hành cung cấp, không chứng minh quota CPU/RAM được bảo đảm cho container. Client và server chia sẻ tiến trình/event loop nên kết quả không đại diện cho tải phân tán.

## 3. Dữ liệu và thiết kế thí nghiệm

- Ba mức N: **10, 50, 100 task**; mỗi mức **5 lượt**, tổng 15 lượt và 800 PUT trong vùng đo.
- ID: `n<N>-r<lượt>-<i>`, không trùng trong lượt.
- Task gồm title, description ngắn, completed=false, deleted=false, version=0 và timestamp mẫu cố định cộng chỉ số i. Timestamp dùng cho dữ liệu mẫu, không dùng đo thời gian thực thi.
- Mỗi lượt xóa Map qua admin reset trước vùng đo. Reset không đưa vào thời gian; seq vẫn tăng trong tiến trình.
- Chaos được tắt trước đo, không cấu hình lỗi ngẫu nhiên hoặc độ trễ giả lập cho vùng hiệu năng.
- Có 10 PUT warm-up ngoài vùng đo; trước đó cùng tiến trình đã chạy 7 ca HTTP. Vì vậy đây là phép đo tiến trình đã hoạt động, không phải cold start.
- Không đo xóa/cập nhật, conflict hoặc retry trong bảng hiệu năng; các thao tác đó thuộc kiểm thử chức năng D6.

Các lượt đo theo thứ tự 10 → 50 → 100, không trộn ngẫu nhiên. Warm-up, JIT, tái sử dụng kết nối và tải máy có thể ảnh hưởng số liệu.

## 4. Cách đo và công thức

Một lượt:

1. Reset Map, chuẩn bị dữ liệu.
2. Ghi `t0`, lần lượt PUT N task; đọc JSON từng response và lưu làm tập kỳ vọng.
3. Ghi `t1`, GET `/tasks?updatedAfter=0`, đọc JSON.
4. Ghi `t2`, sau đó kiểm tra assertion và tính nhất quán ngoài vùng thời gian.

Công thức:

```text
T_push = t1 - t0
T_pull = t2 - t1
T_total = t2 - t0
Trung bình = tổng T của 5 lượt / 5
Tỷ lệ PUT thành công = số PUT HTTP 200 / số PUT trong vùng đo × 100%
Tỷ lệ lượt thành công = số lượt đủ HTTP đúng, N task, 0 trùng, 0 lệch / 15 × 100%
Số ID trùng = số phần tử GET - số ID khác nhau trong GET
```

Số lệch: duyệt hợp hai tập ID kỳ vọng và nhận được. Một ID bị tính lệch nếu thiếu/thừa hoặc JSON task không bằng response PUT đã xác nhận, gồm version/seq và các trường nội dung. Harness dùng JSON.stringify trên response của cùng server, có cùng thứ tự trường; đây không phải bộ so sánh JSON chuẩn hóa dùng cho mọi hệ thống.

Phép so sánh không gọi menu kiểm tra nhất quán trên Android. Nó kiểm tra hai chiều tập dữ liệu HTTP nên bao gồm cả ID thiếu/thừa.

## 5. Kết quả tổng hợp

Đơn vị thời gian: **ms**. Làm tròn 3 chữ số thập phân; trung bình tính từ giá trị gốc, nên tổng các số đã làm tròn có thể lệch 0.001 ms.

| N task | Số lượt | Push TB | Pull TB | Tổng TB | Tổng nhỏ nhất | Tổng lớn nhất | Lượt đạt | ID trùng | ID lệch |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 10 | 5 | 19.997 | 1.903 | 21.900 | 19.309 | 26.575 | 5/5 (100%) | 0 | 0 |
| 50 | 5 | 91.596 | 1.809 | 93.405 | 83.827 | 99.881 | 5/5 (100%) | 0 | 0 |
| 100 | 5 | 165.948 | 1.821 | 167.769 | 152.701 | 180.645 | 5/5 (100%) | 0 | 0 |

- PUT thành công: **800/800 (100%)** trong vùng hiệu năng.
- GET thành công: **15/15 (100%)** trong vùng hiệu năng.
- Lượt đạt đầy đủ assertion: **15/15 (100%)**.
- Mỗi lượt nhận đúng N task; không ghi nhận ID trùng hoặc ID/nội dung lệch.
- Các tỷ lệ trên không bao gồm 7 ca cố tình tạo lỗi/xung đột, warm-up hoặc admin reset. Chúng cũng không phải tỷ lệ sync Android do app ghi nhận.

## 6. Bảng số liệu từng lượt

| N task | Lượt | Push ms | Pull ms | Tổng ms | PUT 200 / tổng PUT | Task nhận | ID trùng | ID lệch |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 10 | 1 | 21.216 | 2.006 | 23.222 | 10/10 | 10 | 0 | 0 |
| 10 | 2 | 24.740 | 1.836 | 26.575 | 10/10 | 10 | 0 | 0 |
| 10 | 3 | 18.030 | 1.804 | 19.834 | 10/10 | 10 | 0 | 0 |
| 10 | 4 | 18.403 | 2.158 | 20.561 | 10/10 | 10 | 0 | 0 |
| 10 | 5 | 17.596 | 1.713 | 19.309 | 10/10 | 10 | 0 | 0 |
| 50 | 1 | 97.676 | 2.206 | 99.881 | 50/50 | 50 | 0 | 0 |
| 50 | 2 | 97.063 | 1.906 | 98.968 | 50/50 | 50 | 0 | 0 |
| 50 | 3 | 95.239 | 1.619 | 96.858 | 50/50 | 50 | 0 | 0 |
| 50 | 4 | 85.766 | 1.723 | 87.489 | 50/50 | 50 | 0 | 0 |
| 50 | 5 | 82.235 | 1.592 | 83.827 | 50/50 | 50 | 0 | 0 |
| 100 | 1 | 172.860 | 2.190 | 175.051 | 100/100 | 100 | 0 | 0 |
| 100 | 2 | 178.696 | 1.949 | 180.645 | 100/100 | 100 | 0 | 0 |
| 100 | 3 | 162.427 | 1.701 | 164.129 | 100/100 | 100 | 0 | 0 |
| 100 | 4 | 151.047 | 1.654 | 152.701 | 100/100 | 100 | 0 | 0 |
| 100 | 5 | 164.710 | 1.612 | 166.321 | 100/100 | 100 | 0 | 0 |

## 7. Phân tích

Trong mẫu này, tổng thời gian tăng khi số PUT tuần tự tăng. Push chiếm phần lớn thời gian vì mỗi task là một HTTP request riêng; pull chỉ là một GET trả toàn bộ N task. Không có API batch đang được đo.

Tổng trung bình mức 100 task bằng khoảng 7.66 lần mức 10 task trong bộ số liệu này. Không diễn giải thành quy luật tuyến tính hoặc khả năng mở rộng đã được chứng minh: chỉ có ba cỡ dữ liệu nhỏ, cùng tiến trình và không có tải đồng thời.

Một số lượt sau nhanh hơn lượt trước có thể chịu ảnh hưởng của JIT, kết nối và biến động lịch chạy; chưa có phép đo tách riêng để xác định nguyên nhân. Không suy ra tốc độ ổn định chỉ từ năm lượt.

Không có ID lệch trong vùng đo chứng minh dữ liệu PUT đã xác nhận có thể được GET lại đúng trong các lượt này. Nó không chứng minh hai thiết bị Android luôn hội tụ, dữ liệu tồn tại sau restart, hoặc mọi retry đều không gây tác dụng phụ.

## 8. Kết quả chức năng liên quan

Theo [D6](D6_Bo_Kiem_Thu_Va_Ket_Qua_Kiem_Thu.md): bộ npm test sẵn có pass 1 test tích hợp; 7 ca bổ sung pass, gồm version cũ trả 409, xóa mềm, HTTP 500/phục hồi, client timeout rồi gửi lại và delta rỗng.

Phần timeout bổ sung dùng delay server 200 ms và timeout client Node 30 ms. Sau khi server đã áp dụng request, retry nhận 409 và chỉ còn một task cùng ID. Đây không phải đo backoff hoặc retry tự động của WorkManager.

Build Android bị chặn khi tải JDK 25 từ api.foojay.io nhận HTTP 403. Chưa có kết quả thời gian sync Android, tỷ lệ sync Android hoặc thực nghiệm hai emulator trong đợt này.

## 9. Hạn chế và phạm vi kết luận

- Đo trên localhost, không có mất mạng thật, độ trễ LAN/WAN hoặc giới hạn pin Android.
- Chưa đo Room, Flow/UI, thời gian xếp hàng WorkManager, reboot hoặc force-stop.
- Server RAM mất dữ liệu/cursor khi restart; phép đo không đánh giá độ bền lưu trữ.
- Nhiều thiết bị đồng thời và cập nhật/xóa xung đột không nằm trong vùng hiệu năng.
- Dữ liệu nhỏ, năm lượt mỗi mức; không có percentile p95 đáng tin cậy hay phép đo stress.
- Không có số liệu CPU/RAM tiêu thụ trong lúc sync; thông số máy không phải mức sử dụng.
- Các thời gian báo cáo là của đợt cloud này; không áp dụng như cam kết hiệu năng trên máy người dùng.
- Chưa có ảnh chụp Android hoặc log WorkManager; không coi hướng dẫn D6 là kết quả đã thực hiện.

Kết luận: API server hoạt động đúng các assertion đã thực thi, và trong 15 lượt tuần tự 10/50/100 task không ghi nhận sai lệch dữ liệu HTTP. D7 hiện có số đo thực tế ở phạm vi API; chưa đủ dữ liệu để đánh giá đầy đủ hiệu năng và tính nhất quán của toàn hệ thống Android offline-first.

## Phụ lục A. Nguồn harness thực nghiệm

Đây là mã của harness tạm đã chạy để sinh 7 ca kiểm tra và bảng số liệu trên; được nhúng làm minh chứng, không thêm vào mã nguồn ứng dụng. Đường dẫn `/workspace/...` và `/tmp/...` là đường dẫn của môi trường cloud đã đo. File tạm không được coi là artifact Git cần tồn tại lâu dài.

Đã chạy bằng `node /tmp/d6-d7-server-experiment.cjs`; mã thoát 0. Harness đóng server trong finally. Khi tái lập ở đường dẫn khác, các đường dẫn tuyệt đối trong harness phải khớp nơi đặt repository và nơi ghi kết quả. Để tái lập số đo cần dùng môi trường và dependency tương ứng; không kỳ vọng số mili giây giống hệt.

```javascript
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const { performance } = require('node:perf_hooks');
const app = require('/workspace/android-auto-sync-app/TaskManagerApp/server/Server.js');
(async () => {
 const server = app.listen(0, '127.0.0.1');
 await new Promise(resolve => server.once('listening', resolve));
 const base = `http://127.0.0.1:${server.address().port}`;
 const request = (path, method='GET', body, signal) => fetch(base + path, {
  method, headers:{'Content-Type':'application/json'},
  body: body === undefined ? undefined : JSON.stringify(body), signal
 });
 const make = i => ({title:`Task ${i}`,description:`D7 sample ${i}`,completed:false,deleted:false,version:0,updatedAt:1793318400000+i});
 const checks=[]; const rows=[];
 async function check(id, fn) { await fn(); checks.push({id,status:'PASS'}); console.log(`${id}: PASS`); }
 try {
  await check('API-01',async()=>{const r=await request('/tasks/check','PUT',make(1));assert.equal(r.status,200);assert.equal((await r.json()).version,1);});
  await check('API-02',async()=>{const r=await request('/tasks/check','PUT',make(1));assert.equal(r.status,409);const d=await(await request('/tasks')).json();assert.equal(d.tasks.length,1);});
  await check('API-03',async()=>{await request('/admin/simulate-edit/check','POST');const r=await request('/tasks/check','PUT',{...make(1),version:1});assert.equal(r.status,409);assert.equal((await r.json()).server.version,2);});
  await check('API-04',async()=>{const r=await request('/tasks/check','PUT',{...make(1),version:2,deleted:true});assert.equal(r.status,200);const d=await(await request('/tasks?updatedAfter=2')).json();assert.equal(d.tasks.length,1);assert.equal(d.tasks[0].deleted,true);});
  await check('API-05',async()=>{await request('/admin/chaos?failRate=1','POST');assert.equal((await request('/tasks')).status,500);await request('/admin/chaos','POST');assert.equal((await request('/tasks')).status,200);});
  await check('API-06',async()=>{await request('/admin/chaos?delayMs=200','POST');let aborted=false;try {await request('/tasks/timeout','PUT',make(2),AbortSignal.timeout(30));}catch(e){assert.equal(e.name,'TimeoutError');aborted=true;}assert.ok(aborted);await new Promise(r=>setTimeout(r,250));await request('/admin/chaos','POST');assert.equal((await request('/tasks/timeout','PUT',make(2))).status,409);const d=await(await request('/tasks')).json();assert.equal(d.tasks.filter(t=>t.id==='timeout').length,1);});
  await check('API-07',async()=>{const d=await(await request('/tasks')).json();const empty=await(await request(`/tasks?updatedAfter=${d.serverTime}`)).json();assert.deepEqual(empty.tasks,[]);});
  // Warm up HTTP and JSON paths; warm-up is excluded from measured trials.
  for(let i=0;i<10;i++) await request(`/tasks/warm-${i}`,'PUT',make(i));
  for(const n of [10,50,100]) for(let trial=1;trial<=5;trial++) {
   await request('/admin/reset','POST');
   const expected=new Map(); let successful=0;
   const start=performance.now();
   for(let i=0;i<n;i++) {
    const id=`n${n}-r${trial}-${i}`;
    const r=await request(`/tasks/${id}`,'PUT',make(i));
    const task=await r.json();
    if(r.status===200) {successful++;expected.set(id,task);}
   }
   const pushed=performance.now();
   const response=await request('/tasks?updatedAfter=0');
   const delta=await response.json();
   const end=performance.now();
   assert.equal(response.status,200);
   const received=new Map(delta.tasks.map(t=>[t.id,t]));
   const ids=new Set([...expected.keys(),...received.keys()]);
   let inconsistent=0;
   for(const id of ids) if(JSON.stringify(expected.get(id))!==JSON.stringify(received.get(id))) inconsistent++;
   const row={n,trial,pushMs:pushed-start,pullMs:end-pushed,totalMs:end-start,successful,putAttempts:n,pullSuccessful:response.status===200,duplicates:delta.tasks.length-received.size,inconsistent,received:delta.tasks.length};
   assert.equal(successful,n);assert.equal(row.received,n);assert.equal(row.duplicates,0);assert.equal(inconsistent,0);
   rows.push(row);console.log('MEASURE '+JSON.stringify(row));
  }
  fs.writeFileSync('/tmp/d6-d7-results.json',JSON.stringify({date:new Date().toISOString(),node:process.version,platform:process.platform,arch:process.arch,cpu:os.cpus()[0]?.model,logicalCpus:os.cpus().length,memoryGiB:os.totalmem()/2**30,checks,rows},null,2));
 } finally { await new Promise(resolve=>server.close(resolve)); }
})().catch(e=>{console.error(e);process.exitCode=1;});
```
