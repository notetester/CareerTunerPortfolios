import { useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { LayoutDashboard, Briefcase, MessageSquare, TrendingUp, Grid3x3 } from "lucide-react";
import { haptic } from "@/platform/haptics";
import { MobileMoreSheet } from "./MobileMoreSheet";

const TABS = [
  { label: "대시보드", href: "/dashboard", icon: LayoutDashboard },
  { label: "지원 건", href: "/applications", icon: Briefcase },
  { label: "면접", href: "/interview", icon: MessageSquare },
  { label: "분석", href: "/analysis", icon: TrendingUp },
] as const;

/** 모바일 하단 탭 내비게이션(핵심 4개 + 더보기). lg 이상에서는 숨김. */
export function MobileBottomNav() {
  const location = useLocation();
  const navigate = useNavigate();
  const [moreOpen, setMoreOpen] = useState(false);

  const isActive = (href: string) => location.pathname === href || location.pathname.startsWith(href + "/");

  const go = (href: string) => {
    haptic("light");
    navigate(href);
  };

  return (
    <>
      <nav
        className="fixed inset-x-0 bottom-0 z-50 border-t border-slate-200 bg-white/95 backdrop-blur lg:hidden"
        style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
      >
        <div className="mx-auto grid max-w-lg grid-cols-5">
          {TABS.map((tab) => {
            const active = isActive(tab.href);
            return (
              <button
                key={tab.href}
                onClick={() => go(tab.href)}
                className={`flex flex-col items-center gap-0.5 py-2 text-[10px] font-medium transition-colors ${
                  active ? "text-blue-600" : "text-slate-500"
                }`}
              >
                <tab.icon className={`size-5 ${active ? "scale-110" : ""} transition-transform`} />
                {tab.label}
              </button>
            );
          })}
          <button
            onClick={() => { haptic("light"); setMoreOpen(true); }}
            className={`flex flex-col items-center gap-0.5 py-2 text-[10px] font-medium ${moreOpen ? "text-blue-600" : "text-slate-500"}`}
          >
            <Grid3x3 className="size-5" />
            더보기
          </button>
        </div>
      </nav>

      <MobileMoreSheet open={moreOpen} onClose={() => setMoreOpen(false)} />
    </>
  );
}
