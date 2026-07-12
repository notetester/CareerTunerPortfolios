import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { transformWithOxc } from "vite";

async function importTypeScriptModule(relativePath) {
  const source = await readFile(resolve(relativePath), "utf8");
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

const { PendingInterviewFileRegistry } = await importTypeScriptModule(
  "src/app/lib/pendingInterviewFilesCore.ts",
);

{
  const serverDeletes = [];
  const registry = new PendingInterviewFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });

  assert.equal(await registry.delete(10), false);
  assert.deepEqual(serverDeletes, [], "등록되지 않은 ID는 서버에 삭제 요청을 보내면 안 된다");

  registry.register(10);
  assert.equal(await registry.delete(10), true);
  assert.deepEqual(serverDeletes, [10]);
}

{
  const serverDeletes = [];
  const lateUpload = deferred();
  const controller = new AbortController();
  const registry = new PendingInterviewFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  const tracked = registry.trackUpload(lateUpload.promise, controller);

  registry.forget();
  assert.equal(controller.signal.aborted, true, "forget은 이전 계정 업로드를 즉시 중단해야 한다");
  assert.equal(registry.snapshot().activeUploadCount, 0, "폐기한 세대의 업로드는 현재 계정 상태에 남으면 안 된다");

  registry.register(12);
  lateUpload.resolve({ id: 11 });
  assert.deepEqual(await tracked, { id: 11 });
  assert.deepEqual(registry.snapshot().pendingIds, [12], "늦은 이전 업로드가 새 세대에 재등록되면 안 된다");
  assert.equal(await registry.delete(11), false);
  assert.deepEqual(serverDeletes, [], "폐기된 업로드 ID를 서버에서 삭제하지 말고 orphan 정리에 맡겨야 한다");
}

{
  const serverDeletes = [];
  const oldUpload = deferred();
  const oldController = new AbortController();
  const registry = new PendingInterviewFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  const trackedOldUpload = registry.trackUpload(oldUpload.promise, oldController);
  const drain = registry.abortUploadsAndDiscard(true);
  assert.equal(oldController.signal.aborted, true, "drain은 시작 세대의 업로드를 중단해야 한다");

  registry.register(22);
  oldUpload.resolve({ id: 21 });
  await trackedOldUpload;
  const result = await drain;
  assert.deepEqual(result, { deletedIds: [21], failedIds: [] });
  assert.deepEqual(serverDeletes, [21]);
  assert.deepEqual(registry.snapshot().pendingIds, [22], "drain 도중 등록된 새 세대 파일은 건드리면 안 된다");
}

{
  const serverDeletes = [];
  const deletion = deferred();
  const registry = new PendingInterviewFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
    await deletion.promise;
  });
  registry.register(31);
  const deleting = registry.delete(31);
  assert.deepEqual(serverDeletes, [31]);

  registry.forget();
  registry.register(32);
  deletion.reject(new Error("network failure"));
  assert.equal(await deleting, false);
  assert.deepEqual(registry.snapshot().pendingIds, [32], "폐기 뒤 삭제 실패 콜백이 이전 ID를 되살리면 안 된다");
  assert.equal(await registry.delete(31), false);
  assert.deepEqual(serverDeletes, [31], "폐기된 ID에 두 번째 서버 삭제를 보내면 안 된다");
}

{
  const serverDeletes = [];
  const registry = new PendingInterviewFileRegistry(async (fileId) => {
    serverDeletes.push(fileId);
  });
  registry.register(41);
  registry.protect(41);
  const result = await registry.abortUploadsAndDiscard();
  assert.deepEqual(result, { deletedIds: [], failedIds: [] });
  assert.deepEqual(serverDeletes, [], "저장 여부가 불명인 보호 파일은 클라이언트가 삭제하면 안 된다");
  assert.deepEqual(registry.snapshot().protectedIds, [41]);

  registry.forget();
  assert.deepEqual(registry.snapshot().pendingIds, []);
  assert.deepEqual(registry.snapshot().protectedIds, [], "보호 파일은 서버 orphan scheduler에 맡기고 로컬에서 잊어야 한다");
  assert.deepEqual(serverDeletes, []);
}

console.log("pending interview file registry tests passed");
