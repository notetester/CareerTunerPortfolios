// 관리자 광고 타입 (W7). 백엔드 AdminAdRequest / AdminAdResponse 와 1:1.

export type AdPlacement = "HOME_BANNER" | "FEED_INLINE" | "SIDEBAR" | "INTERSTITIAL";
export type AdTargetPlatform = "WEB" | "APP" | "DESKTOP" | "ALL";

export interface AdminAd {
  id: number;
  title: string;
  imageFileId: number | null;
  imageUrl: string | null;
  linkUrl: string | null;
  placement: AdPlacement;
  targetPlatform: AdTargetPlatform;
  startAt: string | null;
  endAt: string | null;
  active: boolean;
  priority: number;
  weight: number;
  impressionCount: number;
  clickCount: number;
  ctr: number;
  createdAt: string | null;
  updatedAt: string | null;
}

/** 등록/수정 폼 페이로드. datetime-local 값은 ISO 로 변환해 전송한다. */
export interface AdminAdPayload {
  title: string;
  imageFileId: number | null;
  linkUrl: string | null;
  placement: AdPlacement;
  targetPlatform: AdTargetPlatform;
  startAt: string | null;
  endAt: string | null;
  active: boolean;
  priority: number;
  weight: number;
}

export const PLACEMENT_LABELS: Record<AdPlacement, string> = {
  HOME_BANNER: "홈 배너",
  FEED_INLINE: "피드 인라인",
  SIDEBAR: "사이드바",
  INTERSTITIAL: "전면(인터스티셜)",
};

export const PLATFORM_LABELS: Record<AdTargetPlatform, string> = {
  WEB: "웹",
  APP: "모바일 앱",
  DESKTOP: "데스크톱",
  ALL: "전체",
};
