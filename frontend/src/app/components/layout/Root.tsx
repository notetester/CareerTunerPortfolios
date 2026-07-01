import { Outlet, ScrollRestoration, useLocation } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { LandingPage } from "@/features/landing/pages/LandingPage";
import { isNativeApp } from "@/platform/capacitor";
import { OnboardingFlow, isOnboarded } from "@/features/onboarding/OnboardingFlow";
import { AppHome } from "@/features/onboarding/AppHome";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { ChatbotBubble } from "../../../features/support/components/ChatbotWidget";
import { ApplicationExtractionMonitor } from "@/features/applications/components/ApplicationExtractionMonitor";
import { MobileBottomNav } from "./MobileBottomNav";
import { OfflineBanner } from "./OfflineBanner";

export function Root() {
  const location = useLocation();
  const { isAuthenticated, loading } = useAuth();
  // 앱(네이티브) 진입: 온보딩 미완료면 온보딩 퍼널, 완료면 검색창 메인(AppHome).
  // 웹에선 ?ob(온보딩)·?home(검색창 메인) 쿼리로 미리볼 수 있다(디자인 확인용).
  const search = typeof window !== "undefined" ? window.location.search : "";
  const forceOnboarding = new URLSearchParams(search).has("ob");
  const forceHome = new URLSearchParams(search).has("home");
  if (location.pathname === "/" && !loading) {
    if (forceOnboarding) return <OnboardingFlow />;
    if (forceHome) return <AppHome />;
    if (isNativeApp()) return isOnboarded() ? <AppHome /> : <OnboardingFlow />;
    // 비로그인 홈(/)은 랜딩 페이지를 헤더/푸터 없이 전체화면으로 렌더한다.
    if (!isAuthenticated) return <LandingPage />;
  }
  const isApplicationDetail = /^\/applications\/(?:new|\d+)/.test(location.pathname);
  const isAdmin = location.pathname.startsWith("/admin");
  // 관리자/로그인 화면은 자체 내비라 하단 탭을 숨긴다.
  const showMobileNav = !isAdmin && location.pathname !== "/login";

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      {/* 라우트 이동 시 스크롤을 맨 위로 복원. 푸터(하단) 링크로 이동해도 새 페이지가 바닥에 걸리지 않게 한다. */}
      <ScrollRestoration />
      <ApplicationExtractionMonitor />
      <OfflineBanner />
      {!isAdmin && <Header />}
      {/* 하단 탭에 콘텐츠가 가리지 않도록 모바일에서 하단 패딩(탭 높이 + safe-area) 확보 */}
      <main className={`flex-1 ${showMobileNav ? "pb-[calc(56px+env(safe-area-inset-bottom))] xl:pb-0" : ""}`}>
        <Outlet />
      </main>
      {!isApplicationDetail && !isAdmin && <Footer />}
      {/* 하단 탭이 있는 모바일에서는 플로팅 챗봇이 탭과 겹치므로 데스크톱에서만 띄운다(모바일은 더보기>고객센터). */}
      {!isAdmin && (showMobileNav ? <div className="hidden xl:contents">{<ChatbotBubble />}</div> : <ChatbotBubble />)}
      {showMobileNav && <MobileBottomNav />}
    </div>
  );
}
