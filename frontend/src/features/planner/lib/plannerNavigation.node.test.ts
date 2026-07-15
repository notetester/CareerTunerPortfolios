import assert from "node:assert/strict";
import test from "node:test";
import {
  findPlannerEditTarget,
  parsePlannerItemId,
  plannerEditHref,
} from "./plannerNavigation.ts";

test("오버레이 편집 이동은 독립 화면 경로와 편집 ID를 함께 보존한다", () => {
  assert.equal(plannerEditHref("memo", 72), "/planner/memos?edit=72");
  assert.equal(plannerEditHref("schedule", 105), "/planner/schedule?edit=105");
});

test("편집 ID는 안전한 양의 정수만 허용한다", () => {
  assert.equal(parsePlannerItemId("72"), 72);
  assert.equal(parsePlannerItemId("0"), null);
  assert.equal(parsePlannerItemId("-1"), null);
  assert.equal(parsePlannerItemId("1.5"), null);
  assert.equal(parsePlannerItemId("unknown"), null);
  assert.equal(parsePlannerItemId(null), null);
  assert.throws(() => plannerEditHref("memo", 0), /양의 정수/);
});

test("대상 화면은 API 목록에서 URL의 편집 대상을 다시 결합한다", () => {
  const items = [
    { id: 72, title: "첫 번째" },
    { id: 105, title: "편집 대상" },
  ];

  assert.deepEqual(findPlannerEditTarget(items, 105), items[1]);
  assert.equal(findPlannerEditTarget(items, 999), null);
  assert.equal(findPlannerEditTarget(items, null), null);
});
