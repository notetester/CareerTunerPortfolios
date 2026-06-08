import { useEffect, useState } from "react";
import { FileText, RefreshCw } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { getBPromptViews } from "../api";
import type { AdminPromptView } from "../types";

export function AdminPromptsPage() {
  const [prompts, setPrompts] = useState<AdminPromptView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setPrompts(await getBPromptViews());
    } catch (err) {
      setError(err instanceof Error ? err.message : "프롬프트를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-7xl space-y-5 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <Badge className="mb-2 bg-slate-900 text-white">B 관리자</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
              <FileText className="size-6 text-blue-600" />
              B 프롬프트 확인
            </h1>
            <p className="mt-1 text-sm text-slate-500">공고 분석과 기업 분석에 적용되는 프롬프트와 출력 스키마를 확인합니다.</p>
          </div>
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </section>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="grid gap-4 lg:grid-cols-2">
          {loading ? (
            <div className="h-64 animate-pulse rounded-lg bg-slate-200" />
          ) : prompts.map((prompt) => (
            <Card key={prompt.feature} className="border-slate-200 bg-white">
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-lg text-slate-950">{prompt.name}</CardTitle>
                    <p className="mt-1 text-sm leading-6 text-slate-500">{prompt.purpose}</p>
                  </div>
                  <Badge className="bg-blue-100 text-blue-700">{prompt.version}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="text-xs font-semibold text-slate-500">출력 스키마</div>
                  <div className="mt-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                    {prompt.schemaSummary}
                  </div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-slate-500">System Prompt</div>
                  <pre className="mt-2 max-h-80 overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-950 p-4 text-xs leading-5 text-slate-100">
                    {prompt.systemPrompt}
                  </pre>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
