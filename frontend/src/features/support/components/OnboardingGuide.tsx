import { useEffect, useRef, useState } from "react";
import {
  X, ArrowRight, ArrowLeft, Check, PenLine, FileText, UserRound, Briefcase,
  Link2, FileUp, ClipboardPaste, ShieldCheck, Video, Loader2, Github, Lightbulb,
  Minimize2, Maximize2, ClipboardList, CheckCircle2,
} from "lucide-react";

import { useOnboardingGuide } from "../hooks/useOnboardingGuide";
import {
  COPY, DOC_SLOTS, LINK_FIELDS, ROLES, STEP_DOTS,
  type DocSlot, type GuideStep, type LinkKey,
} from "../onboarding/guideData";

const ORCH_GLYPH = "✦";
const CUSTOM_ROLE = "__custom__";

const DOC_ICON = { FileText, UserRound, Briefcase } as const;

/** **볼드**만 안전 변환(BotBubble 과 동일 규칙). */
function rich(text: string) {
  return { __html: text.replace(/\*\*(.*?)\*\*/g, '<b class="text-foreground">$1</b>') };
}

/* ════════════════ 작은 조각 ════════════════ */
function GuideAvatar({ size = 26 }: { size?: number }) {
  return (
    <div className="rounded-full flex items-center justify-center text-white shrink-0 font-bold"
      style={{ width: size, height: size, background: "var(--gradient-orchestrator)", fontSize: size * 0.5 }}>
      {ORCH_GLYPH}
    </div>
  );
}

/** ✦ 봇 말풍선 — 각 스텝 안내 카피. */
function GuideBubble({ text }: { text: string }) {
  return (
    <div className="flex gap-2.5 items-start mb-3.5">
      <div className="mt-0.5"><GuideAvatar size={28} /></div>
      <div className="flex-1 text-[13px] leading-[1.65] text-foreground"
        dangerouslySetInnerHTML={rich(text)} />
    </div>
  );
}

function Chip({ label, selected, onClick, dashed, icon }: {
  label: string; selected?: boolean; onClick: () => void; dashed?: boolean; icon?: React.ReactNode;
}) {
  return (
    <button onClick={onClick}
      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full text-[12.5px] font-semibold transition-colors"
      style={
        selected
          ? { background: "var(--orch-violet)", border: "1.5px solid var(--orch-violet)", color: "#fff" }
          : {
              background: "var(--card)",
              border: dashed ? "1px dashed var(--orch-point)" : "1px solid var(--border)",
              color: dashed ? "var(--orch-violet)" : "var(--muted-foreground)",
            }
      }>
      {selected && <Check size={13} />}
      {icon}
      {label}
    </button>
  );
}

/** 진행 점(1..6). analyzing 은 fit 위치로. dots 로 인테이크 서브셋(빈 슬롯만큼)도 그린다. */
function StepDots({ step, dots = STEP_DOTS }: { step: GuideStep; dots?: GuideStep[] }) {
  const active = step === "analyzing" ? "fit" : step;
  const idx = dots.indexOf(active as GuideStep);
  return (
    <div className="flex items-center gap-1.5">
      {dots.map((s, i) => (
        <span key={s} className="h-1.5 rounded-full transition-all"
          style={{
            width: i === idx ? 16 : 6,
            background: i <= idx ? "var(--orch-violet)" : "var(--border)",
          }} />
      ))}
    </div>
  );
}

/* ════════════════ 첨부 칩 ════════════════ */
function FileChip({ name, uploading, error, onRemove }: {
  name: string; uploading: boolean; error?: boolean; onRemove: () => void;
}) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-[11.5px] max-w-full"
      style={{
        borderColor: error ? "var(--destructive)" : "var(--border)",
        background: "var(--card)",
        color: error ? "var(--destructive)" : "var(--foreground)",
      }}>
      {uploading ? <Loader2 size={12} className="animate-spin shrink-0" />
        : error ? <X size={12} className="shrink-0" />
        : <Check size={12} className="shrink-0" style={{ color: "var(--orch-violet)" }} />}
      <span className="truncate">{name}</span>
      <button onClick={onRemove} aria-label="첨부 제거" className="text-muted-foreground hover:text-foreground shrink-0">
        <X size={12} />
      </button>
    </span>
  );
}

/** ④ 온보딩 매핑 모드에서 서버가 이끄는 스텝 순서(docs 는 로컬 스텝 — ④ 프로토콜 밖, 파일은 ready 병합). */
const SERVER_ORDER: GuideStep[] = ["role", "skills", "docs", "jd"];

/** ④ 온보딩 매핑 모드의 서버 국면 — 라우트("④온보딩:직무" 등)에서 호출부가 파생해 내려준다. */
export type ServerGuidePhase = "role" | "skills" | "jd" | "waiting";

