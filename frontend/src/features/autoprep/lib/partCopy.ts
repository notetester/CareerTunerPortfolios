import { UserRound, FileText, Target, PenLine, Mic, MessagesSquare, type LucideIcon } from "lucide-react";

import type { PartState } from "../hooks/useAutoPrepRun";

export type PartKey = "PROFILE" | "JOB" | "FIT" | "WRITE" | "INTERVIEW" | "COMMUNITY";

/** 그리드 위치 고정 순서 — 상태가 바뀌어도 카드를 재정렬하지 않는다. */
export const PART_ORDER: PartKey[] = ["PROFILE", "JOB", "FIT", "WRITE", "INTERVIEW", "COMMUNITY"];

export const PART_META: Record<PartKey, { label: string; icon: LucideIcon }> = {
  PROFILE: { label: "프로필·역량 정리", icon: UserRound },
  JOB: { label: "공고 분석", icon: FileText },
  FIT: { label: "적합도 분석", icon: Target },
  WRITE: { label: "자소서 교정", icon: PenLine },
  INTERVIEW: { label: "예상 면접 질문", icon: Mic },
  COMMUNITY: { label: "커뮤니티 후기", icon: MessagesSquare },
};

const RUNNING_DEFAULT: Record<PartKey, string> = {
  PROFILE: "보유 스펙·경력을 정리하는 중…",
  JOB: "채용공고에서 핵심 요건을 뽑는 중…",
  FIT: "공고 요건과 회원님 경험을 맞춰보는 중…",
  WRITE: "문장에서 다듬을 곳을 찾는 중…",
  INTERVIEW: "이 공고에 맞춰 예상 질문을 만드는 중…",
  COMMUNITY: "참고할 만한 후기를 찾는 중…",
};

/** running 카드 본문 — 백엔드 substep 이벤트 텍스트를 우선, 없으면 파트 기본 문구. */
export function runningSubstep(part: PartState): string {
  const last = part.substeps[part.substeps.length - 1];
  return last?.desc || RUNNING_DEFAULT[part.key as PartKey] || "준비하는 중…";
}

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

function countList(v: unknown): number | null {
  return Array.isArray(v) ? v.length : null;
}

/** 콤마·개행 등으로 나열된 요건 문자열의 항목 수(근사치). */
function countDelimited(v: unknown): number | null {
  if (typeof v !== "string" || !v.trim()) return null;
  return v.split(/[,\n·、]/).map((s) => s.trim()).filter(Boolean).length;
}

export interface DoneSummary {
  text: string;
  chips?: string[];
  score?: number;
}

/** done 카드 — 파트별 해석 한 줄 + detail에서 안전하게 파생한 결과 칩(형태가 다르면 칩 생략). */
export function doneSummary(part: PartState): DoneSummary {
  const detail = part.result?.detail;
  const d = asRecord(detail);
  switch (part.key as PartKey) {
    case "PROFILE": {
      const strengths = countList(d?.strengths);
      const gaps = countList(d?.gaps);
      const chips: string[] = [];
      if (strengths != null) chips.push(`강점 ${strengths}`);
      if (gaps != null) chips.push(`보완 ${gaps}`);
      return { text: "이력에서 내세울 강점과 보완점을 정리했어요.", chips: chips.length ? chips : undefined };
    }
    case "JOB": {
      const total = (countDelimited(d?.requiredSkills) ?? 0) + (countDelimited(d?.preferredSkills) ?? 0);
      return {
        text: "공고에서 핵심 요건을 뽑아 정리했어요.",
        chips: total > 0 ? [`핵심 요건 ${total}`] : undefined,
      };
    }
    case "FIT": {
      const score = typeof d?.fitScore === "number" ? d.fitScore : undefined;
      const gaps = countDelimited(d?.missingSkills);
      return {
        text: "지원 건과 회원님 경험을 비교해 적합도를 산출했어요.",
        score,
        chips: gaps ? [`보완하면 좋을 ${gaps}곳`] : undefined,
      };
    }
    case "WRITE": {
      const edits = countList(d?.issues);
      return {
        text: "자소서 문장을 다듬고 근거를 보강했어요.",
        chips: edits != null ? [`${edits}곳 교정`] : undefined,
      };
    }
    case "INTERVIEW": {
      const questions = countList(d?.questions);
      return {
        text: "이 공고에 맞춘 예상 질문을 준비했어요.",
        chips: questions != null ? [`예상 질문 ${questions}개`] : undefined,
      };
    }
    case "COMMUNITY": {
      const posts = countList(detail);
      return {
        text: "비슷한 상황의 커뮤니티 후기를 모았어요.",
        chips: posts != null ? [`후기 ${posts}개`] : undefined,
      };
    }
    default:
      return { text: part.result?.summary ?? "완료했어요." };
  }
}

