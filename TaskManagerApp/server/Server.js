// Server mẫu (Yêu cầu 3). Chạy: npm install && npm start  -> http://localhost:3000
// Emulator Android gọi qua http://10.0.2.2:3000/
const express = require('express');
const app = express();
app.use(express.json());

const tasks = new Map();   // id -> task (lưu RAM; muốn bền hơn thì ghi ra file/DB)
let seq = 0;               // bộ đếm tăng đơn điệu, dùng làm "con trỏ" đồng bộ tăng dần
let chaos = { failRate: 0, delayMs: 0 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------- Admin / giả lập (đặt TRƯỚC middleware gây lỗi) ----------
app.post('/admin/chaos', (req, res) => {
  const failRate = Number(req.query.failRate || 0);
  const delayMs = Number(req.query.delayMs || 0);
  if (!Number.isFinite(failRate) || failRate < 0 || failRate > 1 ||
      !Number.isFinite(delayMs) || delayMs < 0 || delayMs > 60000) return res.sendStatus(400);
  chaos = { failRate, delayMs };
  res.json(chaos);
});
// Giả lập "máy B" sửa công việc trực tiếp trên server (Yêu cầu 6)
app.post('/admin/simulate-edit/:id', (req, res) => {
  const t = tasks.get(req.params.id);
  if (!t) return res.sendStatus(404);
  t.title += ' [máy B]';
  t.version += 1;
  t.updatedAt = Date.now();
  t.seq = ++seq;
  res.json(t);
});
app.post('/admin/reset', (req, res) => { tasks.clear(); res.sendStatus(204); });

// ---------- Giả lập lỗi 500 / timeout (Yêu cầu 7) ----------
app.use(async (req, res, next) => {
  if (chaos.delayMs) await sleep(chaos.delayMs);          // delayMs > timeout client => timeout
  if (Math.random() < chaos.failRate) return res.status(500).json({ error: 'chaos' });
  next();
});

// ---------- API công việc ----------
// Lấy thay đổi tăng dần, KÈM bản ghi đã xóa mềm (deleted=true)
app.get('/tasks', (req, res) => {
  const since = +req.query.updatedAfter || 0;
  res.json({ tasks: [...tasks.values()].filter((t) => t.seq > since), serverTime: seq });
});

// Tạo/cập nhật (upsert theo id => gửi lại không tạo bản ghi trùng)
// Client gửi `version` = phiên bản server mà nó dựa vào khi sửa.
app.put('/tasks/:id', (req, res) => {
  const b = req.body;
  if (!b || typeof b.title !== 'string' || typeof b.completed !== 'boolean' ||
      typeof b.deleted !== 'boolean' || !Number.isSafeInteger(b.version) || b.version < 0 ||
      !Number.isSafeInteger(b.updatedAt) || b.updatedAt < 0 ||
      (b.description != null && typeof b.description !== 'string')) return res.sendStatus(400);
  const cur = tasks.get(req.params.id);
  if (cur && b.version !== cur.version) {
    return res.status(409).json({ server: cur });         // xung đột
  }
  const t = {
    id: req.params.id, title: b.title, description: b.description,
    completed: b.completed, deleted: b.deleted,
    version: (cur ? cur.version : 0) + 1,
    updatedAt: b.updatedAt, seq: ++seq,
  };
  tasks.set(t.id, t);
  res.json(t);
});

if (require.main === module) {
  app.listen(3000, () => console.log('TaskSync server: http://localhost:3000'));
}
module.exports = app;
