import { Outlet, ScrollRestoration, useLocation } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { LandingPage } from "@/features/landing/pages/LandingPage";
import { isNativeApp } from "@/platform/capacitor";
import { OnboardingFlow, isOnboarded } from "@/features/onboarding/OnboardingFlow";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { ChatbotBubble } from "../../../features/support/components/ChatbotWidget";
import { ApplicationExtractionMonitor } from "@/features/applications/components/ApplicationExtractionMonitor";
import { MobileBottomNav } from "./MobileBottomNav";
import { OfflineBanner } from "./OfflineBanner";

export function Root() {
  const location = useLocation();
  const { isAuthenticated, loading } = useAuth();
  // 앱(네이티브) 첫 실행이면 온보딩 퍼널(로그인→구독→알림→검색창 메인)을 띄운다.
  // 웹에서는 ?ob 쿼리로 강제로 미리볼 수 있다(디자인 확인용). 완료 후엔 localStorage 로 다시 안 뜬다.
  const forceOnboarding =
    typeof window !== "undefined" && new URLSearchParams(window.location.search).has("ob");
  if (location.pathname === "/" && !loading && ((isNativeApp() && !isOnboarded()) || forceOnboarding)) {
    return <OnboardingFlow />;
  }
  // 비로그인 홈(/)은 랜딩 페이지를 헤더/푸터 없이 전체화면으로 렌더한다.
  if (location.pathname === "/" && !loading && !isAuthenticated) {
    return <LandingPage />;
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
      <Header />
      {/* 하단 탭에 콘텐츠가 가리지 않도록 모바일에서 하단 패딩(탭 높이 + safe-area) 확보 */}
      <main className={`flex-1 ${showMobileNav ? "pb-[calc(56px+env(safe-area-inset-bottom))] xl:pb-0" : ""}`}>
        <Outlet />
      </main>
      {!isApplicationDetail && <Footer />}
      {/* 하단 탭이 있는 모바일에서는 플로팅 챗봇이 탭과 겹치므로 데스크톱에서만 띄운다(모바일은 더보기>고객센터). */}
      {!isAdmin && (showMobileNav ? <div className="hidden xl:contents">{<ChatbotBubble />}</div> : <ChatbotBubble />)}
      {showMobileNav && <MobileBottomNav />}
    </div>
  );
}
