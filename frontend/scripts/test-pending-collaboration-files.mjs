import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { transformWithOxc } from "vite";

async function importRegistryModule() {
  const relativePath = "src/app/lib/pendingCollaborationFiles.ts";
  const source = (await readFile(resolve(relativePath), "utf8"))
    .replace('import { api } from "./api";', "const api = globalThis.__pendingCollaborationApiMock;");
  const { code } = await transformWithOxc(source, relativePath, {
    lang: "ts",
    target: "es2022",
  });
  const encoded = Buffer.from(code, "utf8").toString("base64");
  return import(`data:text/javascript;base64,${encoded}`);
}

function deferred() {
  let resolvePromise;
  let rejectPromise;
  const promise = new Promise((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });
  return { promise, resolve: resolvePromise, reject: rejectPromise };
}

globalThis.__pendingCollaborationApiMock = async () => undefined;
const { PendingCollaborationFileRegistry } = await importRegistryModule();
delete globalThis.__pendingCollaborationApiMock;

{
  const serverDeletes = [];
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  assert.equal(await registry.delete(10), false);
  assert.deepEqual(serverDeletes, [], "등록되지 않은 첨부는 새 계정 토큰으로 삭제하면 안 된다");

  await registry.trackUpload(Promise.resolve({ id: 10 }));
  assert.equal(await registry.delete(10), true);
  assert.deepEqual(serverDeletes, [10], "Messenger의 등록된 X 삭제는 유지해야 한다");
}

{
  const registry = new PendingCollaborationFileRegistry(async () => {
    throw new Error("delete failed");
  });
  await registry.trackUpload(Promise.resolve({ id: 15 }));
  await assert.rejects(registry.delete(15), /delete failed/, "X 삭제 실패는 Messenger 오류 UX까지 전파해야 한다");
  assert.deepEqual(registry.snapshot().pendingIds, [15], "현재 세대의 삭제 실패는 사용자가 재시도할 수 있어야 한다");
}

{
  const serverDeletes = [];
  const lateUpload = deferred();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  const tracked = registry.trackUpload(lateUpload.promise);
  registry.forget();
  registry.trackUpload(Promise.resolve({ id: 12 }));

  lateUpload.resolve({ id: 11 });
  await tracked;
  await Promise.resolve();
  assert.deepEqual(registry.snapshot().pendingIds, [12], "이전 계정의 늦은 업로드가 새 세대에 등록되면 안 된다");
  assert.equal(registry.snapshot().activeUploadCount, 0);
  assert.equal(await registry.delete(11), false);
  assert.deepEqual(serverDeletes, [], "폐기된 업로드는 서버 orphan scheduler에 맡겨야 한다");
}

{
  const serverDeletes = [];
  const oldUpload = deferred();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  const trackedOldUpload = registry.trackUpload(oldUpload.promise);
  const drain = registry.discard();
  await registry.trackUpload(Promise.resolve({ id: 22 }));

  oldUpload.resolve({ id: 21 });
  await trackedOldUpload;
  assert.deepEqual(await drain, { deletedIds: [21], failedIds: [] });
  assert.deepEqual(serverDeletes, [21]);
  assert.deepEqual(registry.snapshot().pendingIds, [22], "drain 뒤 새 세대 첨부는 정리하면 안 된다");
}

{
  const serverDeletes = [];
  const oldestUpload = deferred();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  const trackedOldest = registry.trackUpload(oldestUpload.promise);
  const firstDrain = registry.discard();
  await registry.trackUpload(Promise.resolve({ id: 82 }));
  const secondDrain = registry.discard();
  await registry.trackUpload(Promise.resolve({ id: 83 }));

  oldestUpload.resolve({ id: 81 });
  await trackedOldest;
  assert.deepEqual(await firstDrain, { deletedIds: [81], failedIds: [] });
  assert.deepEqual(await secondDrain, { deletedIds: [82], failedIds: [] });
  assert.deepEqual(serverDeletes.sort((a, b) => a - b), [81, 82]);
  assert.deepEqual(registry.snapshot().pendingIds, [83], "겹친 discard도 각 호출 세대만 정리해야 한다");
}

