import { api } from "@/app/lib/api";

import type { HomeSummary } from "../types/homeSummary";

/**
 * 로그인 홈 요약 조회(C 담당). 현재 홈 화면은 대시보드 요약을 재사용하지만,
 * 홈 전용 표현이 필요해지면 이 엔드포인트로 전환한다.
 */
export function getHomeSummary() {
  return api<HomeSummary>("/home/summary");
}
