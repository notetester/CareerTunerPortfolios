import type { MockContext, MockRoute } from "../../registry";
import { iso } from "../../registry";
import type {
  AdminCorrectionDetail,
  AdminCorrectionFailureRow,
  AdminCorrectionPage,
  AdminCorrectionRow,
  AdminCorrectionSummary,
} from "@/admin/features/corrections/types";

const details: AdminCorrectionDetail[] = [
  {
    id: 7303, userId: 9001, userEmail: "demo@careertuner.dev", userName: "김데모",
    applicationCaseId: 101, companyName: "네오핀", jobTitle: "백엔드 엔지니어",
    correctionType: "SELF_INTRO", sourceType: "DIRECT_INPUT", sourceRefId: null,
    originalText: "프로젝트에서 API 개발을 담당했습니다. 문제가 생겼을 때 팀원들과 해결했습니다.",
    improvedText: "프로젝트의 핵심 API를 설계·구현하며 응답 지연 문제를 추적했습니다. 팀원과 지표를 공유하고 쿼리를 개선해 평균 응답 시간을 단축했습니다.",
    resultJson: JSON.stringify({ summary: "역할과 문제 해결 결과를 구체화했습니다.", issues: ["성과 수치가 부족함", "협업 과정이 추상적임"], changeReasons: ["담당 범위를 명확히 표현", "문제·행동·결과 순서로 재구성"], suggestions: ["실제 응답 시간 수치를 추가하세요."] }),
    status: "SUCCESS", aiUsageLogId: 8303, model: "correction-3b", inputTokens: 160, outputTokens: 230,
    totalTokens: 390, creditUsed: 2, adminMemo: "장문 보존 정상", createdAt: iso(0),
  },
  {
    id: 7302, userId: 9002, userEmail: "jiwon.park@example.com", userName: "박지원",
    applicationCaseId: 102, companyName: "마루소프트", jobTitle: "프론트엔드 개발자",
    correctionType: "INTERVIEW_ANSWER", sourceType: "DIRECT_INPUT", sourceRefId: null,
    originalText: "갈등이 있었지만 대화로 해결했습니다.",
    improvedText: "일정 산정 방식에 대한 의견 차이가 발생했을 때 작업을 기능 단위로 나눠 근거를 비교했습니다. 합의된 기준으로 일정을 다시 산정해 출시 범위를 지켰습니다.",
    resultJson: JSON.stringify({ summary: "갈등 해결 과정을 행동 중심으로 보완했습니다.", issues: ["갈등 원인이 불명확함"], changeReasons: ["합의 과정 추가"], suggestions: ["본인의 제안이 반영된 결과를 설명하세요."] }),
    status: "SUCCESS", aiUsageLogId: 8302, model: "correction-3b", inputTokens: 120, outputTokens: 190,
    totalTokens: 310, creditUsed: 2, adminMemo: null, createdAt: iso(1),
  },
  {
    id: 7301, userId: 9003, userEmail: "minseo.choi@example.com", userName: "최민서",
    applicationCaseId: null, companyName: null, jobTitle: null,
    correctionType: "RESUME", sourceType: "DIRECT_INPUT", sourceRefId: null,
    originalText: "Spring Boot 개발, MySQL 사용, 배포 경험",
    improvedText: "Spring Boot 기반 REST API 개발 · MyBatis/MySQL 데이터 계층 구현 · CI 파이프라인을 통한 배포 자동화 경험",
    resultJson: JSON.stringify({ summary: "기술 경험을 수행 단위로 정리했습니다.", issues: [], changeReasons: ["기술과 역할 연결"], suggestions: ["프로젝트 규모를 함께 기재하세요."] }),
    status: "SUCCESS", aiUsageLogId: 8301, model: "correction-3b", inputTokens: 80, outputTokens: 130,
    totalTokens: 210, creditUsed: 2, adminMemo: null, createdAt: iso(3),
  },
];

