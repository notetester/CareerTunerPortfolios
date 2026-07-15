import assert from "node:assert/strict";
import test from "node:test";
import {
  mergeProfileSectionPatch,
  ProfileSectionConflictError,
  profileValueEquals,
  rebaseDraftAfterCommit,
} from "./profileSectionMerge.ts";

interface ProfileFixture {
  desiredJob: string;
  resumeText: string;
  selfIntro: string;
  skills: string[];
  preferences: { region: string; workType: string };
  versionNo: number;
}

const baseline: ProfileFixture = {
  desiredJob: "백엔드 개발자",
  resumeText: "이전 이력서",
  selfIntro: "이전 자기소개",
  skills: ["Java"],
  preferences: { region: "서울", workType: "출근" },
  versionNo: 1,
};

test("다른 section이 먼저 저장한 필드는 최신 서버 값을 유지한다", () => {
  const latest = { ...baseline, desiredJob: "플랫폼 개발자", versionNo: 2 };
  const saved = mergeProfileSectionPatch(baseline, latest, { resumeText: "새 이력서" });

  assert.equal(saved.desiredJob, "플랫폼 개발자");
  assert.equal(saved.resumeText, "새 이력서");
  assert.equal(saved.versionNo, 2);
});

test("같은 profile field를 양쪽이 다르게 수정하면 자동 덮어쓰지 않는다", () => {
  const latest = { ...baseline, resumeText: "다른 탭의 이력서", versionNo: 2 };
  assert.throws(
    () => mergeProfileSectionPatch(baseline, latest, { resumeText: "현재 탭의 이력서" }),
    (error) => error instanceof ProfileSectionConflictError
      && error.fields.length === 1
      && error.fields[0] === "resumeText",
  );
});

test("로컬에서 바꾸지 않은 field는 서버의 최신 변경을 그대로 채택한다", () => {
  const latest = { ...baseline, resumeText: "다른 화면에서 저장한 이력서", versionNo: 2 };
  const saved = mergeProfileSectionPatch(baseline, latest, { resumeText: baseline.resumeText });
  assert.equal(saved.resumeText, "다른 화면에서 저장한 이력서");
  assert.equal(saved.versionNo, 2);
});

test("양쪽의 최종 값이 같으면 충돌이 아니다", () => {
  const latest = { ...baseline, selfIntro: "같은 결과", versionNo: 2 };
  const saved = mergeProfileSectionPatch(baseline, latest, { selfIntro: "같은 결과" });
  assert.equal(saved.selfIntro, "같은 결과");
  assert.equal(saved.versionNo, 2);
});

test("객체 키 순서가 달라도 동일한 JSON 값으로 판정한다", () => {
  assert.equal(
    profileValueEquals({ region: "서울", workType: "원격" }, { workType: "원격", region: "서울" }),
    true,
  );
});

test("저장 중 입력한 field만 커밋 응답 위에 보존한다", () => {
  const requestStart = { text: "저장 버튼을 누른 내용", note: "기존 메모" };
  const current = { text: "저장 중 더 입력한 내용", note: "기존 메모" };
  const committed = { text: "저장 버튼을 누른 내용", note: "서버 정규화 메모" };

  assert.deepEqual(rebaseDraftAfterCommit(requestStart, current, committed), {
    draft: {
      text: "저장 중 더 입력한 내용",
      note: "서버 정규화 메모",
    },
    conflicts: [],
  });
});

test("같은 객체의 서로 다른 하위 field 변경은 둘 다 보존한다", () => {
  const requestStart = { preferences: { region: "서울", salary: "4000" } };
  const current = { preferences: { region: "부산", salary: "4000" } };
  const committed = { preferences: { region: "서울", salary: "5000" } };

  assert.deepEqual(rebaseDraftAfterCommit(requestStart, current, committed), {
    draft: { preferences: { region: "부산", salary: "5000" } },
    conflicts: [],
  });
});

test("같은 하위 field를 양쪽이 다르게 바꾸면 로컬 입력을 보존하고 충돌을 보고한다", () => {
  const requestStart = { preferences: { region: "서울", salary: "4000" } };
  const current = { preferences: { region: "부산", salary: "4000" } };
  const committed = { preferences: { region: "대전", salary: "4000" } };

  assert.deepEqual(rebaseDraftAfterCommit(requestStart, current, committed), {
    draft: { preferences: { region: "부산", salary: "4000" } },
    conflicts: ["draft.preferences.region"],
  });
});

test("배열을 양쪽이 바꾸면 순서 추정 대신 충돌로 닫는다", () => {
  const requestStart = { career: [{ company: "A" }] };
  const current = { career: [{ company: "A" }, { company: "B" }] };
  const committed = { career: [{ company: "A+" }] };

  const result = rebaseDraftAfterCommit(requestStart, current, committed);
  assert.deepEqual(result.draft, current);
  assert.deepEqual(result.conflicts, ["draft.career"]);
});
