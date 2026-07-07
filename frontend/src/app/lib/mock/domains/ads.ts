// 데모/목: 광고 시스템 (/ads/**, /admin/ads/**).
// 백엔드 의미론 미러:
//   - 공개 노출(GET /ads): 활성·기간·플랫폼 매치 광고를 반환. 단, 로그인 사용자가 유료플랜(FREE 아님)이면 빈 배열.
//     * 데모 로그인 사용자(demoUser)는 plan="PRO"(유료)라 사용자 화면 <AdSlot> 은 노출되지 않는다(정상 동작).
//       무료 노출을 확인하려면 demoUser.plan 을 "FREE" 로 바꾸면 된다.
//   - 노출/클릭 집계: in-memory 카운터 +1.
//   - 관리자 CRUD(/admin/ads): 등록/수정/토글/삭제 — 세션 내 in-memory 상태 유지.
import { demoUser } from "../data";
import type { MockRoute } from "../registry";
import { iso } from "../registry";

interface MockAd {
  id: number;
  title: string;
  imageFileId: number | null;
  imageUrl: string | null;
  linkUrl: string | null;
  placement: string;
  targetPlatform: string;
  startAt: string | null;
  endAt: string | null;
  active: boolean;
  priority: number;
  weight: number;
  impressionCount: number;
  clickCount: number;
  createdAt: string;
  updatedAt: string;
}

let adSeq = 5003;
const ads: MockAd[] = [
  {
    id: 5001,
    title: "커리어튜너 PRO 30% 할인",
    imageFileId: null,
    imageUrl: null,
    linkUrl: "https://careertuner.example/pro",
    placement: "HOME_BANNER",
    targetPlatform: "ALL",
    startAt: null,
    endAt: null,
    active: true,
    priority: 10,
    weight: 3,
    impressionCount: 1820,
    clickCount: 96,
    createdAt: iso(12),
    updatedAt: iso(2),
  },
  {
    id: 5002,
    title: "개발자 채용 박람회 2026",
    imageFileId: null,
    imageUrl: null,
    linkUrl: "https://jobfair.example",
    placement: "FEED_INLINE",
    targetPlatform: "WEB",
    startAt: iso(5),
    endAt: iso(-20),
    active: true,
    priority: 5,
    weight: 1,
    impressionCount: 640,
    clickCount: 22,
    createdAt: iso(6),
    updatedAt: iso(1),
  },
  {
    id: 5000,
    title: "면접 코칭 웨비나(마감)",
    imageFileId: null,
    imageUrl: null,
    linkUrl: "https://webinar.example",
    placement: "SIDEBAR",
    targetPlatform: "ALL",
    startAt: iso(30),
    endAt: iso(10),
    active: false,
    priority: 0,
    weight: 1,
    impressionCount: 310,
    clickCount: 8,
    createdAt: iso(31),
    updatedAt: iso(9),
  },
];

function isPaid(plan: string): boolean {
  return plan.toUpperCase() !== "FREE";
}

/** 공개 응답 형태(AdResponse). */
interface PublicAd {
  id: number;
  title: string;
  imageUrl: string | null;
  linkUrl: string | null;
  placement: string;
  targetPlatform: string;
}

/** 활성·기간(now)·플랫폼(ALL 또는 요청) 매치. 유료플랜이면 빈 배열. */
function serveAds(placement: string, platform: string, limit: number): PublicAd[] {
  if (isPaid(demoUser.plan)) return [];
  const now = Date.now();
  const candidates = ads.filter((ad) => {
    if (!ad.active || ad.placement !== placement) return false;
    if (ad.startAt && new Date(ad.startAt).getTime() > now) return false;
    if (ad.endAt && new Date(ad.endAt).getTime() <= now) return false;
    return ad.targetPlatform === "ALL" || ad.targetPlatform === platform;
  });
  if (candidates.length === 0) return [];
  const topPriority = Math.max(...candidates.map((ad) => ad.priority));
  const pool = candidates.filter((ad) => ad.priority === topPriority);
  return pool.slice(0, Math.max(1, Math.min(limit, 5))).map(publicView);
}

function publicView(ad: MockAd): PublicAd {
  return {
    id: ad.id,
    title: ad.title,
    imageUrl: ad.imageUrl,
    linkUrl: ad.linkUrl,
    placement: ad.placement,
    targetPlatform: ad.targetPlatform,
  };
}

