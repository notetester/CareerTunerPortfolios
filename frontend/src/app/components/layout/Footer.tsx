import { Link } from "react-router";
import { Camera, MessageCircle, Send, Sparkles, Video } from "lucide-react";
import { isAppContext } from "@/platform/capacitor";

export const FOOTER_SECTIONS = [
  {
    title: "서비스",
    links: [
      { label: "기능 소개", href: "/features" },
      { label: "AI 가상 면접", href: "/interview" },
      { label: "공고 분석", href: "/job-analysis" },
      { label: "취업 분석", href: "/analysis" },
      { label: "AI 첨삭", href: "/correction" },
      { label: "커뮤니티", href: "/community" },
    ],
  },
  {
    title: "결제/구독",
    links: [
      { label: "요금제", href: "/billing/plans" },
      { label: "AI 사용량", href: "/billing/usage" },
      { label: "크레딧 충전", href: "/billing/credits" },
      { label: "결제 내역", href: "/billing/history" },
      { label: "요금제 비교", href: "/pricing" },
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
      { label: "이력서 분석 동의", href: "/legal/resume-analysis-consent" },
      { label: "마케팅 수신 동의", href: "/legal/marketing" },
      { label: "저작권 정책", href: "/legal/copyright" },
    ],
  },
];

export const FOOTER_SOCIAL_LINKS = [
  { icon: Video, label: "YouTube", href: "/company/social/youtube" },
  { icon: Camera, label: "Instagram", href: "/company/social/instagram" },
  { icon: Send, label: "Twitter", href: "/company/social/twitter" },
  { icon: MessageCircle, label: "KakaoTalk", href: "/company/social/kakao" },
];

export function Footer() {
  // 앱(네이티브/앱 미리보기)에선 웹용 푸터(SEO·법적고지·마케팅 링크)를 숨긴다. 웹은 그대로 노출.
  if (isAppContext()) return null;

  return (
    <footer className="bg-muted text-muted-foreground">
      {/* Main footer */}
      <div className="max-w-[1400px] mx-auto px-6 pt-12 pb-[calc(6rem+env(safe-area-inset-bottom))] xl:pb-8">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-8 mb-12">
          {/* Brand */}
          <div className="col-span-2 md:col-span-3 lg:col-span-1 space-y-4">
            <Link to="/" className="flex items-center gap-2">
              <div className="size-8 rounded-lg bg-accent-soft flex items-center justify-center">
                <Sparkles className="size-4 text-primary" />
              </div>
              <span className="font-black text-foreground text-lg">CareerTuner</span>
            </Link>
            <p className="text-sm leading-relaxed">
              AI 기반 채용공고 분석 및 맞춤형 면접 지원 플랫폼
            </p>
            <div className="space-y-1 text-sm">
              <p>운영: CareerTuner 프로젝트 팀</p>
              <p>공개 단계: 포트폴리오·공개 베타</p>
              <p>
                이메일:{" "}
                <a className="hover:text-foreground hover:underline" href="mailto:support@careertuner.dev">
                  support@careertuner.dev
                </a>
              </p>
            </div>
            {/* Social */}
            <div className="flex items-center gap-3 pt-2">
              {FOOTER_SOCIAL_LINKS.map((s) => (
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
          {FOOTER_SECTIONS.map((section) => (
            <div key={section.title} className="space-y-4">
              <h4 className="font-semibold text-foreground text-sm">{section.title}</h4>
              <ul className="space-y-2">
                {section.links.map((link) => (
                  <li key={link.label}>
                    <Link
                      to={link.href}
                      className="text-sm hover:text-foreground transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* Bottom bar — 법적 링크는 위 "법적 고지" 컬럼에 있으므로 여기선 저작권 표기만(중복 제거) */}
        <div className="border-t border-border pt-8 text-xs">
          <p className="text-muted-foreground">© 2026 CareerTuner. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}