{
  const serverDeletes = [];
  const oldUpload = deferred();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  const trackedOld = registry.trackUpload(oldUpload.promise);
  const drain = registry.discard();
  registry.forget();
  await registry.trackUpload(Promise.resolve({ id: 92 }));

  oldUpload.resolve({ id: 91 });
  await trackedOld;
  await drain;
  assert.deepEqual(serverDeletes, [], "계정 전환으로 폐기된 drain은 이전 업로드를 새 토큰으로 삭제하면 안 된다");
  assert.deepEqual(registry.snapshot().pendingIds, [92], "이전 drain의 잔여 ID를 새 계정 세대로 이관하면 안 된다");
}

{
  const serverDeletes = [];
  const deletion = deferred();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
    await deletion.promise;
  });
  await registry.trackUpload(Promise.resolve({ id: 31 }));
  const deleting = registry.delete(31);

  registry.forget();
  await registry.trackUpload(Promise.resolve({ id: 32 }));
  deletion.reject(new Error("network failure"));
  await assert.rejects(deleting, /network failure/);
  assert.deepEqual(registry.snapshot().pendingIds, [32], "폐기 뒤 삭제 실패가 이전 ID를 재등록하면 안 된다");
  assert.equal(await registry.delete(31), false);
  assert.deepEqual(serverDeletes, [31]);
}

{
  const firstDeletion = deferred();
  const lateDeletion = deferred();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    if (fileId === 71) await firstDeletion.promise;
    if (fileId === 72) await lateDeletion.promise;
  });
  await registry.trackUpload(Promise.resolve({ id: 71 }));
  await registry.trackUpload(Promise.resolve({ id: 72 }));
  const deletingFirst = registry.delete(71);
  const drain = registry.discard();
  await new Promise((resolveImmediate) => setImmediate(resolveImmediate));
  const deletingLate = registry.delete(72);

  firstDeletion.resolve();
  assert.equal(await deletingFirst, true);
  await drain;
  lateDeletion.reject(new Error("late delete failure"));
  await assert.rejects(deletingLate, /late delete failure/);
  assert.deepEqual(registry.snapshot().pendingIds, [], "drain이 봉인한 세대에 늦은 삭제 실패가 ID를 재등록하면 안 된다");
}

{
  const serverDeletes = [];
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  await registry.trackUpload(Promise.resolve({ id: 41 }));
  await registry.trackUpload(Promise.resolve({ id: 42 }));
  const result = await registry.discard([41, 999]);
  assert.deepEqual(result, { deletedIds: [41], failedIds: [] });
  assert.deepEqual(serverDeletes, [41], "명시 목록에서도 등록된 ID만 서버 삭제해야 한다");
  assert.deepEqual(registry.snapshot().pendingIds, [42]);
}

{
  const attempts = new Map();
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    const attempt = (attempts.get(fileId) ?? 0) + 1;
    attempts.set(fileId, attempt);
    if (attempt === 1) throw new Error("temporary failure");
  });
  await registry.trackUpload(Promise.resolve({ id: 51 }));
  assert.deepEqual(await registry.discard(), { deletedIds: [], failedIds: [51] });
  assert.deepEqual(registry.snapshot().pendingIds, [51]);
  assert.deepEqual(await registry.discard(), { deletedIds: [51], failedIds: [] });
  assert.equal(attempts.get(51), 2, "같은 계정의 명시적 다음 정리에서는 실패 ID를 재시도할 수 있어야 한다");
}

{
  const serverDeletes = [];
  const registry = new PendingCollaborationFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  await registry.trackUpload(Promise.resolve({ id: 61 }));
  registry.markLinked([61]);
  assert.deepEqual(await registry.discard(), { deletedIds: [], failedIds: [] });
  assert.deepEqual(serverDeletes, [], "메시지에 연결된 첨부를 pending 정리하면 안 된다");

  await registry.trackUpload(Promise.resolve({ id: 62 }));
  registry.forget();
  assert.deepEqual(serverDeletes, [], "forget 자체는 서버 삭제를 호출하면 안 된다");
  assert.deepEqual(registry.snapshot().pendingIds, []);
}

console.log("pending collaboration file registry tests passed");
