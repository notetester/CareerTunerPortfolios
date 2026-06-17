import { useEffect, useState } from "react";

/**
 * 예상 시간 기반 "가짜" 진행바.
 *
 * LLM 응답은 실제 진행률을 알 수 없으므로(요청 → 블랙박스 → 응답), 예상 시간 동안 0→90% 까지
 * 점근적으로 차오르게 하고, active 가 false 가 되면(= 응답 도착) 100% 를 채운 뒤 사라진다.
 * 순수 타이머 기반이라 백엔드 provider(OpenAI / 로컬 LLM 등)를 바꿔도 그대로 동작한다.
 */
export function InterviewProgressBar({
  active,
  estimatedMs = 12000,
  label,
}: {
  active: boolean;
  estimatedMs?: number;
  label?: string;
}) {
  const [pct, setPct] = useState(0);
  const [show, setShow] = useState(false);

  // 진행 중: 0 → 90% 로 점근(처음 빠르게, 뒤로 갈수록 느리게).
  useEffect(() => {
    if (!active) return;
    setShow(true);
    setPct(0);
    const start = Date.now();
    const id = setInterval(() => {
      const elapsed = Date.now() - start;
      setPct(Math.min(90, 90 * (1 - Math.exp(-elapsed / (estimatedMs * 0.6)))));
    }, 120);
    return () => clearInterval(id);
  }, [active, estimatedMs]);

  // 완료(active=false): 100% 를 채우고 잠깐 뒤 숨긴다.
  useEffect(() => {
    if (active || !show) return;
    setPct(100);
    const t = setTimeout(() => setShow(false), 450);
    return () => clearTimeout(t);
  }, [active, show]);

  if (!show) return null;

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>{label ?? "AI가 처리 중이에요"}</span>
        <span className="tabular-nums font-semibold text-slate-600">{Math.round(pct)}%</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full bg-gradient-to-r from-blue-500 to-indigo-500 transition-all duration-150 ease-out"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
