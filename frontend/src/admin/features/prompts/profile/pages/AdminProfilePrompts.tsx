import { useEffect, useState } from "react";
import { FileText, LockKeyhole, RefreshCw, ShieldCheck } from "lucide-react";
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
      title="프로필 AI 평가 기준 관리"
      icon={FileText}
      desc="프로필 AI 분석에 사용하는 활성 버전, 평가 기준, 가중치, 출력 스키마, 검증 상태를 조회합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      {error && <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      <Card className="border-slate-200 bg-card">
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
          <div className="grid gap-3 md:grid-cols-4">
            <StatusCard label="활성 버전" value={prompt?.version ?? "-"} tone="blue" />
            <StatusCard label="운영 상태" value="운영 적용 중" tone="green" />
            <StatusCard label="수정 정책" value="읽기 전용" tone="slate" />
            <StatusCard label="변경 방식" value="리뷰 후 배포" tone="slate" />
          </div>

          <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
            <div className="flex items-start gap-3">
              <LockKeyhole className="mt-0.5 size-5 shrink-0 text-blue-600" />
              <div>
                <div className="text-sm font-bold text-blue-950">운영 정책</div>
                <p className="mt-1 text-sm leading-6 text-blue-800">
                  이 화면은 프롬프트 자유 입력이나 즉시 반영을 제공하지 않습니다. 관리자는 현재 운영에 적용된
                  검증 버전과 품질 기준을 조회하고, 실제 변경은 코드 리뷰와 배포 절차를 거쳐 반영합니다.
                </p>
              </div>
            </div>
          </div>

          {(prompt?.evaluationCriteria?.length ?? 0) > 0 && (
            <div>
              <div className="text-xs font-semibold text-slate-500">평가 기준</div>
              <div className="mt-2 grid gap-2 md:grid-cols-2">
                {prompt?.evaluationCriteria?.map((criterion) => (
                  <div key={criterion.criterion} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <div className="text-sm font-bold text-slate-900">{criterion.label}</div>
                    <div className="mt-1 text-xs leading-5 text-slate-500">{criterion.description}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
          {(prompt?.weightProfiles?.length ?? 0) > 0 && (
            <div>
              <div className="text-xs font-semibold text-slate-500">직무군별 가중치</div>
              <div className="mt-2 overflow-x-auto rounded-lg border border-slate-200">
                <table className="min-w-full divide-y divide-slate-200 text-sm">
                  <thead className="bg-slate-50 text-xs font-bold text-slate-500">
                    <tr>
                      <th className="px-3 py-2 text-left">직무군</th>
                      {prompt?.evaluationCriteria?.map((criterion) => (
                        <th key={criterion.criterion} className="px-3 py-2 text-right">{criterion.label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-card">
                    {prompt?.weightProfiles?.map((profile) => (
                      <tr key={profile.jobFamily}>
                        <td className="px-3 py-2">
                          <div className="font-bold text-slate-900">{profile.label}</div>
                          <div className="text-xs text-slate-500">{profile.description}</div>
                        </td>
                        {prompt?.evaluationCriteria?.map((criterion) => (
                          <td key={criterion.criterion} className="px-3 py-2 text-right font-semibold text-slate-700">
                            {profile.weights[criterion.criterion] ?? 0}%
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
          <div>
            <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-500">
              <ShieldCheck className="size-4 text-emerald-600" />
              검증 체크리스트
            </div>
            <div className="grid gap-2 md:grid-cols-2">
              {[
                "출력 JSON 스키마 필수 필드 검증",
                "직무군별 가중치 합계 100% 검증",
                "AI 데이터 동의 확인 후 실행",
                "검증 실패 시 fallback 및 사용 이력 기록",
              ].map((item) => (
                <div key={item} className="rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-800">
                  {item}
                </div>
              ))}
            </div>
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-500">AI 결과 항목</div>
            <div className="mt-2 overflow-x-auto rounded-lg border border-slate-200">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-xs font-bold text-slate-500">
                  <tr>
                    <th className="px-3 py-2 text-left">항목</th>
                    <th className="px-3 py-2 text-left">화면 표시 의미</th>
                    <th className="px-3 py-2 text-left">타입</th>
                    <th className="px-3 py-2 text-left">검증</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-card">
                  {PROFILE_OUTPUT_FIELDS.map((field) => (
                    <tr key={field.key}>
                      <td className="px-3 py-2 font-semibold text-slate-900">{field.label}</td>
                      <td className="px-3 py-2 text-slate-600">{field.description}</td>
                      <td className="px-3 py-2 text-slate-600">{field.type}</td>
                      <td className="px-3 py-2">
                        <Badge className="bg-emerald-100 text-emerald-700">{field.required ? "필수" : "선택"}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              실제 JSON Schema 원문은 일반 관리자 화면에 노출하지 않고, 서버 응답 검증기와 PromptCatalog에서 관리합니다.
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-semibold text-slate-500">프롬프트 원문 관리 위치</div>
            <div className="mt-2 grid gap-2 md:grid-cols-3">
              <PolicyItem label="원문 위치" value="서버 PromptCatalog" />
              <PolicyItem label="변경 권한" value="개발자/슈퍼관리자" />
              <PolicyItem label="반영 방식" value="코드 리뷰 후 배포" />
            </div>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              일반 관리자 화면에서는 시스템 프롬프트 원문을 직접 노출하지 않습니다. 운영자는 활성 버전과 검증 상태만
              확인하고, 원문 변경은 서버 코드의 프롬프트 카탈로그에서 관리합니다.
            </p>
          </div>
        </CardContent>
      </Card>
    </AdminShell>
  );
}

const PROFILE_OUTPUT_FIELDS = [
  { key: "summary", label: "프로필 요약", description: "사용자 프로필의 핵심 방향성을 한 문단으로 요약", type: "텍스트", required: true },
  { key: "extractedSkills", label: "추출 역량", description: "프로필에서 확인된 직무 역량 목록", type: "목록", required: true },
  { key: "strengths", label: "강점", description: "지원자가 현재 갖춘 경쟁력", type: "목록", required: true },
  { key: "gaps", label: "보완점", description: "희망 직무 대비 부족하거나 추가 입력이 필요한 항목", type: "목록", required: true },
  { key: "recommendations", label: "추천 액션", description: "프로필 개선을 위한 다음 행동 제안", type: "목록", required: true },
  { key: "criterionScores", label: "평가 기준별 점수", description: "평가 기준별 원점수, 근거, 개선 방향", type: "점수 목록", required: true },
];

function StatusCard({ label, value, tone }: { label: string; value: string; tone: "blue" | "green" | "slate" }) {
  const toneClass = {
    blue: "bg-blue-50 text-blue-700",
    green: "bg-emerald-50 text-emerald-700",
    slate: "bg-slate-50 text-slate-700",
  }[tone];

  return (
    <div className={`rounded-lg px-3 py-3 ${toneClass}`}>
      <div className="text-[11px] font-semibold uppercase opacity-70">{label}</div>
      <div className="mt-1 break-words text-sm font-bold">{value}</div>
    </div>
  );
}

function PolicyItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-card px-3 py-2">
      <div className="text-[11px] font-semibold uppercase text-slate-400">{label}</div>
      <div className="mt-1 text-sm font-bold text-slate-800">{value}</div>
    </div>
  );
}
