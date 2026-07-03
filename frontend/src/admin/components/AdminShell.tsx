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
  CreditCard,
  ExternalLink,
  FileText,
  FileUser,
  Gauge,
  History,
  LayoutDashboard,
  ListChecks,
  LockKeyhole,
  LogOut,
  Mail,
  MailCheck,
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
import "./admin-shell.css";

type PermissionGroupCode =
  | "MEMBER_ADMIN"
  | "AI_ADMIN"
  | "BILLING_ADMIN"
  | "CONTENT_ADMIN"
  | "AUDIT_ADMIN"
  | "POLICY_ADMIN";

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

const DEFAULT_GROUPS_BY_ROLE: Record<string, PermissionGroupCode[]> = {
  USER: [],
  ADMIN: ["MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN"],
  SUPER_ADMIN: ["MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN", "POLICY_ADMIN"],
};

const PERMISSION_CODE_TO_GROUPS: Record<string, PermissionGroupCode[]> = {
  MEMBER_ADMIN: ["MEMBER_ADMIN"],
  USER_READ: ["MEMBER_ADMIN"],
  USER_STATUS_WRITE: ["MEMBER_ADMIN"],
  BLOCK_MANAGE: ["MEMBER_ADMIN"],
  PROFILE_READ: ["MEMBER_ADMIN"],
  CONSENT_READ: ["MEMBER_ADMIN"],
  AI_ADMIN: ["AI_ADMIN"],
  AI_USAGE_READ: ["AI_ADMIN"],
  AI_OPERATION_MANAGE: ["AI_ADMIN"],
  ANALYSIS_READ: ["AI_ADMIN"],
  INTERVIEW_READ: ["AI_ADMIN"],
  BILLING_ADMIN: ["BILLING_ADMIN"],
  BILLING_READ: ["BILLING_ADMIN"],
  BILLING_WRITE: ["BILLING_ADMIN"],
  CONTENT_ADMIN: ["CONTENT_ADMIN"],
  CONTENT_MANAGE: ["CONTENT_ADMIN"],
  AUDIT_ADMIN: ["AUDIT_ADMIN"],
  SECURITY_LOG_READ: ["AUDIT_ADMIN"],
  EMAIL_AUDIT_READ: ["AUDIT_ADMIN"],
  ADMIN_AUDIT_READ: ["AUDIT_ADMIN"],
  POLICY_ADMIN: ["POLICY_ADMIN"],
  POLICY_MANAGE: ["POLICY_ADMIN"],
  ADMIN_PERMISSION_MANAGE: ["POLICY_ADMIN"],
};

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
      { key: "profiles", label: "프로필 관리", icon: FileUser, href: "/admin/profiles", permissionGroups: ["MEMBER_ADMIN"] },
      { key: "consents", label: "동의 관리", icon: ClipboardCheck, href: "/admin/consents", permissionGroups: ["MEMBER_ADMIN"] },
      { key: "security-audit", label: "로그인/보안 감사", icon: LockKeyhole, href: "/admin/audit/security", permissionGroups: ["AUDIT_ADMIN"] },
      { key: "email-audit", label: "이메일 감사", icon: MailCheck, href: "/admin/audit/email", permissionGroups: ["AUDIT_ADMIN"] },
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
      { key: "plans", label: "요금제 관리", icon: Package, href: "/admin/plans", permissionGroups: ["BILLING_ADMIN"] },
    ],
  },
  {
    key: "content",
    label: "콘텐츠/고객지원",
    items: [
      { key: "reports", label: "신고·검수 관리", icon: MessageSquareWarning, href: "/admin/community", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "notices", label: "공지사항", icon: Megaphone, href: "/admin/notices", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "faq", label: "FAQ 관리", icon: CircleHelp, href: "/admin/faq", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "ai-support", label: "AI 상담 운영", icon: Bot, href: "/admin/ai-support", permissionGroups: ["CONTENT_ADMIN", "AI_ADMIN"] },
      { key: "inquiries", label: "문의 관리", icon: Mail, href: "/admin/inquiries", permissionGroups: ["CONTENT_ADMIN"] },
      { key: "terms", label: "약관 관리", icon: Scale, href: "/admin/terms", permissionGroups: ["CONTENT_ADMIN", "POLICY_ADMIN"] },
      { key: "notifications", label: "알림 모니터링", icon: Bell, href: "/admin/notifications", permissionGroups: ["CONTENT_ADMIN"] },
    ],
  },
  {
    key: "policy",
    label: "정책/감사",
    items: [
      { key: "super-admin", label: "권한 관리", icon: ShieldCheck, href: "/admin/super", permissionGroups: ["POLICY_ADMIN"], superOnly: true },
      { key: "policies", label: "운영 정책 관리", icon: SlidersHorizontal, href: "/admin/policies", permissionGroups: ["POLICY_ADMIN"], superOnly: true },
      { key: "action-logs", label: "관리자 활동 로그", icon: History, href: "/admin/action-logs", permissionGroups: ["AUDIT_ADMIN", "POLICY_ADMIN"] },
      { key: "logs", label: "시스템 로그", icon: ScrollText, href: "/admin/logs", permissionGroups: ["AUDIT_ADMIN"] },
    ],
  },
];

