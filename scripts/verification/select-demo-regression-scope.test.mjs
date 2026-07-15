import assert from "node:assert/strict";
import test from "node:test";

import manifest from "../../docs/verification/demo-readiness-checks.json" with { type: "json" };
import { addGitFileList, globToRegExp, selectChecks } from "./select-demo-regression-scope.mjs";

test("working-tree file aggregation keeps staged, unstaged, and untracked paths", () => {
  const files = new Set();
  addGitFileList(files, "unstaged.md\nstaged.md\nuntracked.md\nstaged.md\n");
  assert.deepEqual([...files], ["unstaged.md", "staged.md", "untracked.md"]);
});

test("double-star patterns match nested and direct files", () => {
  const matcher = globToRegExp("backend/src/main/java/com/careertuner/profile/**");
  assert.equal(matcher.test("backend/src/main/java/com/careertuner/profile/Profile.java"), true);
  assert.equal(matcher.test("backend/src/main/java/com/careertuner/profile/service/ProfileService.java"), true);
  assert.equal(matcher.test("backend/src/main/java/com/careertuner/interview/Profile.java"), false);
});

test("profile change selects only A profile and its required cross-platform gates", () => {
  const result = selectChecks(manifest, [
    "backend/src/main/java/com/careertuner/profile/service/ProfileServiceImpl.java",
    "frontend/scripts/test-profile-save-boundaries.mjs",
  ]);
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

test("environment profile and test datasource changes expand to the whole verification map", () => {
  const result = selectChecks(manifest, [
    "backend/src/main/resources/application-aws.yaml",
    "backend/src/test/resources/application.properties",
    "config/environments.json",
  ]);
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

test("dependencies, NCS, interview ML, admin chatbot, and documentation tooling stay mapped", () => {
  const result = selectChecks(manifest, [
    "frontend/package-lock.json",
    ".github/dependabot.yml",
    "ml/ncs-catalog/scripts/load_ncs_db.py",
    "ml/career-strategy-llm/scripts/run_e2e_production_baseline.py",
    "ml/interview-finetune/briefing.py",
    "backend/src/main/java/com/careertuner/admin/chatbot/controller/AdminChatbotConversationController.java",
    "docs/planning/prototypes/orchestrator-flow.svg",
    "scripts/docs/check-markdown-links.mjs",
  ]);
  const ids = result.selected.map((check) => check.id);
  assert.ok(ids.includes("C-FIT-ANALYSIS"));
  assert.ok(ids.includes("D-INTERVIEW"));
  assert.ok(ids.includes("F-SUPPORT-CONTENT"));
  assert.ok(ids.includes("PLATFORM-MOBILE"));
  assert.ok(ids.includes("PLATFORM-WEB"));
  assert.ok(ids.includes("RELEASE-GATE"));
  assert.deepEqual(result.unmatchedFiles, []);
});

test("exact submodule gitlink changes are intentionally ignored", () => {
  const result = selectChecks(manifest, ["docs/ai-reports"]);
  assert.deepEqual(result.selected, []);
  assert.deepEqual(result.unmatchedFiles, []);
});

test("documentation artifacts and removed runtime logs are intentionally ignored", () => {
  const result = selectChecks(manifest, [
    "docs/archive/2026-06/a-profile-db-design-snapshot.pdf",
    "frontend/.env.example",
    "logs/careertuner-2026-07-08.0.log.gz",
  ]);
  assert.deepEqual(result.selected, []);
  assert.deepEqual(result.unmatchedFiles, []);
});

test("billing mock mutations select the billing boundary", () => {
  const result = selectChecks(manifest, ["frontend/src/app/lib/mock/domains/billing.ts"]);
  assert.ok(result.selected.some((check) => check.id === "E-BILLING-CREDITS"));
  assert.deepEqual(result.unmatchedFiles, []);
});

test("unknown code file is reported instead of silently skipped", () => {
  const result = selectChecks(manifest, ["tools/new-runtime.ts"]);
  assert.deepEqual(result.selected, []);
  assert.deepEqual(result.unmatchedFiles, ["tools/new-runtime.ts"]);
});
