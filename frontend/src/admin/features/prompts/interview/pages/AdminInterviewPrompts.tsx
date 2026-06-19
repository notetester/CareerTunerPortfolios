import { useEffect, useState } from "react";
import { FileText, RefreshCw } from "lucide-react";
import AdminShell from "../../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { getInterviewPromptViews } from "../api";
import type { AdminPromptView } from "../types";

export default function AdminInterviewPromptsPage() {
  const [prompts, setPrompts] = useState<AdminPromptView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setPrompts(await getInterviewPromptViews());
    } catch (err) {
      setError(err instanceof Error ? err.message : "면접 프롬프트를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <AdminShell
      active="prompts"
      breadcrumb="면접 프롬프트"
      title="면접 AI 프롬프트 관리"
      icon={FileText}
      desc="질문 생성·답변 평가·모범답안·꼬리질문·압박 반박·Critic·Judge·Planner·리포트 등 면접 도메인 프롬프트 9종을 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}
      <div className="space-y-4">
        {prompts.map((p) => (
          <Card key={p.feature} className="border-slate-200 bg-card">
            <CardHeader>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <CardTitle className="text-lg text-slate-950">{p.name}</CardTitle>
                  <p className="mt-1 text-sm leading-6 text-slate-500">{p.purpose}</p>
                </div>
                <Badge className="bg-blue-100 text-blue-700">{p.version}</Badge>
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-xs font-semibold text-slate-500">System Prompt</div>
              <pre className="mt-2 max-h-96 overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-[#0b0c0e] p-4 text-xs leading-5 text-[#e6e6e6]">
                {p.systemPrompt}
              </pre>
            </CardContent>
          </Card>
        ))}
        {!loading && prompts.length === 0 && (
          <div className="rounded-lg border border-dashed border-slate-200 bg-card p-8 text-center text-sm text-slate-400">
            프롬프트가 없습니다.
          </div>
        )}
      </div>
    </AdminShell>
  );
}
