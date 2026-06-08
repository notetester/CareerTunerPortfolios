import type { ReactNode } from "react";
import { Link, useLocation } from "react-router";
import {
  LayoutDashboard, Users, CreditCard, MessageSquareWarning,
  Megaphone, CircleHelp, Mail, Search, Bell, ChevronRight,
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
  { key: "members", label: "회원 관리", icon: Users, ct: "1,248", href: "/admin/users" },
  { key: "payments", label: "결제 관리", icon: CreditCard, ct: "34", href: "/admin/payments" },
  { key: "reports", label: "게시판/신고", icon: MessageSquareWarning, ct: "12", href: "/admin/community" },
  { key: "notices", label: "공지사항", icon: Megaphone, ct: "7", href: "/admin/notices" },
  { key: "faq", label: "FAQ 관리", icon: CircleHelp, href: "/admin/faq" },
  { key: "inquiries", label: "문의 관리", icon: Mail, ct: "9", href: "/admin/inquiries" },
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
            const isActive = item.key === active;
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
