// 광고 노출 타입 (W7). 백엔드 AdResponse / AdClickResponse 와 1:1.

/** 광고 배치 위치. 백엔드 chk_advertisement_placement 와 일치. */
export type AdPlacement = "HOME_BANNER" | "FEED_INLINE" | "SIDEBAR" | "INTERSTITIAL";

/** 타겟 플랫폼. 노출 요청 시 현재 플랫폼(WEB/APP/DESKTOP)을 전달한다. */
export type AdPlatform = "WEB" | "APP" | "DESKTOP";

/** 공개 노출 광고. imageUrl 이 없으면 텍스트 배너로 표현한다. */
export interface Ad {
  id: number;
  title: string;
  imageUrl: string | null;
  linkUrl: string | null;
  placement: AdPlacement;
  targetPlatform: string;
}

/** 클릭 처리 응답 — 이동할 URL. */
export interface AdClick {
  id: number;
  linkUrl: string | null;
}