/* ════════════════ 본체 ════════════════ */
export function OnboardingGuide({ onClose, onGotoInterview, wide, onCollapse, onExpand, intake, onSlotFilled, server }: {
  onClose: () => void;
  /** 면접 권유 CTA — 수집한 caseId 를 실어 D 면접 페이지로 인계(없으면 null). */
  onGotoInterview: (caseId: number | null) => void;
  /** 플로팅(넓은 화면)이면 스텝+명세 보드 2단 레이아웃. */
  wide?: boolean;
  /** ⤡ 코너로 최소화(가이드 상태 유지). */
  onCollapse?: () => void;
  /** ⤢ 코너에서 다시 플로팅으로. */
  onExpand?: () => void;
  /**
   * ③ 인테이크 CASE 되묻기 매핑 모드 — 지정한 스텝(빈 슬롯만큼)만 밟고, 마지막 스텝(jd)에서
   * 지원 건을 만들어 onSlotFilled 로 돌려준다. 적합도/면접 스텝은 없음(이후 진행은 인테이크가 이어감).
   */
  intake?: { steps: GuideStep[] };
  /** intake 모드에서 공고→지원 건 생성 완료 시 — 호출부가 기존 인테이크 프로토콜(selectedCaseId)로 회신한다. */
  onSlotFilled?: (r: { caseId: number; coverLetterFileIds: number[] }) => void;
  /**
   * ④ 깡통 온보딩(백엔드 텍스트 프로토콜) 매핑 모드 — 표시 스텝을 서버 국면(라우트)이 이끈다.
   * role/skills/jd 제출은 onSubmit 으로 기존 텍스트 답변 형식 그대로 회신하고(직무 텍스트→기술 CSV→공고 본문),
   * docs 는 로컬 스텝(자소서 fileId 는 ready 시 run 요청에 병합). waiting 은 공고 추출 대기 화면.
   */
  server?: {
    phase: ServerGuidePhase;
    /** 현재 국면의 봇 문장(백엔드 카피 그대로 표시 — 카피 이원화 방지). */
    bubbleText?: string;
    /** 회신 전송 중(봇 thinking) — 버튼 비활성+스피너. */
    submitting: boolean;
    /** caseId: 공고 "파일" 경로 — 가이드가 B 업로드로 지원 건을 먼저 만들고 id 를 실어 보낸다(④가 입양). */
    onSubmit: (step: "role" | "skills" | "jd", text: string,
               meta: { coverLetterFileIds: number[]; caseId?: number }) => void;
  };
}) {
  const g = useOnboardingGuide(server ? "role" : intake?.steps[0] ?? "role");
  const order = server ? SERVER_ORDER : intake?.steps ?? ORDER;

  // ── ④ 서버 국면 → 로컬 스텝 동기화. jd 국면 "첫" 진입은 docs(로컬)부터 밟는다.
  //    g.step 은 의도적으로 deps 제외 — 사용자의 docs→jd 로컬 이동을 국면 재실행으로 되돌리지 않기 위해.
  useEffect(() => {
    if (!server) return;
    if (server.phase === "role" || server.phase === "skills") {
      g.go(server.phase);
    } else if (server.phase === "jd" && g.step !== "docs" && g.step !== "jd") {
      g.go("docs");
    }
    // waiting 은 스텝 이동 없음 — 대기 화면이 본문을 대체한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [server?.phase]);

  /**
   * ④ 회신 — 자소서 fileId 를 함께 실어 호출부가 ready 병합을 예약할 수 있게 한다.
   * jd 에 파일/URL 이 있으면(붙여넣기 텍스트가 없을 때) 텍스트 프로토콜 대신 B 생성 API 로 지원 건을
   * 먼저 만들고(ensureCase) caseId 를 실어 보낸다 — 백엔드 AWAIT_POSTING 이 입양해 같은 추출 폴링으로 합류.
   */
  const submitServer = async (step: "role" | "skills" | "jd", text: string) => {
    if (!server) return;
    const meta = { coverLetterFileIds: g.collect().coverLetterFileIds };
    // ensureCase 우선순위(파일>텍스트>URL)와 정합: 텍스트가 있으면 기존 텍스트 프로토콜이 처리하므로
    // 파일 또는 (텍스트 없이) URL 만 있을 때 케이스 선생성 경로를 탄다.
    const viaCase = step === "jd" && (g.jd.file || (!text && g.jd.url.trim()));
    if (viaCase) {
      setSubmitting(true);
      setSubmitError(null);
      try {
        const caseId = await g.ensureCase();
        if (caseId != null) {
          const label = g.jd.file ? `공고 파일(${g.jd.file.name})을 올렸어요` : "공고 링크로 올렸어요";
          server.onSubmit("jd", label, { ...meta, caseId });
          return;
        }
      } catch (e) {
        setSubmitError(e instanceof Error ? e.message : "공고 등록에 실패했어요. 잠시 후 다시 시도해 주세요.");
        return;
      } finally {
        setSubmitting(false);
      }
    }
    server.onSubmit(step, text, meta);
  };

  // intake 제출(케이스 생성) 진행/오류 — 가이드 자체 실행(runReal)과 분리된 상태.
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const submitIntake = async () => {
    if (!onSlotFilled || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const caseId = await g.ensureCase();
      if (caseId == null) {
        setSubmitError("공고 파일이나 내용을 넣어야 지원 건을 만들 수 있어요.");
        return;
      }
      onSlotFilled({ caseId, coverLetterFileIds: g.collect().coverLetterFileIds });
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : "지원 건 생성에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  };

  // 스텝별 파일 input (자소서/이력서/포폴 + 공고).
  const fileRefs = {
    cover: useRef<HTMLInputElement>(null),
    resume: useRef<HTMLInputElement>(null),
    portfolio: useRef<HTMLInputElement>(null),
    jd: useRef<HTMLInputElement>(null),
  };

  const closeAll = () => { g.reset(); onClose(); };

  return (
    <div className="absolute inset-0 z-20 flex flex-col bg-card">
      {/* ── Header: 진행 점 + 닫기 ── */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border"
        style={{ background: "var(--orch-header-tint)" }}>
        <GuideAvatar size={30} />
        <div className="flex-1 min-w-0">
          <div className="text-[13px] font-extrabold leading-tight">
            {intake || server ? "면접 준비 · 부족한 정보만 채워요" : "대화로 준비 시작"}
          </div>
          <div className="mt-1.5"><StepDots step={g.step} dots={intake || server ? order : STEP_DOTS} /></div>
        </div>
        {wide && onCollapse ? (
          <button onClick={onCollapse} aria-label="코너로 최소화"
            className="w-7 h-7 rounded-lg flex items-center justify-center text-muted-foreground hover:bg-secondary transition-colors shrink-0">
            <Minimize2 size={15} />
          </button>
        ) : !wide && onExpand ? (
          <button onClick={onExpand} aria-label="크게 펼치기"
            className="w-7 h-7 rounded-lg flex items-center justify-center text-muted-foreground hover:bg-secondary transition-colors shrink-0">
            <Maximize2 size={15} />
          </button>
        ) : null}
        <button onClick={closeAll} aria-label="가이드 닫기"
          className="w-7 h-7 rounded-lg flex items-center justify-center text-muted-foreground hover:bg-secondary transition-colors shrink-0">
          <X size={16} />
        </button>
      </div>

      {/* ── Body ── (플로팅: 스텝 컬럼 + 준비 명세 보드 나란히) */}
      <div className="flex-1 flex min-h-0">
        <div className="flex-1 overflow-y-auto px-4 py-4 min-w-0" style={{ background: "var(--orch-chat-bg)" }}>
          <div className={wide ? "mx-auto w-full max-w-[560px]" : ""}>
            {server?.phase === "waiting" ? (
              <ServerWaitingView text={server.bubbleText} />
            ) : (
              <>
                {g.step === "role" && <RoleStep g={g} bubble={server?.bubbleText} />}
                {g.step === "skills" && <SkillsStep g={g} bubble={server?.bubbleText} />}
                {g.step === "docs" && <DocsStep g={g} fileRefs={fileRefs} />}
                {g.step === "jd" && (
                  <JdStep g={g} jdRef={fileRefs.jd} bubble={server?.bubbleText} serverMode={!!server} />
                )}
                {g.step === "analyzing" && <AnalyzingStep />}
                {g.step === "fit" && <FitStep g={g} />}
                {g.step === "interview" && <InterviewStep g={g} onGoto={() => onGotoInterview(g.caseId)} onDone={closeAll} />}
              </>
            )}
          </div>
        </div>
        {wide && <SummaryBoard g={g} />}
      </div>

      {/* ── Footer: 뒤로 · 건너뛰기 · 다음 (intake=지원 건 생성 제출 / server=④ 텍스트 회신) ── */}
      {server?.phase === "waiting" ? null : (
        <GuideFooter g={g} onClose={closeAll} order={order} intakeMode={!!intake}
          serverMode={!!server} serverSubmitting={server?.submitting} onServerSubmit={submitServer}
          submitting={submitting} submitError={submitError} onSubmitIntake={() => void submitIntake()} />
      )}
    </div>
  );
}

/* ── ④ 공고 추출 대기 화면(서버 국면 waiting) — 봇 문장을 그대로 보여주고 폴링은 호출부가 돈다. ── */
function ServerWaitingView({ text }: { text?: string }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-10">
      <Loader2 size={32} className="animate-spin mb-4" style={{ color: "var(--orch-violet)" }} />
      <div className="text-[12.5px] leading-[1.6] text-foreground max-w-[320px]"
        dangerouslySetInnerHTML={rich(text ?? "공고에서 회사·직무를 읽고 있어요. 몇 초면 돼요.")} />
    </div>
  );
}

type G = ReturnType<typeof useOnboardingGuide>;
type FileRefs = {
  cover: React.RefObject<HTMLInputElement>;
  resume: React.RefObject<HTMLInputElement>;
  portfolio: React.RefObject<HTMLInputElement>;
  jd: React.RefObject<HTMLInputElement>;
};

/* ── STEP 1: 직군 ── */
function RoleStep({ g, bubble }: { g: G; bubble?: string }) {
  const custom = g.role === CUSTOM_ROLE;
  return (
    <>
      <GuideBubble text={bubble ?? COPY.role} />
      <div className="text-[10.5px] font-extrabold text-muted-foreground mb-2 tracking-wide">직군 · 분야</div>
      <div className="flex flex-wrap gap-1.5">
        {ROLES.map((r) => (
          <Chip key={r} label={r} selected={g.role === r} onClick={() => g.setRole(r)} />
        ))}
        <Chip label="직접 입력" dashed selected={custom} icon={<PenLine size={13} />}
          onClick={() => g.setRole(CUSTOM_ROLE)} />
      </div>
      {custom && (
        <input autoFocus value={g.customRole} onChange={(e) => g.setCustomRole(e.target.value)}
          placeholder="예: 바이오·임상, 통번역 …"
          className="mt-3 w-full rounded-lg border border-border bg-background px-3 py-2.5 text-[13px] outline-none focus:border-primary" />
      )}
      <p className="mt-3 text-[11.5px] leading-[1.5] text-muted-foreground">{COPY.roleHint}</p>
    </>
  );
}

/* ── STEP 2: 핵심 역량(직군 적응형) ── */
function SkillsStep({ g, bubble }: { g: G; bubble?: string }) {
  const roleLabel = g.role === CUSTOM_ROLE ? g.customRole || "선택하신 직군" : g.role ?? "선택하신 직군";
  return (
    <>
      <GuideBubble text={bubble ?? COPY.skills(roleLabel)} />
      <div className="flex flex-wrap gap-1.5">
        {g.field.skills.map((s) => (
          <Chip key={s} label={s} selected={g.skills.includes(s)} onClick={() => g.toggleSkill(s)} />
        ))}
      </div>
      <div className="mt-3 flex items-start gap-2 rounded-[10px] px-3 py-2.5"
        style={{ background: "var(--orch-surface)", border: "1px solid var(--orch-point)" }}>
        <Lightbulb size={14} className="shrink-0 mt-0.5" style={{ color: "var(--orch-violet)" }} />
        <div className="text-[11.5px] leading-[1.5] text-muted-foreground">
          <b className="text-foreground">직군별 자동 전환</b> — 고르신 분야에 맞춰 역량·질문 언어가 바뀌어요.
        </div>
      </div>
    </>
  );
}

/* ── STEP 3: 서류 + 링크 ── */
function DocsStep({ g, fileRefs }: { g: G; fileRefs: FileRefs }) {
  return (
    <>
      <GuideBubble text={COPY.docs} />
      <div className="flex flex-col gap-2">
        {DOC_SLOTS.map((slot) => (
          <DocRow key={slot.key} slot={slot} g={g} inputRef={fileRefs[slot.key]} />
        ))}
      </div>

      {/* 직군별 링크 필드(분석에 넣음 vs 저장만 구분) */}
      {g.field.links.length > 0 && (
        <div className="mt-4">
          <div className="text-[10.5px] font-extrabold text-muted-foreground mb-2 tracking-wide">링크 · 포트폴리오</div>
          <div className="flex flex-col gap-2.5">
            {g.field.links.map((key) => (
              <LinkFieldRow key={key} lkey={key} g={g} />
            ))}
          </div>
        </div>
      )}
    </>
  );
}

function DocRow({ slot, g, inputRef }: { slot: DocSlot; g: G; inputRef: React.RefObject<HTMLInputElement> }) {
  const Icon = DOC_ICON[slot.icon];
  const mine = g.docs.filter((d) => d.slot === slot.key);
  return (
    <div>
      <div className="flex items-center gap-2.5 rounded-xl border border-border px-3 py-2.5"
        style={slot.key === "cover" ? { borderStyle: "dashed", borderColor: "var(--orch-point)", background: "var(--orch-surface)" } : undefined}>
        <span className="w-8 h-8 rounded-[9px] flex items-center justify-center shrink-0"
          style={{ background: "var(--orch-surface)", color: "var(--orch-violet)" }}>
          <Icon size={16} />
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-[12.5px] font-bold truncate">{slot.label}</div>
          <div className="text-[10.5px] text-muted-foreground truncate">{slot.desc}</div>
        </div>
        <button onClick={() => inputRef.current?.click()}
          className="text-[11.5px] font-bold shrink-0" style={{ color: "var(--orch-violet)" }}>
          올리기
        </button>
      </div>
      {mine.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {mine.map((d, i) => (
            <FileChip key={i} name={d.file.name} uploading={d.uploading} error={d.error}
              onRemove={() => g.removeDoc(d)} />
          ))}
        </div>
      )}
      <input ref={inputRef} type="file" className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) void g.addDoc(slot.key, slot.kind, f);
          e.target.value = "";
        }} />
    </div>
  );
}