const SKIP_TEXT: Partial<Record<PartKey, string>> = {
  WRITE: "자소서가 아직 없어서 이 부분은 건너뛰었어요. 첨부하면 이어서 교정해드릴게요.",
  PROFILE: "프로필이 아직 없어서 이 부분은 건너뛰었어요.",
  JOB: "지원 건이 없어서 이 부분은 건너뛰었어요.",
  FIT: "지원 건이 없어서 이 부분은 건너뛰었어요.",
  INTERVIEW: "지원 건이 없어서 이 부분은 건너뛰었어요.",
};

export function skippedReason(part: PartState): string {
  return SKIP_TEXT[part.key as PartKey] ?? part.result?.summary ?? "필요한 정보가 없어서 이 부분은 건너뛰었어요.";
}

/** failed 카드 사유 — 전부 실패(failed-muted)일 때는 더 약한 문구로 반복 경고를 피한다. */
export function failedReason(muted: boolean): string {
  return muted
    ? "연결 문제로 시작하지 못했어요."
    : "분석이 잠깐 막혔어요 — 일시적인 오류라서 다시 시도하면 대부분 해결돼요.";
}

export interface PartAction {
  label: string;
  path: string;
}

/** done 카드의 인라인 액션(결과물로 이동). */
export function actionFor(key: string, caseId: number | null): PartAction | null {
  switch (key) {
    case "INTERVIEW":
      return { label: "면접 시작", path: caseId ? `/interview?caseId=${caseId}&tab=modes` : "/interview" };
    case "JOB":
      return caseId ? { label: "지원 건 열기", path: `/applications/${caseId}/job-analysis` } : null;
    case "FIT":
      return caseId ? { label: "지원 건 열기", path: `/applications/${caseId}/fit` } : null;
    case "WRITE":
      // 자소서 교정 결과 → E 첨삭 페이지 딥링크(tab=cover 자소서 탭 + caseId 프리셀렉트 — Correction.tsx 가 이미 소비).
      return caseId ? { label: "자소서 첨삭 이어가기", path: `/correction?tab=cover&caseId=${caseId}` } : null;
    case "COMMUNITY":
      return { label: "커뮤니티에서 보기", path: "/community" };
    default:
      return null;
  }
}

export interface StatusBand {
  headline: string;
  subtext: string;
  /** 재시도 컨트롤 노출 종별 — 동작은 WorkView 의 onRetry(마지막 요청 전체 재실행)로 연결.
   *  부분 재실행 API 부재로 failedOnly 도 전체 재실행이며 라벨은 "다시 시도"(WorkView 참조). */
  retryControl: "failedOnly" | "retryAll" | null;
  showProgressBar: boolean;
  allFailed: boolean;
  /** 6파트 전부 done(해피 패스) — 헤드라인 옆 초록 체크 원 노출 조건. */
  allDone: boolean;
  /** 예상질문 파트가 done이 되는 즉시(전체 완료를 기다리지 않고) true — 면접 시작 CTA 활성 조건. */
  interviewReady: boolean;
}

