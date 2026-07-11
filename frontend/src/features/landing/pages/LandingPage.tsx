import { useEffect, useRef, useState } from "react";
import { Brain, ClipboardList, Mic, MessageCircle, PenLine, Target, Users } from "lucide-react";
import { Link } from "react-router";
import { FOOTER_SECTIONS, FOOTER_SOCIAL_LINKS } from "@/app/components/layout/Footer";
import "../styles/landing.css";

const CHIPS = ["카카오 프론트엔드 면접 준비해줘", "압박 면접 연습하고 싶어", "자소서부터 봐줘"];

type StepState = "idle" | "active" | "done";

const STEP_DEFS = [
  { icon: MessageCircle, t: "인테이크 챗봇", d: "요청 분석 · 회사·직무·레벨 파악" },
  { icon: Brain, t: "두뇌 · Plan", d: "확정 슬롯 → 실행계획 생성" },
  { icon: ClipboardList, t: "공고 분석", d: "필수·우대·담당업무 추출" },
  { icon: Target, t: "적합도·전략", d: "프로필 대비 적합도·부족역량" },
  { icon: PenLine, t: "자소서·첨삭", d: "자소서 초안 · 답변 첨삭" },
  { icon: Mic, t: "모의면접", d: "예상 질문 · 평가 · 리포트" },
  { icon: Users, t: "커뮤니티 추천", d: "관련 후기 · 실제 기출" },
];

const DEFAULT_Q = "네이버 백엔드 신입 통째로 준비해줘";

/**
 * CareerTuner 랜딩 페이지 (비로그인 / 진입). 다크 Linear 톤(theme.css .dark 토큰 기반, .ctl 스코프).
 * Root 레이아웃 밖에서 헤더 없이 풀스크린으로 렌더한다. "시작하기" → 기존 비로그인 홈(/home).
 */
