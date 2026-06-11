import { useEffect, useState } from "react";
import { FileText, RefreshCw } from "lucide-react";
import AdminShell from "../../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { getProfilePromptView } from "../api";
import type { AdminPromptView } from "../types";

export default function AdminProfilePromptsPage() {
  const [prompt, setPrompt] = useState<AdminPromptView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setPrompt(await getProfilePromptView());
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 프롬프트를 불러오지 못했습니다.");
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
      breadcrumb="프로필 프롬프트"
      title="프로필 AI 프롬프트 관리"
      icon={FileText}
      desc="프로필 요약, 직무 역량 추출, 완성도 진단에 사용하는 프롬프트와 출력 스키마를 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      {error && <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      <Card className="border-slate-200 bg-white">
        <CardHeader>
          <div className="flex items-start justify-between gap-3">
            <div>
              <CardTitle className="text-lg text-slate-950">{prompt?.name ?? "프로필 프롬프트"}</CardTitle>
              <p className="mt-1 text-sm leading-6 text-slate-500">{prompt?.purpose ?? "로딩 중..."}</p>
            </div>
            {prompt?.version && <Badge className="bg-blue-100 text-blue-700">{prompt.version}</Badge>}
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <div className="text-xs font-semibold text-slate-500">출력 스키마</div>
            <pre className="mt-2 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs leading-5 text-slate-700">
              {prompt?.schemaSummary || "-"}
            </pre>
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-500">System Prompt</div>
            <pre className="mt-2 max-h-[520px] overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-950 p-4 text-xs leading-5 text-slate-100">
              {prompt?.systemPrompt || "-"}
            </pre>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}
