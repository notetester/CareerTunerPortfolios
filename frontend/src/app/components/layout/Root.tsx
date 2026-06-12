import { Outlet, useLocation } from "react-router";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { ApplicationExtractionMonitor } from "@/features/applications/components/ApplicationExtractionMonitor";

export function Root() {
  const location = useLocation();
  const isApplicationDetail = /^\/applications\/(?:new|\d+)/.test(location.pathname);

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <ApplicationExtractionMonitor />
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      {!isApplicationDetail && <Footer />}
    </div>
  );
}
