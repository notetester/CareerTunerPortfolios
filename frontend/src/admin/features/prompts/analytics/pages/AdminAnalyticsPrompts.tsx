import { useEffect, useState } from "react";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { AlertTriangle, BarChart3, CheckCircle2, FileInput, FileOutput, Loader2 } from "lucide-react";
import { getAnalyticsPrompts } from "../api/analyticsPromptApi";
import type { AnalyticsPromptTemplate } from "../types/analyticsPrompt";

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}

export default function AdminAnalyticsPromptsPage() {
  const [prompts, setPrompts] = useState<AnalyticsPromptTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);

    getAnalyticsPrompts()
      .then((data) => {
        if (!ignore) setPrompts(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "분석 프롬프트를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <section>
          <Badge className="mb-3 bg-indigo-600 text-white">Prompt Ops</Badge>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <BarChart3 className="size-6 text-indigo-600" />
            장기 분석 프롬프트 운영 확인
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            장기 취업 경향, 대시보드 다음 액션, 직무별 준비도 프롬프트의 품질 기준을 확인합니다.
          </p>
        </section>

        {loading && (
          <Card className="border border-slate-200 bg-card">
            <CardContent className="flex items-center gap-2 p-5 text-sm text-slate-500">
              <Loader2 className="size-4 animate-spin" />
              프롬프트 정보를 불러오는 중입니다.
            </CardContent>
          </Card>
        )}

        {error && (
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="flex items-center gap-2 p-5 text-sm text-red-700">
              <AlertTriangle className="size-4" />
              {error}
            </CardContent>
          </Card>
        )}

        {!loading && !error && (
          <div className="grid gap-5 lg:grid-cols-3">
            {prompts.map((prompt) => (
              <PromptCard key={prompt.key} prompt={prompt} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function PromptCard({ prompt }: { prompt: AnalyticsPromptTemplate }) {
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="space-y-2 text-base">
          <div className="flex items-start justify-between gap-2">
            <span>{prompt.name}</span>
            <Badge className="bg-slate-100 text-slate-600">{prompt.version}</Badge>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <Badge className="bg-indigo-100 text-indigo-700">{prompt.status}</Badge>
            <Badge className="bg-slate-100 text-slate-500">검토 {formatDate(prompt.lastReviewedAt)}</Badge>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm leading-relaxed text-slate-600">{prompt.purpose}</p>
        <InfoList title="입력" icon="input" items={prompt.inputFields} />
        <InfoList title="출력" icon="output" items={prompt.outputFields} />
        <InfoList title="품질 체크" icon="check" items={prompt.qualityChecklist} />
        <InfoList title="위험 노트" icon="risk" items={prompt.riskNotes} />
      </CardContent>
    </Card>
  );
}

function InfoList({ title, icon, items }: { title: string; icon: "input" | "output" | "check" | "risk"; items: string[] }) {
  const Icon = icon === "input" ? FileInput : icon === "output" ? FileOutput : icon === "risk" ? AlertTriangle : CheckCircle2;
  const tone = icon === "risk" ? "text-amber-600" : icon === "check" ? "text-green-600" : "text-indigo-600";

  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-xs font-bold text-slate-500">
        <Icon className={`size-3.5 ${tone}`} />
        {title}
      </div>
      <div className="space-y-1.5">
        {items.map((item) => (
          <div key={item} className="rounded bg-slate-50 px-2 py-1.5 text-xs leading-relaxed text-slate-600">
            {item}
          </div>
        ))}
      </div>
    </div>
  );
}
