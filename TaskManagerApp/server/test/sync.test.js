const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const app = require('../Server');
let server, base;
before(async () => {
  server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => new Promise(resolve => server.close(resolve)));
const request = (path, method = 'GET', body) => fetch(base + path, {
  method, headers: { 'Content-Type': 'application/json' },
  body: body === undefined ? undefined : JSON.stringify(body)
});

test('push, delta, conflict, retry, tombstone and recovery use the Android contract', async () => {
  let task = { id: 'phone-a', title: 'Offline task', description: null,
    completed: false, deleted: false, version: 0, updatedAt: Date.now() };
  let response = await request('/tasks/phone-a', 'PUT', task);
  assert.equal(response.status, 200);
  task = await response.json();
  assert.equal(task.version, 1);
  const first = await (await request('/tasks?updatedAfter=0')).json();
  assert.deepEqual(first.tasks, [task]);
  assert.deepEqual((await (await request(`/tasks?updatedAfter=${first.serverTime}`)).json()).tasks, []);
  const other = await (await request('/admin/simulate-edit/phone-a', 'POST')).json();
  response = await request('/tasks/phone-a', 'PUT', task);
  assert.equal(response.status, 409);
  assert.deepEqual((await response.json()).server, other);
  response = await request('/tasks/phone-a', 'PUT', { ...task, version: other.version, title: 'Client wins' });
  assert.equal(response.status, 200);
  task = await response.json();
  // A lost response and retry cannot create a duplicate or silently overwrite a newer version.
  response = await request('/tasks/phone-a', 'PUT', { ...task, version: other.version });
  assert.equal(response.status, 409);
  response = await request('/tasks/phone-a', 'PUT', { ...task, deleted: true });
  assert.equal(response.status, 200);
  const delta = await (await request(`/tasks?updatedAfter=${first.serverTime}`)).json();
  assert.equal(delta.tasks.length, 1);
  assert.equal(delta.tasks[0].deleted, true);
  assert.equal(delta.tasks[0].completed, false);
  assert.ok(delta.serverTime > first.serverTime);
  assert.equal((await request('/tasks/invalid', 'PUT', {})).status, 400);
  assert.equal((await request('/admin/chaos?failRate=2', 'POST')).status, 400);
  await request('/admin/chaos?failRate=1', 'POST');
  assert.equal((await request('/tasks')).status, 500);
  await request('/admin/chaos?failRate=0&delayMs=25', 'POST');
  const started = Date.now();
  assert.equal((await request('/tasks')).status, 200);
  assert.ok(Date.now() - started >= 20);
  await request('/admin/chaos', 'POST');
  await request('/admin/reset', 'POST');
  response = await request('/tasks/new', 'PUT', { ...task, version: 0 });
  assert.equal(response.status, 200);
  const afterReset = await (await request(`/tasks?updatedAfter=${delta.serverTime}`)).json();
  assert.equal(afterReset.tasks.length, 1);
});
