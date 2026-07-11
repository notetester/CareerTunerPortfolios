import { useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { Home, MessageSquare, Mic, Bell, Grid3x3 } from "lucide-react";
import { haptic } from "@/platform/haptics";
import { homePath } from "@/platform/capacitor";
import { useNotificationStore } from "@/features/notification/hooks/useNotificationStore";
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

export function MobileBottomNav({ alwaysVisible = false }: { alwaysVisible?: boolean }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [moreOpen, setMoreOpen] = useState(false);
  // 헤더 NotificationBell 과 같은 스토어를 구독한다(폴링/갱신은 NotificationBell 이 담당).
  const unreadCount = useNotificationStore((state) => state.unreadCount);

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
        className={`fixed inset-x-0 bottom-0 z-50 bg-card/95 backdrop-blur before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:border-t before:border-border ${alwaysVisible ? "" : "xl:hidden"}`}
        style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
        aria-label="앱 주요 메뉴"
      >
        <div className="mx-auto grid max-w-lg grid-cols-5">
          {TABS.map((tab) => {
            const active = isActive(tab.href);
            const showUnread = tab.href === "/notifications" && unreadCount > 0;
            return (
              <button
                type="button"
                key={tab.href}
                onClick={() => go(tab.href)}
                className={`flex min-h-14 flex-col items-center justify-center gap-0.5 text-[10px] font-medium transition-colors ${
                  active ? "text-primary" : "text-muted-foreground"
                }`}
                aria-current={active ? "page" : undefined}
              >
                <span className="relative">
                  <tab.icon
                    className={`size-5 transition-transform ${active ? "-translate-y-px" : ""}`}
                  />
                  {showUnread && (
                    <span
                      className="absolute -right-2 -top-1 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-red-500 px-0.5 text-[9px] font-bold leading-none text-white"
                      aria-label={`미읽음 알림 ${unreadCount}개`}
                    >
                      {unreadCount > 9 ? "9+" : unreadCount}
                    </span>
                  )}
                </span>
                {tab.label}
              </button>
            );
          })}
          <button
            type="button"
            onClick={() => {
              haptic("light");
              setMoreOpen(true);
            }}
            className={`flex min-h-14 flex-col items-center justify-center gap-0.5 text-[10px] font-medium ${
              moreOpen ? "text-primary" : "text-muted-foreground"
            }`}
            aria-expanded={moreOpen}
            aria-haspopup="dialog"
          >
            <Grid3x3 className="size-5" />
            더보기
          </button>
        </div>
      </nav>

      <MobileMoreSheet
        open={moreOpen}
        onClose={() => setMoreOpen(false)}
        alwaysVisible={alwaysVisible}
      />
    </>
  );
}