function LinkFieldRow({ lkey, g }: { lkey: LinkKey; g: G }) {
  const meta = LINK_FIELDS[lkey];
  const Icon = lkey === "github" ? Github : lkey === "blog" ? ClipboardPaste : Link2;
  const value = g.links[lkey] ?? "";
  return (
    <div>
      <div className="flex items-center gap-1.5 text-[11px] font-bold text-foreground mb-1">
        <Icon size={13} style={{ color: "var(--orch-violet)" }} />
        {meta.label}
      </div>
      {meta.mode === "paste" ? (
        <textarea value={value} onChange={(e) => g.setLink(lkey, e.target.value)} rows={2}
          placeholder={meta.placeholder}
          className="w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-[12.5px] outline-none focus:border-primary placeholder:text-muted-foreground" />
      ) : (
        <input value={value} onChange={(e) => g.setLink(lkey, e.target.value)}
          placeholder={meta.placeholder}
          className="w-full rounded-lg border border-border bg-background px-3 py-2 text-[12.5px] outline-none focus:border-primary placeholder:text-muted-foreground" />
      )}
      <div className="mt-1 text-[10.5px] leading-[1.45] text-muted-foreground">{meta.hint}</div>
    </div>
  );
}

/* ── STEP 4: 공고문 ── */
function JdStep({ g, jdRef, bubble, serverMode }: {
  g: G; jdRef: React.RefObject<HTMLInputElement>;
  bubble?: string;
  /** ④ 온보딩 매핑 모드 — 붙여넣기 영역을 크게(입력 방식 자체는 링크·파일·붙여넣기 모두 동일 지원). */
  serverMode?: boolean;
}) {
  return (
    <>
      <GuideBubble text={bubble ?? COPY.jd} />
      <div className="flex items-center gap-2 rounded-xl border border-border px-3 py-2.5 mb-2.5">
        <Link2 size={15} className="shrink-0" style={{ color: "var(--orch-violet)" }} />
        <input value={g.jd.url} onChange={(e) => g.setJdUrl(e.target.value)}
          placeholder="공고 링크 붙여넣기 — saramin.co.kr, jobkorea.co.kr …"
          className="flex-1 min-w-0 bg-transparent text-[12.5px] outline-none placeholder:text-muted-foreground" />
      </div>
      <div className="flex gap-2 mb-2.5">
        <button onClick={() => jdRef.current?.click()}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full border border-border bg-card text-[11.5px] font-semibold text-muted-foreground hover:text-foreground transition-colors">
          <FileUp size={13} /> 파일 업로드
        </button>
        <span className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full border border-border bg-card text-[11.5px] font-semibold text-muted-foreground">
          <ClipboardPaste size={13} /> 텍스트 붙여넣기 ↓
        </span>
      </div>
      <textarea value={g.jd.text} onChange={(e) => g.setJdText(e.target.value)} rows={serverMode ? 6 : 4}
        placeholder={serverMode ? "공고 전문을 그대로 붙여넣어 주세요 — 회사명·직무·자격요건이 담긴 원문이면 좋아요." : "공고 내용을 그대로 붙여넣어도 돼요."}
        className="w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-[12.5px] outline-none focus:border-primary placeholder:text-muted-foreground" />
      {g.jd.file && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {/* 공고 파일은 실행 시 지원 건으로 만든다(여기선 선택만) */}
          <FileChip name={g.jd.file.name} uploading={false} onRemove={g.removeJdFile} />
        </div>
      )}
      {/* 케이스 생성 업로드가 PDF/IMAGE sourceType 만 받으므로 선택 단계에서 제한. */}
      <input ref={jdRef} type="file" className="hidden" accept="application/pdf,image/*"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) void g.addJdFile(f);
          e.target.value = "";
        }} />
    </>
  );
}

