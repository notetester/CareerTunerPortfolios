/**
 * 음성/아바타 면접 탭의 튜토리얼 전용 프리뷰.
 *
 * 이 두 탭은 실제로 외부 SDK(OpenAI Realtime / LiveAvatar)에 연결하고 카메라·마이크를
 * 쓰기 때문에, 튜토리얼에서는 실제 세션을 열지 않고 "이런 기능이고 결과는 이렇게 나온다"는
 * 예시 화면만 보여준다. (단계 C)
 */
export function TutorialMediaPreview({ kind }: { kind: "voice" | "avatar" }) {
  const label = kind === "avatar" ? "아바타 화상 면접" : "음성 모의면접";
  const desc =
    kind === "avatar"
      ? "아바타 면접관이 음성으로 질문하고, 웹캠으로 표정·자세·음성을 분석해 종합 점수를 냅니다. 원본 영상은 기기에서만 분석되고 서버에 저장되지 않습니다."
      : "AI 면접관이 음성으로 질문하고, 답변의 말 속도·필러·톤·반응 속도 등 말하기 지표를 분석합니다.";
  const items: [string, number][] =
    kind === "avatar"
      ? [
          ["표정", 84],
          ["시선 처리", 79],
          ["자세", 82],
          ["음성 안정감", 80],
        ]
      : [
          ["말 속도", 83],
          ["유창성(필러)", 78],
          ["톤 안정감", 81],
          ["자신감", 80],
        ];

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-indigo-200 bg-indigo-50 p-4 text-sm text-indigo-700">
        <b>튜토리얼 데모</b> — {label}은 실제 면접에서 카메라·마이크로 진행됩니다. 여기서는 예시 결과만 보여드려요.
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-6">
        <p className="text-sm leading-relaxed text-slate-600">{desc}</p>

        <div className="mt-5 flex items-center gap-3">
          <span className="text-4xl font-black text-indigo-600">
            82<span className="text-base font-bold text-slate-400">/100</span>
          </span>
          <span className="text-sm text-slate-500">예시 종합 점수</span>
        </div>

        <div className="mt-5 space-y-3">
          {items.map(([itemLabel, value]) => (
            <div key={itemLabel}>
              <div className="mb-1 flex items-baseline justify-between text-sm">
                <span className="font-semibold text-slate-700">{itemLabel}</span>
                <span className="font-bold text-indigo-600">{value}</span>
              </div>
              <div className="h-2 rounded bg-slate-100">
                <div className="h-2 rounded bg-indigo-500" style={{ width: `${value}%` }} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
