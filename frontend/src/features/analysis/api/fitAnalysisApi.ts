import { api } from "@/app/lib/api";
import type { AiModelChoice } from "@/app/components/ai/ModelPicker";
import { runWithAiCharge } from "@/features/billing/api/aiChargePreviewApi";
import type { CareerCertificateStrategy, CareerRoadmap, FitAnalysisDetail, FitAnalysisHistoryEntry, FitAnalysisLearningTask } from "../types/fitAnalysis";

export function getFitAnalyses() {
  return api<FitAnalysisDetail[]>("/fit-analyses");
}

export function getFitAnalysisByApplicationCase(applicationCaseId: number) {
  return api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}`);
}

/** 장기 커리어 자격증 전략(희망직무 기준) — 현재 지원 건 전략과 분리된 사용자 단위 조회. 외부 API 미호출(결정론). */
export function getCareerCertificateStrategy() {
  return api<CareerCertificateStrategy>("/fit-analyses/career-certificate-strategy");
}

/** 장기 커리어 로드맵(결정론) — 확인된 실일정(자격증 회차·지원 마감) + 월 단위 학습 계획 블록. 근거 수집 포함이라 수 초 걸릴 수 있다. */
export function getCareerRoadmap(months: number) {
  return api<CareerRoadmap>(`/fit-analyses/career-roadmap?months=${months}`);
}

/** 재분석 히스토리(최신순). 직전 분석 대비 점수·역량 변화를 포함한다. */
export function getFitAnalysisHistory(applicationCaseId: number) {
  return api<FitAnalysisHistoryEntry[]>(`/fit-analyses/application-cases/${applicationCaseId}/history`);
}

/**
 * 적합도 분석 생성/재생성(C 담당 AI 12~15). 백엔드는 현재 mock, API 키 주입 시 실 분석으로 전환된다.
 */
export function generateFitAnalysis(
  applicationCaseId: number,
  certificateStrategy = false,
  model: AiModelChoice = "AUTO",
) {
  // certificateStrategy=true(학습/자격증 탭 요청)면 자격증 관점을 함께 평가한다(무조건 추천은 아님).
  // model 은 설명 생성 provider 만 선택(AUTO=현행 폴백) — 판단값(점수·매칭·부족)은 규칙엔진 소유라 모델 무관 동일.
  const params = new URLSearchParams();
  if (certificateStrategy) params.set("certificateStrategy", "true");
  if (model && model !== "AUTO") params.set("model", model);
  const query = params.toString() ? `?${params.toString()}` : "";
  return runWithAiCharge("FIT_ANALYSIS", (headers) =>
    api<FitAnalysisDetail>(`/fit-analyses/application-cases/${applicationCaseId}${query}`, { method: "POST", headers }));
}

export function updateFitAnalysisLearningTask(fitAnalysisId: number, taskId: number, completed: boolean) {
  return api<FitAnalysisLearningTask>(`/fit-analyses/${fitAnalysisId}/learning-tasks/${taskId}`, {
    method: "PATCH",
    body: JSON.stringify({ completed }),
  });
}
