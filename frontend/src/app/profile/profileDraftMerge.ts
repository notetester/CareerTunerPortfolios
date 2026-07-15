import type { ProfileAnalyzeDraft, UserProfile } from "./profileApi";

/** 확인 카드 체크박스 초기값 — 건수가 0이면 false (빈 [] 덮어쓰기 방지). */
export function draftPickFromCounts(counts: {
  education: number;
  career: number;
  projects: number;
  skills: number;
  portfolioLinks: number;
}) {
  return {
    education: counts.education > 0,
    career: counts.career > 0,
    projects: counts.projects > 0,
    skills: counts.skills > 0,
    portfolioLinks: counts.portfolioLinks > 0,
  };
}

export type DraftApplyOpts = {
  education: boolean;
  career: boolean;
  projects: boolean;
  skills: boolean;
  portfolioLinks: boolean;
};

function nonEmptyArray(v: unknown): v is unknown[] {
  return Array.isArray(v) && v.length > 0;
}

function unionStrings(existing: unknown, incoming: string[] | null | undefined): unknown {
  if (!incoming?.length) return existing;
  const base = Array.isArray(existing) ? (existing as string[]).map(String) : [];
  const seen = new Set(base.map((s) => s.toLowerCase()));
  const merged = [...base];
  for (const s of incoming) {
    const k = String(s).toLowerCase();
    if (!seen.has(k)) {
      seen.add(k);
      merged.push(s);
    }
  }
  return merged;
}

/**
 * 승인된 필드만 현재 프로필에 병합. 빈 배열은 기존 값을 지우므로 교체하지 않는다.
 * 온보딩 applyProfileDraft / Profile 확인 카드가 동일 규칙을 쓴다.
 */
export function mergeApprovedProfileDraft(
  cur: UserProfile,
  draft: ProfileAnalyzeDraft,
  opts: DraftApplyOpts,
): UserProfile {
  return {
    // saveProfile()이 이 값을 baseVersionNo로 변환한다. 승인 초안 GET→merge→PUT도
    // 일반 프로필 편집과 같은 optimistic-lock 기준을 잃으면 안 된다.
    versionNo: cur.versionNo,
    desiredJob: cur.desiredJob ?? null,
    desiredIndustry: cur.desiredIndustry ?? null,
    education:
      opts.education && nonEmptyArray(draft.education) ? draft.education : cur.education,
    career: opts.career && nonEmptyArray(draft.career) ? draft.career : cur.career,
    projects: opts.projects && nonEmptyArray(draft.projects) ? draft.projects : cur.projects,
    skills: opts.skills ? unionStrings(cur.skills, draft.skills ?? undefined) : cur.skills,
    certificates: cur.certificates,
    languages: cur.languages,
    portfolioLinks: opts.portfolioLinks
      ? unionStrings(cur.portfolioLinks, draft.portfolioLinks ?? undefined)
      : cur.portfolioLinks,
    resumeText: cur.resumeText ?? null,
    selfIntro: cur.selfIntro ?? null,
    preferences: cur.preferences,
  };
}
