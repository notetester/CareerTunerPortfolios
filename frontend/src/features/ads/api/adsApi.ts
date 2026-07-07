import { api } from "@/app/lib/api";
import type { Ad, AdClick, AdPlacement, AdPlatform } from "../types/ads";

/**
 * 배치·플랫폼 매치 광고 조회. 유료플랜 사용자에게는 백엔드가 빈 배열을 반환한다.
 * (비로그인 공개는 SecurityConfig permitAll 배선이 필요 — 현재는 로그인 사용자 대상.)
 */
export function fetchAds(
  placement: AdPlacement,
  platform: AdPlatform = "WEB",
  limit = 1,
): Promise<Ad[]> {
  const query = new URLSearchParams({ placement, platform, limit: String(limit) });
  return api<Ad[]>(`/ads?${query.toString()}`, { method: "GET" });
}

/** 노출 집계 +1 (가시성 도달 시 1회 발사, best-effort). */
export function recordAdImpression(adId: number): Promise<void> {
  return api<void>(`/ads/${adId}/impression`, { method: "POST" }).catch(() => undefined);
}

/** 클릭 집계 +1 후 이동 URL 을 받는다. */
export function recordAdClick(adId: number): Promise<AdClick> {
  return api<AdClick>(`/ads/${adId}/click`, { method: "POST" });
}
