export const INTERVIEW_SECTIONS = [
  "modes",
  "questions",
  "practice",
  "live",
  "avatar",
  "evaluation",
  "report",
] as const;

export type InterviewSection = (typeof INTERVIEW_SECTIONS)[number];

export const LEGACY_INTERVIEW_TABS = [...INTERVIEW_SECTIONS, "correction"] as const;
export type InterviewTab = (typeof LEGACY_INTERVIEW_TABS)[number];

export const INTERVIEW_SECTION_PATHS: Record<InterviewSection, string> = {
  modes: "/interview/modes",
  questions: "/interview/questions",
  practice: "/interview/practice",
  live: "/interview/live",
  avatar: "/interview/avatar",
  evaluation: "/interview/evaluation",
  report: "/interview/reports",
};

export const INTERVIEW_SECTION_META: Record<InterviewSection, {
  label: string;
  description: string;
  sessionRequired: boolean;
}> = {
  modes: {
    label: "면접 모드 선택",
    description: "지원 건과 면접 유형을 고르고 새 면접을 시작합니다.",
    sessionRequired: false,
  },
  questions: {
    label: "예상 면접 질문",
    description: "선택한 지원 건에 맞춘 질문을 만들고 답변을 연습합니다.",
    sessionRequired: true,
  },
  practice: {
    label: "복습 테스트",
    description: "예상 질문을 무작위로 풀고 답변을 한 번에 채점합니다.",
    sessionRequired: true,
  },
  live: {
    label: "음성 모의면접",
    description: "실시간 또는 로컬 음성 면접으로 말하기를 훈련합니다.",
    sessionRequired: true,
  },
  avatar: {
    label: "아바타 화상 면접",
    description: "표정·시선·음성을 함께 분석하는 화상 면접을 진행합니다.",
    sessionRequired: true,
  },
  evaluation: {
    label: "답변 평가",
    description: "저장된 답변의 점수와 개선안, 채점 기준을 함께 확인합니다.",
    sessionRequired: true,
  },
  report: {
    label: "면접 리포트",
    description: "답변과 음성·영상 분석을 합친 세션별 결과를 확인합니다.",
    sessionRequired: true,
  },
};

export function parseInterviewSection(value: string | null): InterviewSection | null {
  return value && INTERVIEW_SECTIONS.includes(value as InterviewSection)
    ? value as InterviewSection
    : null;
}

export function parseInterviewTab(value: string | null): InterviewTab | null {
  return value && LEGACY_INTERVIEW_TABS.includes(value as InterviewTab)
    ? value as InterviewTab
    : null;
}

export function interviewSectionHref(
  section: InterviewSection,
  options: {
    sessionId?: number | null;
    caseId?: number | null;
    preserve?: URLSearchParams;
  } = {},
): string {
  const search = new URLSearchParams(options.preserve);
  search.delete("tab");
  search.delete("auto");

  if (options.sessionId != null) search.set("session", String(options.sessionId));
  else search.delete("session");

  if (options.caseId != null) search.set("caseId", String(options.caseId));
  else search.delete("caseId");

  const query = search.toString();
  return `${INTERVIEW_SECTION_PATHS[section]}${query ? `?${query}` : ""}`;
}
