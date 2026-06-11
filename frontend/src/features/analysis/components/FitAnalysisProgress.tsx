import { useEffect, useState } from "react";
import { CheckCircle2, Loader2 } from "lucide-react";
import { Card, CardContent } from "@/app/components/ui/card";

/**
 * 적합도 분석 실행 중 단계별 진행 상태(디자인 분석 §9: 화면을 차단하는 단일 스피너 대신
 * 공고문 확인 → 요구사항 추출 → 프로필 비교 → 전략 생성 → 결과 저장 단계를 보여준다).
 * 백엔드는 단일 요청으로 처리하므로 단계 전환은 평균 처리 시간 기준의 클라이언트 연출이며,
 * 응답이 오면 부모가 이 컴포넌트를 내리고 결과를 표시한다.
 */
const stages = [
  "공고문 확인",
  "요구사항 추출",
  "프로필 비교",
  "준비 전략 생성",
  "결과 저장",
] as const;

const STAGE_INTERVAL_MS = 1200;

export function FitAnalysisProgress() {
  const [stageIndex, setStageIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      // 마지막 단계에서 멈춰 응답 대기 상태임을 보여준다(완료 연출은 실제 응답이 담당).
      setStageIndex((current) => Math.min(current + 1, stages.length - 1));
    }, STAGE_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  return (
    <Card className="border border-blue-200 bg-blue-50/60">
      <CardContent className="p-5">
        <div className="text-sm font-bold text-blue-900">AI 적합도 분석 진행 중</div>
        <ol className="mt-3 space-y-2">
          {stages.map((stage, index) => {
            const done = index < stageIndex;
            const active = index === stageIndex;
            return (
              <li key={stage} className="flex items-center gap-2.5 text-sm">
                <span className="flex size-5 shrink-0 items-center justify-center">
                  {done ? (
                    <CheckCircle2 className="size-4 text-green-600" />
                  ) : active ? (
                    <Loader2 className="size-4 animate-spin text-blue-600" />
                  ) : (
                    <span className="size-2 rounded-full bg-slate-300" />
                  )}
                </span>
                <span className={done ? "text-slate-500 line-through" : active ? "font-semibold text-blue-800" : "text-slate-400"}>
                  {stage}
                </span>
              </li>
            );
          })}
        </ol>
        <p className="mt-3 text-xs text-blue-700">
          분석에는 보통 수 초에서 수십 초가 걸립니다. 화면을 벗어나도 분석은 계속 진행됩니다.
        </p>
      </CardContent>
    </Card>
  );
}
