import { EVALUATION_CRITERIA, SCORE_BANDS } from "../types/interview";
import type { InterviewSession } from "../types/interview";
import { CorrectionInfoTab } from "./CorrectionInfoTab";

/** 실제 세션 답변 평가와 정적 채점 기준을 한 화면에서 제공한다. */
export function EvaluationCriteriaTab({ session }: { session: InterviewSession | null }) {
  return (
    <div className="space-y-8">
      {session && <CorrectionInfoTab session={session} />}

      <section>
        <div className="mb-4">
          <h2 className="font-bold text-slate-800">AI 답변 평가 기준</h2>
          <p className="mt-1 text-sm text-slate-500">실제 답변 결과와 함께 AI가 사용하는 평가 항목과 점수 구간을 확인하세요.</p>
        </div>
        <div className="grid max-w-4xl gap-5 md:grid-cols-2">
          <div>
            <h3 className="mb-4 font-bold text-slate-800">답변 평가 항목</h3>
            <div className="space-y-3">
              {EVALUATION_CRITERIA.map((e) => (
                <div key={e.label} className="rounded-xl border border-slate-200 bg-card p-3">
                  <div className="text-sm font-semibold text-slate-800">{e.label}</div>
                  <div className="mt-0.5 text-xs text-slate-500">{e.desc}</div>
                </div>
              ))}
            </div>
          </div>
          <div>
            <h3 className="mb-4 font-bold text-slate-800">답변 채점 기준</h3>
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
      </section>
    </div>
  );
}
