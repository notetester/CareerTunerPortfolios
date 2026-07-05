import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/app/auth/AuthContext";
import { fetchAds, recordAdClick, recordAdImpression } from "../api/adsApi";
import type { Ad, AdPlacement, AdPlatform } from "../types/ads";

/** 현재 실행 플랫폼 추정. 네이티브 앱 래퍼는 전역 플래그를 세팅할 수 있어 우선 확인한다. */
function detectPlatform(): AdPlatform {
  const runtime = (globalThis as { __CT_PLATFORM__?: string }).__CT_PLATFORM__;
  if (runtime === "APP" || runtime === "DESKTOP") return runtime;
  return "WEB";
}

interface AdSlotProps {
  placement: AdPlacement;
  /** 미지정 시 런타임에서 추정(WEB 기본). */
  platform?: AdPlatform;
  /** 여러 개 노출이 필요하면 지정(기본 1). */
  limit?: number;
  className?: string;
}

/**
 * 자립형 광고 슬롯.
 *
 * <p>동작:
 * <ul>
 *   <li>유료플랜(plan !== FREE) 사용자에게는 아예 렌더하지 않는다(백엔드도 빈 응답 — 이중 방어).</li>
 *   <li>마운트 시 배치·플랫폼 매치 광고를 가져온다.</li>
 *   <li>IntersectionObserver 로 50% 가시 시점에 노출 집계 1회 발사.</li>
 *   <li>클릭 시 클릭 집계 후 linkUrl 로 이동(외부는 새 탭).</li>
 * </ul>
 * 삽입 지점(HomePage/CommunityHomePage 등)은 공통 레이아웃이라 이 컴포넌트만 배치하면 된다.
 */
export function AdSlot({ placement, platform, limit = 1, className }: AdSlotProps) {
  const { user } = useAuth();
  const plan = user?.plan ?? "FREE";
  const isPaid = plan.toUpperCase() !== "FREE";

  const [ads, setAds] = useState<Ad[]>([]);
  // 노출 집계를 광고당 1회만 발사하기 위한 가드.
  const firedImpressions = useRef<Set<number>>(new Set());

  useEffect(() => {
    if (isPaid) return;
    let cancelled = false;
    fetchAds(placement, platform ?? detectPlatform(), limit)
      .then((result) => {
        if (!cancelled) setAds(result);
      })
      .catch(() => {
        if (!cancelled) setAds([]);
      });
    return () => {
      cancelled = true;
    };
  }, [placement, platform, limit, isPaid]);

  const handleClick = useCallback((ad: Ad) => {
    if (!ad.linkUrl) return;
    // 클릭 집계 후 이동(집계 실패해도 이동은 진행).
    recordAdClick(ad.id)
      .then((result) => openLink(result.linkUrl ?? ad.linkUrl))
      .catch(() => openLink(ad.linkUrl));
  }, []);

  if (isPaid || ads.length === 0) {
    return null;
  }

  return (
    <div className={className} data-ad-placement={placement}>
      {ads.map((ad) => (
        <AdItem
          key={ad.id}
          ad={ad}
          onVisible={() => {
            if (firedImpressions.current.has(ad.id)) return;
            firedImpressions.current.add(ad.id);
            void recordAdImpression(ad.id);
          }}
          onClick={() => handleClick(ad)}
        />
      ))}
    </div>
  );
}

function openLink(url: string | null) {
  if (!url) return;
  window.open(url, "_blank", "noopener,noreferrer");
}

interface AdItemProps {
  ad: Ad;
  onVisible: () => void;
  onClick: () => void;
}

function AdItem({ ad, onVisible, onClick }: AdItemProps) {
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const node = ref.current;
    if (!node || typeof IntersectionObserver === "undefined") {
      // 옵저버 미지원 환경은 즉시 노출로 간주.
      onVisible();
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && entry.intersectionRatio >= 0.5) {
            onVisible();
            observer.disconnect();
            break;
          }
        }
      },
      { threshold: 0.5 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [onVisible]);

  const clickable = !!ad.linkUrl;

  return (
    <div
      ref={ref}
      role={clickable ? "link" : undefined}
      tabIndex={clickable ? 0 : undefined}
      onClick={clickable ? onClick : undefined}
      onKeyDown={
        clickable
          ? (event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onClick();
              }
            }
          : undefined
      }
      className={[
        "relative overflow-hidden rounded-lg border border-slate-200 bg-white",
        clickable ? "cursor-pointer transition hover:border-slate-300 hover:shadow-sm" : "",
      ].join(" ")}
      aria-label={clickable ? `광고: ${ad.title}` : undefined}
    >
      <span className="absolute right-2 top-2 z-10 rounded bg-slate-900/70 px-1.5 py-0.5 text-[10px] font-medium text-white">
        AD
      </span>
      {ad.imageUrl ? (
        <img
          src={ad.imageUrl}
          alt={ad.title}
          className="block h-auto w-full max-w-full object-cover"
          loading="lazy"
        />
      ) : (
        <div className="flex min-h-[80px] items-center justify-center px-4 py-6 text-center text-sm font-medium text-slate-700">
          {ad.title}
        </div>
      )}
    </div>
  );
}
