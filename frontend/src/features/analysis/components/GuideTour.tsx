import { useEffect, useState, useCallback } from "react";
import { createPortal } from "react-dom";
import { Compass, X, ArrowLeft, ArrowRight } from "lucide-react";

// C 영역 페이지 안내(가이드 투어). 강제가 아니라 '페이지 안내' 버튼을 눌러야 시작하고,
// ESC·끝내기·건너뛰기로 언제든 중단할 수 있다. 외부 의존성 없이 스포트라이트 1엘리먼트 방식
// (box-shadow 로 주변만 어둡게)으로 구현. C 화면에서만 사용하는 자체완결 컴포넌트.

export type TourStep = {
  /** 강조할 요소 CSS 선택자. data-tour 속성 사용 권장. 없으면 화면 중앙 안내. */
  selector?: string;
  title: string;
  body: string;
};

const PAD = 8;

function GuideTour({ steps, onClose }: { steps: TourStep[]; onClose: () => void }) {
  const [i, setI] = useState(0);
  const [rect, setRect] = useState<DOMRect | null>(null);
  const step = steps[i];
  const last = i === steps.length - 1;

  const next = useCallback(() => (last ? onClose() : setI((v) => v + 1)), [last, onClose]);
  const prev = useCallback(() => setI((v) => Math.max(0, v - 1)), []);

  // 타깃 위치 측정 + 스크롤 + 리사이즈/스크롤 추적
  useEffect(() => {
    const sel = step?.selector;
    const find = () => (sel ? (document.querySelector(sel) as HTMLElement | null) : null);
    const el = find();
    if (el) el.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
    const update = () => {
      const e = find();
      setRect(e ? e.getBoundingClientRect() : null);
    };
    update();
    const t = setTimeout(update, 360); // smooth 스크롤 후 재측정
    window.addEventListener("resize", update);
    window.addEventListener("scroll", update, true);
    return () => {
      clearTimeout(t);
      window.removeEventListener("resize", update);
      window.removeEventListener("scroll", update, true);
    };
  }, [step?.selector]);

  // 키보드: ESC 종료, ←/→ 이동
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowRight") next();
      else if (e.key === "ArrowLeft") prev();
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [next, prev, onClose]);

  if (!step) return null;

  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const TW = Math.min(340, vw - 24);

  // 툴팁 위치: 타깃 아래 우선, 공간 없으면 위, 없으면 중앙
  let tipTop: number;
  let tipLeft: number;
  if (rect) {
    const below = rect.bottom + 14;
    const aboveSpace = rect.top;
    const placeBelow = vh - rect.bottom > 240 || aboveSpace < 240;
    tipTop = placeBelow ? below : Math.max(14, rect.top - 14 - 210);
    tipLeft = Math.min(Math.max(12, rect.left), vw - TW - 12);
  } else {
    tipTop = vh / 2 - 110;
    tipLeft = vw / 2 - TW / 2;
  }

  return createPortal(
    <div className="fixed inset-0 z-[1000]" role="dialog" aria-modal="true" aria-label="페이지 안내">
      {/* 배경 클릭 차단(투명). 실수 종료 방지 위해 클릭으로 닫지 않음 */}
      <div className="absolute inset-0" />
      {/* 스포트라이트 — 타깃 주변만 어둡게 */}
      {rect ? (
        <div
          className="absolute rounded-xl pointer-events-none transition-all duration-200"
          style={{
            top: rect.top - PAD,
            left: rect.left - PAD,
            width: rect.width + PAD * 2,
            height: rect.height + PAD * 2,
            boxShadow: "0 0 0 9999px rgba(15,16,28,0.62)",
            outline: "2px solid var(--color-primary, #6C5CE0)",
            outlineOffset: 2,
          }}
        />
      ) : (
        <div className="absolute inset-0" style={{ background: "rgba(15,16,28,0.62)" }} />
      )}

      {/* 툴팁 카드 */}
      <div
        className="absolute bg-popover text-foreground rounded-xl border border-border shadow-[var(--shadow-pop)] p-4"
        style={{ top: tipTop, left: tipLeft, width: TW }}
      >
        <div className="flex items-start justify-between gap-2">
          <span className="text-xs font-semibold text-primary">{i + 1} / {steps.length}</span>
          <button onClick={onClose} aria-label="안내 끝내기" className="text-muted-foreground hover:text-foreground -mt-1 -mr-1 p-1">
            <X className="size-4" />
          </button>
        </div>
        <h3 className="mt-1 text-base font-bold">{step.title}</h3>
        <p className="mt-1.5 text-sm text-muted-foreground leading-relaxed">{step.body}</p>
        {/* 진행 점 */}
        <div className="mt-3 flex items-center gap-1.5">
          {steps.map((_, idx) => (
            <span key={idx} className={`h-1.5 rounded-full transition-all ${idx === i ? "w-5 bg-primary" : "w-1.5 bg-border"}`} />
          ))}
        </div>
        <div className="mt-3 flex items-center justify-between">
          <button onClick={onClose} className="text-xs text-muted-foreground hover:text-foreground">건너뛰기</button>
          <div className="flex items-center gap-1.5">
            <button
              onClick={prev}
              disabled={i === 0}
              className="inline-flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-sm text-foreground hover:bg-accent disabled:opacity-40 disabled:hover:bg-transparent"
            >
              <ArrowLeft className="size-3.5" /> 이전
            </button>
            <button
              onClick={next}
              className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90"
            >
              {last ? "완료" : "다음"} {!last && <ArrowRight className="size-3.5" />}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/** C 페이지 상단에 두는 '페이지 안내' 버튼. 눌러야 투어가 시작된다(강제 아님). */
export function GuideButton({ steps, label = "페이지 안내", className = "" }: { steps: TourStep[]; label?: string; className?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={`inline-flex items-center gap-1.5 rounded-lg border border-border bg-background px-3 py-1.5 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors ${className}`}
      >
        <Compass className="size-4 text-primary" />
        {label}
      </button>
      {open && <GuideTour steps={steps} onClose={() => setOpen(false)} />}
    </>
  );
}
