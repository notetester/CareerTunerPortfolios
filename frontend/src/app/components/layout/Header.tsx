import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { homePath } from "@/platform/capacitor";
import { useAuth } from "../../auth/AuthContext";
import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import { NotificationBell } from "@/features/notification/components/NotificationBell";
import { HeaderNav } from "./HeaderNav";
import { ThemeToggle } from "./ThemeToggle";
import "@/features/notification/styles/notification.css";
import {
  Sparkles,
  ChevronDown,
  Menu,
  X,
  User,
  LayoutDashboard,
  FileText,
  MessageSquare,
  MessagesSquare,
  TrendingUp,
  Users,
  CreditCard,
  Settings,
  Briefcase,
  PenTool,
  BookOpen,
  Target,
  BarChart2,
  Award,
  LogOut,
  ShieldCheck,
  CalendarClock,
} from "lucide-react";

const navItems = [
  {
    label: "대시보드",
    href: "/dashboard",
    icon: LayoutDashboard,
  },
  {
    label: "내 프로필",
    href: "/profile",
    icon: User,
    children: [
      { label: "기본 정보", href: "/profile?tab=basic" },
      { label: "이력서 관리", href: "/profile?tab=resume" },
      { label: "자기소개서 관리", href: "/profile?tab=cover" },
      { label: "경력/프로젝트 관리", href: "/profile?tab=career" },
      { label: "기술스택 관리", href: "/profile?tab=skills" },
      { label: "자격증/학력 관리", href: "/profile?tab=certificates" },
    ],
  },
  {
    label: "지원 건 관리",
    href: "/applications",
    icon: Briefcase,
    children: [
      { label: "전체 지원 건 목록", href: "/applications" },
      { label: "새 지원 건 만들기", href: "/applications/new" },
      { label: "내 스펙과 비교", href: "/applications?tab=fit" },
      { label: "지원 전략", href: "/applications?tab=strategy" },
      { label: "학습/자격증 추천", href: "/applications?tab=learning" },
    ],
  },
  {
    label: "AI 가상 면접",
    href: "/interview",
    icon: MessageSquare,
    children: [
      { label: "면접 모드 선택", href: "/interview?tab=modes" },
      { label: "예상 면접 질문", href: "/interview?tab=questions" },
      { label: "복습 테스트", href: "/interview?tab=practice" },
      { label: "음성 모의면접", href: "/interview?tab=live" },
      { label: "아바타 화상 면접", href: "/interview?tab=avatar" },
      { label: "답변 평가", href: "/interview?tab=evaluation" },
      { label: "면접 리포트", href: "/interview?tab=report" },
    ],
  },
  {
    label: "AI 첨삭",
    href: "/correction",
    icon: PenTool,
    children: [
      { label: "답변 첨삭", href: "/correction?tab=answer" },
      { label: "자기소개서 첨삭", href: "/correction?tab=cover" },
      { label: "이력서 첨삭", href: "/correction?tab=resume" },
      { label: "포트폴리오 설명 첨삭", href: "/correction?tab=portfolio" },
    ],
  },
  {
    label: "취업 분석",
    href: "/analysis",
    icon: TrendingUp,
    children: [
      { label: "내 지원 경향", href: "/analysis" },
      { label: "자주 부족한 역량", href: "/analysis?tab=weakness" },
      { label: "직무별 준비도", href: "/analysis?tab=readiness" },
      { label: "면접 점수 변화", href: "/analysis?tab=score" },
      { label: "추천 지원 방향", href: "/analysis?tab=recommendation" },
    ],
  },
  {
    label: "플래너",
    href: "/planner",
    icon: CalendarClock,
    children: [
      { label: "일정 관리", href: "/planner" },
      { label: "메모 관리", href: "/planner?tab=memo" },
      { label: "오버레이 관리", href: "/planner?tab=overlay" },
    ],
  },
  {
    label: "커뮤니티",
    href: "/community",
    icon: Users,
    children: [
      { label: "인기글", href: "/community?sort=likes" },
      { label: "내 활동", href: "/community/activity" },
    ],
  },
  {
    label: "메신저",
    href: "/messenger",
    icon: MessagesSquare,
    children: [
      { label: "내 채팅방", href: "/messenger" },
      { label: "공개방 찾기", href: "/messenger?tab=discover" },
      { label: "친구 관리", href: "/messenger?tab=friends" },
    ],
  },
  {
    label: "결제/구독",
    href: "/billing",
    icon: CreditCard,
    children: [
      { label: "요금제", href: "/billing?tab=plans" },
      { label: "AI 사용량", href: "/billing?tab=usage" },
      { label: "크레딧 충전", href: "/billing?tab=credits" },
      { label: "결제 내역", href: "/billing?tab=history" },
    ],
  },
  {
    label: "설정",
    href: "/settings",
    icon: Settings,
    children: [
      { label: "계정 설정", href: "/settings?tab=account" },
      { label: "개인정보 관리", href: "/settings?tab=privacy" },
      { label: "AI 데이터 사용 동의", href: "/settings?tab=ai-consent" },
      { label: "알림 설정", href: "/settings?tab=notifications" },
    ],
  },
  {
    label: "관리자",
    href: "/admin",
    icon: ShieldCheck,
    children: [
      { label: "관리자 대시보드", href: "/admin" },
      { label: "지원 건 관리", href: "/admin/application-cases" },
      { label: "회원 관리", href: "/admin/users" },
      { label: "결제 관리", href: "/admin/payments" },
      { label: "AI 사용량 관리", href: "/admin/ai-usage" },
      { label: "공고 분석 조회", href: "/admin/job-analysis" },
      { label: "기업 분석 조회", href: "/admin/company-analysis" },
      { label: "면접 세션 관리", href: "/admin/interviews" },
      { label: "게시판/신고 관리", href: "/admin/community" },
      { label: "공지사항 관리", href: "/admin/notices" },
      { label: "요금제 관리", href: "/admin/plans" },
      { label: "프롬프트 템플릿", href: "/admin/prompts" },
      { label: "시스템 로그", href: "/admin/logs" },
    ],
  },
];

