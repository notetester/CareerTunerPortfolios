import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { ArrowRight, CheckCircle2, Loader2, ThumbsDown, ThumbsUp } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { getSessionReview } from "../api/interviewApi";
import type { InterviewSession, SessionReviewItem } from "../types/interview";

// 면접 답변 평가/즉시 개선안은 D가 보여주고, 별도 버전 이력·모델 선택 첨삭은 E 화면으로 원본 참조를 넘긴다.
export function CorrectionInfoTab({ session }: { session: InterviewSession | null }) {
  const [items, setItems] = useState<SessionReviewItem[]>([]);
  const [selectedAnswerId, setSelectedAnswerId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setItems([]);
    setSelectedAnswerId(null);
    setError(null);
    if (!session) return () => { cancelled = true; };
    setLoading(true);
    void getSessionReview(session.id)
      .then((review) => {
        if (cancelled) return;
        const answered = review.items.filter((item) => item.answerId != null && item.answerText?.trim());
        setItems(answered);
        setSelectedAnswerId(answered[0]?.answerId ?? null);
      })
      .catch((reason: unknown) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : "면접 답변을 불러오지 못했습니다.");
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [session]);

  const selected = useMemo(
    () => items.find((item) => item.answerId === selectedAnswerId) ?? items[0] ?? null,
    [items, selectedAnswerId],
  );
  const correctionLink = selected?.answerId && session
    ? `/correction/answer?caseId=${session.applicationCaseId}&sourceRefId=${selected.answerId}`
    : "/correction/answer";

  if (!session) {
    return <EmptyMessage text="면접 세션을 시작하거나 최근 기록을 이어받으면 실제 답변을 첨삭할 수 있습니다." />;
  }
  if (loading) {
    return <EmptyMessage text="면접 답변을 불러오는 중입니다…" loading />;
  }
  if (error) {
    return <EmptyMessage text={error} />;
  }
  if (!selected) {
    return <EmptyMessage text="저장된 답변이 없습니다. 예상 질문 또는 복습 테스트에서 답변을 제출한 뒤 이용해 주세요." />;
  }

  return (
    <div className="max-w-3xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-bold text-slate-800">AI 면접 답변 첨삭</h2>
        {items.length > 1 && (
          <select
            aria-label="첨삭할 면접 답변"
            value={selected.answerId ?? ""}
            onChange={(event) => setSelectedAnswerId(Number(event.target.value))}
            className="max-w-full rounded-lg border border-slate-200 bg-card px-3 py-2 text-sm text-slate-700"
          >
            {items.map((item, index) => (
              <option key={item.answerId} value={item.answerId ?? ""}>Q{index + 1}. {item.question}</option>
            ))}
          </select>
        )}
      </div>
      <div className="rounded-xl bg-slate-100 p-4">
        <div className="mb-1 text-sm font-bold text-slate-700">질문</div>
        <p className="text-sm text-slate-800">{selected.question}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="border-2 border-red-200 bg-red-50">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-1.5 text-sm text-red-700">
              <ThumbsDown className="size-4" /> 내 원답변
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="rounded-lg border border-red-100 bg-card p-3 text-sm text-slate-700">
              {selected.answerText}
            </div>
            <div className="rounded-lg bg-red-100 p-3 text-xs text-red-700">
              <div className="mb-1 font-bold">면접 평가 {selected.score != null ? `· ${selected.score}점` : ""}</div>
              {selected.feedback || "저장된 평가 피드백이 없습니다."}
            </div>
          </CardContent>
        </Card>
        <Card className="border-2 border-green-200 bg-green-50">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-1.5 text-sm text-green-700">
              <ThumbsUp className="size-4" /> 면접 평가 개선안
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="rounded-lg border border-green-100 bg-card p-3 text-sm leading-relaxed text-slate-700">
              {selected.improvedAnswer || "별도 첨삭에서 모델과 요구사항을 선택해 개선안을 생성할 수 있습니다."}
            </div>
            <div className="flex items-center gap-1.5 text-xs text-green-700">
              <CheckCircle2 className="size-3.5 shrink-0" /> 원답변은 덮어쓰지 않고 출처로 연결됩니다.
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm text-slate-600">
          모델 선택·버전 이력·다시 시도는 별도 첨삭 작업에서 관리합니다.
        </div>
        <Button asChild variant="outline" className="shrink-0 gap-1.5">
          <Link to={correctionLink}>
            이 답변 첨삭하기 <ArrowRight className="size-4" />
          </Link>
        </Button>
      </div>
    </div>
  );
}

function EmptyMessage({ text, loading = false }: { text: string; loading?: boolean }) {
  return (
    <div className="flex min-h-40 items-center justify-center gap-2 rounded-xl border border-dashed border-slate-200 bg-card p-6 text-center text-sm text-slate-500">
      {loading && <Loader2 className="size-4 animate-spin" />}
      {text}
    </div>
  );
}
