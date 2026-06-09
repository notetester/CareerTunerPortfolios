import { EVALUATION_CRITERIA, SCORE_BANDS } from "../types/interview";

/** 답변 평가 항목 + 채점 구간 (정적 안내). */
export function EvaluationCriteriaTab() {
  return (
    <div className="grid max-w-4xl gap-5 md:grid-cols-2">
      <div>
        <h2 className="mb-4 font-bold text-slate-800">답변 평가 항목</h2>
        <div className="space-y-3">
          {EVALUATION_CRITERIA.map((e) => (
            <div key={e.label} className="rounded-xl border border-slate-200 bg-white p-3">
              <div className="text-sm font-semibold text-slate-800">{e.label}</div>
              <div className="mt-0.5 text-xs text-slate-500">{e.desc}</div>
            </div>
          ))}
        </div>
      </div>
      <div>
        <h2 className="mb-4 font-bold text-slate-800">답변 채점 기준</h2>
        <div className="space-y-3">
          {SCORE_BANDS.map((s) => (
            <div key={s.range} className={`rounded-xl border p-3 ${s.color}`}>
              <div className="text-sm font-black">{s.range}</div>
              <div className="mt-0.5 text-xs opacity-80">{s.desc}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
