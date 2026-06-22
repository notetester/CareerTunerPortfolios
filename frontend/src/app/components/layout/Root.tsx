import { Outlet, useLocation } from "react-router";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { ChatbotBubble } from "../../../features/support/components/ChatbotWidget";
import { ApplicationExtractionMonitor } from "@/features/applications/components/ApplicationExtractionMonitor";
import { MobileBottomNav } from "./MobileBottomNav";
import { OfflineBanner } from "./OfflineBanner";

export function Root() {
  const location = useLocation();
  const isApplicationDetail = /^\/applications\/(?:new|\d+)/.test(location.pathname);
  const isAdmin = location.pathname.startsWith("/admin");
  // 관리자/로그인 화면은 자체 내비라 하단 탭을 숨긴다.
  const showMobileNav = !isAdmin && location.pathname !== "/login";

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
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
