import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import {
  Activity,
  BarChart3,
  Bell,
  Bot,
  Briefcase,
  Building2,
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
  type LucideIcon,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { ThemeToggle } from "@/app/components/layout/ThemeToggle";
import { NotificationBell } from "@/features/notification/components/NotificationBell";
import { useAdminPendingCounts, topSeverity, sumCounts, type PendingSeverity } from "@/admin/hooks/useAdminPendingCounts";
import { useAdminPermissions } from "@/admin/hooks/useAdminPermissions";
import {
  isAdminRole,
  permissionGroupsFromCodes,
  type PermissionGroupCode,
} from "@/admin/auth/adminAccess";
import "./admin-shell.css";

interface NavItem {
  key: string;
  label: string;
  icon: LucideIcon;
  href: string;
  permissionGroups?: PermissionGroupCode[];
  superOnly?: boolean;
  ct?: string;
}

interface NavGroup {
  key: string;
  label: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    key: "overview",
    label: "운영 현황",
    items: [
      { key: "dashboard", label: "대시보드", icon: LayoutDashboard, href: "/admin" },
      { key: "ops-dashboard", label: "운영 대시보드", icon: Activity, href: "/admin/dashboard" },
      { key: "admin-home", label: "운영 작업함", icon: ListChecks, href: "/admin/home" },
    ],
  },
  {
    key: "member",
    label: "회원/보안 운영",
    items: [
      { key: "members", label: "회원 관리", icon: Users, href: "/admin/users", permissionGroups: ["MEMBER_ADMIN"] },
      { key: "blocked-users", label: "차단 관리", icon: ShieldAlert, href: "/admin/users/blocked", permissionGroups: ["MEMBER_ADMIN", "AUDIT_ADMIN"] },
      { key: "security-ops", label: "보안 운영 센터", icon: ShieldCheck, href: "/admin/security", permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
      { key: "login-risk-policy", label: "로그인 잠금 정책", icon: ShieldAlert, href: "/admin/security/login-risk", permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
      { key: "profiles", label: "프로필 관리", icon: FileUser, href: "/admin/profiles", permissionGroups: ["MEMBER_ADMIN"] },
      { key: "consents", label: "동의 관리", icon: ClipboardCheck, href: "/admin/consents", permissionGroups: ["MEMBER_ADMIN"] },
      // W1: 기업 계정 전환 신청 승인/반려
      { key: "company-applications", label: "기업 신청 관리", icon: Building2, href: "/admin/company/applications", permissionGroups: ["MEMBER_ADMIN"] },
      { key: "security-audit", label: "로그인/보안 감사", icon: LockKeyhole, href: "/admin/audit/security", permissionGroups: ["AUDIT_ADMIN"] },
      { key: "email-audit", label: "이메일 감사", icon: MailCheck, href: "/admin/audit/email", permissionGroups: ["AUDIT_ADMIN"] },
      { key: "email-audit-log", label: "이메일 발급 감사(전역)", icon: MailWarning, href: "/admin/audit/email-log", permissionGroups: ["AUDIT_ADMIN"] },
      { key: "activity-logs", label: "활동 로그(전수)", icon: Activity, href: "/admin/audit/activity", permissionGroups: ["AUDIT_ADMIN"] },
    ],
  },
  {
    key: "ai",
    label: "AI/분석 운영",
    items: [
      { key: "analytics", label: "분석 통계", icon: TrendingUp, href: "/admin/analytics", permissionGroups: ["AI_ADMIN"] },
      { key: "fit-analysis", label: "적합도 분석", icon: Target, href: "/admin/fit-analysis", permissionGroups: ["AI_ADMIN"] },
      { key: "application-cases", label: "지원 건 관리", icon: Briefcase, href: "/admin/application-cases", permissionGroups: ["MEMBER_ADMIN", "AI_ADMIN"] },
      { key: "job-analysis", label: "공고 분석 조회", icon: BarChart3, href: "/admin/job-analysis", permissionGroups: ["AI_ADMIN"] },
      { key: "company-analysis", label: "기업 분석 조회", icon: Building2, href: "/admin/company-analysis", permissionGroups: ["AI_ADMIN"] },
      { key: "interviews", label: "면접 모니터링", icon: MessageSquare, href: "/admin/interviews", permissionGroups: ["AI_ADMIN"] },
      { key: "interview-reports", label: "면접 리포트", icon: ClipboardList, href: "/admin/interview/reports", permissionGroups: ["AI_ADMIN"] },
      { key: "corrections", label: "첨삭 모니터링", icon: FilePenLine, href: "/admin/corrections", permissionGroups: ["AI_ADMIN"] },
      { key: "ai-usage", label: "AI 사용량", icon: Gauge, href: "/admin/ai-usage", permissionGroups: ["AI_ADMIN"] },
      { key: "ai-settings", label: "AI 운영 설정", icon: SlidersHorizontal, href: "/admin/ai-settings", permissionGroups: ["AI_ADMIN"] },
      { key: "prompts", label: "프롬프트 템플릿", icon: FileText, href: "/admin/prompts", permissionGroups: ["AI_ADMIN"] },
    ],
  },
  {
    key: "billing",
    label: "결제/구독",
    items: [
      { key: "payments", label: "결제 관리", icon: CreditCard, href: "/admin/payments", permissionGroups: ["BILLING_ADMIN"] },
      { key: "credits", label: "크레딧 관리", icon: Coins, href: "/admin/credits", permissionGroups: ["BILLING_ADMIN"] },
      { key: "rewards", label: "리워드/레벨", icon: Gift, href: "/admin/rewards", permissionGroups: ["BILLING_ADMIN"] },
      { key: "plans", label: "요금제 관리", icon: Package, href: "/admin/plans", permissionGroups: ["BILLING_ADMIN"] },
    ],
  },
  {
    key: "content",
    label: "콘텐츠/고객지원",
    items: [
      { key: "reports", label: "신고·검수 관리", icon: MessageSquareWarning, href: "/admin/community", permissionGroups: ["CONTENT_ADMIN"] },
      // W1: 기업 채용공고 등록/수정 검토 큐
      { key: "job-posting-review", label: "공고 검토", icon: Briefcase, href: "/admin/company/job-postings", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "ads", label: "광고 관리", icon: Megaphone, href: "/admin/ads", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "notices", label: "공지사항", icon: Megaphone, href: "/admin/notices", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "faq", label: "FAQ 관리", icon: CircleHelp, href: "/admin/faq", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "ai-support", label: "AI 상담 운영", icon: Bot, href: "/admin/ai-support", permissionGroups: ["CONTENT_ADMIN", "AI_ADMIN"] },
      { key: "chatbot-governance", label: "챗봇 거버넌스", icon: Bot, href: "/admin/chatbot-governance", permissionGroups: ["AI_ADMIN"] },
      { key: "inquiries", label: "문의 관리", icon: Mail, href: "/admin/inquiries", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "terms", label: "약관 관리", icon: Scale, href: "/admin/terms", permissionGroups: ["CONTENT_ADMIN", "POLICY_ADMIN"] },
      { key: "notifications", label: "알림 모니터링", icon: Bell, href: "/admin/notifications", permissionGroups: ["CONTENT_ADMIN"] },
      // 본인 수신 설정이므로 세부 권한 없이 모든 관리자에게 노출한다.
      { key: "admin-notification-settings", label: "관리자 알림 설정", icon: MailCheck, href: "/admin/notification-settings" },
    ],
  },
  {
    key: "policy",
    label: "정책/감사",
    items: [
      { key: "super-admin", label: "권한 관리", icon: ShieldCheck, href: "/admin/super", permissionGroups: ["POLICY_ADMIN"], superOnly: true },
      { key: "policies", label: "운영 정책 관리", icon: SlidersHorizontal, href: "/admin/policies", permissionGroups: ["POLICY_ADMIN"], superOnly: true },
      { key: "runtime-settings", label: "런타임 설정", icon: SlidersHorizontal, href: "/admin/runtime-settings", permissionGroups: ["POLICY_ADMIN"], superOnly: true },
      { key: "staff-grades", label: "직원 등급/급여", icon: BadgeDollarSign, href: "/admin/staff-grades", permissionGroups: ["POLICY_ADMIN"], superOnly: true },
      { key: "action-logs", label: "관리자 활동 로그", icon: History, href: "/admin/action-logs", permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
      { key: "logs", label: "시스템 로그", icon: ScrollText, href: "/admin/logs", permissionGroups: ["AUDIT_ADMIN"] },
    ],
  },
];

/** 미처리 큐 숫자 뱃지. count 0이면 렌더하지 않는다. 색은 severity(RED/YELLOW). */
NAV_GROUPS.find((group) => group.key === "member")?.items.splice(3, 0, {
  key: "mfa-policy",
  label: "MFA 정책",
  icon: ShieldCheck,
  href: "/admin/security/mfa-policy",
  permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"],
});

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
  const pending = useAdminPendingCounts(canUseAdmin);
  const mePermissions = useAdminPermissions(user?.id ?? null, role, role === "ADMIN");
  const grantedGroups = mePermissions.status === "ready" && mePermissions.data
    ? permissionGroupsFromCodes(mePermissions.data.permissions)
    : new Set<PermissionGroupCode>();
  const handleLogout = async () => {
    await logout();
    navigate("/");
  };
  const canUseCurrentPage = canUseAdmin && canAccessNavKey(active, role, grantedGroups);
  const visibleGroups = canUseAdmin
    ? NAV_GROUPS.map((group) => ({
        ...group,
        items: group.items.filter((item) => canAccessNavItem(item, role, grantedGroups)),
      })).filter((group) => group.items.length > 0)
    : [];
  const displayName = user?.name?.trim() || user?.email?.trim() || "관리자";
  const displayInitial = displayName.charAt(0).toUpperCase();
  const displayRole = role === "SUPER_ADMIN" ? "최고 관리자" : "관리자";

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
      <aside className="adm__side">
        <div className="adm__logo">
          <span className="adm__logo-icon">CT</span>
          <span className="adm__logo-text">
            CareerTuner <b>Admin</b>
          </span>
        </div>

        <nav className="adm__nav">
          {visibleGroups.map((group) => (
            <div className="adm__nav-group" key={group.key}>
              <div className="adm__nav-group-label">{group.label}</div>
              {group.items.map((item) => {
                const isActive = item.key === active || location.pathname === item.href;
                return (
                  <Link
                    key={item.key}
                    to={item.href}
                    className={`adm__nav-item ${isActive ? "is-active" : ""}`}
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
          ))}
        </nav>
      </aside>

      <div className="adm__main">
        <header className="adm__topbar">
          <div className="adm__search">
            <Search />
            <input type="text" placeholder="검색..." />
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

function canAccessNavKey(key: string, role: string | null | undefined, grantedGroups: Set<PermissionGroupCode>): boolean {
  const item = NAV_GROUPS.flatMap((group) => group.items).find((candidate) => candidate.key === key);
  return item ? canAccessNavItem(item, role, grantedGroups) : false;
}

function canAccessNavItem(item: NavItem, role: string | null | undefined, grantedGroups: Set<PermissionGroupCode>): boolean {
  if (role === "SUPER_ADMIN") return true;
  if (role !== "ADMIN") return false;
  if (item.superOnly) return false;
  if (!item.permissionGroups || item.permissionGroups.length === 0) return true;
  return item.permissionGroups.some((group) => grantedGroups.has(group));
}