/** 상태줄 헤드라인 결정 로직(핸드오프 우선순위: 전부실패 → 전체완료 → 혼합 → 진행중). */
export function resolveStatusBand(parts: PartState[], running: boolean, company: string | null): StatusBand {
  const anyRunning = running || parts.some((p) => p.status === "running" || p.status === "pending");
  const settled = parts.filter((p) => p.status !== "pending" && p.status !== "running");
  const failedParts = parts.filter((p) => p.status === "failed");
  const skippedParts = parts.filter((p) => p.status === "skipped");
  const allFailed = parts.length > 0 && settled.length === parts.length && failedParts.length === parts.length;
  const allDone = parts.length > 0 && settled.length === parts.length && parts.every((p) => p.status === "done");
  const interviewDone = parts.find((p) => p.key === "INTERVIEW")?.status === "done";

  if (allFailed) {
    return {
      headline: "준비를 시작하지 못했어요",
      subtext: "서비스 연결에 일시적인 문제가 있었어요. 다시 시도하면 대부분 해결되고, 계속되면 문의하기로 알려주세요.",
      retryControl: "retryAll",
      showProgressBar: false,
      allFailed: true,
      allDone: false,
      interviewReady: false,
    };
  }

  if (!anyRunning && allDone) {
    const fit = parts.find((p) => p.key === "FIT");
    const write = parts.find((p) => p.key === "WRITE");
    const interview = parts.find((p) => p.key === "INTERVIEW");
    const fitScore = fit ? doneSummary(fit).score : undefined;
    const questionCount = interview ? doneSummary(interview).chips?.[0] : undefined;
    const writeCount = write ? doneSummary(write).chips?.[0] : undefined;
    const bits = [
      fitScore != null ? `적합도 ${fitScore}점` : null,
      questionCount ?? null,
      writeCount ? `자소서 ${writeCount.replace("곳 교정", "곳 교정까지")}` : null,
    ].filter(Boolean);
    return {
      headline: "면접 준비가 모두 끝났어요 — 지금 바로 시작할 수 있어요",
      subtext: bits.length ? `${bits.join(" · ")} 다 준비됐어요.` : "준비한 내용을 바로 확인할 수 있어요.",
      retryControl: null,
      showProgressBar: false,
      allFailed: false,
      allDone: true,
      interviewReady: true,
    };
  }

  if (!anyRunning && settled.length === parts.length) {
    const subParts: string[] = [];
    if (skippedParts.length) subParts.push("자소서는 첨부하면 이어서 교정해드려요.");
    if (failedParts.length) subParts.push(`막힌 ${failedParts.length === 1 ? "한" : "두"} 가지는 다시 시도하면 대부분 해결돼요.`);
    return {
      headline: interviewDone ? "핵심 준비는 끝났어요 — 면접은 지금 볼 수 있어요" : "일부 준비가 끝났어요 — 안 된 것부터 마저 해볼게요",
      subtext: subParts.join(" "),
      retryControl: failedParts.length ? "failedOnly" : null,
      showProgressBar: false,
      allFailed: false,
      allDone: false,
      interviewReady: interviewDone,
    };
  }

  return {
    headline: company ? `${company} 면접 준비를 하고 있어요` : "면접 준비를 하고 있어요",
    subtext: "여섯 가지를 한꺼번에 준비하고 있어요 — 먼저 끝난 것부터 바로 열어볼 수 있어요.",
    retryControl: null,
    showProgressBar: true,
    allFailed: false,
    allDone: false,
    interviewReady: interviewDone,
  };
}

/** Footer 헬퍼 텍스트 — "문의하기" 부분만 링크로 렌더링할 수 있게 앞/뒤로 쪼갠다. */
export interface FooterHelper {
  before: string;
  contactLabel: string | null;
  after: string;
}

export function footerHelperText(band: StatusBand, running: boolean): FooterHelper {
  if (band.allFailed) {
    return { before: "잠시 뒤에도 안 되면 ", contactLabel: "문의하기", after: "로 알려주세요 — 준비된 내용은 사라지지 않아요" };
  }
  if (running) {
    return { before: "나머지는 준비되는 대로 이 화면에 채워드릴게요", contactLabel: null, after: "" };
  }
  if (band.retryControl === "failedOnly") {
    return { before: "다시 시도해도 안 되면 ", contactLabel: "문의하기", after: "로 알려주세요" };
  }
  return { before: "준비 내용은 지원 건에서 언제든 다시 볼 수 있어요", contactLabel: null, after: "" };
}
