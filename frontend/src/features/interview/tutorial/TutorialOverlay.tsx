import { useEffect, useState, type CSSProperties } from "react";
import { useTutorialStore } from "./tutorialStore";
import { TUT_STEPS } from "./tutSteps";

/**
 * 면접 튜토리얼 스포트라이트 오버레이. deploy-day v2 의 TutorialOverlay 이식.
 *
 * 타겟(data-tut) 요소를 측정해 4분할 가림막 + 스포트라이트 + 설명 풍선을 그린다.
 * 가림막은 클릭을 막아(pointer-events:auto) 타겟 외 영역을 못 누르게 가이드하고,
 * 타겟 자체는 가림막 사이 구멍이라 그대로 클릭된다(awaitTab 진행).
 */
export function TutorialOverlay() {
  const active = useTutorialStore((s) => s.active);
  const step = useTutorialStore((s) => s.step);
  const next = useTutorialStore((s) => s.next);
  const stop = useTutorialStore((s) => s.stop);

  const [rect, setRect] = useState<DOMRect | null>(null);
  const cur = active ? TUT_STEPS[step] : null;

  useEffect(() => {
    if (!cur || !cur.targetId) {
      setRect(null);
      return;
    }
    let alive = true;
    const measure = (tries = 0) => {
      if (!alive) return;
      const el = document.querySelector(`[data-tut="${cur.targetId}"]`);
      if (!el) {
        if (tries < 15) setTimeout(() => measure(tries + 1), 80);
        else setRect(null);
        return;
      }
      el.scrollIntoView({ block: "center", behavior: "smooth" });
      setTimeout(() => {
        if (alive) setRect(el.getBoundingClientRect());
      }, 120);
    };
    measure();
    const onMove = () => {
      const el = cur.targetId ? document.querySelector(`[data-tut="${cur.targetId}"]`) : null;
      if (el) setRect(el.getBoundingClientRect());
    };
    window.addEventListener("resize", onMove);
    window.addEventListener("scroll", onMove, true);
    return () => {
      alive = false;
      window.removeEventListener("resize", onMove);
      window.removeEventListener("scroll", onMove, true);
    };
  }, [cur, step]);

  if (!active || !cur) return null;

  const last = step >= TUT_STEPS.length - 1;
  const manual = !cur.awaitTab; // awaitTab 이 없으면 수동 [다음] 버튼
  const hole = rect;
  const vh = typeof window !== "undefined" ? window.innerHeight : 800;
  const below = !hole || hole.top < vh / 2;

  const bubblePos: CSSProperties = hole
    ? {
        position: "fixed",
        left: "50%",
        transform: "translateX(-50%)",
        ...(below ? { top: hole.bottom + 16 } : { bottom: vh - hole.top + 16 }),
      }
    : { position: "fixed", left: "50%", top: "50%", transform: "translate(-50%, -50%)" };

  return (
    <div className="fixed inset-0 z-[200]" style={{ pointerEvents: "none" }}>
      {hole ? (
        <>
          <Scrim style={{ left: 0, top: 0, width: "100%", height: Math.max(0, hole.top) }} />
          <Scrim style={{ left: 0, top: hole.bottom, width: "100%", height: Math.max(0, vh - hole.bottom) }} />
          <Scrim style={{ left: 0, top: hole.top, width: Math.max(0, hole.left), height: hole.height }} />
          <Scrim style={{ left: hole.right, top: hole.top, width: `calc(100% - ${hole.right}px)`, height: hole.height }} />
          <div
            style={{
              position: "fixed",
              left: hole.left - 5,
              top: hole.top - 5,
              width: hole.width + 10,
              height: hole.height + 10,
              border: "2px solid #6366f1",
              borderRadius: 10,
              boxShadow: "0 0 0 3px rgba(99,102,241,.25), 0 0 18px 3px rgba(99,102,241,.35)",
              pointerEvents: "none",
              transition: "all .3s cubic-bezier(.4,0,.2,1)",
            }}
          />
        </>
      ) : (
        <div className="absolute inset-0 bg-slate-900/60" style={{ pointerEvents: "auto" }} />
      )}

      <div style={{ ...bubblePos, maxWidth: 420, width: "90%", pointerEvents: "auto" }}>
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-2xl">
          <div className="text-xs font-bold text-indigo-500">
            튜토리얼 · {step + 1}/{TUT_STEPS.length}
          </div>
          <h3 className="mt-1 text-base font-black text-slate-900">{cur.title}</h3>
          <p className="mt-2 text-sm leading-relaxed text-slate-600">{cur.body}</p>
          <div className="mt-4 flex items-center gap-3">
            <button onClick={stop} className="text-xs text-slate-400 transition-colors hover:text-slate-600">
              그만두기
            </button>
            <span className="flex-1" />
            {manual ? (
              <button
                onClick={next}
                className="rounded-lg bg-indigo-600 px-4 py-1.5 text-sm font-bold text-white transition-colors hover:bg-indigo-700"
              >
                {last ? "완료" : "다음"}
              </button>
            ) : (
              <span className="animate-pulse text-xs font-semibold text-indigo-500">
                {below ? "↑" : "↓"} 표시된 탭을 눌러보세요
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Scrim({ style }: { style: CSSProperties }) {
  return <div style={{ position: "fixed", background: "rgba(15,23,42,.55)", pointerEvents: "auto", ...style }} />;
}