export function LandingPage() {
  const [query, setQuery] = useState("");
  const [demoQ, setDemoQ] = useState(DEFAULT_Q);
  const [states, setStates] = useState<StepState[]>(() => STEP_DEFS.map(() => "idle"));
  const runningRef = useRef(false);
  const timersRef = useRef<number[]>([]);

  function runDemo(q?: string) {
    const text = (q ?? query).trim() || DEFAULT_Q;
    setDemoQ(text);
    document.getElementById("ctl-demo")?.scrollIntoView({ behavior: "smooth" });
    if (runningRef.current) return;
    runningRef.current = true;
    setStates(STEP_DEFS.map(() => "idle"));
    let i = 0;
    const tick = () => {
      setStates((prev) => {
        const next = [...prev];
        if (i > 0) next[i - 1] = "done";
        if (i < next.length) next[i] = "active";
        return next;
      });
      if (i >= STEP_DEFS.length) {
        runningRef.current = false;
        return;
      }
      i += 1;
      timersRef.current.push(window.setTimeout(tick, 720));
    };
    timersRef.current.push(window.setTimeout(tick, 350));
  }

  useEffect(() => {
    const timers = timersRef.current;
    return () => timers.forEach((t) => clearTimeout(t));
  }, []);

  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("in");
            io.unobserve(e.target);
          }
        });
      },
      { threshold: 0.15 },
    );
    document.querySelectorAll(".ctl .fu").forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <div className="ctl">
      <nav>
        <div className="wrap">
          <div className="logo"><span className="mk" />CareerTuner</div>
          <div className="nav-links">
            <a href="#ctl-demo">자동 준비</a>
            <a href="#ctl-pipe">파이프라인</a>
            <a href="#ctl-llm">자체 AI</a>
            <Link className="btn btn-primary" to="/home">시작하기</Link>
          </div>
        </div>
      </nav>

      <header className="hero">
        <div className="glow" />
        <div className="grid" />
        <div className="badge"><span className="dot" />AI 취업 전략 플랫폼 · 2026</div>
        <h1>채용공고에 맞춰,<br /><span className="grad-text">한 줄이면</span> 됩니다.</h1>
        <p className="lead">공고만 넣으면 — 분석부터 적합도, 자소서, 모의면접, 리포트까지. AI가 처음부터 끝까지 준비합니다.</p>
        <div className="haejwo">
          <div className="haejwo-box">
            <span className="ai">
              <svg className="i" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
            </span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") runDemo(); }}
              placeholder="네이버 백엔드 신입 통째로 준비해줘"
              autoComplete="off"
            />
            <button type="button" onClick={() => runDemo()} title="실행">
              <svg className="i" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
            </button>
          </div>
          <div className="chips">
            {CHIPS.map((c) => (
              <span key={c} className="chip" onClick={() => { setQuery(c); runDemo(c); }}>{c}</span>
            ))}
          </div>
        </div>
        <div className="scroll-hint">
          <svg className="i" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M6 9l6 6 6-6" /></svg>
        </div>
      </header>

      <section className="block" id="ctl-demo">
        <div className="wrap center">
          <div className="sec-eyebrow fu">ONE LINE, FULL PREP</div>
          <h2 className="sec-title fu">말 한마디로,<br />6개의 AI가 움직입니다.</h2>
          <p className="sec-sub fu">엉성하게 던져도 챗봇이 되묻고, 두뇌가 계획을 세우고, 6개 파트가 릴레이로 실행합니다.</p>
          <div className="demo-stage fu">
            <div className="demo-query">
              <svg className="i" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
              <span>요청</span> <b>{demoQ}</b>
            </div>
            <div>
              {STEP_DEFS.map((s, idx) => (
                <div key={s.t} className={`step${states[idx] === "idle" ? "" : ` ${states[idx]}`}`}>
                  <div className="ico"><s.icon size={16} /></div>
                  <div className="txt"><div className="t">{s.t}</div><div className="d">{s.d}</div></div>
                  <div className="stat">{states[idx] === "active" ? <span className="spin" /> : states[idx] === "done" ? "완료" : "대기"}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="block" id="ctl-pipe">
        <div className="wrap center">
          <div className="sec-eyebrow fu">THE RELAY</div>
          <h2 className="sec-title fu">하나의 요청,<br />6명의 AI 릴레이.</h2>
          <p className="sec-sub fu">각 단계가 다음 단계의 입력이 됩니다. 한 곳이 멈춰도 폴백으로 끝까지 완주합니다.</p>
          <div className="pipe">
            <div className="pcard fu"><div className="num">01 · PROFILE</div><h3>프로필·스펙</h3><div className="who">이력서 요약 · 역량 추출 · 완성도</div><p>내 경력과 스펙을 AI가 구조화해 적합도 분석의 입력으로 만듭니다.</p></div>
            <div className="pcard fu"><div className="num">02 · JOB</div><h3>공고 분석</h3><div className="who">필수·우대·담당업무·기업현황</div><p>공고 원문(텍스트·PDF·이미지)을 구조화해 무엇을 요구하는지 뽑아냅니다.</p></div>
            <div className="pcard fu"><div className="num">03 · FIT</div><h3>적합도·전략</h3><div className="who">매칭 점수 · 부족역량 · 로드맵</div><p>프로필과 공고를 대조해 합격 가능성과 보완 전략을 제시합니다.<span className="tag">자체 LLM</span></p></div>
            <div className="pcard fu"><div className="num">04 · WRITE</div><h3>자소서·첨삭</h3><div className="who">자소서 초안 · 답변 첨삭</div><p>공고 맥락에 맞춰 자기소개서와 면접 답변을 다듬어 줍니다.</p></div>
            <div className="pcard fu"><div className="num">05 · INTERVIEW</div><h3>모의면접</h3><div className="who">질문·꼬리질문·평가·리포트</div><p>실제 면접관처럼 묻고, 음성·표정까지 평가해 리포트를 만듭니다.<span className="tag">자체 LLM</span></p></div>
            <div className="pcard fu"><div className="num">06 · COMMUNITY</div><h3>커뮤니티·검열</h3><div className="who">기출 추천 · 산출물 검열</div><p>실제 합격 후기와 기출을 추천하고, 산출물의 문제 표현을 걸러줍니다.<span className="tag">자체 LLM</span></p></div>
          </div>
        </div>
      </section>

      <section className="block" id="ctl-llm">
        <div className="wrap center">
          <div className="sec-eyebrow fu">BUILT, NOT BORROWED</div>
          <h2 className="sec-title fu">남의 GPT가 아니라,<br />우리가 직접 만든 모델로.</h2>
          <p className="sec-sub fu">핵심 파트는 직접 파인튜닝한 자체 LLM이 돌아갑니다. 외부 API는 안전망(폴백)일 뿐입니다.</p>
          <div className="llm-row">
            <div className="llm-card fu"><div className="big"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="4" /></svg></div><h3>적합도 엔진</h3><div className="model">careertuner-c-3b · LoRA</div><p>Qwen 기반 파인튜닝. 점수는 규칙엔진, 설명은 자체 모델이 맡는 뉴로-심볼릭 구조.</p></div>
            <div className="llm-card fu"><div className="big"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M12 2a3 3 0 0 0-3 3v1a3 3 0 0 0-2 5 3 3 0 0 0 2 5v1a3 3 0 0 0 6 0v-1a3 3 0 0 0 2-5 3 3 0 0 0-2-5V5a3 3 0 0 0-3-3z" /></svg></div><h3>면접 엔진</h3><div className="model">interview-eval · LoRA + LightGBM</div><p>답변 평가는 자체 LLM, 음성·표정 비언어 평가는 자체 LightGBM 모델로.</p></div>
            <div className="llm-card fu"><div className="big"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /></svg></div><h3>커뮤니티 엔진</h3><div className="model">gemma4 · Ollama</div><p>검열·태깅·기출 추출·추천·인테이크 챗봇까지 자체 로컬 모델로 가동.</p></div>
          </div>
          <p className="fallback fu">장애 시 폴백 <code>자체 모델 → Claude → OpenAI</code> — 무엇이 멈춰도 결과는 나옵니다.</p>
        </div>
      </section>

      <section className="block">
        <div className="wrap center">
          <div className="sec-eyebrow fu">ONE WORKSPACE</div>
          <h2 className="sec-title fu">지원 건 하나에,<br />전부 모입니다.</h2>
          <div className="feat">
            <div className="fcard fu"><div className="fi"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /></svg></div><h3>지원 건 중심</h3><p>공고가 아니라 ‘지원 건’ 단위. 기업·직무별로 분석·전략·면접 기록이 한곳에 쌓입니다.</p></div>
            <div className="fcard fu"><div className="fi"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M3 3v18h18" /><path d="M7 14l4-4 3 3 5-6" /></svg></div><h3>적합도 한눈에</h3><p>내 스펙과 공고의 매칭 점수, 부족한 역량, 학습 로드맵까지 자동으로.</p></div>
            <div className="fcard fu"><div className="fi"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="9" y="2" width="6" height="12" rx="3" /><path d="M5 10a7 7 0 0 0 14 0M12 19v3" /></svg></div><h3>진짜 같은 모의면접</h3><p>꼬리질문·압박면접·음성/영상 평가. 면접관 AI가 끝까지 진행하고 리포트를 줍니다.</p></div>
            <div className="fcard fu"><div className="fi"><svg className="i" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3a15 15 0 0 1 0 18a15 15 0 0 1 0-18z" /></svg></div><h3>집단지성 커뮤니티</h3><p>같은 회사·직무 지원자들의 실제 후기와 기출이 내 준비에 바로 연결됩니다.</p></div>
          </div>
        </div>
      </section>

      <section className="cta-block">
        <div className="glow" />
        <div className="wrap">
          <h2 className="fu">지금, 당신의 취업을<br /><span className="grad-text">튜닝</span>하세요.</h2>
          <Link className="btn btn-primary lg fu" to="/home">CareerTuner 시작하기 →</Link>
        </div>
      </section>

      <footer className="landing-footer">
        <div className="wrap">
          <div className="landing-footer-grid">
            <div className="landing-footer-brand">
              <Link className="landing-footer-logo" to="/">CareerTuner</Link>
              <p>채용공고와 사용자 근거를 지원 건 하나로 연결하는 AI 취업 전략 플랫폼</p>
              <a href="mailto:support@careertuner.dev">support@careertuner.dev</a>
              <div className="landing-footer-social">
                {FOOTER_SOCIAL_LINKS.map((social) => (
                  <Link key={social.label} to={social.href}>{social.label}</Link>
                ))}
              </div>
            </div>
            {FOOTER_SECTIONS.map((section) => (
              <section key={section.title} className="landing-footer-section">
                <h3>{section.title}</h3>
                <ul>
                  {section.links.map((item) => (
                    <li key={item.label}><Link to={item.href}>{item.label}</Link></li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
          <div className="landing-footer-bottom">© 2026 CareerTuner. 포트폴리오·공개 베타 프로젝트.</div>
        </div>
      </footer>
    </div>
  );
}
