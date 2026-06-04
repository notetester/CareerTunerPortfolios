import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import {
  Sparkles,
  ChevronDown,
  Menu,
  X,
  Bell,
  User,
  LayoutDashboard,
  FileText,
  MessageSquare,
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
      { label: "기본 정보", href: "/profile" },
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
      { label: "새 지원 건 만들기", href: "/applications?action=new" },
      { label: "전체 지원 건 목록", href: "/applications" },
      { label: "공고문 분석 결과", href: "/applications?tab=analysis" },
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
      { label: "면접 모드 선택", href: "/interview" },
      { label: "예상 질문 목록", href: "/interview?tab=questions" },
      { label: "실전 모의면접", href: "/interview?mode=practice" },
      { label: "음성 면접", href: "/interview?mode=voice" },
      { label: "답변 평가", href: "/interview?tab=evaluation" },
      { label: "면접 리포트", href: "/interview?tab=report" },
    ],
  },
  {
    label: "AI 첨삭",
    href: "/interview?tab=correction",
    icon: PenTool,
    children: [
      { label: "답변 첨삭", href: "/interview?tab=answer-correction" },
      { label: "자기소개서 첨삭", href: "/interview?tab=cover-correction" },
      { label: "이력서 첨삭", href: "/interview?tab=resume-correction" },
      { label: "포트폴리오 첨삭", href: "/interview?tab=portfolio-correction" },
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
    label: "커뮤니티",
    href: "/community",
    icon: Users,
    children: [
      { label: "취업 후기", href: "/community?cat=hired" },
      { label: "면접 후기", href: "/community?cat=interview" },
      { label: "직무별 질문 공유", href: "/community?cat=questions" },
      { label: "합격 전략 게시판", href: "/community?cat=strategy" },
      { label: "자유게시판", href: "/community?cat=free" },
    ],
  },
];