/** 미처리 큐 숫자 뱃지. count 0이면 렌더하지 않는다. 색은 severity(RED/YELLOW). */
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
  const pending = useAdminPendingCounts();
  const { user, logout } = useAuth();
  const handleLogout = async () => {
    await logout();
    navigate("/");
  };
  const role = user?.role;
  const canUseAdmin = role === "ADMIN" || role === "SUPER_ADMIN";
  const grantedGroups = getGrantedGroups(user);
  const canUseCurrentPage = canUseAdmin && canAccessNavKey(active, role, grantedGroups);
  const visibleGroups = canUseAdmin
    ? NAV_GROUPS.map((group) => ({
        ...group,
        items: group.items.filter((item) => canAccessNavItem(item, role, grantedGroups)),
      })).filter((group) => group.items.length > 0)
    : [];

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
              <div className="adm__avatar">A</div>
              <span className="adm__profile-name">관리자</span>
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
              {actions && <div className="adm__actions">{actions}</div>}
            </div>
          </div>

          {canUseCurrentPage ? (
            children
          ) : (
            <section className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
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

function getGrantedGroups(user: unknown): Set<PermissionGroupCode> {
  if (!isRecord(user)) return new Set();
  const role = typeof user.role === "string" ? user.role : "USER";
  const explicitGroups = [
    ...readStringArray(user.permissionGroups),
    ...readStringArray(user.groupCodes),
    ...readStringArray(user.groups),
    ...readStringArray(user.permissions).flatMap((code) => PERMISSION_CODE_TO_GROUPS[code] ?? []),
  ].filter(isPermissionGroupCode);

  if (explicitGroups.length > 0) {
    return new Set(explicitGroups);
  }

  return new Set(DEFAULT_GROUPS_BY_ROLE[role] ?? []);
}

function canAccessNavKey(key: string, role: string | undefined, grantedGroups: Set<PermissionGroupCode>): boolean {
  const item = NAV_GROUPS.flatMap((group) => group.items).find((candidate) => candidate.key === key);
  return item ? canAccessNavItem(item, role, grantedGroups) : role === "ADMIN" || role === "SUPER_ADMIN";
}

function canAccessNavItem(item: NavItem, role: string | undefined, grantedGroups: Set<PermissionGroupCode>): boolean {
  if (role === "SUPER_ADMIN") return true;
  if (role !== "ADMIN") return false;
  if (item.superOnly) return false;
  if (!item.permissionGroups || item.permissionGroups.length === 0) return true;
  return item.permissionGroups.some((group) => grantedGroups.has(group));
}

function isPermissionGroupCode(value: string): value is PermissionGroupCode {
  return ["MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN", "POLICY_ADMIN"].includes(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object";
}

function readStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}
