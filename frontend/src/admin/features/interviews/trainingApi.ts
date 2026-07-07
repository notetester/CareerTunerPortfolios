import { api } from "@/app/lib/api";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken } from "@/app/lib/tokenStore";
import type { EvalHarnessResult, FineTuneResult, TrainingStats } from "./types";

// 관리자 면접 학습 파이프라인: /api/admin/interview/training/**

/** 학습 샘플 통계(누적 수 + 평균 점수). */
export function getTrainingStats(): Promise<TrainingStats> {
  return api<TrainingStats>("/admin/interview/training/stats", { method: "GET" });
}

/** 평가 하니스(LLM-as-judge) 실행 → 채점 일관성 측정. */
export function runEvalHarness(sampleSize = 20): Promise<EvalHarnessResult> {
  return api<EvalHarnessResult>(`/admin/interview/training/eval?sampleSize=${sampleSize}`, { method: "POST" });
}

/** 파인튜닝 잡 생성(OpenAI). 자체 모델 LoRA 는 ml/interview-finetune 참고. */
export function startFineTune(baseModel?: string): Promise<FineTuneResult> {
  const query = baseModel ? `?baseModel=${encodeURIComponent(baseModel)}` : "";
  return api<FineTuneResult>(`/admin/interview/training/fine-tune${query}`, { method: "POST" });
}

// export 는 JSONL 바이너리 다운로드라 ApiResponse envelope 가 아니므로 별도 fetch.
// 베이스 URL 은 apiBase() 단일 소스를 사용한다(런타임 오버라이드 반영).

/** 학습 데이터 JSONL 파일 다운로드. */
export async function downloadTrainingJsonl(limit = 1000): Promise<void> {
  const token = getAccessToken();
  const res = await fetch(`${apiBase()}/admin/interview/training/export?limit=${limit}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    throw new Error(`학습 데이터 내보내기에 실패했습니다 (${res.status})`);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "interview-training.jsonl";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
