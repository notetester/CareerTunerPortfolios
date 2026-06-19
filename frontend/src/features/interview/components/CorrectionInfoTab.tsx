import { Link } from "react-router";
import { ArrowRight, ThumbsDown, ThumbsUp, CheckCircle2 } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";

// 면접 답변 첨삭(개선 답변)만 면접 도메인(D) 범위.
// 자소서/이력서/포트폴리오 첨삭은 첨삭 도메인(correction, E 담당)으로 연결한다.
const EXAMPLE = {
  question: "React에서 성능 최적화를 어떻게 하시나요?",
  original: "useMemo와 useCallback을 사용합니다. 그리고 React.memo도 씁니다.",
  aiEval: "방향은 맞지만 구체적인 사용 상황, 판단 기준, 실제 경험이 없어 답변이 너무 짧고 피상적입니다.",
  improved:
    "React 성능 최적화는 상황에 맞게 접근합니다. 불필요한 리렌더링을 막기 위해 useMemo·useCallback을 복잡한 계산이나 자식에 전달하는 함수에 선택 적용하고, 대시보드 프로젝트에서 차트 계산에 useMemo를 적용해 렌더링 시간을 약 40% 단축한 경험이 있습니다. React.memo는 반복 렌더되는 리스트 아이템에, Code Splitting은 번들 최적화에 함께 적용합니다.",
  points: ["구체적 사용 상황 명시", "실제 수치 결과 포함", "판단 기준 설명"],
};

export function CorrectionInfoTab() {
  return (
    <div className="max-w-3xl space-y-5">
      <h2 className="font-bold text-slate-800">AI 면접 답변 첨삭</h2>
      <div className="rounded-xl bg-slate-100 p-4">
        <div className="mb-1 text-sm font-bold text-slate-700">질문</div>
        <p className="text-sm text-slate-800">{EXAMPLE.question}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="border-2 border-red-200 bg-red-50">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-1.5 text-sm text-red-700">
              <ThumbsDown className="size-4" /> 원답변
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="rounded-lg border border-red-100 bg-card p-3 text-sm text-slate-700">
              {EXAMPLE.original}
            </div>
            <div className="rounded-lg bg-red-100 p-3 text-xs text-red-700">
              <div className="mb-1 font-bold">AI 평가</div>
              {EXAMPLE.aiEval}
            </div>
          </CardContent>
        </Card>
        <Card className="border-2 border-green-200 bg-green-50">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-1.5 text-sm text-green-700">
              <ThumbsUp className="size-4" /> AI 개선 답변
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="rounded-lg border border-green-100 bg-card p-3 text-sm leading-relaxed text-slate-700">
              {EXAMPLE.improved}
            </div>
            <div className="space-y-1">
              {EXAMPLE.points.map((p) => (
                <div key={p} className="flex items-center gap-1.5 text-xs text-green-700">
                  <CheckCircle2 className="size-3.5 shrink-0" /> {p}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="flex items-center justify-between rounded-xl border border-slate-200 bg-card p-4">
        <div className="text-sm text-slate-600">
          자기소개서 · 이력서 · 포트폴리오 첨삭은 첨삭 메뉴에서 이용할 수 있습니다.
        </div>
        <Button asChild variant="outline" className="gap-1.5">
          <Link to="/correction">
            첨삭 메뉴로 <ArrowRight className="size-4" />
          </Link>
        </Button>
      </div>
    </div>
  );
}
