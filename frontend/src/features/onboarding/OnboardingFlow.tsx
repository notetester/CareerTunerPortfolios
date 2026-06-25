import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { enablePush, isPushSupported } from "@/platform/push";
import { subscriptionFallbackPlans, toDisplayPlans } from "@/features/billing/utils/subscriptionDisplay";
import { Sparkles, FileText, MessageSquare, Mic, Video, UserRound, X, Bell, type LucideIcon } from "lucide-react";
import "./onboarding.css";

/**
 * 앱 온보딩 퍼널 (마누스 레퍼런스): 로그인 → 구독 제안(무료 스킵) → 알림 권한 → 검색창 메인(/home).
 * 네이티브 앱 + 미완료일 때만 Root 에서 진입한다. mock 모드(VITE_USE_MOCK)에서도 전부 동작한다.
 * docs/AI_ORCHESTRATOR.md 11.5 참조. 포트폴리오/시연용이라 결제는 토스 외부결제 그대로(스토어 정책 무관).
 */

const KEY = "careertuner.onboarding";
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
// ?ob 로 강제 미리보기 중이고 실데이터 모드면, 실제 로그인 없이 화면만 넘긴다(디자인 확인용).
const PREVIEW = typeof window !== "undefined" && new URLSearchParams(window.location.search).has("ob") && !USE_MOCK;

export function isOnboarded(): boolean {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "{}").completed === true;
  } catch {
    return false;
  }
}
function markOnboarded() {
  try {
    localStorage.setItem(KEY, JSON.stringify({ completed: true }));
  } catch {
    /* 저장 실패해도 흐름은 진행 */
  }
}

type Step = "login" | "billing" | "permission";
const ORDER: Step[] = ["login", "billing", "permission"];

const GoogleIcon = (
  <svg viewBox="0 0 48 48" width="17" height="17" aria-hidden>
    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
  </svg>
);
const KakaoIcon = (
  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden>
    <path fill="#191919" d="M12 3.5C6.9 3.5 2.75 6.74 2.75 10.74c0 2.55 1.7 4.79 4.27 6.07-.16.57-.92 3.19-.95 3.39 0 0-.02.16.08.22.1.06.23.01.23.01.28-.04 3.27-2.14 3.79-2.5.59.08 1.2.13 1.83.13 5.1 0 9.25-3.24 9.25-7.24S17.1 3.5 12 3.5z" />
  </svg>
);
const NaverIcon = (
  <svg viewBox="0 0 24 24" width="13" height="13" aria-hidden>
    <path fill="#fff" d="M16.27 3v9.41L8.73 3H4v18h4.73v-9.41L16.27 21H21V3z" />
  </svg>
);
const MailIcon = (
  <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
    <rect x="3" y="5" width="18" height="14" rx="2" />
    <path d="m3 7 9 6 9-6" />
  </svg>
);

const PROVIDERS = [
  { id: "google", label: "Google", icon: GoogleIcon, cls: "g" },
  { id: "kakao", label: "카카오", icon: KakaoIcon, cls: "k" },
  { id: "naver", label: "네이버", icon: NaverIcon, cls: "n" },
] as const;