export function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const isLoggedIn = isAuthenticated;
  const canSeeAdminMenu = user?.role === "ADMIN" || user?.role === "SUPER_ADMIN";
  const visibleNavItems = navItems.filter((item) => item.href !== "/admin" || canSeeAdminMenu);
  const userInitial = user?.name?.trim()?.charAt(0) ?? user?.email?.charAt(0).toUpperCase() ?? "U";
  const planLabel = user?.plan === "FREE" ? "무료 플랜" : user?.plan === "PRO" ? "프로 플랜" : user?.plan ?? "기본 플랜";
  const credit = user?.credit ?? 0;

  const handleLogout = async () => {
    await logout();
    setUserMenuOpen(false);
    setMobileOpen(false);
    navigate("/");
  };

  return (
    <header
      className="bg-background/80 backdrop-blur-xl border-b border-border sticky top-0 z-50 overflow-x-clip"
      // 노치/상태바 영역 확보 — iOS PWA standalone 및 네이티브 오버레이 상태바에서 헤더가 가리지 않게.
      style={{ paddingTop: "env(safe-area-inset-top)" }}
    >
      {/* Top utility bar */}
      <div className="hidden md:block bg-muted text-muted-foreground text-xs py-1.5 border-b border-border">
        <div className="w-full max-w-[1400px] mx-auto px-6 flex items-center justify-between">
          <span>CareerTuner - AI 기반 채용공고 분석 및 맞춤형 면접 지원 플랫폼</span>
          <div className="flex items-center gap-4">
            <Link to="/support" className="hover:text-foreground transition-colors">고객센터</Link>
            <span className="text-border">|</span>
            <Link to="/support/notices" className="hover:text-foreground transition-colors">공지사항</Link>
            <span className="text-border">|</span>
            <Link to="/support/faq" className="hover:text-foreground transition-colors">FAQ</Link>
          </div>
        </div>
      </div>

      {/* Main header */}
      <div className="w-full max-w-[1400px] mx-auto px-4 sm:px-6">
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center h-16 lg:flex lg:justify-between">
          {/* Logo */}
          <Link to={homePath()} className="flex items-center gap-2.5 min-w-0">
            <div className="size-9 rounded-lg bg-primary flex items-center justify-center shadow-[inset_0_1px_0_rgba(255,255,255,0.2)]">
              <Sparkles className="size-5 text-primary-foreground" />
            </div>
            <div>
              <span className="text-lg font-bold tracking-tight text-foreground">
                CareerTuner
              </span>
              <div className="text-[10px] text-muted-foreground leading-none -mt-0.5">AI 취업 전략 플랫폼</div>
            </div>
          </Link>

          {/* Desktop Navigation (폭이 부족하면 넘치는 항목을 '더보기'로 보냄 → HeaderNav) */}
          <HeaderNav items={visibleNavItems} pathname={location.pathname} />

          {/* Right side */}
          <div className="flex items-center gap-1 sm:gap-2 justify-self-end">
            <ThemeToggle />
            {isLoggedIn ? (
              <>
                {/* Credits badge */}
                <div className="hidden md:flex items-center gap-1.5 bg-muted border border-border rounded-full px-3 py-1">
                  <Award className="size-3.5 text-primary" />
                  <span className="text-xs font-semibold text-muted-foreground">크레딧 <span className="text-foreground">{credit}</span></span>
                </div>

                {/* Notifications */}
                <NotificationBell />

                {/* User menu */}
                <div className="relative hidden sm:block" onMouseEnter={() => setUserMenuOpen(true)} onMouseLeave={() => setUserMenuOpen(false)}>
                  <button className="flex items-center gap-2 p-1.5 rounded-lg hover:bg-accent transition-colors">
                    <div className="size-8 rounded-full bg-primary flex items-center justify-center text-primary-foreground text-sm font-bold">
                      {userInitial}
                    </div>
                    <ChevronDown className="size-3.5 text-muted-foreground" />
                  </button>
                  {userMenuOpen && (
                    <div className="absolute top-full right-0 mt-1 w-48 bg-popover rounded-xl border border-border py-2 z-50 shadow-[var(--shadow-pop)]">
                      <div className="px-4 py-2 border-b border-border">
                        <div className="font-semibold text-sm text-foreground">{user?.name ?? "사용자"}</div>
                        <div className="text-xs text-muted-foreground truncate">{user?.email}</div>
                        <Badge className="mt-1 bg-accent-soft text-primary text-xs px-2 py-0.5">{planLabel}</Badge>
                      </div>
                      <Link to="/dashboard" className="flex items-center gap-2 px-4 py-2 text-sm text-foreground hover:bg-accent">
                        <LayoutDashboard className="size-4" /> 대시보드
                      </Link>
                      <Link to="/profile" className="flex items-center gap-2 px-4 py-2 text-sm text-foreground hover:bg-accent">
                        <User className="size-4" /> 내 프로필
                      </Link>
                      <Link to="/billing" className="flex items-center gap-2 px-4 py-2 text-sm text-foreground hover:bg-accent">
                        <CreditCard className="size-4" /> 결제/구독
                      </Link>
                      <div className="border-t border-border mt-1 pt-1">
                        <button
                          onClick={handleLogout}
                          className="flex w-full items-center gap-2 px-4 py-2 text-sm text-destructive hover:bg-destructive/10"
                        >
                          <LogOut className="size-4" /> 로그아웃
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </>
            ) : (
              <>
                <Button variant="ghost" size="sm" className="hidden sm:inline-flex" onClick={() => navigate("/login")}>
                  로그인
                </Button>
                <Button
                  size="sm"
                  className="hidden sm:inline-flex whitespace-nowrap"
                  onClick={() => navigate("/login")}
                >
                  무료 시작
                </Button>
              </>
            )}

            {/* Mobile menu button */}
            <button
              className="inline-flex size-10 items-center justify-center rounded-lg border border-border text-foreground hover:bg-accent transition-colors xl:hidden"
              onClick={() => setMobileOpen(!mobileOpen)}
              aria-label="메뉴 열기"
            >
              {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div className="xl:hidden border-t border-border bg-background">
          <div className="w-full max-w-[1400px] mx-auto px-4 sm:px-6 py-4 space-y-3">
            {visibleNavItems.map((item) => (
              <div key={item.href} className="rounded-xl border border-border bg-card p-1">
                <Link
                  to={item.href}
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium text-foreground hover:bg-accent"
                  onClick={() => setMobileOpen(false)}
                >
                  <item.icon className="size-4 text-muted-foreground" />
                  {item.label}
                </Link>
                {item.children && (
                  <div className="grid grid-cols-2 gap-1 px-2 pb-2">
                    {item.children.map((child) => (
                      <Link
                        key={child.href}
                        to={child.href}
                        className="rounded-md px-2 py-1.5 text-xs text-muted-foreground hover:bg-accent hover:text-foreground"
                        onClick={() => setMobileOpen(false)}
                      >
                        {child.label}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            ))}
            {isLoggedIn ? (
              <div className="pt-3 border-t border-border space-y-2">
                <div className="rounded-lg bg-muted px-3 py-2">
                  <div className="text-sm font-semibold text-foreground">{user?.name ?? "사용자"}</div>
                  <div className="text-xs text-muted-foreground truncate">{user?.email}</div>
                  <div className="mt-1 text-xs font-semibold text-muted-foreground">크레딧 {credit}</div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Button variant="outline" onClick={() => { navigate("/profile"); setMobileOpen(false); }}>내 프로필</Button>
                  <Button variant="outline" onClick={() => { navigate("/billing?tab=credits"); setMobileOpen(false); }}>크레딧</Button>
                </div>
                <Button variant="outline" className="w-full text-destructive hover:bg-destructive/10" onClick={handleLogout}>
                  로그아웃
                </Button>
              </div>
            ) : (
              <div className="pt-3 border-t border-border flex gap-2">
                <Button variant="outline" className="flex-1" onClick={() => { navigate("/login"); setMobileOpen(false); }}>로그인</Button>
                <Button className="flex-1" onClick={() => { navigate("/login"); setMobileOpen(false); }}>무료 시작</Button>
              </div>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
