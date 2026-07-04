import { api } from "@/app/lib/api";

// 기기 핸드오프: 데스크탑 ↔ 폰 사이에서 면접 세션을 이어한다.
// 백엔드 계약: POST /api/interview/sessions/{id}/dispatch
//  → 내 다른 기기들에 INTERVIEW_DISPATCH 알림(link=/interview?session={id})을 발송한다.
//  폰에서 알림을 탭하면 InterviewPage 의 ?session 딥링크가 세션을 복원해 이어진다.

/** 이 세션을 폰(다른 기기)으로 보내기 — 알림 + 딥링크 발송. */
export function dispatchToPhone(sessionId: number): Promise<void> {
  return api<void>(`/interview/sessions/${sessionId}/dispatch`, { method: "POST" });
}
