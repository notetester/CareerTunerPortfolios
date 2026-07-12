import assert from "node:assert/strict";
import test from "node:test";

import manifest from "../../docs/verification/demo-readiness-checks.json" with { type: "json" };
import { globToRegExp, selectChecks } from "./select-demo-regression-scope.mjs";

test("double-star patterns match nested and direct files", () => {
  const matcher = globToRegExp("backend/src/main/java/com/careertuner/profile/**");
  assert.equal(matcher.test("backend/src/main/java/com/careertuner/profile/Profile.java"), true);
  assert.equal(matcher.test("backend/src/main/java/com/careertuner/profile/service/ProfileService.java"), true);
  assert.equal(matcher.test("backend/src/main/java/com/careertuner/interview/Profile.java"), false);
});

test("profile change selects only A profile and its required cross-platform gates", () => {
  const result = selectChecks(manifest, ["backend/src/main/java/com/careertuner/profile/service/ProfileServiceImpl.java"]);
  assert.deepEqual(result.selected.map((check) => check.id), [
    "A-PROFILE",
    "DATA-LIFECYCLE",
    "PLATFORM-MOBILE",
    "PLATFORM-WEB",
    "RELEASE-GATE",
  ]);
  assert.deepEqual(result.unmatchedFiles, []);
});

test("common API change expands to the whole verification map", () => {
  const result = selectChecks(manifest, ["frontend/src/app/lib/api.ts"]);
  assert.equal(result.selected.length, manifest.checks.length);
  assert.deepEqual(result.unmatchedFiles, []);
});

test("actual applicationcase and home package paths map to their owning areas", () => {
  const result = selectChecks(manifest, [
    "backend/src/main/java/com/careertuner/applicationcase/service/ApplicationCaseServiceImpl.java",
    "backend/src/main/java/com/careertuner/home/controller/HomeController.java",
  ]);
  const ids = result.selected.map((check) => check.id);
  assert.ok(ids.includes("B-APPLICATION"));
  assert.ok(ids.includes("C-DASHBOARD"));
  assert.deepEqual(result.unmatchedFiles, []);
});

test("unknown code file is reported instead of silently skipped", () => {
  const result = selectChecks(manifest, ["tools/new-runtime.ts"]);
  assert.deepEqual(result.selected, []);
  assert.deepEqual(result.unmatchedFiles, ["tools/new-runtime.ts"]);
});