const failures: AdminCorrectionFailureRow[] = [
  { id: 8399, userId: 9004, userEmail: "hyun.kang@example.com", userName: "강현", applicationCaseId: 104, companyName: "코어랩", jobTitle: "데이터 엔지니어", featureType: "CORRECTION_PORTFOLIO", model: "correction-3b", inputTokens: 1850, outputTokens: 0, totalTokens: 1850, errorMessage: "Self model request timed out after 40 seconds.", createdAt: iso(0) },
  { id: 8398, userId: 9002, userEmail: "jiwon.park@example.com", userName: "박지원", applicationCaseId: 102, companyName: "마루소프트", jobTitle: "프론트엔드 개발자", featureType: "CORRECTION_SELF_INTRO", model: "correction-3b", inputTokens: 940, outputTokens: 310, totalTokens: 1250, errorMessage: "Output length ratio did not satisfy the preservation policy.", createdAt: iso(2) },
];

function rows(): AdminCorrectionRow[] {
  return details.map(({ sourceRefId: _sourceRefId, originalText: _originalText, improvedText: _improvedText, resultJson: _resultJson, aiUsageLogId: _aiUsageLogId, inputTokens: _inputTokens, outputTokens: _outputTokens, adminMemo, ...row }) => ({ ...row, hasMemo: Boolean(adminMemo) }));
}

function summary(): AdminCorrectionSummary {
  const success = details.filter((item) => item.status === "SUCCESS").length;
  return { totalRequests: details.length, successCount: success, failureCount: failures.length, memoCount: details.filter((item) => Boolean(item.adminMemo)).length, todayCount: 1 };
}

export const adminCorrectionRoutes: MockRoute[] = [
  { method: "GET", pattern: /^\/admin\/corrections\/summary$/, handler: () => summary() },
  { method: "GET", pattern: /^\/admin\/corrections\/ai-failures$/, handler: ({ query }) => failures.slice(0, Math.min(Number(query.get("limit") ?? 50) || 50, 200)) },
  {
    method: "GET", pattern: /^\/admin\/corrections\/(\d+)$/, handler: ({ params }) => {
      const detail = details.find((item) => item.id === Number(params[0]));
      if (!detail) throw new Error("첨삭 요청을 찾을 수 없습니다.");
      return { ...detail };
    },
  },
  {
    method: "PUT", pattern: /^\/admin\/corrections\/(\d+)\/memo$/, handler: ({ params, body }) => {
      const detail = details.find((item) => item.id === Number(params[0]));
      if (!detail) throw new Error("첨삭 요청을 찾을 수 없습니다.");
      const request = body as { memo?: unknown };
      if (request.memo !== null && request.memo !== undefined && typeof request.memo !== "string") throw new Error("운영 메모 형식이 올바르지 않습니다.");
      const memo = typeof request.memo === "string" ? request.memo.trim() : "";
      if (memo.length > 2000) throw new Error("운영 메모는 2000자 이하여야 합니다.");
      detail.adminMemo = memo || null;
      return null;
    },
  },
  {
    method: "GET", pattern: /^\/admin\/corrections$/, handler: ({ query }: MockContext): AdminCorrectionPage => {
      const keyword = (query.get("keyword") ?? "").trim().toLowerCase();
      const correctionType = query.get("correctionType");
      const memoState = query.get("memoState");
      const page = Math.max(1, Number(query.get("page") ?? 1) || 1);
      const size = Math.min(100, Math.max(1, Number(query.get("size") ?? 20) || 20));
      const filtered = rows().filter((row) => {
        const haystack = [row.userEmail, row.userName, row.companyName, row.jobTitle].filter(Boolean).join(" ").toLowerCase();
        if (keyword && !haystack.includes(keyword)) return false;
        if (correctionType && row.correctionType !== correctionType) return false;
        if (memoState === "HAS_MEMO" && !row.hasMemo) return false;
        if (memoState === "NO_MEMO" && row.hasMemo) return false;
        return true;
      }).sort((a, b) => b.id - a.id);
      const offset = (page - 1) * size;
      return { items: filtered.slice(offset, offset + size), total: filtered.length, page, size };
    },
  },
];
