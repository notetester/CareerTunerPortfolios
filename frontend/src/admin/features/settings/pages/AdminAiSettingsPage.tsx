import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, RefreshCw, Save, SlidersHorizontal } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import AdminShell from "../../../components/AdminShell";
import {
  getJobPostingFallbackSetting,
  getJobPostingUploadLimitSetting,
  updateJobPostingFallbackSetting,
  updateJobPostingUploadLimitSetting,
} from "../api";
import type {
  AdminJobPostingFallbackSetting,
  AdminJobPostingUploadLimitSetting,
  JobPostingFallbackStage,
} from "../types";

const STAGE_LABELS: Record<JobPostingFallbackStage, { title: string; description: string }> = {
  JOB_POSTING_PDF_OCR: {
    title: "PDF OCR fallback",
    description: "자체 PDF 텍스트 추출과 Python worker가 실패한 경우에만 OpenAI PDF OCR을 허용합니다.",
  },
  JOB_POSTING_IMAGE_OCR: {
    title: "Image OCR fallback",
    description: "자체 이미지 OCR/Python worker가 실패한 경우에만 OpenAI 이미지 OCR을 허용합니다.",
  },
};

function sourceLabel(source: string): string {
  return source === "DATABASE"
    ? "관리자 저장값"
    : source === "PROPERTIES"
      ? "환경변수 초기값"
      : "기본값";
}

