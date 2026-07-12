import { useEffect } from "react";
import { useNavigate } from "react-router";
import {
  User, PenTool, Users, CreditCard, Settings, LifeBuoy, ShieldCheck,
  LogOut, X, Download, Share, Award, LayoutDashboard, Briefcase, TrendingUp, MessagesSquare,
  CalendarClock,
} from "lucide-react";
import { useAuth } from "../../auth/AuthContext";
import { haptic } from "@/platform/haptics";
import { canPromptInstall, needsManualInstall, promptInstall } from "@/platform/install";
import { registerNativeOverlayLifecycle } from "@/platform/nativeOverlayLifecycle";

interface MoreItem {
  label: string;
  href: string;
  icon: typeof User;
  adminOnly?: boolean;
}

// 하단 탭이 홈/세션/면접/알림으로 재편되며 대시보드·지원 건·분석은 여기로 이동 (Claude 앱 문법).
const MORE_ITEMS: MoreItem[] = [
  { label: "대시보드", href: "/dashboard", icon: LayoutDashboard },
  { label: "지원 건", href: "/applications", icon: Briefcase },
  { label: "분석", href: "/analysis", icon: TrendingUp },
  { label: "플래너", href: "/planner", icon: CalendarClock },
  { label: "메신저", href: "/messenger", icon: MessagesSquare },
  { label: "내 프로필", href: "/profile", icon: User },
  { label: "AI 첨삭", href: "/correction", icon: PenTool },
  { label: "커뮤니티", href: "/community", icon: Users },
  { label: "결제/구독", href: "/billing", icon: CreditCard },
  { label: "설정", href: "/settings", icon: Settings },
  { label: "고객센터", href: "/support", icon: LifeBuoy },
  { label: "관리자", href: "/admin", icon: ShieldCheck, adminOnly: true },
];

/** 모바일 하단탭 "더보기" 시트 — 보조 메뉴 + 계정 + 설치/로그아웃. */
export function MobileMoreSheet({
  open,
  onClose,
  alwaysVisible = false,
}: {
  open: boolean;
  onClose: () => void;
  alwaysVisible?: boolean;
}) {
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();
  const isAdmin = user?.role === "ADMIN" || user?.role === "SUPER_ADMIN";

  const go = (href: string) => {
    haptic("light");
    onClose();
    navigate(href);
  };

  const items = MORE_ITEMS.filter((i) => !i.adminOnly || isAdmin);

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [onClose, open]);

  useEffect(() => {
    if (!open) return undefined;
    return registerNativeOverlayLifecycle({ onBack: onClose, onSuspend: onClose });
  }, [onClose, open]);

  // 닫힌 시트는 포커스·스크린리더 트리에서도 완전히 제거한다.
  if (!open) return null;

  return (
    <>
      <div
        className={`fixed inset-0 z-[60] bg-black/40 ${alwaysVisible ? "" : "xl:hidden"}`}
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className={`fixed inset-x-0 bottom-0 z-[61] max-h-[calc(100dvh-env(safe-area-inset-top)-8px)] overflow-y-auto overscroll-contain rounded-t-2xl bg-card text-card-foreground shadow-2xl ${alwaysVisible ? "" : "xl:hidden"}`}
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 12px)" }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="mobile-more-sheet-title"
      >
        <div className="mx-auto mt-2 h-1.5 w-10 rounded-full bg-border" />
        <div className="flex items-center justify-between px-5 pb-1 pt-3">
          <h2 id="mobile-more-sheet-title" className="text-base font-bold text-foreground">더보기</h2>
          <button onClick={onClose} aria-label="닫기" className="rounded-lg p-1.5 text-muted-foreground hover:bg-accent hover:text-foreground">
            <X className="size-5" />
          </button>
        </div>

        {/* 계정 */}
        {isAuthenticated ? (
          <button
            onClick={() => go("/settings?tab=account")}
            className="mx-4 mb-2 flex w-[calc(100%-2rem)] items-center gap-3 rounded-xl bg-muted p-3 text-left"
          >
            <div className="flex size-11 items-center justify-center rounded-full bg-accent-soft text-base font-bold text-primary">
              {user?.name?.trim()?.charAt(0) ?? user?.email?.charAt(0).toUpperCase() ?? "U"}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-bold text-foreground">{user?.name ?? "사용자"}</div>
              <div className="truncate text-xs text-muted-foreground">{user?.email}</div>
            </div>
            <span className="flex items-center gap-1 rounded-full bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">
              <Award className="size-3.5" /> {user?.credit ?? 0}
            </span>
          </button>
        ) : (
          <div className="mx-4 mb-2">
            <button
              onClick={() => go("/login")}
              className="w-full rounded-xl bg-primary py-3 text-sm font-semibold text-white"
            >
              로그인 / 회원가입
            </button>
          </div>
        )}

        {/* 메뉴 그리드 */}
        <div className="grid grid-cols-4 gap-1 px-3 pb-2">
          {items.map((item) => (
            <button
              key={item.href}
              onClick={() => go(item.href)}
              className="flex flex-col items-center gap-1.5 rounded-xl p-3 text-muted-foreground active:bg-accent active:text-foreground"
            >
              <item.icon className="size-6" />
              <span className="text-[11px] font-medium">{item.label}</span>
            </button>
          ))}
        </div>

        {/* 설치 안내 */}
        {(canPromptInstall() || needsManualInstall()) && (
          <div className="mx-4 mb-2 rounded-xl border border-blue-100 bg-blue-50 p-3">
            {canPromptInstall() ? (
              <button
                onClick={() => { haptic("medium"); void promptInstall(); }}
                className="flex w-full items-center justify-center gap-2 text-sm font-semibold text-blue-700"
              >
                <Download className="size-4" /> 홈 화면에 앱 설치
              </button>
            ) : (
              <div className="flex items-center gap-2 text-xs text-blue-700">
                <Share className="size-4 shrink-0" />
                <span>공유 버튼 → "홈 화면에 추가"로 앱처럼 설치할 수 있어요.</span>
              </div>
            )}
          </div>
        )}

        {isAuthenticated && (
          <div className="px-4 pt-1">
            <button
              onClick={async () => { haptic("light"); await logout(); onClose(); navigate("/"); }}
              className="flex w-full items-center justify-center gap-2 rounded-xl border border-border py-3 text-sm font-semibold text-red-600 active:bg-red-50 dark:text-red-400 dark:active:bg-red-500/10"
            >
              <LogOut className="size-4" /> 로그아웃
            </button>
          </div>
        )}
      </div>
    </>
  );
}
