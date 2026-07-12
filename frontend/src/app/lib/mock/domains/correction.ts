import type { MockRoute } from "../registry";
import { iso } from "../registry";
import type { CorrectionCreateRequest, CorrectionResponse } from "@/features/correction/types/correction";
import { CORRECTION_MIN_CREDIT_COST } from "@/features/correction/types/correction";
import { chargeMockAiUsage } from "./billing";

let nextId = 304;
let warmed = false;
const correctionsByRequestKey = new Map<string, CorrectionResponse>();

const corrections: CorrectionResponse[] = [
  {
    id: 303,
    applicationCaseId: 101,
    correctionType: "SELF_INTRO",
    sourceType: "DIRECT_INPUT",
    sourceRefId: null,
    originalText: "고객의 요구사항을 정리하고 팀원들과 협업해 프로젝트를 완료했습니다.",
    improvedText: "고객 인터뷰 5건에서 핵심 요구사항을 정리하고 우선순위를 합의해, 팀 일정 내 프로젝트를 완료했습니다.",
    sourceSnapshot: JSON.stringify({ fitAnalysis: { fitAnalysisId: 5101, missingSkills: ["대규모 트래픽 경험"], strategy: "API 성능 개선 근거를 강조" }, requestedModel: "AUTO", actualModel: "mock-correction" }),
    summary: "역할과 행동을 구체화하고 협업 결과가 드러나도록 개선했습니다.",
    issues: ["본인의 역할이 추상적입니다.", "성과를 확인할 기준이 부족합니다."],
    changeReasons: ["고객 인터뷰 횟수와 우선순위 합의 과정을 추가했습니다."],
    suggestions: ["일정 단축률이나 고객 피드백 수치를 추가해 보세요."],
    status: "SUCCESS",
    aiUsageLogId: 901,
    createdAt: iso(1),
  },
  {
    id: 302,
    applicationCaseId: null,
    correctionType: "INTERVIEW_ANSWER",
    sourceType: "DIRECT_INPUT",
    sourceRefId: null,
    originalText: "갈등이 있었지만 대화로 잘 해결했습니다.",
    improvedText: "일정 기준을 두고 의견이 엇갈렸을 때 각 안의 위험을 표로 정리했고, 공통 기준을 합의해 마감일을 지켰습니다.",
    summary: "갈등 상황, 해결 행동, 결과가 순서대로 보이도록 보강했습니다.",
    issues: ["갈등 원인과 해결 행동이 구체적이지 않습니다."],
    changeReasons: ["의사결정 기준과 합의 과정을 명시했습니다."],
    suggestions: ["본인이 제안한 기준을 한 가지 더 구체화해 보세요."],
    status: "SUCCESS",
    aiUsageLogId: 900,
    createdAt: iso(3),
  },
];

export const correctionRoutes: MockRoute[] = [
  {
    method: "POST",
    pattern: /^\/corrections\/warmup$/,
    handler: () => {
      const status = warmed ? "ALREADY_WARM" : "STARTED";
      warmed = true;
      return { status, model: "careertuner-e-correction-3b:latest" };
    },
  },
  {
    method: "GET",
    pattern: /^\/corrections\/sources\/interview-answers\/(\d+)$/,
    handler: ({ params }) => ({
      sourceRefId: Number(params[0]),
      applicationCaseId: 101,
      sessionId: 801,
      questionId: 8101,
      questionText: "협업 갈등을 해결한 경험을 설명해 주세요.",
      originalText: "일정 기준에 대한 의견 차이를 대화로 조율해 마감일을 지켰습니다.",
      score: 72,
      feedback: "본인의 행동과 합의 기준을 더 구체적으로 설명해 주세요.",
      answeredAt: iso(0),
    }),
  },
  {
    method: "POST",
    pattern: /^\/corrections$/,
    handler: ({ body, query }) => {
      const request = body as CorrectionCreateRequest;
      if (!request.policyAcknowledgementKey) {
        throw new Error("차감 정책 확인키가 필요합니다.");
      }
      if (!request.requestKey) {
        throw new Error("첨삭 요청키가 필요합니다.");
      }
      const existing = correctionsByRequestKey.get(request.requestKey);
      if (existing) {
        return { ...existing, replayed: true };
      }
      const originalText = request.originalText.trim();
      const result: CorrectionResponse = {
        id: nextId++,
        applicationCaseId: request.applicationCaseId ?? null,
        correctionType: request.correctionType,
        sourceType: request.sourceType || "DIRECT_INPUT",
        sourceRefId: request.sourceRefId ?? null,
        originalText,
        improvedText: improveText(originalText),
        sourceSnapshot: JSON.stringify({
          contextVersion: "e-correction-context-v1",
          fitAnalysis: request.applicationCaseId ? { fitAnalysisId: 5101, missingSkills: ["대규모 트래픽 경험"], strategy: "근거 중심 표현" } : undefined,
          interviewAnswer: request.sourceRefId ? { answerId: request.sourceRefId } : undefined,
          requestedModel: query.get("model") ?? "AUTO",
          actualModel: `mock-demo:${query.get("model") ?? "AUTO"}`,
        }),
        summary: "핵심 행동과 결과가 먼저 보이도록 문장을 정리했습니다.",
        issues: ["역할과 성과가 한 문장에 섞여 있었습니다."],
        changeReasons: ["행동과 결과를 분리하고 능동형 표현으로 바꿨습니다."],
        suggestions: ["가능하면 기간, 횟수, 개선율 같은 근거를 추가하세요."],
        status: "SUCCESS",
        aiUsageLogId: 900 + nextId,
        replayed: false,
        createdAt: new Date().toISOString(),
      };
      chargeMockAiUsage(`CORRECTION_${request.correctionType}`, CORRECTION_MIN_CREDIT_COST);
      corrections.unshift(result);
      correctionsByRequestKey.set(request.requestKey, result);
      return result;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/corrections\/(\d+)$/,
    handler: ({ params }) => {
      const index = corrections.findIndex((item) => item.id === Number(params[0]));
      if (index < 0) throw new Error("첨삭 기록을 찾을 수 없습니다.");
      corrections.splice(index, 1);
      return null;
    },
  },
  {
    method: "GET",
    pattern: /^\/corrections$/,
    handler: ({ query }) => {
      const applicationCaseId = query.get("applicationCaseId");
      const correctionType = query.get("correctionType");
      const limit = Math.max(1, Number(query.get("limit") ?? 20) || 20);
      return corrections
        .filter((item) => applicationCaseId === null || item.applicationCaseId === Number(applicationCaseId))
        .filter((item) => correctionType === null || item.correctionType === correctionType)
        .slice(0, limit);
    },
  },
  {
    method: "GET",
    pattern: /^\/corrections\/(\d+)$/,
    handler: ({ params }) => {
      const result = corrections.find((item) => item.id === Number(params[0]));
      if (!result) throw new Error("첨삭 기록을 찾을 수 없습니다.");
      return result;
    },
  },
];

function improveText(originalText: string) {
  return `핵심 역할과 결과가 드러나도록 다듬은 문장입니다.\n\n${originalText}`;
}
