import type { MockRoute } from "../registry";
import type {
  AutoPrepIntakeResponse,
  AutoPrepRequest,
  PrepModeOption,
} from "@/features/autoprep/types/autoPrep";

const FULL_STEPS = ["PROFILE", "JOB", "FIT", "WRITE", "INTERVIEW", "COMMUNITY"];
const MODE_OPTIONS: PrepModeOption[] = [
  { code: "BASIC", label: "기본 면접" },
  { code: "JOB", label: "직무 면접" },
  { code: "PERSONALITY", label: "인성 면접" },
  { code: "PRESSURE", label: "압박 면접" },
  { code: "RESUME", label: "자소서 기반" },
  { code: "PORTFOLIO", label: "포트폴리오 기반" },
  { code: "REAL", label: "실전 종합" },
  { code: "COMPANY", label: "기업 맞춤" },
];

function requestOf(body: unknown): AutoPrepRequest {
  return body && typeof body === "object" ? body as AutoPrepRequest : {};
}

let nextBinaryCaseId = 89_000;
const binaryCasesByPendingFile = new Map<number, number>();

function formNumber(body: unknown, key: string): number | null {
  if (!(body instanceof FormData)) return null;
  const parsed = Number(body.get(key));
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

/** 정적 데모에서도 공고 첨부→인테이크→실행 화면을 끊김 없이 시연하는 결정적 계약. */
export const autoprepRoutes: MockRoute[] = [
  {
    method: "POST",
    pattern: /^\/auto-prep\/job-posting-case\/upload$/,
    handler: ({ body }) => {
      const pendingFileId = formNumber(body, "pendingFileId") ?? 88_000;
      const applicationCaseId = binaryCasesByPendingFile.get(pendingFileId) ?? nextBinaryCaseId++;
      binaryCasesByPendingFile.set(pendingFileId, applicationCaseId);
      return { applicationCaseId };
    },
  },
  {
    method: "POST",
    pattern: /^\/auto-prep\/run\/cancel$/,
    handler: () => null,
  },
  {
    method: "POST",
    pattern: /^\/auto-prep\/intake$/,
    handler: ({ body }): AutoPrepIntakeResponse => {
      const req = requestOf(body);
      const query = req.query?.trim() ?? "";
      const writeOnly = query.includes("자소서만") || query.includes("첨삭만");
      const steps = writeOnly ? ["WRITE"] : FULL_STEPS;
      const needsCase = steps.some((step) => step === "JOB" || step === "FIT" || step === "INTERVIEW");
      const attachedPosting = Boolean(req.jobPostingFileIds?.length);
      const caseId = req.applicationCaseId ?? (attachedPosting ? 101 : null);
      const plan = {
        intent: writeOnly ? "CUSTOM_PREP" : "FULL_PREP",
        slots: {
          company: caseId ? "카카오" : null,
          jobTitle: caseId ? "프론트엔드 개발자" : null,
          mode: req.mode ?? "BASIC",
          applicationCaseId: caseId,
        },
        steps,
      };

      if (needsCase && attachedPosting && req.applicationCaseId == null) {
        return {
          plan,
          ready: false,
          message: "첨부한 공고를 읽어 지원 건을 준비하고 있어요. 잠시만 기다려 주세요…",
          nextAsk: "EXTRACTING",
          candidates: [],
          modes: [],
          applicationCaseId: caseId,
        };
      }
      if (needsCase && caseId == null) {
        return {
          plan,
          ready: false,
          message: "어느 지원 건으로 준비할까요?",
          nextAsk: "CASE",
          candidates: [
            { id: 101, companyName: "카카오", jobTitle: "프론트엔드 개발자", status: "READY" },
            { id: 102, companyName: "네이버", jobTitle: "백엔드 개발자", status: "READY" },
          ],
          modes: [],
        };
      }
      if (steps.includes("INTERVIEW") && !req.mode) {
        return {
          plan,
          ready: false,
          message: "면접 모드는 어떤 걸로 할까요?",
          nextAsk: "MODE",
          candidates: [],
          modes: MODE_OPTIONS,
        };
      }
      return {
        plan,
        ready: true,
        message: "좋아요 — 선택한 지원 건 기준으로 지금 바로 준비를 시작할게요.",
        nextAsk: null,
        candidates: [],
        modes: [],
      };
    },
  },
];
