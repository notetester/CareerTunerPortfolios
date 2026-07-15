/**
 * Real-path selftest for approve-only draft merge (criterion 4).
 * Run: npx tsx src/app/profile/profileDraftMerge.selftest.ts
 */
import { draftPickFromCounts, mergeApprovedProfileDraft } from "./profileDraftMerge";

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) {
    console.error("FAIL:", msg);
    process.exit(1);
  }
}

const pick = draftPickFromCounts({
  education: 0,
  career: 0,
  projects: 0,
  skills: 2,
  portfolioLinks: 0,
});
assert(pick.education === false, "education pick false when count 0");
assert(pick.career === false, "career pick false when count 0");
assert(pick.projects === false, "projects pick false when count 0");
assert(pick.skills === true, "skills pick true when count > 0");

const cur = {
  versionNo: 7,
  education: [{ school: "Keep Me U" }],
  career: [{ company: "Keep Co" }],
  projects: [{ title: "Keep Proj" }],
  skills: ["Python"],
  portfolioLinks: ["https://old.example"],
  resumeText: "r",
  selfIntro: "s",
};

const emptyDraft = {
  education: [] as unknown[],
  career: [] as unknown[],
  projects: [] as unknown[],
  skills: ["Java"],
  portfolioLinks: [] as string[],
};

// Even if all opts true, empty arrays must NOT wipe existing education/career/projects.
const merged = mergeApprovedProfileDraft(cur, emptyDraft, {
  education: true,
  career: true,
  projects: true,
  skills: true,
  portfolioLinks: true,
});
assert(
  JSON.stringify(merged.education) === JSON.stringify(cur.education),
  "empty education draft must not wipe",
);
assert(
  JSON.stringify(merged.career) === JSON.stringify(cur.career),
  "empty career draft must not wipe",
);
assert(
  JSON.stringify(merged.projects) === JSON.stringify(cur.projects),
  "empty projects draft must not wipe",
);
assert(
  Array.isArray(merged.skills) &&
    merged.skills.includes("Python") &&
    merged.skills.includes("Java"),
  "skills union",
);
assert(merged.versionNo === 7, "draft merge must preserve optimistic-lock version");

const filled = mergeApprovedProfileDraft(
  cur,
  {
    education: [{ school: "New U" }],
    career: [],
    projects: [],
    skills: [],
    portfolioLinks: [],
  },
  {
    education: true,
    career: false,
    projects: false,
    skills: false,
    portfolioLinks: false,
  },
);
assert(
  Array.isArray(filled.education) &&
    (filled.education[0] as { school: string }).school === "New U",
  "non-empty education replaces when approved",
);
assert(
  JSON.stringify(filled.career) === JSON.stringify(cur.career),
  "unapproved/empty career keeps current",
);

console.log("PASS profileDraftMerge selftest");