export function Header() {
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const isLoggedIn = location.pathname !== "/" && location.pathname !== "/login";

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-50 shadow-sm">
      {/* Top utility bar */}
      <div className="bg-gradient-to-r from-blue-700 to-indigo-700 text-white text-xs py-1.5">
        <div className="max-w-[1400px] mx-auto px-6 flex items-center justify-between">
          <span>🎯 CareerTuner - AI 기반 채용공고 분석 및 맞춤형 면접 지원 플랫폼</span>
          <div className="flex items-center gap-4">
            <a href="#" className="hover:text-blue-200 transition-colors">고객센터</a>
            <span className="text-blue-400">|</span>
            <a href="#" className="hover:text-blue-200 transition-colors">공지사항</a>
            <span className="text-blue-400">|</span>
            <a href="#" className="hover:text-blue-200 transition-colors">FAQ</a>
          </div>
        </div>
      </div>

      {/* Main header */}
      <div className="max-w-[1400px] mx-auto px-6">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 flex-shrink-0">
            <div className="size-9 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center shadow-md">
              <Sparkles className="size-5 text-white" />
            </div>
            <div>
              <span className="text-lg font-black bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">
                CareerTuner
              </span>
              <div className="text-[10px] text-slate-400 leading-none -mt-0.5">AI 취업 전략 플랫폼</div>
            </div>
          </Link>

          {/* Desktop Navigation */}
          <nav className="hidden lg:flex items-center gap-1">
            {navItems.map((item) => (
              <div
                key={item.href}
                className="relative"
                onMouseEnter={() => setOpenMenu(item.label)}
                onMouseLeave={() => setOpenMenu(null)}
              >
                <Link
                  to={item.href}
                  className={`flex items-center gap-1 px-3 py-2 text-sm font-medium rounded-md transition-colors ${
                    location.pathname === item.href
                      ? "text-blue-600 bg-blue-50"
                      : "text-slate-700 hover:text-blue-600 hover:bg-slate-50"
                  }`}
                >
                  {item.label}
                  {item.children && <ChevronDown className="size-3.5 opacity-60" />}
                </Link>

                {item.children && openMenu === item.label && (
                  <div className="absolute top-full left-0 mt-0 w-52 bg-white rounded-xl shadow-xl border border-slate-100 py-2 z-50">
                    <div className="px-3 py-1.5 mb-1 border-b border-slate-100">
                      <div className="flex items-center gap-2 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                        <item.icon className="size-3.5" />
                        {item.label}
                      </div>
                    </div>
                    {item.children.map((child) => (
                      <Link
                        key={child.href}
                        to={child.href}
                        className="flex items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:text-blue-600 hover:bg-blue-50 transition-colors"
                      >
                        {child.label}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </nav>

          {/* Right side */}
          <div className="flex items-center gap-2">
            {isLoggedIn ? (
              <>
                {/* Credits badge */}
                <div className="hidden md:flex items-center gap-1.5 bg-amber-50 border border-amber-200 rounded-full px-3 py-1">
                  <Award className="size-3.5 text-amber-600" />
                  <span className="text-xs font-semibold text-amber-700">크레딧 <span className="text-amber-900">42</span></span>
                </div>

                {/* Notifications */}
                <button className="relative p-2 rounded-lg hover:bg-slate-100 transition-colors">
                  <Bell className="size-5 text-slate-600" />
                  <span className="absolute top-1.5 right-1.5 size-2 bg-red-500 rounded-full"></span>
                </button>

                {/* User menu */}
                <div className="relative" onMouseEnter={() => setUserMenuOpen(true)} onMouseLeave={() => setUserMenuOpen(false)}>
                  <button className="flex items-center gap-2 p-1.5 rounded-lg hover:bg-slate-100 transition-colors">
                    <div className="size-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-500 flex items-center justify-center text-white text-sm font-bold">
                      김
                    </div>
                    <ChevronDown className="size-3.5 text-slate-500" />
                  </button>
                  {userMenuOpen && (
                    <div className="absolute top-full right-0 mt-1 w-48 bg-white rounded-xl shadow-xl border border-slate-100 py-2 z-50">
                      <div className="px-4 py-2 border-b border-slate-100">
                        <div className="font-semibold text-sm text-slate-900">김지원</div>
                        <div className="text-xs text-slate-500">jiwon@example.com</div>
                        <Badge className="mt-1 bg-blue-100 text-blue-700 text-xs px-2 py-0.5">프로 플랜</Badge>
                      </div>
                      <Link to="/dashboard" className="flex items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:text-blue-600 hover:bg-blue-50">
                        <LayoutDashboard className="size-4" /> 대시보드
                      </Link>
                      <Link to="/profile" className="flex items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:text-blue-600 hover:bg-blue-50">
                        <User className="size-4" /> 내 프로필
                      </Link>
                      <Link to="/pricing" className="flex items-center gap-2 px-4 py-2 text-sm text-slate-700 hover:text-blue-600 hover:bg-blue-50">
                        <CreditCard className="size-4" /> 요금제/크레딧
                      </Link>
                      <div className="border-t border-slate-100 mt-1 pt-1">
                        <button
                          onClick={() => navigate("/")}
                          className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
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
                <Button variant="ghost" size="sm" onClick={() => navigate("/login")}>
                  로그인
                </Button>
                <Button
                  size="sm"
                  className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700"
                  onClick={() => navigate("/login")}
                >
                  무료 시작
                </Button>
              </>
            )}

            {/* Mobile menu button */}
            <button
              className="lg:hidden p-2 rounded-lg hover:bg-slate-100 transition-colors"
              onClick={() => setMobileOpen(!mobileOpen)}
            >
              {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div className="lg:hidden border-t border-slate-200 bg-white">
          <div className="max-w-[1400px] mx-auto px-6 py-4 space-y-1">
            {navItems.map((item) => (
              <div key={item.href}>
                <Link
                  to={item.href}
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-50"
                  onClick={() => setMobileOpen(false)}
                >
                  <item.icon className="size-4 text-slate-500" />
                  {item.label}
                </Link>
              </div>
            ))}
            <div className="pt-3 border-t border-slate-100 flex gap-2">
              <Button variant="outline" className="flex-1" onClick={() => { navigate("/login"); setMobileOpen(false); }}>로그인</Button>
              <Button className="flex-1 bg-gradient-to-r from-blue-600 to-indigo-600" onClick={() => { navigate("/login"); setMobileOpen(false); }}>무료 시작</Button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