export function AdminAiSettingsPage() {
  const [setting, setSetting] = useState<AdminJobPostingFallbackSetting | null>(null);
  const [enabled, setEnabled] = useState(false);
  const [allowedStages, setAllowedStages] = useState<JobPostingFallbackStage[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);
  const [uploadSetting, setUploadSetting] = useState<AdminJobPostingUploadLimitSetting | null>(null);
  const [uploadMb, setUploadMb] = useState("");
  const [uploadSaving, setUploadSaving] = useState(false);

  const dirty = useMemo(() => {
    if (!setting) return false;
    const previous = [...setting.allowedStages].sort().join(",");
    const next = [...allowedStages].sort().join(",");
    return setting.enabled !== enabled || previous !== next;
  }, [allowedStages, enabled, setting]);

  const load = async () => {
    setLoading(true);
    setError(null);
    setSavedMessage(null);
    try {
      const [response, uploadResponse] = await Promise.all([
        getJobPostingFallbackSetting(),
        getJobPostingUploadLimitSetting(),
      ]);
      setSetting(response);
      setEnabled(response.enabled);
      setAllowedStages(response.allowedStages);
      setUploadSetting(uploadResponse);
      setUploadMb(String(Math.round(uploadResponse.maxBytes / (1024 * 1024))));
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 설정을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const toggleStage = (stage: JobPostingFallbackStage) => {
    setAllowedStages((current) =>
      current.includes(stage)
        ? current.filter((item) => item !== stage)
        : [...current, stage],
    );
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const response = await updateJobPostingFallbackSetting({
        enabled,
        allowedStages: enabled ? allowedStages : [],
      });
      setSetting(response);
      setEnabled(response.enabled);
      setAllowedStages(response.allowedStages);
      setSavedMessage("OpenAI fallback allowlist가 저장됐습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 설정을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const uploadMinMb = uploadSetting ? Math.round(uploadSetting.minBytes / (1024 * 1024)) : 1;
  const uploadMaxMb = uploadSetting ? Math.round(uploadSetting.maxAllowedBytes / (1024 * 1024)) : 20;
  const uploadCurrentMb = uploadSetting ? Math.round(uploadSetting.maxBytes / (1024 * 1024)) : 0;
  const uploadDirty = uploadSetting != null && uploadMb !== "" && Number(uploadMb) !== uploadCurrentMb;

  const saveUpload = async () => {
    const mb = Number(uploadMb);
    if (!Number.isFinite(mb) || mb < uploadMinMb || mb > uploadMaxMb) {
      setError(`업로드 한도는 ${uploadMinMb}MB ~ ${uploadMaxMb}MB 범위여야 합니다.`);
      return;
    }
    setUploadSaving(true);
    setError(null);
    setSavedMessage(null);
    try {
      const response = await updateJobPostingUploadLimitSetting({ maxBytes: Math.round(mb * 1024 * 1024) });
      setUploadSetting(response);
      setUploadMb(String(Math.round(response.maxBytes / (1024 * 1024))));
      setSavedMessage("공고 업로드 크기 한도가 저장됐습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "업로드 한도를 저장하지 못했습니다.");
    } finally {
      setUploadSaving(false);
    }
  };

  const availableStages = setting?.availableStages ?? ["JOB_POSTING_PDF_OCR", "JOB_POSTING_IMAGE_OCR"];

  return (
    <AdminShell
      active="ai-settings"
      breadcrumb="AI 설정"
      title="AI 운영 설정"
      icon={SlidersHorizontal}
      desc="자체 AI 우선 정책을 유지하면서 승인된 단계에서만 OpenAI fallback을 허용합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading || saving}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        {error && <div className="break-words rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {savedMessage && <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{savedMessage}</div>}

        <Card className="border-slate-200 bg-white">
          <CardHeader className="gap-2">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <CardTitle className="text-lg font-bold text-slate-950">공고 추출 OpenAI fallback</CardTitle>
                <p className="mt-1 text-sm leading-6 text-slate-500">
                  기본 서비스 경로는 자체 문서 추출/Python worker입니다. 이 설정은 자체 구현이 실패한 특정 단계에서만 백업 호출을 허용합니다.
                </p>
              </div>
              <Badge className={enabled ? "bg-amber-100 text-amber-800" : "bg-slate-100 text-slate-700"}>
                {enabled ? "fallback 허용" : "기본 비활성"}
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-5">
            {loading ? (
              <div className="h-48 animate-pulse rounded-lg bg-slate-100" />
            ) : (
              <>
                <label className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
                  <input
                    type="checkbox"
                    className="mt-1 size-4 accent-blue-600"
                    checked={enabled}
                    onChange={(event) => setEnabled(event.target.checked)}
                  />
                  <span className="min-w-0">
                    <span className="block font-semibold text-slate-900">OpenAI fallback 전역 허용</span>
                    <span className="mt-1 block text-sm leading-6 text-slate-500">
                      이 값이 꺼져 있으면 아래 stage가 선택되어 있어도 OpenAI fallback은 호출되지 않습니다.
                    </span>
                  </span>
                </label>

                <div className="grid gap-3 md:grid-cols-2">
                  {availableStages.map((stage) => {
                    const meta = STAGE_LABELS[stage] ?? { title: stage, description: "승인된 fallback 단계입니다." };
                    return (
                      <label
                        key={stage}
                        className={`flex min-h-32 items-start gap-3 rounded-lg border p-4 transition-colors ${
                          enabled && allowedStages.includes(stage)
                            ? "border-amber-300 bg-amber-50"
                            : "border-slate-200 bg-white"
                        }`}
                      >
                        <input
                          type="checkbox"
                          className="mt-1 size-4 accent-blue-600"
                          checked={allowedStages.includes(stage)}
                          disabled={!enabled}
                          onChange={() => toggleStage(stage)}
                        />
                        <span className="min-w-0">
                          <span className="block font-semibold text-slate-900">{meta.title}</span>
                          <span className="mt-1 block text-sm leading-6 text-slate-500">{meta.description}</span>
                          <code className="mt-2 inline-block max-w-full rounded bg-slate-100 px-2 py-1 text-xs text-slate-600">
                            {stage}
                          </code>
                        </span>
                      </label>
                    );
                  })}
                </div>

                <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="text-sm text-slate-600">
                    <div className="font-semibold text-slate-900">현재 적용 출처: {sourceLabel(setting?.source ?? "DEFAULT")}</div>
                    <div className="mt-1">
                      저장 후에는 DB 관리자 설정이 환경변수보다 우선합니다. 비용/사용량은 B AI 사용량 로그에서 확인합니다.
                    </div>
                  </div>
                  <Button
                    className="bg-blue-600 text-white hover:bg-blue-700"
                    disabled={saving || loading || !dirty}
                    onClick={() => void save()}
                  >
                    {saving ? <RefreshCw className="size-4 animate-spin" /> : <Save className="size-4" />}
                    저장
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white">
          <CardHeader className="gap-2">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <CardTitle className="text-lg font-bold text-slate-950">공고 업로드 파일 크기 한도</CardTitle>
                <p className="mt-1 text-sm leading-6 text-slate-500">
                  공고(PDF/이미지) 업로드 파일의 최대 크기입니다. 서버 multipart 상한(상위 안전 상한) 안에서 실효 한도가 됩니다.
                </p>
              </div>
              <Badge className="bg-slate-100 text-slate-700">
                {uploadSetting ? `${uploadCurrentMb}MB` : "-"}
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-5">
            {loading ? (
              <div className="h-24 animate-pulse rounded-lg bg-slate-100" />
            ) : (
              <>
                <label className="flex flex-col gap-2">
                  <span className="font-semibold text-slate-900">최대 업로드 크기 (MB)</span>
                  <input
                    type="number"
                    min={uploadMinMb}
                    max={uploadMaxMb}
                    step={1}
                    className="w-40 rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    value={uploadMb}
                    onChange={(event) => setUploadMb(event.target.value)}
                  />
                  <span className="text-sm text-slate-500">
                    {uploadMinMb}MB ~ {uploadMaxMb}MB 범위에서 설정할 수 있습니다.
                  </span>
                </label>

                <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="text-sm text-slate-600">
                    <div className="font-semibold text-slate-900">현재 적용 출처: {sourceLabel(uploadSetting?.source ?? "PROPERTIES")}</div>
                    <div className="mt-1">저장하면 DB 관리자 설정이 환경변수 기본값보다 우선합니다.</div>
                  </div>
                  <Button
                    className="bg-blue-600 text-white hover:bg-blue-700"
                    disabled={uploadSaving || loading || !uploadDirty}
                    onClick={() => void saveUpload()}
                  >
                    {uploadSaving ? <RefreshCw className="size-4 animate-spin" /> : <Save className="size-4" />}
                    저장
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <div className="flex gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" />
          <p>
            품질 실패만으로 OpenAI fallback을 자동 호출하지 않습니다. 전역 허용과 stage allowlist가 모두 켜진 경우에만 백업 경로가 열립니다.
          </p>
        </div>
      </div>
    </AdminShell>
  );
}
