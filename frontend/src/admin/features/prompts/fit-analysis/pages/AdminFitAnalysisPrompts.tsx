import { useEffect, useState } from "react";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { AlertTriangle, CheckCircle2, FileInput, FileOutput, Loader2, Target } from "lucide-react";
import AdminShell from "../../../../components/AdminShell";
import { getFitAnalysisPrompts } from "../api/fitAnalysisPromptApi";
import type { FitAnalysisPromptTemplate } from "../types/fitAnalysisPrompt";

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}

export default function AdminFitAnalysisPromptsPage() {
  const [prompts, setPrompts] = useState<FitAnalysisPromptTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);

    getFitAnalysisPrompts()
      .then((data) => {
        if (!ignore) setPrompts(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "적합도 프롬프트를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  return (
    <AdminShell
      active="fit-analysis"
      breadcrumb="적합도 프롬프트"
      title="적합도 분석 프롬프트 운영 확인"
      icon={Target}
      desc="공고-스펙 비교, 부족 역량 추천, 지원 전략 생성 프롬프트의 목적과 품질 기준을 확인합니다."
    >
      <div className="space-y-6">
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
    </AdminShell>
  );
}

function PromptCard({ prompt }: { prompt: FitAnalysisPromptTemplate }) {
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="space-y-2 text-base">
          <div className="flex items-start justify-between gap-2">
            <span>{prompt.name}</span>
            <Badge className="bg-slate-100 text-slate-600">{prompt.version}</Badge>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <Badge className="bg-blue-100 text-blue-700">{prompt.status}</Badge>
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
  const tone = icon === "risk" ? "text-amber-600" : icon === "check" ? "text-green-600" : "text-blue-600";

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
