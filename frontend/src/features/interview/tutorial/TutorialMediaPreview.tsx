import { useEffect, useRef, useState } from "react";
import { Loader2, Mic, Play, Video } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Progress } from "@/app/components/ui/progress";

/**
 * 음성/아바타 면접 탭의 데모/튜토리얼 프리뷰.
 *
 * 이 두 탭은 실제로 외부 SDK(OpenAI Realtime / LiveAvatar)와 카메라·마이크를 쓰므로,
 * 데모에서는 실제 세션을 열지 않고 "면접이 이렇게 진행된다"를 스크립트로 시뮬레이션한다.
 * 질문이 순차로 등장하고 → 분석 중 → 예시 점수가 나오는 흐름을 타이머로 연출한다. (단계 C)
 */
type Phase = "idle" | "running" | "analyzing" | "scored";

const DEMO_QUESTIONS = [
  "1분 안에 자기소개를 해주세요.",
  "이 직무에 지원한 동기가 무엇인가요?",
  "가장 도전적이었던 경험과 그 해결 과정을 말씀해주세요.",
];

export function TutorialMediaPreview({ kind }: { kind: "voice" | "avatar" }) {
  const label = kind === "avatar" ? "아바타 화상 면접" : "음성 모의면접";
  const Icon = kind === "avatar" ? Video : Mic;
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

  const [phase, setPhase] = useState<Phase>("idle");
  const [qIdx, setQIdx] = useState(0);
  const timers = useRef<number[]>([]);

  const clearTimers = () => {
    timers.current.forEach((t) => clearTimeout(t));
    timers.current = [];
  };
  useEffect(() => clearTimers, []);

  const start = () => {
    clearTimers();
    setPhase("running");
    setQIdx(0);
    const n = DEMO_QUESTIONS.length;
    for (let i = 1; i < n; i++) {
      timers.current.push(window.setTimeout(() => setQIdx(i), i * 2600));
    }
    timers.current.push(window.setTimeout(() => setPhase("analyzing"), n * 2600));
    timers.current.push(window.setTimeout(() => setPhase("scored"), n * 2600 + 1800));
  };

  return (
    <div className="space-y-4">
      <div
        data-tut={kind === "avatar" ? "tut-media-avatar" : "tut-media-voice"}
        className="rounded-xl border border-indigo-200 bg-indigo-50 p-4 text-sm text-indigo-700"
      >
        <b>데모</b> — {label}은 실제로 카메라·마이크로 진행됩니다. 여기서는 진행 흐름을 예시로 보여드려요.
      </div>

      {/* 면접관 화면 */}
      <div className="relative flex aspect-video w-full items-center justify-center overflow-hidden rounded-xl bg-slate-900 text-slate-300">
        <div className="flex flex-col items-center gap-2">
          <Icon className="size-10 opacity-70" />
          <span className="text-sm">{kind === "avatar" ? "아바타 면접관" : "AI 음성 면접관"}</span>
        </div>
        {phase === "running" && (
          <div className="absolute inset-x-0 bottom-0 bg-black/55 p-4 text-center text-sm text-white">
            면접관: {DEMO_QUESTIONS[qIdx]}
          </div>
        )}
      </div>

      {phase === "idle" && (
        <Button onClick={start} className="gap-1.5 bg-indigo-600 hover:bg-indigo-700">
          <Play className="size-4" /> 데모 면접 시작
        </Button>
      )}

      {phase === "running" && (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>
              질문 {qIdx + 1}/{DEMO_QUESTIONS.length} · 답변 듣는 중…
            </span>
          </div>
          <Progress value={((qIdx + 1) / DEMO_QUESTIONS.length) * 100} />
        </div>
      )}

      {phase === "analyzing" && (
        <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-card p-6 text-sm text-slate-500">
          <Loader2 className="size-4 animate-spin" /> {kind === "avatar" ? "표정·자세·음성 분석 중…" : "음성 지표 분석 중…"}
        </div>
      )}

      {phase === "scored" && (
        <div className="rounded-xl border border-slate-200 bg-card p-6">
          <div className="flex items-center gap-3">
            <span className="text-4xl font-black text-indigo-600">
              82<span className="text-base font-bold text-slate-400">/100</span>
            </span>
            <span className="text-sm text-slate-500">예시 종합 점수</span>
            <Button onClick={start} variant="outline" size="sm" className="ml-auto">
              다시 보기
            </Button>
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
      )}
    </div>
  );
}
