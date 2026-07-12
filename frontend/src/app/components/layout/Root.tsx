import { lazy, Suspense, type ReactNode } from "react";
import { Outlet, ScrollRestoration, useLocation } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { useConsent } from "@/app/auth/ConsentContext";
import { RequiredConsentBoundary } from "@/app/auth/ConsentGate";
import { LandingPage } from "@/features/landing/pages/LandingPage";
import { isNativeApp } from "@/platform/capacitor";
import { OnboardingFlow, isOnboarded } from "@/features/onboarding/OnboardingFlow";
import { AppHome } from "@/features/onboarding/AppHome";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { ChatbotBubble } from "../../../features/support/components/ChatbotWidget";
import { ApplicationExtractionMonitor } from "@/features/applications/components/ApplicationExtractionMonitor";
import { MobileBottomNav } from "./MobileBottomNav";
import { NotificationRuntime } from "@/features/notification/components/NotificationRuntime";
import { OfflineBanner } from "./OfflineBanner";
import { OutageFallbackBanner } from "./OutageFallbackBanner";
import { RefundPolicyToastGate } from "@/features/billing/components/RefundPolicyToastGate";
import { MfaApprovalWatcher } from "@/app/components/security/MfaApprovalWatcher";
import { PlannerFloatingOverlay } from "@/features/planner/components/PlannerFloatingOverlay";
import type { AdSlot as AdSlotComponent } from "@/features/ads/components/AdSlot";

// 광고차단기가 /ads/ 경로의 모듈 요청을 차단하면(dev 의 ERR_BLOCKED_BY_CLIENT) 정적 임포트는 앱 셸 전체를
// 빈 화면으로 만든다. 지연 로드 + 실패 시 무광고 렌더로 격리한다(차단 = 광고 미노출이 의미상으로도 맞다).
const AdSlot = lazy(() =>
  import("@/features/ads/components/AdSlot")
    .then((m) => ({ default: m.AdSlot }))
    .catch(() => ({ default: (() => null) as unknown as typeof AdSlotComponent })),
);

function ServiceStatusBanners() {
  return (
    <>
      <OfflineBanner />
      <OutageFallbackBanner />
    </>
  );
}

function StandalonePage({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <div className="sticky top-0 z-[60]">
        <ServiceStatusBanners />
      </div>
      {children}
    </div>
  );
}

export function Root() {
  const location = useLocation();
  const { isAuthenticated, loading } = useAuth();
  const { status: consentStatus } = useConsent();
  // 앱(네이티브) 진입: 온보딩 미완료면 온보딩 퍼널, 완료면 검색창 메인(AppHome).
  // 웹에선 ?ob(온보딩)·?home(검색창 메인) 쿼리로 미리볼 수 있다(디자인 확인용).
  const search = typeof window !== "undefined" ? window.location.search : "";
  const forceOnboarding = new URLSearchParams(search).has("ob");
  const forceHome = new URLSearchParams(search).has("home");
  const nativeApp = isNativeApp();
  let renderAppHome = false;
  if (location.pathname === "/" && !loading) {
    if (forceOnboarding) return <StandalonePage><OnboardingFlow /></StandalonePage>;
    if (forceHome) renderAppHome = true;
    else if (nativeApp && !isOnboarded()) return <StandalonePage><OnboardingFlow /></StandalonePage>;
    else if (nativeApp) renderAppHome = true;
    // 비로그인 홈(/)은 랜딩 페이지를 헤더/푸터 없이 전체화면으로 렌더한다.
    else if (!isAuthenticated) return <StandalonePage><LandingPage /></StandalonePage>;
  }
  const isApplicationDetail = /^\/applications\/(?:new|\d+)/.test(location.pathname);
  const isAdmin = location.pathname.startsWith("/admin");
  const isMessenger = location.pathname.startsWith("/messenger");
  const isMobileRoute = location.pathname.startsWith("/m/");
  // 관리자/로그인 화면은 자체 내비라 하단 탭을 숨긴다.
  const showMobileNav = !isAdmin && location.pathname !== "/login";
  const mobileNavPadding = showMobileNav
    ? nativeApp
      ? "pb-[calc(56px+env(safe-area-inset-bottom))]"
      : "pb-[calc(56px+env(safe-area-inset-bottom))] xl:pb-0"
    : "";

  return (
    <div className={`${renderAppHome ? "h-[100dvh] overflow-hidden" : "min-h-screen"} flex flex-col bg-background text-foreground`}>
      {/* 라우트 이동 시 스크롤을 맨 위로 복원. 푸터(하단) 링크로 이동해도 새 페이지가 바닥에 걸리지 않게 한다.
          ⚠ 첫 로드(딥링크·새로고침)의 location.key 는 URL 과 무관하게 전부 "default" 라, 기본 키 그대로면
          다른 페이지에서 저장된 스크롤이 새 URL 에 복원돼 짧은 페이지가 빈 화면처럼 보인다(카톡 공유·폰 핸드오프
          딥링크에서 재현). 첫 로드만 URL 별 키로 분리해 오염을 막고, 앱 내 이동은 기본 동작(push=톱, back=복원) 유지. */}
      <ScrollRestoration
        getKey={(location) =>
          location.key === "default" ? `${location.pathname}${location.search}` : location.key
        }
      />
      <NotificationRuntime />
      <ApplicationExtractionMonitor />
      <MfaApprovalWatcher />
      <PlannerFloatingOverlay enabled={isAuthenticated && !isAdmin && !isMobileRoute && !renderAppHome && consentStatus?.aiDataAgreed === true} />
      {!isMobileRoute && (
        <div className={isAdmin ? "relative z-[60]" : "sticky top-0 z-[60]"}>
          <ServiceStatusBanners />
          {!isAdmin && !renderAppHome && <Header />}
        </div>
      )}
      <RefundPolicyToastGate enabled={isAuthenticated && !isAdmin && !isMobileRoute} />
      {!isAdmin && !isMobileRoute && !renderAppHome && (
        <Suspense fallback={null}>
          <AdSlot placement="HOME_BANNER" />
        </Suspense>
      )}
      {/* 하단 탭에 콘텐츠가 가리지 않도록 모바일에서 하단 패딩(탭 높이 + safe-area) 확보 */}
      <main className={`flex min-h-0 flex-1 flex-col ${mobileNavPadding}`}>
        <RequiredConsentBoundary>
          {renderAppHome ? <AppHome /> : <Outlet />}
        </RequiredConsentBoundary>
      </main>
      {!isApplicationDetail && !isAdmin && !isMessenger && !isMobileRoute && !renderAppHome && <Footer />}
      {/* 하단 탭이 있는 모바일에서는 플로팅 챗봇이 탭과 겹치므로 데스크톱에서만 띄운다(모바일은 더보기>고객센터). */}
      {!isAdmin && !isMessenger && !isMobileRoute && !renderAppHome && (showMobileNav ? <div className="hidden xl:contents">{<ChatbotBubble />}</div> : <ChatbotBubble />)}
      {showMobileNav && <MobileBottomNav alwaysVisible={nativeApp} />}
    </div>
  );
}
