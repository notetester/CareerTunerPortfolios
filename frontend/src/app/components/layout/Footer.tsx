import { Link } from "react-router";
import { Sparkles, Youtube, Instagram, Twitter, MessageCircle } from "lucide-react";

const footerLinks = [
  {
    title: "서비스",
    links: [
      { label: "기능 소개", href: "/features" },
      { label: "AI 가상 면접", href: "/interview" },
      { label: "공고 분석", href: "/applications" },
      { label: "취업 분석", href: "/analysis" },
      { label: "AI 첨삭", href: "/correction" },
      { label: "커뮤니티", href: "/community" },
    ],
  },
  {
    title: "결제/구독",
    links: [
      { label: "요금제", href: "/billing?tab=plans" },
      { label: "AI 사용량", href: "/billing?tab=usage" },
      { label: "크레딧 충전", href: "/billing?tab=credits" },
      { label: "결제 내역", href: "/billing?tab=history" },
      { label: "기존 요금제 화면", href: "/pricing" },
    ],
  },
  {
    title: "회사",
    links: [
      { label: "서비스 소개", href: "/service/about" },
      { label: "팀 소개", href: "/company/team" },
      { label: "채용", href: "/company/careers" },
      { label: "블로그", href: "/company/blog" },
      { label: "보도자료", href: "/company/press" },
    ],
  },
  {
    title: "고객 지원",
    links: [
      { label: "고객센터", href: "/support" },
      { label: "사용 가이드", href: "/support/guide" },
      { label: "자주 묻는 질문", href: "/support/faq" },
      { label: "공지사항", href: "/support/notices" },
      { label: "문의하기", href: "/support/contact" },
    ],
  },
  {
    title: "법적 고지",
    links: [
      { label: "이용약관", href: "/legal/terms" },
      { label: "개인정보처리방침", href: "/legal/privacy" },
      { label: "AI 데이터 이용 동의", href: "/legal/ai-data-consent" },
      { label: "저작권 정책", href: "/legal/copyright" },
    ],
  },
];

export function Footer() {
  return (
    <footer className="bg-muted text-muted-foreground">
      {/* Main footer */}
      <div className="max-w-[1400px] mx-auto px-6 pt-12 pb-8">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8 mb-12">
          {/* Brand */}
          <div className="col-span-2 md:col-span-3 lg:col-span-1 space-y-4">
            <Link to="/" className="flex items-center gap-2">
              <div className="size-8 rounded-lg bg-accent-soft flex items-center justify-center">
                <Sparkles className="size-4 text-primary" />
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
                { icon: Youtube, label: "YouTube", href: "/company/social?channel=youtube" },
                { icon: Instagram, label: "Instagram", href: "/company/social?channel=instagram" },
                { icon: Twitter, label: "Twitter", href: "/company/social?channel=twitter" },
                { icon: MessageCircle, label: "KakaoTalk", href: "/company/social?channel=kakao" },
              ].map((s) => (
                <Link
                  key={s.label}
                  to={s.href}
                  aria-label={s.label}
                  className="size-8 rounded-lg bg-card flex items-center justify-center hover:bg-secondary border border-border transition-colors"
                >
                  <s.icon className="size-4" />
                </Link>
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
            <Link to="/legal/terms" className="hover:text-white transition-colors">이용약관</Link>
            <span className="text-slate-700">|</span>
            <Link to="/legal/privacy" className="hover:text-white transition-colors font-medium text-slate-300">개인정보처리방침</Link>
            <span className="text-slate-700">|</span>
            <Link to="/legal/ai-data-consent" className="hover:text-white transition-colors">AI 데이터 이용 동의</Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
