import { Link } from "react-router";
import { Sparkles, Youtube, Instagram, Twitter, MessageCircle } from "lucide-react";

const footerLinks = [
  {
    title: "서비스",
    links: [
      { label: "기능 소개", href: "/#features" },
      { label: "AI 가상 면접", href: "/interview" },
      { label: "공고 분석", href: "/applications" },
      { label: "취업 분석", href: "/analysis" },
      { label: "커뮤니티", href: "/community" },
    ],
  },
  {
    title: "요금제",
    links: [
      { label: "무료 플랜", href: "/pricing" },
      { label: "베이직 플랜", href: "/pricing" },
      { label: "프로 플랜", href: "/pricing" },
      { label: "프리미엄 플랜", href: "/pricing" },
      { label: "크레딧 충전", href: "/pricing?tab=credits" },
    ],
  },
  {
    title: "회사",
    links: [
      { label: "서비스 소개", href: "#" },
      { label: "팀 소개", href: "#" },
      { label: "채용", href: "#" },
      { label: "블로그", href: "#" },
      { label: "보도자료", href: "#" },
    ],
  },
  {
    title: "고객 지원",
    links: [
      { label: "고객센터", href: "#" },
      { label: "사용 가이드", href: "#" },
      { label: "자주 묻는 질문", href: "#" },
      { label: "공지사항", href: "#" },
      { label: "문의하기", href: "#" },
    ],
  },
  {
    title: "법적 고지",
    links: [
      { label: "이용약관", href: "#" },
      { label: "개인정보처리방침", href: "#" },
      { label: "AI 데이터 이용 동의", href: "#" },
      { label: "저작권 정책", href: "#" },
    ],
  },
];

export function Footer() {
  return (
    <footer className="bg-slate-900 text-slate-400">
      {/* Main footer */}
      <div className="max-w-[1400px] mx-auto px-6 pt-12 pb-8">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8 mb-12">
          {/* Brand */}
          <div className="col-span-2 md:col-span-3 lg:col-span-1 space-y-4">
            <Link to="/" className="flex items-center gap-2">
              <div className="size-8 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center">
                <Sparkles className="size-4 text-white" />
              </div>
              <span className="font-black text-white text-lg">CareerTuner</span>
            </Link>
            <p className="text-sm leading-relaxed">
              AI 기반 채용공고 분석 및 맞춤형 면접 지원 플랫폼
            </p>
            <div className="space-y-1 text-sm">
              <p>상호: (주)에이아이잡프렙</p>
              <p>사업자등록번호: 000-00-00000</p>
              <p>대표: 홍길동</p>
              <p>이메일: redacted-5752040532f5106a@example.com</p>
            </div>
            {/* Social */}
            <div className="flex items-center gap-3 pt-2">
              {[
                { icon: Youtube, label: "YouTube" },
                { icon: Instagram, label: "Instagram" },
                { icon: Twitter, label: "Twitter" },
                { icon: MessageCircle, label: "KakaoTalk" },
              ].map((s) => (
                <a
                  key={s.label}
                  href="#"
                  aria-label={s.label}
                  className="size-8 rounded-lg bg-slate-800 flex items-center justify-center hover:bg-slate-700 transition-colors"
                >
                  <s.icon className="size-4" />
                </a>
              ))}
            </div>
          </div>

          {/* Links */}
          {footerLinks.map((section) => (
            <div key={section.title} className="space-y-4">
              <h4 className="font-semibold text-white text-sm">{section.title}</h4>
              <ul className="space-y-2">
                {section.links.map((link) => (
                  <li key={link.label}>
                    <Link
                      to={link.href}
                      className="text-sm hover:text-white transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* Bottom bar */}
        <div className="border-t border-slate-800 pt-8 flex flex-col md:flex-row items-center justify-between gap-4 text-xs">
          <p>© 2026 CareerTuner. All rights reserved.</p>
          <div className="flex items-center gap-4">
            <a href="#" className="hover:text-white transition-colors">이용약관</a>
            <span className="text-slate-700">|</span>
            <a href="#" className="hover:text-white transition-colors font-medium text-slate-300">개인정보처리방침</a>
            <span className="text-slate-700">|</span>
            <a href="#" className="hover:text-white transition-colors">AI 데이터 이용 동의</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
