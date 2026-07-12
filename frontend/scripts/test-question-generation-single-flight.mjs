import assert from "node:assert/strict";
import test from "node:test";
import { ModelAwareSingleFlight } from "../src/features/interview/api/questionGenerationSingleFlight.ts";

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

test("같은 세션과 모델의 동시 호출은 동일 Promise와 action key 의도로 합친다", async () => {
  const flight = new ModelAwareSingleFlight();
  const gate = deferred();
  const starts = [];
  let nextKey = 0;
  const first = flight.run(7, "CLAUDE", async () => {
    starts.push({ model: "CLAUDE", key: `key-${++nextKey}` });
    await gate.promise;
    return "claude-result";
  });
  const duplicate = flight.run(7, "CLAUDE", async () => {
    starts.push({ model: "CLAUDE", key: `key-${++nextKey}` });
    return "unexpected";
  });

  assert.strictEqual(duplicate, first);
  assert.deepEqual(starts, [{ model: "CLAUDE", key: "key-1" }]);
  gate.resolve();
  assert.equal(await duplicate, "claude-result");
});

test("실행 중 다른 모델 선택은 앞 요청 뒤 새 Promise와 새 action key로 실행한다", async () => {
  const flight = new ModelAwareSingleFlight();
  const gate = deferred();
  const starts = [];
  let nextKey = 0;
  const first = flight.run(11, "CLAUDE", async () => {
    starts.push({ model: "CLAUDE", key: `key-${++nextKey}` });
    await gate.promise;
    return "claude-result";
  });
  const changed = flight.run(11, "OPENAI", async () => {
    starts.push({ model: "OPENAI", key: `key-${++nextKey}` });
    return "openai-result";
  });

  assert.notStrictEqual(changed, first);
  assert.deepEqual(starts, [{ model: "CLAUDE", key: "key-1" }]);
  gate.resolve();
  assert.equal(await first, "claude-result");
  assert.equal(await changed, "openai-result");
  assert.deepEqual(starts, [
    { model: "CLAUDE", key: "key-1" },
    { model: "OPENAI", key: "key-2" },
  ]);
});

test("앞 요청 실패 후에도 사용자가 바꾼 모델의 새 의도는 실행한다", async () => {
  const flight = new ModelAwareSingleFlight();
  const gate = deferred();
  const starts = [];
  const first = flight.run(15, "CLAUDE", async () => {
    starts.push("CLAUDE");
    await gate.promise;
    return "unreachable";
  });
  const changed = flight.run(15, "CAREERTUNER", async () => {
    starts.push("CAREERTUNER");
    return "self-result";
  });

  gate.reject(new Error("timeout"));
  await assert.rejects(first, /timeout/);
  assert.equal(await changed, "self-result");
  assert.deepEqual(starts, ["CLAUDE", "CAREERTUNER"]);
});
