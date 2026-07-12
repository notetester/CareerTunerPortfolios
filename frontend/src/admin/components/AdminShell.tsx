import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import {
  Activity,
  BarChart3,
  Bell,
  Bot,
  Briefcase,
  Building2,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  ClipboardCheck,
  ClipboardList,
  Coins,
  Gift,
  BadgeDollarSign,
  CreditCard,
  ExternalLink,
  FileText,
  FilePenLine,
  FileUser,
  Gauge,
  History,
  LayoutDashboard,
  ListChecks,
  LockKeyhole,
  LogOut,
  Mail,
  MailCheck,
  MailWarning,
  Menu,
  Megaphone,
  MessageSquare,
  MessageSquareWarning,
  Package,
  Scale,
  ScrollText,
  Search,
  ShieldAlert,
  ShieldCheck,
  SlidersHorizontal,
  Target,
  TrendingUp,
  Users,
  X,
  type LucideIcon,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { ThemeToggle } from "@/app/components/layout/ThemeToggle";
import { NotificationBell } from "@/features/notification/components/NotificationBell";
import { useAdminPendingCounts, topSeverity, sumCounts, type PendingSeverity } from "@/admin/hooks/useAdminPendingCounts";
import { useAdminPermissions } from "@/admin/hooks/useAdminPermissions";
import {
  ADMIN_ROUTE_POLICIES,
  adminRoutePolicy,
  canAccessAdminRoute,
  isAdminRole,
  type AdminRoutePath,
} from "@/admin/auth/adminAccess";
import "./admin-shell.css";

interface NavItem {
  key: string;
  label: string;
  icon: LucideIcon;
  href: `/${AdminRoutePath}`;
  ct?: string;
}

interface NavGroup {
  key: string;
  label: string;
  icon: LucideIcon;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    key: "overview",
    label: "운영 현황",
    icon: LayoutDashboard,
    items: [
      { key: "ops-dashboard", label: "운영 대시보드", icon: Activity, href: "/admin/dashboard" },
      { key: "admin-home", label: "운영 작업함", icon: ListChecks, href: "/admin/home" },
      { key: "admin-notification-settings", label: "내 알림 설정", icon: MailCheck, href: "/admin/notification-settings" },
    ],
  },
  {
    key: "member",
    label: "회원·기업",
    icon: Users,
    items: [
      { key: "members", label: "회원 관리", icon: Users, href: "/admin/users" },
      { key: "blocked-users", label: "차단 관리", icon: ShieldAlert, href: "/admin/users/blocked" },
      { key: "profiles", label: "프로필 관리", icon: FileUser, href: "/admin/profiles" },
      { key: "consents", label: "동의 관리", icon: ClipboardCheck, href: "/admin/consents" },
      { key: "company-applications", label: "기업 신청 관리", icon: Building2, href: "/admin/company/applications" },
    ],
  },
  {
    key: "security",
    label: "보안·감사",
    icon: ShieldCheck,
    items: [
      { key: "security-ops", label: "보안 운영 센터", icon: ShieldCheck, href: "/admin/security" },
      { key: "login-risk-policy", label: "로그인 잠금 정책", icon: ShieldAlert, href: "/admin/security/login-risk" },
      { key: "mfa-policy", label: "MFA 정책", icon: ShieldCheck, href: "/admin/security/mfa-policy" },
      { key: "security-audit", label: "로그인/보안 감사", icon: LockKeyhole, href: "/admin/audit/security" },
      { key: "email-audit-log", label: "이메일 발급 감사", icon: MailWarning, href: "/admin/audit/email" },
      { key: "activity-logs", label: "회원 활동 로그", icon: Activity, href: "/admin/audit/activity" },
      { key: "action-logs", label: "관리자 활동 로그", icon: History, href: "/admin/action-logs" },
      { key: "logs", label: "시스템 로그", icon: ScrollText, href: "/admin/logs" },
    ],
  },
  {
    key: "ai",
    label: "AI·분석",
    icon: Bot,
    items: [
      { key: "analytics", label: "분석 통계", icon: TrendingUp, href: "/admin/analytics" },
      { key: "fit-analysis", label: "적합도 분석", icon: Target, href: "/admin/fit-analysis" },
      { key: "application-cases", label: "지원 건 관리", icon: Briefcase, href: "/admin/application-cases" },
      { key: "job-analysis", label: "공고 분석 조회", icon: BarChart3, href: "/admin/job-analysis" },
      { key: "company-analysis", label: "기업 분석 조회", icon: Building2, href: "/admin/company-analysis" },
      { key: "interviews", label: "면접 모니터링", icon: MessageSquare, href: "/admin/interviews" },
      { key: "interview-reports", label: "면접 리포트", icon: ClipboardList, href: "/admin/interview/reports" },
      { key: "corrections", label: "첨삭 모니터링", icon: FilePenLine, href: "/admin/corrections" },
      { key: "ai-usage", label: "AI 사용량", icon: Gauge, href: "/admin/ai-usage" },
      { key: "ai-settings", label: "공고 업로드 설정", icon: SlidersHorizontal, href: "/admin/ai-settings" },
      // 프롬프트 템플릿(B 공고/기업)은 읽기 전용 확인 화면이라 메뉴에서 숨김. 라우트(/admin/prompts)는 유지.
      // { key: "prompts", label: "프롬프트 템플릿", icon: FileText, href: "/admin/prompts" },
    ],
  },
  {
    key: "billing",
    label: "결제·구독",
    icon: CreditCard,
    items: [
      { key: "payments", label: "결제 관리", icon: CreditCard, href: "/admin/payments" },
      { key: "credits", label: "크레딧 관리", icon: Coins, href: "/admin/credits" },
      { key: "rewards", label: "리워드·레벨", icon: Gift, href: "/admin/rewards" },
      { key: "plans", label: "요금제 관리", icon: Package, href: "/admin/plans" },
    ],
  },
  {
    key: "content",
    label: "콘텐츠·지원",
    icon: MessageSquare,
    items: [
      { key: "reports", label: "신고·검수 관리", icon: MessageSquareWarning, href: "/admin/community" },
      { key: "job-posting-review", label: "공고 검토", icon: Briefcase, href: "/admin/company/job-postings" },
      { key: "ads", label: "광고 관리", icon: Megaphone, href: "/admin/ads" },
      { key: "notices", label: "공지사항", icon: Megaphone, href: "/admin/notices" },
      { key: "faq", label: "FAQ 관리", icon: CircleHelp, href: "/admin/faq" },
      { key: "ai-support", label: "AI 상담 운영", icon: Bot, href: "/admin/ai-support" },
      { key: "chatbot-governance", label: "챗봇 거버넌스", icon: Bot, href: "/admin/chatbot-governance" },
      { key: "inquiries", label: "문의 관리", icon: Mail, href: "/admin/inquiries" },
      { key: "terms", label: "약관 관리", icon: Scale, href: "/admin/terms" },
      { key: "notifications", label: "알림 모니터링", icon: Bell, href: "/admin/notifications" },
    ],
  },
  {
    key: "policy",
    label: "정책·권한",
    icon: SlidersHorizontal,
    items: [
      { key: "super-admin", label: "권한 관리", icon: ShieldCheck, href: "/admin/super" },
      { key: "policies", label: "운영 정책 관리", icon: SlidersHorizontal, href: "/admin/policies" },
      { key: "runtime-settings", label: "런타임 설정", icon: SlidersHorizontal, href: "/admin/runtime-settings" },
      { key: "staff-grades", label: "직원 등급·급여", icon: BadgeDollarSign, href: "/admin/staff-grades" },
    ],
  },
];