export function OnboardingFlow() {
  const nav = useNavigate();
  const { login } = useAuth();
  // ?ob 미리보기·앱 첫 실행 모두 로그인 화면부터 보여준다(이미 로그인돼 있어도 온보딩은 처음부터).
  const [step, setStep] = useState<Step>("login");
  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [emailOpen, setEmailOpen] = useState(false);

  const finish = () => {
    markOnboarded();
    nav("/", { replace: true });
  };

  // mock 모드: 아무 값이나 통과(데모 계정). 소셜 버튼도 동일하게 demo 로그인으로 시연.
  // TODO(실연동): 소셜은 useAuth().socialLogin(provider)로 OAuth 리다이렉트.
  const doLogin = async () => {
    setBusy(true);
    setErr("");
    try {
      if (!PREVIEW) await login(email.trim() || "demo@careertuner.dev", pw || "demo1234");
      setStep("billing");
    } catch {
      setErr("로그인에 실패했어요. 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const askPush = async () => {
    setBusy(true);
    try {
      if (isPushSupported()) await enablePush();
    } catch {
      /* 권한 거부/미지원이어도 온보딩은 완료 */
    } finally {
      setBusy(false);
      finish();
    }
  };

  const stepIndex = ORDER.indexOf(step);

  return (
    <div className="ob">
      <div className="ob-shell">
        <div className="ob-dots" aria-hidden>
          {ORDER.map((s, i) => (
            <span key={s} className={`ob-dot${i <= stepIndex ? " on" : ""}`} />
          ))}
        </div>

        {step === "login" && (
          <section className="ob-card ob-login ob-fade">
            <img className="ob-logo" src="/icons/icon.svg" alt="CareerTuner" width={72} height={72} />
            <h1 className="ob-welcome">
              <span className="ob-brand">CareerTuner</span>에<br />오신 것을 환영합니다
            </h1>

            <div className="ob-social">
              {PROVIDERS.map((p) => (
                <button key={p.id} className={`ob-soc s-${p.cls}`} onClick={doLogin} disabled={busy}>
                  <span className="ob-soc-ic">{p.icon}</span>
                  <span className="ob-soc-tx">{p.label}로 계속하기</span>
                </button>
              ))}
            </div>

            <div className="ob-or"><span>또는</span></div>

            {!emailOpen ? (
              <button className="ob-soc s-mail" onClick={() => setEmailOpen(true)} disabled={busy}>
                <span className="ob-soc-ic">{MailIcon}</span>
                <span className="ob-soc-tx">이메일로 계속하기</span>
              </button>
            ) : (
              <div className="ob-form">
                <input
                  className="ob-input"
                  type="email"
                  placeholder="이메일"
                  autoFocus
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && doLogin()}
                />
                <input
                  className="ob-input"
                  type="password"
                  placeholder="비밀번호"
                  value={pw}
                  onChange={(e) => setPw(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && doLogin()}
                />
                {err && <div className="ob-err">{err}</div>}
                <button className="ob-primary" onClick={doLogin} disabled={busy}>
                  {busy ? "잠시만요…" : "계속하기"}
                </button>
              </div>
            )}

            {USE_MOCK && <p className="ob-hint">데모 — 아무 버튼이나 누르면 바로 들어가져요.</p>}
            <p className="ob-legal">
              계속 진행하면 <a href="/legal/terms">서비스 약관</a>에 동의하며,{" "}
              <a href="/legal/privacy">개인정보 처리방침</a>을 읽었음을 확인하는 것입니다.
            </p>
          </section>
        )}

        {step === "billing" && <BillingStep onFree={() => setStep("permission")} onSkip={() => setStep("permission")} />}

        {step === "permission" && (
          <section className="ob-card ob-fade ob-center">
            <div className="ob-bell"><Bell size={30} strokeWidth={1.6} /></div>
            <h1 className="ob-welcome ob-welcome-sm">알림 권한 요청</h1>
            <p className="ob-sub">메시지와 면접 진행 상황을 실시간으로 받기 위해 필요합니다.</p>
            <div className="ob-actions">
              <button className="ob-primary" onClick={askPush} disabled={busy}>
                {busy ? "요청 중…" : "허용"}
              </button>
              <button className="ob-ghost" onClick={finish} disabled={busy}>나중에</button>
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

const BENEFIT_ICON: Record<string, LucideIcon> = {
  APPLICATION_ANALYSIS: FileText,
  MOCK_INTERVIEW: MessageSquare,
  VOICE_INTERVIEW: Mic,
  VIDEO_ANALYSIS: Video,
  AVATAR_INTERVIEW: UserRound,
};

function BillingStep({ onFree, onSkip }: { onFree: () => void; onSkip: () => void }) {
  const pro = useMemo(() => {
    const plans = toDisplayPlans(subscriptionFallbackPlans());
    return plans.find((p) => p.code === "PRO") ?? plans[0];
  }, []);

  return (
    <section className="ob-card ob-bill ob-fade">
      <button className="ob-x" onClick={onSkip} aria-label="건너뛰기">
        <X size={18} />
      </button>
      <div className="ob-spark">
        <Sparkles size={34} strokeWidth={1.6} />
      </div>
      <h1 className="ob-welcome ob-welcome-sm">
        {pro.name} 플랜으로<br />더 빠르게 준비하기
      </h1>

      <ul className="ob-benefits">
        {pro.benefits.map((b) => {
          const Icon = BENEFIT_ICON[b.code] ?? Sparkles;
          return (
            <li key={b.code} className={b.disabled ? "off" : ""}>
              <Icon className="ob-b-ic" size={19} strokeWidth={1.7} />
              <span className="ob-b-label">{b.label}</span>
              <span className="ob-b-text">{b.text}</span>
            </li>
          );
        })}
      </ul>

      <div className="ob-pricebox">
        <span className="ob-radio" />
        <span className="ob-pb-name">{pro.name}</span>
        <span className="ob-pb-val">{pro.monthlyPrice}</span>
        <span className="ob-pb-per">/ 월</span>
      </div>

      <button className="ob-primary" onClick={onFree}>무료로 계속하기</button>
      <button className="ob-textbtn" onClick={onSkip}>요금제는 나중에</button>
      <p className="ob-hint">결제 안 해도 무료로 시작할 수 있어요. 필요할 때 업그레이드하면 됩니다.</p>
    </section>
  );
}
