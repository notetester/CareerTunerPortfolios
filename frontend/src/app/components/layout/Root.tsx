import { Outlet, useLocation } from "react-router";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { ChatbotBubble } from "../../../features/support/components/ChatbotWidget";

export function Root() {
  const location = useLocation();
  const isApplicationDetail = /^\/applications\/(?:new|\d+)/.test(location.pathname);
  const isAdmin = location.pathname.startsWith("/admin");

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      {!isApplicationDetail && <Footer />}
      {!isAdmin && <ChatbotBubble />}
    </div>
  );
}
