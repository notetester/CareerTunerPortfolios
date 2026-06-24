import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { enablePush, isPushSupported } from "@/platform/push";
import { subscriptionFallbackPlans, toDisplayPlans } from "@/features/billing/utils/subscriptionDisplay";
import "./onboarding.css";

/**
 * 앱 온보딩 퍼널 (마누스 레퍼런스): 로그인 → 구독 제안(무료 스킵) → 알림 권한 → 검색창 메인(/home).
 * 네이티브 앱 + 미완료일 때만 Root 에서 진입한다. mock 모드(VITE_USE_MOCK)에서도 전부 동작한다.
 * docs/AI_ORCHESTRATOR.md 11.5 참조. 포트폴리오/시연용이라 결제는 토스 외부결제 그대로(스토어 정책 무관).
 */

const KEY = "careertuner.onboarding";
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

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

const PROVIDERS = [
  ["google", "Google", "G"],
  ["kakao", "카카오", "K"],
  ["naver", "네이버", "N"],
] as const;

export function OnboardingFlow() {
  const nav = useNavigate();
  const { login, isAuthenticated } = useAuth();
  const [step, setStep] = useState<Step>(isAuthenticated ? "billing" : "login");
  const [email, setEmail] = useState("");
  const [pw, setPw] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  const finish = () => {
    markOnboarded();
    nav("/home", { replace: true });
  };

  // mock 모드: 아무 값이나 통과(데모 계정). 소셜 버튼도 동일하게 demo 로그인으로 시연.
  // TODO(실연동): 소셜은 useAuth().socialLogin(provider)로 OAuth 리다이렉트.
  const doLogin = async () => {
    setBusy(true);
    setErr("");
    try {
      await login(email.trim() || "demo@careertuner.dev", pw || "demo1234");
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
          <section className="ob-card ob-fade">
            <div className="ob-mark">✦</div>
            <h1 className="ob-h1">CareerTuner에<br />오신 것을 환영합니다</h1>
            <p className="ob-sub">한 줄이면 지원 한 건을 통째로 준비해요.</p>

            <div className="ob-social">
              {PROVIDERS.map(([id, label, icon]) => (
                <button key={id} className="ob-soc" onClick={doLogin} disabled={busy}>
                  <span className="ob-soc-i">{icon}</span>
                  {label}으로 계속하기
                </button>
              ))}
            </div>

            <div className="ob-or"><span>또는</span></div>

            <div className="ob-form">
              <input
                className="ob-input"
                type="email"
                placeholder="이메일"
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
                {busy ? "잠시만요…" : "이메일로 계속하기"}
              </button>
            </div>

            {USE_MOCK && <p className="ob-hint">데모 모드 — 아무 값이나 입력하면 바로 들어가져요.</p>}
            <p className="ob-legal">
              계속 진행하면 <a href="/legal/terms">서비스 약관</a>에 동의하며,{" "}
              <a href="/legal/privacy">개인정보 처리방침</a>을 읽었음을 확인하는 것입니다.
            </p>
          </section>
        )}

        {step === "billing" && <BillingStep onFree={() => setStep("permission")} onSkip={() => setStep("permission")} />}

        {step === "permission" && (
          <section className="ob-card ob-fade ob-center">
            <div className="ob-bell">🔔</div>
            <h1 className="ob-h1">알림을 켜둘까요?</h1>
            <p className="ob-sub">
              면접 준비 진행 상황과 결과를 실시간으로 받아볼 수 있어요. 언제든 설정에서 끌 수 있습니다.
            </p>
            <div className="ob-actions">
              <button className="ob-primary" onClick={askPush} disabled={busy}>
                {busy ? "요청 중…" : "알림 허용"}
              </button>
              <button className="ob-ghost" onClick={finish} disabled={busy}>
                나중에 할게요
              </button>
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

function BillingStep({ onFree, onSkip }: { onFree: () => void; onSkip: () => void }) {
  const pro = useMemo(() => {
    const plans = toDisplayPlans(subscriptionFallbackPlans());
    return plans.find((p) => p.code === "PRO") ?? plans[0];
  }, []);

  return (
    <section className="ob-card ob-fade">
      <button className="ob-x" onClick={onSkip} aria-label="건너뛰기">✕</button>
      <div className="ob-mark">✨</div>
      <h1 className="ob-h1">더 빠르게 준비하려면</h1>
      <p className="ob-sub">{pro.name} 플랜으로 모든 기능을 충분히 쓸 수 있어요.</p>

      <ul className="ob-benefits">
        {pro.benefits.map((b) => (
          <li key={b.code} className={b.disabled ? "off" : ""}>
            <span className="ob-b-ic">{b.disabled ? "—" : b.premium ? "★" : "✓"}</span>
            <span className="ob-b-label">{b.label}</span>
            <span className="ob-b-text">{b.text}</span>
          </li>
        ))}
      </ul>

      <div className="ob-price">
        <span className="ob-price-plan">{pro.name}</span>
        <span className="ob-price-val">{pro.monthlyPrice}</span>
        <span className="ob-price-per">/ 월</span>
      </div>

      <div className="ob-actions">
        <button className="ob-primary" onClick={onFree}>무료로 계속하기</button>
        <button className="ob-ghost" onClick={onSkip}>요금제는 나중에</button>
      </div>
      <p className="ob-hint">결제 안 해도 무료로 시작할 수 있어요. 필요할 때 업그레이드하면 됩니다.</p>
    </section>
  );
}