function PendingBadge({ count, severity }: { count: number; severity: PendingSeverity }) {
  if (count <= 0) return null;
  return <span className={`adm__nav-ct sev-${severity.toLowerCase()}`}>{count}</span>;
}

interface AdminShellProps {
  active: string;
  breadcrumb: string;
  title: string;
  icon: LucideIcon;
  desc: string;
  actions?: ReactNode;
  children: ReactNode;
}

export default function AdminShell({
  active,
  breadcrumb,
  title,
  icon: Icon,
  desc,
  actions,
  children,
}: AdminShellProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const role = user?.role ?? null;
  const canUseAdmin = isAdminRole(role);
  const mePermissions = useAdminPermissions(user?.id ?? null, role, role === "ADMIN");
  const grantedPermissions = mePermissions.status === "ready" && mePermissions.data
    ? new Set(mePermissions.data.permissions)
    : new Set<string>();
  const canReadPending = role === "SUPER_ADMIN" || grantedPermissions.has("CONTENT_READ");
  const pending = useAdminPendingCounts(canUseAdmin && canReadPending);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [navQuery, setNavQuery] = useState("");
  const navigationRef = useRef<HTMLElement | null>(null);
  const mobileNavSearchRef = useRef<HTMLInputElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const [expandedGroup, setExpandedGroup] = useState<string | null>(() => (
    NAV_GROUPS.find((group) => group.items.some((item) => item.key === active))?.key ?? "overview"
  ));
  const handleLogout = async () => {
    await logout();
    navigate("/");
  };
  const currentRoutePath = location.pathname.replace(/^\/+|\/+$/g, "") as AdminRoutePath;
  const currentRoutePolicy = Object.prototype.hasOwnProperty.call(ADMIN_ROUTE_POLICIES, currentRoutePath)
    ? adminRoutePolicy(currentRoutePath)
    : null;
  const canUseCurrentPage = canUseAdmin && (
    currentRoutePolicy
      ? canAccessAdminRoute(role, currentRoutePolicy, grantedPermissions)
      : canAccessNavKey(active, role, grantedPermissions)
  );
  const accessibleGroups = canUseAdmin
    ? NAV_GROUPS.map((group) => ({
        ...group,
        items: group.items.filter((item) => canAccessNavItem(item, role, grantedPermissions)),
      })).filter((group) => group.items.length > 0)
    : [];
  const normalizedNavQuery = navQuery.trim().toLocaleLowerCase("ko-KR");
  const visibleGroups = normalizedNavQuery
    ? accessibleGroups.map((group) => {
        const groupMatches = group.label.toLocaleLowerCase("ko-KR").includes(normalizedNavQuery);
        return {
          ...group,
          items: groupMatches
            ? group.items
            : group.items.filter((item) => item.label.toLocaleLowerCase("ko-KR").includes(normalizedNavQuery)),
        };
      }).filter((group) => group.items.length > 0)
    : accessibleGroups;
  const displayName = user?.name?.trim() || user?.email?.trim() || "관리자";
  const displayInitial = displayName.charAt(0).toUpperCase();
  const displayRole = role === "SUPER_ADMIN" ? "최고 관리자" : "관리자";

  const handleNavSearch = (value: string) => {
    setNavQuery(value);
  };

  const openMobileNav = () => {
    if (typeof document !== "undefined" && document.activeElement instanceof HTMLElement) {
      previousFocusRef.current = document.activeElement;
    }
    setMobileNavOpen(true);
  };

  useEffect(() => {
    const activeGroup = visibleGroups.find((group) => (
      group.items.some((item) => item.key === active || location.pathname === item.href)
    ));
    if (activeGroup) setExpandedGroup(activeGroup.key);
    setMobileNavOpen(false);
    // permission 응답으로 visibleGroups 객체가 매 렌더마다 새로 만들어지므로 경로/active만 구독한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, location.pathname]);

  useEffect(() => {
    if (!mobileNavOpen) {
      const previousFocus = previousFocusRef.current;
      if (previousFocus?.isConnected) previousFocus.focus();
      previousFocusRef.current = null;
      return;
    }

    const focusFrame = window.requestAnimationFrame(() => mobileNavSearchRef.current?.focus());
    const handleDrawerKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setMobileNavOpen(false);
        return;
      }
      if (event.key !== "Tab") return;

      const navigation = navigationRef.current;
      if (!navigation) return;
      const focusable = Array.from(navigation.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      )).filter((element) => element.getClientRects().length > 0);
      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const activeElement = document.activeElement;
      if (event.shiftKey && (activeElement === first || !navigation.contains(activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (activeElement === last || !navigation.contains(activeElement))) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", handleDrawerKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown", handleDrawerKeyDown);
    };
  }, [mobileNavOpen]);

  // 라우트 boundary가 최종 진입을 막지만, 셸이 다른 곳에서 직접 사용돼도 관리자 chrome/API를 열지 않는다.
  if (!canUseAdmin) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 dark:bg-slate-950">
        <section role="alert" className="max-w-lg rounded-xl border border-slate-200 bg-card p-6 text-center text-sm text-muted-foreground dark:border-slate-800">
          관리자 권한이 필요합니다.
        </section>
      </main>
    );
  }

  return (
    <div className="adm">
      {mobileNavOpen && (
        <button
          type="button"
          className="adm__side-backdrop"
          aria-label="관리자 메뉴 닫기"
          onClick={() => setMobileNavOpen(false)}
        />
      )}
      <aside
        ref={navigationRef}
        id="admin-navigation"
        className={`adm__side ${mobileNavOpen ? "is-mobile-open" : ""}`}
        aria-label="관리자 메뉴"
        role={mobileNavOpen ? "dialog" : undefined}
        aria-modal={mobileNavOpen ? true : undefined}
      >
        <div className="adm__logo">
          <span className="adm__logo-icon">CT</span>
          <span className="adm__logo-text">
            CareerTuner <b>Admin</b>
          </span>
          <button
            type="button"
            className="adm__side-close"
            aria-label="관리자 메뉴 닫기"
            onClick={() => setMobileNavOpen(false)}
          >
            <X />
          </button>
        </div>

        <div className="adm__side-search">
          <Search />
          <input
            ref={mobileNavSearchRef}
            type="search"
            value={navQuery}
            aria-label="모바일 관리자 메뉴 검색"
            placeholder="메뉴 검색..."
            onChange={(event) => handleNavSearch(event.target.value)}
          />
        </div>

        <nav className="adm__nav">
          {visibleGroups.map((group) => {
            const isExpanded = normalizedNavQuery.length > 0 || expandedGroup === group.key;
            const panelId = `admin-nav-group-${group.key}`;
            return (
              <div className="adm__nav-group" key={group.key}>
                <button
                  type="button"
                  className="adm__nav-group-toggle"
                  aria-expanded={isExpanded}
                  aria-controls={panelId}
                  disabled={normalizedNavQuery.length > 0}
                  title={normalizedNavQuery ? "검색 중에는 일치하는 메뉴 그룹이 모두 펼쳐집니다." : undefined}
                  onClick={() => setExpandedGroup((current) => current === group.key ? null : group.key)}
                >
                  <group.icon />
                  <span>{group.label}</span>
                  <span className="adm__nav-group-count" aria-label={`${group.items.length}개 메뉴`}>{group.items.length}</span>
                  <ChevronDown className={isExpanded ? "is-expanded" : ""} aria-hidden="true" />
                </button>
                <div id={panelId} className="adm__nav-group-items" hidden={!isExpanded}>
                  {group.items.map((item) => {
                    const isActive = item.key === active || location.pathname === item.href;
                    return (
                      <Link
                        key={item.key}
                        to={item.href}
                        className={`adm__nav-item ${isActive ? "is-active" : ""}`}
                        aria-current={isActive ? "page" : undefined}
                        onClick={() => setMobileNavOpen(false)}
                      >
                        <item.icon />
                        <span className="adm__nav-label">{item.label}</span>
                        {item.key === "reports" ? (
                          <PendingBadge
                            count={sumCounts(pending?.reports, pending?.hiddenPosts, pending?.hiddenComments)}
                            severity={topSeverity(pending?.reports, pending?.hiddenPosts, pending?.hiddenComments)}
                          />
                        ) : item.key === "inquiries" ? (
                          <PendingBadge
                            count={pending?.tickets?.count ?? 0}
                            severity={pending?.tickets?.severity ?? "NONE"}
                          />
                        ) : (
                          item.ct && <span className="adm__nav-ct">{item.ct}</span>
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            );
          })}
          {normalizedNavQuery && visibleGroups.length === 0 && (
            <div className="adm__nav-empty">일치하는 관리자 메뉴가 없습니다.</div>
          )}
        </nav>
      </aside>

      <div className="adm__main">
        <header className="adm__topbar">
          <div className="adm__topbar-left">
            <button
              type="button"
              className="adm__mobile-menu"
              aria-label="관리자 메뉴 열기"
              aria-controls="admin-navigation"
              aria-expanded={mobileNavOpen}
              onClick={openMobileNav}
            >
              <Menu />
            </button>
            <div className="adm__search">
              <Search />
              <input
                type="search"
                value={navQuery}
                aria-label="관리자 메뉴 검색"
                placeholder="메뉴 검색..."
                onChange={(event) => handleNavSearch(event.target.value)}
              />
            </div>
          </div>
          <div className="adm__topbar-right">
            <Link to="/dashboard" className="adm__topbar-link">
              <ExternalLink className="size-4" /> 사이트로 돌아가기
            </Link>
            <ThemeToggle />
            <NotificationBell />
            <div className="adm__profile">
              <div className="adm__avatar">{displayInitial}</div>
              <span className="adm__profile-name">{displayName} · {displayRole}</span>
            </div>
            <button type="button" onClick={handleLogout} className="adm__topbar-link">
              <LogOut className="size-4" /> 로그아웃
            </button>
          </div>
        </header>

        <div className="adm__content">
          <div className="adm__page-head">
            <div className="adm__bread">
              관리자 <ChevronRight /> {breadcrumb}
            </div>
            <div className="adm__title-row">
              <div>
                <h1 className="adm__title">
                  <Icon /> {title}
                </h1>
                <p className="adm__desc">{desc}</p>
              </div>
              {canUseCurrentPage && actions && <div className="adm__actions">{actions}</div>}
            </div>
          </div>

          {canUseCurrentPage ? (
            children
          ) : (
            <section className="rounded-lg border border-slate-200 bg-card p-6 text-sm text-slate-600 shadow-sm">
              {canUseAdmin
                ? "현재 계정에 이 관리자 메뉴를 볼 수 있는 세부 권한이 없습니다."
                : "관리자 권한이 필요합니다. 관리자 계정으로 로그인한 뒤 다시 접근해 주세요."}
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

function canAccessNavKey(key: string, role: string | null | undefined, grantedPermissions: ReadonlySet<string>): boolean {
  const item = NAV_GROUPS.flatMap((group) => group.items).find((candidate) => candidate.key === key);
  return item ? canAccessNavItem(item, role, grantedPermissions) : false;
}

function canAccessNavItem(item: NavItem, role: string | null | undefined, grantedPermissions: ReadonlySet<string>): boolean {
  const routePath = item.href.slice(1) as AdminRoutePath;
  return canAccessAdminRoute(role, adminRoutePolicy(routePath), grantedPermissions);
}
