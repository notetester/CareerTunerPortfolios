import { Link } from "react-router";
import {
  Briefcase, Target, MessageSquare, PenLine, TrendingUp,
  CreditCard, ArrowRight, Lightbulb, Zap, BarChart3,
  type LucideIcon,
} from "lucide-react";
import "../styles/support.css";

interface GuideFeature {
  icon: LucideIcon;
  t: string;
  d: string;
  link: string;
  href: string;
  tone: string;
  fg: string;
}

interface GuideTip {
  icon: LucideIcon;
  t: string;
  d: string;
}

const GUIDE_FEATURES: GuideFeature[] = [
  { icon: Briefcase, t: "공고 분석", d: "채용공고 URL이나 텍스트를 붙여넣으면 AI가 필수·우대 역량, 난이도, 핵심 키워드를 자동으로 구조화해요.", link: "공고 분석하기", href: "/applications/new", tone: "var(--cat-job-bg)", fg: "var(--cat-job-fg)" },
  { icon: Target, t: "내 스펙 비교", d: "프로필에 등록한 역량과 공고 요구사항을 나란히 비교해 강점과 보완할 역량을 시각적으로 보여줘요.", link: "스펙 비교하기", href: "/applications?tab=fit", tone: "var(--cat-interview-bg)", fg: "var(--cat-interview-fg)" },
  { icon: MessageSquare, t: "AI 가상 면접", d: "직무·인성·실전·음성 면접을 시뮬레이션하고, 답변마다 AI 피드백과 개선 포인트를 바로 확인할 수 있어요.", link: "면접 연습하기", href: "/interview", tone: "var(--cat-role-bg)", fg: "var(--cat-role-fg)" },
  { icon: PenLine, t: "AI 첨삭", d: "자기소개서, 면접 답변, 이력서, 포트폴리오 설명을 AI가 분석해 더 설득력 있는 문장으로 다듬어줘요.", link: "첨삭 받기", href: "/correction", tone: "var(--cat-pass-bg)", fg: "var(--cat-pass-fg)" },
  { icon: TrendingUp, t: "취업 분석", d: "여러 지원 건의 데이터를 종합해 지원 경향, 강·약점, 장기 전략 리포트를 한눈에 확인할 수 있어요.", link: "분석 보기", href: "/analysis", tone: "var(--cat-portfolio-bg)", fg: "var(--cat-portfolio-fg)" },
  { icon: CreditCard, t: "결제·사용량", d: "요금제 선택, AI 크레딧 잔여량, 결제 내역, 사용량 추이를 한 곳에서 관리해요.", link: "결제 관리", href: "/billing", tone: "var(--cat-cert-bg)", fg: "var(--cat-cert-fg)" },
];

const GUIDE_TIPS: GuideTip[] = [
  { icon: Zap, t: "공고 분석 → 면접까지 한 흐름으로", d: "공고를 분석한 뒤 바로 면접 연습으로 이어가면, 해당 직무 맥락이 유지돼서 실전에 가까운 피드백을 받아요." },
  { icon: Target, t: "첨삭은 지원 직무를 먼저 지정하세요", d: "직무를 지정하면 그 직무 기준으로 맞춤 제안이 나와서, 같은 문장도 더 날카롭게 다듬어져요." },
  { icon: BarChart3, t: "지원 건 3개부터 분석이 빛나요", d: "취업 분석은 지원 데이터가 쌓일수록 정확해져요. 3건 이상이면 경향과 전략 리포트를 확인할 수 있어요." },
];

export default function GuidePage() {
  return (
    <div className="ct-page">
      <div className="ct-pagehead">
        <h1>사용 가이드</h1>
        <p>CareerTuner의 주요 기능별 사용법과 활용 팁을 한곳에 모았어요.</p>
      </div>

      {/* 기능 카드 */}
      <div className="ct-guide__grid">
        {GUIDE_FEATURES.map((f) => (
          <Link key={f.t} to={f.href} style={{ textDecoration: "none", color: "inherit" }}>
            <div className="ct-guide__card">
              <div className="ct-guide__ic" style={{ background: f.tone }}>
                <f.icon style={{ color: f.fg }} />
              </div>
              <div className="ct-guide__t">{f.t}</div>
              <div className="ct-guide__d">{f.d}</div>
              <span className="ct-guide__link">{f.link} <ArrowRight /></span>
            </div>
          </Link>
        ))}
      </div>

      {/* 프로팁 */}
      <div className="ct-guide__tips">
        <h2 className="ct-guide__h"><Lightbulb />프로 팁</h2>
        <div className="ct-tipgrid">
          {GUIDE_TIPS.map((t) => (
            <div className="ct-tip" key={t.t}>
              <div className="ct-tip__ic"><t.icon /></div>
              <div className="ct-tip__t">{t.t}</div>
              <div className="ct-tip__d">{t.d}</div>
            </div>
          ))}
        </div>
      </div>

      {/* 하단 CTA */}
      <div className="ct-cta">
        <div className="ct-cta__t">
          <h3>더 궁금한 점이 있으신가요?</h3>
          <p>가이드에서 답을 못 찾으셨다면 운영팀이 1:1로 도와드릴게요.</p>
        </div>
        <Link to="/support/contact">
          <button className="av-btn av-btn--ink" style={{ height: 48, paddingLeft: 24, paddingRight: 24 }}>
            문의하기 <ArrowRight />
          </button>
        </Link>
      </div>
    </div>
  );
}
