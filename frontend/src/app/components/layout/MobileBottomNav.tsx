import { useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { Home, MessageSquare, Mic, Bell, Grid3x3 } from "lucide-react";
import { haptic } from "@/platform/haptics";
import { homePath } from "@/platform/capacitor";
import { MobileMoreSheet } from "./MobileMoreSheet";

/**
 * 모바일 하단 탭 — Claude 앱 문법 (홈 / 세션 / 면접 / 알림 / 더보기).
 * 대시보드·지원 건·분석은 더보기 시트로 이동. 데스크톱(xl↑)에서는 상단 내비가 대신한다.
 */
const TABS = [
  { label: "홈", href: "__home__", icon: Home },
  { label: "세션", href: "/m/sessions", icon: MessageSquare },
  { label: "면접", href: "/interview", icon: Mic },
  { label: "알림", href: "/notifications", icon: Bell },
] as const;

export function MobileBottomNav() {
  const location = useLocation();
  const navigate = useNavigate();
  const [moreOpen, setMoreOpen] = useState(false);

  const resolve = (href: string) => (href === "__home__" ? homePath() : href);
  const isActive = (href: string) => {
    const target = resolve(href).split("?")[0];
    if (target === "/") return location.pathname === "/";
    return location.pathname === target || location.pathname.startsWith(target + "/");
  };

  const go = (href: string) => {
    haptic("light");
    navigate(resolve(href));
  };

  return (
    <>
      <nav
        className="fixed inset-x-0 bottom-0 z-50 border-t border-slate-200 bg-card/95 backdrop-blur dark:border-white/[0.06] dark:bg-[#050506]/95 xl:hidden"
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
                  active ? "text-[#5E6AD2] dark:text-[#7d88de]" : "text-slate-500 dark:text-[#8A8F98]"
                }`}
              >
                <tab.icon
                  className={`size-5 transition-transform ${active ? "-translate-y-px" : ""}`}
                />
                {tab.label}
              </button>
            );
          })}
          <button
            onClick={() => {
              haptic("light");
              setMoreOpen(true);
            }}
            className={`flex flex-col items-center gap-0.5 py-2 text-[10px] font-medium ${
              moreOpen ? "text-[#5E6AD2] dark:text-[#7d88de]" : "text-slate-500 dark:text-[#8A8F98]"
            }`}
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