/* ── (목) 적합도 계산 중 ── */
function AnalyzingStep() {
  return (
    <div className="flex flex-col items-center justify-center text-center py-10">
      <Loader2 size={32} className="animate-spin mb-4" style={{ color: "var(--orch-violet)" }} />
      <div className="flex flex-col gap-1.5">
        {COPY.analyzing.map((line, i) => (
          <div key={i} className="text-[12.5px] leading-[1.5]"
            style={{ color: i === 0 ? "var(--foreground)" : "var(--muted-foreground)", opacity: i === 0 ? 1 : 0.7 }}>
            {line}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ── STEP 5: 적합도 결과(목) ── */
function FitStep({ g }: { g: G }) {
  const fit = g.fit;
  if (!fit) return null;

  const hasScore = fit.fitScore != null; // FIT 이 실제로 돈 경우에만 점수 표시(지어내지 않음)
  const hasChips = fit.strengths.length > 0 || fit.gaps.length > 0;

  // 실행 오류 → 정직하게 표시.
  if (g.runError && !hasScore && !hasChips && !fit.written) {
    return (
      <>
        <GuideBubble text={COPY.fitEmpty} />
        <div className="rounded-xl border border-border px-4 py-3 text-[12px] text-muted-foreground">
          {g.runError}
        </div>
      </>
    );
  }

  return (
    <>
      <GuideBubble text={hasScore ? COPY.fitLead : fit.written ? COPY.fitPending : COPY.fitEmpty} />

      {/* 점수는 FIT 이 실제로 났을 때만. 없으면 링/숫자 자체를 그리지 않음. */}
      {(hasScore || hasChips) && (
        <div className="rounded-2xl border border-border overflow-hidden mb-2.5">
          {hasScore && (
            <div className="flex items-center gap-3.5 px-4 py-3.5" style={{ background: "var(--orch-surface)" }}>
              <div className="relative w-14 h-14 rounded-full flex items-center justify-center shrink-0"
                style={{ background: `conic-gradient(var(--orch-violet) 0turn ${fit.fitScore! / 100}turn, var(--border) ${fit.fitScore! / 100}turn 1turn)` }}>
                <div className="w-11 h-11 rounded-full bg-card flex items-center justify-center text-[15px] font-extrabold"
                  style={{ color: "var(--orch-violet)" }}>
                  {fit.fitScore}
                </div>
              </div>
              <div className="min-w-0">
                <div className="text-[13px] font-extrabold truncate">공고 요건 대비 적합도</div>
                <div className="text-[11px] text-muted-foreground mt-0.5">강점과 보완점을 정리했어요</div>
              </div>
            </div>
          )}
          {hasChips && (
            <div className="px-4 py-3.5 flex flex-col gap-3">
              {fit.strengths.length > 0 && (
                <div>
                  <div className="text-[10.5px] font-extrabold text-green-600 mb-1.5">강점</div>
                  <div className="flex flex-wrap gap-1.5">
                    {fit.strengths.map((s) => (
                      <span key={s} className="px-2.5 py-1 rounded-full text-[11.5px] font-bold bg-green-50 dark:bg-green-500/15 text-green-700 dark:text-green-400">
                        {s}
                      </span>
                    ))}
                  </div>
                </div>
              )}
              {fit.gaps.length > 0 && (
                <div>
                  <div className="text-[10.5px] font-extrabold text-amber-600 mb-1.5">보완점</div>
                  <div className="flex flex-wrap gap-1.5">
                    {fit.gaps.map((s) => (
                      <span key={s} className="px-2.5 py-1 rounded-full text-[11.5px] font-bold border border-amber-200 dark:border-amber-500/30 bg-amber-50 dark:bg-amber-500/15 text-amber-700 dark:text-amber-400">
                        {s}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* 자소서 교정 결과(실제 WRITE) */}
      {fit.written && (
        <div className="flex items-center gap-2 rounded-xl px-3.5 py-3 mb-2.5"
          style={{ background: "var(--orch-surface)", border: "1px solid var(--orch-point)" }}>
          <CheckCircle2 size={16} className="shrink-0 text-green-600" />
          <span className="text-[12px] leading-[1.5]" dangerouslySetInnerHTML={rich(COPY.writeDone)} />
        </div>
      )}

      {/* 케이스는 있으나 FIT 대기(비동기 추출) → 지원 건에서 확인 링크 */}
      {fit.fitPending && g.caseId && (
        <div className="text-[11.5px] leading-[1.5] text-muted-foreground">
          공고 분석이 끝나면 <b className="text-foreground">지원 건</b>에서 적합도를 볼 수 있어요.
        </div>
      )}
    </>
  );
}

/* ── STEP 6: 면접 권유 ── */
function InterviewStep({ g, onGoto, onDone }: { g: G; onGoto: () => void; onDone: () => void }) {
  void g;
  return (
    <>
      <GuideBubble text={COPY.interview} />
      <div className="rounded-2xl border px-4 py-4 text-center"
        style={{ borderColor: "var(--orch-point)", background: "var(--orch-surface)" }}>
        <div className="w-12 h-12 rounded-[13px] flex items-center justify-center mx-auto mb-3 text-white"
          style={{ background: "var(--gradient-orchestrator)" }}>
          <Video size={22} />
        </div>
        <div className="text-[14px] font-extrabold mb-1.5">모의 면접으로 마무리해요</div>
        <div className="text-[12px] leading-[1.6] text-muted-foreground mb-3.5">{COPY.interviewWarm}</div>
        <div className="flex items-center gap-1.5 justify-center text-[11px] text-muted-foreground mb-3.5">
          <ShieldCheck size={13} style={{ color: "var(--orch-violet)" }} />
          카메라·마이크는 면접 화면에서 안내해 드려요.
        </div>
        <button onClick={onGoto}
          className="w-full h-11 rounded-xl text-white text-[13.5px] font-extrabold transition-transform hover:brightness-110"
          style={{ background: "var(--gradient-orchestrator)" }}>
          면접 보러가기
        </button>
        <button onClick={onDone}
          className="mt-2 text-[11.5px] font-semibold text-muted-foreground hover:text-foreground transition-colors">
          나중에 할게요 · 준비 내용은 저장돼요
        </button>
      </div>
    </>
  );
}

/* ════════════════ 준비 명세 보드(플로팅 우측) ════════════════ */
// 대화에서 모은 값을 한눈에 — 목업(OrchestratorMorph) 슬롯 보드의 위젯 버전.
function SummaryRow({ label, value, filled, icon }: {
  label: string; value: string; filled: boolean; icon: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-2.5 px-3 py-2.5 rounded-xl"
      style={filled
        ? { background: "var(--orch-surface)", border: "1px solid var(--orch-point)" }
        : { background: "var(--secondary)", border: "1px solid var(--border)" }}>
      <span className="w-7 h-7 rounded-[8px] flex items-center justify-center shrink-0"
        style={{ background: "var(--card)", color: filled ? "var(--orch-violet)" : "var(--muted-foreground)" }}>
        {icon}
      </span>
      <div className="flex-1 min-w-0">
        <div className="text-[10px] font-semibold text-muted-foreground">{label}</div>
        <div className="text-[12.5px] font-bold truncate" style={{ color: filled ? "var(--foreground)" : "var(--muted-foreground)" }}>
          {value}
        </div>
      </div>
      {filled && <CheckCircle2 size={16} className="shrink-0 text-green-600" />}
    </div>
  );
}

function SummaryBoard({ g }: { g: G }) {
  const roleVal = g.role === CUSTOM_ROLE ? (g.customRole.trim() || "직접 입력") : g.role;
  const docCount = g.docs.filter((d) => d.id != null).length;
  const linkCount = Object.values(g.links).filter((v) => (v ?? "").trim().length > 0).length;
  const jdFilled = !!g.jd.file || g.jd.url.trim().length > 0 || g.jd.text.trim().length > 0;
  const jdVal = g.jd.file?.name || (g.jd.url.trim() ? "링크 첨부" : g.jd.text.trim() ? "붙여넣기" : "첨부 안 함");

  return (
    <div className="w-[300px] flex-none border-l border-border flex flex-col bg-card">
      <div className="px-4 pt-4 pb-2">
        <div className="flex items-center gap-2">
          <ClipboardList size={15} style={{ color: "var(--orch-violet)" }} />
          <span className="text-[13px] font-extrabold">준비 명세</span>
        </div>
        <div className="text-[11px] text-muted-foreground mt-0.5">대화에서 모은 내용</div>
      </div>
      <div className="px-3 pb-3 flex flex-col gap-2 overflow-y-auto">
        <SummaryRow label="직군" value={roleVal ?? "미선택"} filled={!!roleVal} icon={<PenLine size={14} />} />
        <SummaryRow label="핵심 역량" value={g.skills.length ? `${g.skills.length}개 선택` : "미선택"}
          filled={g.skills.length > 0} icon={<Check size={14} />} />
        <SummaryRow label="서류" value={docCount ? `${docCount}개 첨부` : "첨부 안 함"}
          filled={docCount > 0} icon={<FileText size={14} />} />
        <SummaryRow label="공고" value={jdVal} filled={jdFilled} icon={<Briefcase size={14} />} />
        {g.field.links.length > 0 && (
          <SummaryRow label="링크" value={linkCount ? `${linkCount}개 입력` : "없음"}
            filled={linkCount > 0} icon={<Link2 size={14} />} />
        )}
      </div>
    </div>
  );
}

/* ════════════════ Footer 내비 ════════════════ */
const ORDER: GuideStep[] = ["role", "skills", "docs", "jd", "fit", "interview"];

function GuideFooter({ g, onClose, order, intakeMode, serverMode, serverSubmitting, onServerSubmit,
  submitting, submitError, onSubmitIntake }: {
  g: G; onClose: () => void;
  /** 진행 순서 — 기본 전체 흐름 또는 인테이크/온보딩 서브셋(빈 슬롯만큼). */
  order: GuideStep[];
  /** ③ 인테이크 매핑 모드: 마지막 스텝(jd)에서 오케 실행 대신 지원 건 생성 제출. */
  intakeMode?: boolean;
  /** ④ 온보딩 매핑 모드: role/skills/jd 를 서버 텍스트 프로토콜로 회신(뒤로 없음 — 서버 상태가 전진만 한다). */
  serverMode?: boolean;
  serverSubmitting?: boolean;
  onServerSubmit?: (step: "role" | "skills" | "jd", text: string) => void;
  submitting?: boolean;
  submitError?: string | null;
  onSubmitIntake?: () => void;
}) {
  // analyzing 은 자동 전이 — 하단 내비 숨김.
  if (g.step === "analyzing") return null;

  const idx = order.indexOf(g.step);
  const isFirst = idx <= 0;
  const canBack = !isFirst && !serverMode; // ④는 서버 단계가 전진만 하므로 뒤로 없음
  const isLastIntake = intakeMode && idx === order.length - 1;
  const busy = !!submitting || !!serverSubmitting;

  const back = () => {
    // fit 에서 뒤로는 jd 로(analyzing 건너뜀).
    const prev = order[Math.max(0, idx - 1)];
    g.go(prev);
  };

  const advance = () => {
    if (serverMode) {
      if (g.step === "docs") { g.go("jd"); return; } // docs 는 로컬 스텝 — 서버 회신 없음
      const roleText = g.role === CUSTOM_ROLE ? g.customRole.trim() : g.role ?? "";
      if (g.step === "role") { onServerSubmit?.("role", roleText); return; }
      if (g.step === "skills") { onServerSubmit?.("skills", g.skills.join(", ")); return; }
      if (g.step === "jd") { onServerSubmit?.("jd", g.jd.text.trim()); return; }
      return;
    }
    if (isLastIntake) { onSubmitIntake?.(); return; } // 공고 → 케이스 생성 → 인테이크 회신(오케 실행은 인테이크가)
    if (!intakeMode && g.step === "jd") { void g.runReal(); return; } // 공고 → 케이스 생성 + 실제 오케 SSE
    if (g.step === "fit") { g.go("interview"); return; }
    if (g.step === "interview") { onClose(); return; }
    g.go(order[Math.min(order.length - 1, idx + 1)]);
  };

  // 다음 버튼 라벨/활성 조건.
  const nextLabel =
    isLastIntake || (serverMode && g.step === "jd") ? "이 공고로 준비 시작"
    : g.step === "role" ? "다음"
    : g.step === "skills" ? "다음"
    : g.step === "docs" ? "다음"
    : g.step === "jd" ? "적합도 보기"
    : g.step === "fit" ? "면접 권유 보기"
    : "완료";

  // intake 마지막 스텝(jd)은 공고 입력(파일/붙여넣기/URL)이 있어야 지원 건을 만들 수 있다 — 빈손 제출 금지.
  const jdReady = !!g.jd.file || g.jd.text.trim().length > 0 || g.jd.url.trim().length > 0 || g.caseId != null;
  const roleReady = g.role != null && (g.role !== CUSTOM_ROLE || g.customRole.trim().length > 0);
  const nextEnabled = serverMode
    ? !busy && (
        g.step === "role" ? roleReady
        : g.step === "skills" ? g.skills.length > 0        // ④는 답을 그대로 프로필에 저장 — 빈 답 전송 금지
        // 파일/URL 은 B 생성 경로, 붙여넣기는 텍스트 프로토콜(백엔드 최소 공고 길이 20자와 동일 게이트).
        : g.step === "jd" ? (!!g.jd.file || g.jd.url.trim().length > 0 || g.jd.text.trim().length >= 20)
        : true)
    : g.step === "role" ? roleReady
    : isLastIntake ? (jdReady && !submitting)
    : true; // 나머지는 스킵 가능(빈손 진행)

  // ④ 스텝별 좌측 안내(뒤로 버튼 대신) — 비활성 이유를 사람이 읽게.
  const serverHint =
    g.step === "role" ? "고르신 분야에 맞춰 다음 질문이 바뀌어요"
    : g.step === "skills" ? "1개 이상 골라주세요 — 면접 질문·자소서 방향에 쓰여요"
    : g.step === "docs" ? "없으면 바로 다음으로 넘어가도 돼요"
    : "공고 링크·파일·본문 붙여넣기 중 하나면 돼요";

  // interview 스텝은 카드 안 CTA 로 진행 → footer 다음 버튼 숨김.
  if (g.step === "interview") {
    return (
      <div className="px-4 py-3 border-t border-border flex items-center">
        <button onClick={back}
          className="inline-flex items-center gap-1 text-[12px] font-semibold text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft size={14} /> 뒤로
        </button>
      </div>
    );
  }

  return (
    <div className="px-4 py-3 border-t border-border flex flex-col gap-2">
      {/* intake/온보딩 제출 오류 — 지어내지 않고 이유를 그대로. */}
      {submitError && (
        <div className="text-[11px] leading-[1.5]" style={{ color: "var(--destructive)" }}>
          {submitError}
        </div>
      )}
      <div className="flex items-center gap-2">
        {canBack ? (
          <button onClick={back}
            className="inline-flex items-center gap-1 text-[12px] font-semibold text-muted-foreground hover:text-foreground transition-colors">
            <ArrowLeft size={14} /> 뒤로
          </button>
        ) : (
          <span className="text-[11px] text-muted-foreground">
            {serverMode ? serverHint
              : isLastIntake ? "공고만 넣으면 바로 시작할 수 있어요"
              : "건너뛰기 가능 · 없으면 나중에"}
          </span>
        )}
        <div className="ml-auto flex items-center gap-2">
          {g.step !== "role" && g.step !== "fit" && !isLastIntake && !serverMode && (
            <button onClick={advance}
              className="text-[12px] font-semibold text-muted-foreground hover:text-foreground transition-colors">
              건너뛰기
            </button>
          )}
          <button onClick={advance} disabled={!nextEnabled}
            className="inline-flex items-center gap-1 h-9 px-4 rounded-lg text-white text-[12.5px] font-bold transition-all disabled:opacity-40"
            style={{ background: "var(--gradient-orchestrator)" }}>
            {busy && <Loader2 size={14} className="animate-spin" />}
            {nextLabel}
            {!busy && <ArrowRight size={14} />}
          </button>
        </div>
      </div>
    </div>
  );
}