/** 관리자 응답 형태(AdminAdResponse) — CTR 파생 포함. */
function adminView(ad: MockAd) {
  const ctr = ad.impressionCount > 0
    ? Math.round((ad.clickCount * 10000) / ad.impressionCount) / 100
    : 0;
  return { ...ad, ctr };
}

export const adsRoutes: MockRoute[] = [
  // ── 공개 노출 ──
  {
    method: "GET",
    pattern: /^\/ads$/,
    handler: ({ query }) => {
      const placement = query.get("placement") ?? "HOME_BANNER";
      const platform = query.get("platform") ?? "WEB";
      const limit = Number(query.get("limit") ?? 1) || 1;
      return serveAds(placement, platform, limit);
    },
  },
  {
    method: "POST",
    pattern: /^\/ads\/(\d+)\/impression$/,
    handler: ({ params }) => {
      const ad = ads.find((a) => a.id === Number(params[0]));
      if (ad) ad.impressionCount += 1;
      return null;
    },
  },
  {
    method: "POST",
    pattern: /^\/ads\/(\d+)\/click$/,
    handler: ({ params }) => {
      const ad = ads.find((a) => a.id === Number(params[0]));
      if (ad) ad.clickCount += 1;
      return { id: Number(params[0]), linkUrl: ad?.linkUrl ?? null };
    },
  },

  // ── 관리자 CRUD ──
  {
    method: "GET",
    pattern: /^\/admin\/ads$/,
    handler: ({ query }) => {
      const placement = query.get("placement");
      const platform = query.get("platform");
      const activeOnly = query.get("activeOnly") === "true";
      return ads
        .filter((ad) => (placement ? ad.placement === placement : true))
        .filter((ad) => (platform ? ad.targetPlatform === platform : true))
        .filter((ad) => (activeOnly ? ad.active : true))
        .sort((a, b) => b.id - a.id)
        .map(adminView);
    },
  },
  {
    method: "GET",
    pattern: /^\/admin\/ads\/(\d+)$/,
    handler: ({ params }) => {
      const ad = ads.find((a) => a.id === Number(params[0]));
      return ad ? adminView(ad) : null;
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/ads$/,
    handler: ({ body }) => {
      const req = (body ?? {}) as Partial<MockAd>;
      const ad: MockAd = {
        id: ++adSeq,
        title: req.title ?? "새 광고",
        imageFileId: req.imageFileId ?? null,
        imageUrl: req.imageFileId ? `/api/ads/${adSeq}/image` : null,
        linkUrl: req.linkUrl ?? null,
        placement: req.placement ?? "HOME_BANNER",
        targetPlatform: req.targetPlatform ?? "ALL",
        startAt: req.startAt ?? null,
        endAt: req.endAt ?? null,
        active: req.active ?? true,
        priority: req.priority ?? 0,
        weight: req.weight && req.weight > 0 ? req.weight : 1,
        impressionCount: 0,
        clickCount: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      ads.unshift(ad);
      return adminView(ad);
    },
  },
  {
    method: "PUT",
    pattern: /^\/admin\/ads\/(\d+)$/,
    handler: ({ params, body }) => {
      const ad = ads.find((a) => a.id === Number(params[0]));
      if (!ad) return null;
      const req = (body ?? {}) as Partial<MockAd>;
      Object.assign(ad, {
        title: req.title ?? ad.title,
        imageFileId: req.imageFileId ?? null,
        imageUrl: req.imageFileId ? `/api/ads/${ad.id}/image` : null,
        linkUrl: req.linkUrl ?? null,
        placement: req.placement ?? ad.placement,
        targetPlatform: req.targetPlatform ?? ad.targetPlatform,
        startAt: req.startAt ?? null,
        endAt: req.endAt ?? null,
        active: req.active ?? ad.active,
        priority: req.priority ?? ad.priority,
        weight: req.weight && req.weight > 0 ? req.weight : 1,
        updatedAt: new Date().toISOString(),
      });
      return adminView(ad);
    },
  },
  {
    method: "POST",
    pattern: /^\/admin\/ads\/(\d+)\/toggle-active$/,
    handler: ({ params, query }) => {
      const ad = ads.find((a) => a.id === Number(params[0]));
      if (!ad) return null;
      ad.active = query.get("active") === "true";
      ad.updatedAt = new Date().toISOString();
      return adminView(ad);
    },
  },
  {
    method: "DELETE",
    pattern: /^\/admin\/ads\/(\d+)$/,
    handler: ({ params }) => {
      const index = ads.findIndex((a) => a.id === Number(params[0]));
      if (index >= 0) ads.splice(index, 1);
      return null;
    },
  },
];
