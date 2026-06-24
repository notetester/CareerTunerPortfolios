import type { ReactNode } from "react";
import { Link, useLocation } from "react-router";
import {
  LayoutDashboard, Briefcase, BarChart3, Building2, Gauge, FileText,
  Users, CreditCard, MessageSquareWarning, Megaphone, CircleHelp,
  Mail, Search, Bell, ChevronRight,
  Target, TrendingUp, ListChecks, Activity,
  Scale, FileUser, ClipboardCheck, MessageSquare, Package, ScrollText,
  ShieldCheck, SlidersHorizontal, History,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import "./admin-shell.css";

interface NavItem {
  key: string;
  label: string;
  icon: LucideIcon;
  ct?: string;
  href: string;
}

const NAV: NavItem[] = [
  { key: "dashboard", label: "대시보드", icon: LayoutDashboard, href: "/admin" },
  // C 운영 화면. 공통 AdminShell(팀장 영역) NAV에 추가 — 그동안 /admin 랜딩 바로가기로만 도달 가능했던
  // 분석 통계·적합도·운영 대시보드·작업 큐를 사이드바에서 직접 열 수 있게 한다(발견성 보강).
  { key: "analytics", label: "분석 통계", icon: TrendingUp, href: "/admin/analytics" },
  { key: "fit-analysis", label: "적합도 분석", icon: Target, href: "/admin/fit-analysis" },
  { key: "ops-dashboard", label: "운영 대시보드", icon: Activity, href: "/admin/dashboard" },
  { key: "admin-home", label: "운영 작업 큐", icon: ListChecks, href: "/admin/home" },
  { key: "application-cases", label: "지원 건 관리", icon: Briefcase, href: "/admin/application-cases" },
  { key: "job-analysis", label: "공고 분석 조회", icon: BarChart3, href: "/admin/job-analysis" },
  { key: "company-analysis", label: "기업 분석 조회", icon: Building2, href: "/admin/company-analysis" },
  { key: "interviews", label: "면접 모니터링", icon: MessageSquare, href: "/admin/interviews" },
  { key: "ai-usage", label: "B AI 사용량", icon: Gauge, href: "/admin/ai-usage" },
  { key: "prompts", label: "프롬프트 템플릿", icon: FileText, href: "/admin/prompts" },
  { key: "members", label: "회원 관리", icon: Users, ct: "1,248", href: "/admin/users" },
  { key: "profiles", label: "프로필 관리", icon: FileUser, href: "/admin/profiles" },
  { key: "consents", label: "동의 관리", icon: ClipboardCheck, href: "/admin/consents" },
  { key: "super-admin", label: "super 권한 관리", icon: ShieldCheck, href: "/admin/super" },
  { key: "policies", label: "운영 정책 관리", icon: SlidersHorizontal, href: "/admin/policies" },
  { key: "action-logs", label: "관리자 액션 로그", icon: History, href: "/admin/action-logs" },
  { key: "payments", label: "결제 관리", icon: CreditCard, href: "/admin/payments" },
  { key: "plans", label: "요금제 관리", icon: Package, href: "/admin/plans" },
  { key: "reports", label: "신고·검열 관리", icon: MessageSquareWarning, href: "/admin/community" },
  { key: "notices", label: "공지사항", icon: Megaphone, ct: "7", href: "/admin/notices" },
  { key: "faq", label: "FAQ 관리", icon: CircleHelp, href: "/admin/faq" },
  { key: "inquiries", label: "문의 관리", icon: Mail, ct: "9", href: "/admin/inquiries" },
  { key: "terms", label: "약관 관리", icon: Scale, href: "/admin/terms" },
  { key: "notifications", label: "알림 모니터링", icon: Bell, href: "/admin/notifications" },
  { key: "logs", label: "시스템 로그", icon: ScrollText, href: "/admin/logs" },
];

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
  active, breadcrumb, title, icon: Icon, desc, actions, children,
}: AdminShellProps) {
  const location = useLocation();

  return (
    <div className="adm">
      {/* Sidebar */}
      <aside className="adm__side">
        <div className="adm__logo">
          <span className="adm__logo-icon">CT</span>
          <span className="adm__logo-text">CareerTuner <b>Admin</b></span>
        </div>

        <nav className="adm__nav">
          {NAV.map((item) => {
            const isActive = item.key === active || location.pathname === item.href;
            return (
              <Link
                key={item.key}
                to={item.href}
                className={`adm__nav-item ${isActive ? "is-active" : ""}`}
              >
                <item.icon />
                <span className="adm__nav-label">{item.label}</span>
                {item.ct && <span className="adm__nav-ct">{item.ct}</span>}
              </Link>
            );
          })}
        </nav>
      </aside>

      {/* Main */}
      <div className="adm__main">
        {/* Top bar */}
        <header className="adm__topbar">
          <div className="adm__search">
            <Search />
            <input type="text" placeholder="검색..." />
          </div>
          <div className="adm__topbar-right">
            <button className="adm__topbar-bell">
              <Bell />
              <span className="adm__topbar-dot" />
            </button>
            <div className="adm__profile">
              <div className="adm__avatar">A</div>
              <span className="adm__profile-name">관리자</span>
            </div>
          </div>
        </header>

        {/* Page header */}
        <div className="adm__content">
          <div className="adm__page-head">
            <div className="adm__bread">
              관리자 <ChevronRight /> {breadcrumb}
            </div>
            <div className="adm__title-row">
              <div>
                <h1 className="adm__title"><Icon /> {title}</h1>
                <p className="adm__desc">{desc}</p>
              </div>
              {actions && <div className="adm__actions">{actions}</div>}
            </div>
          </div>

          {children}
        </div>
      </div>
    </div>
  );
}
