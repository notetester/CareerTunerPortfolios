import { useEffect, useState } from "react";
import { RefreshCw, Save, SlidersHorizontal } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import AdminShell from "../../../components/AdminShell";
import { getJobPostingUploadLimitSetting, updateJobPostingUploadLimitSetting } from "../api";
import type { AdminJobPostingUploadLimitSetting } from "../types";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

function sourceLabel(source: string): string {
  return source === "DATABASE"
    ? "관리자 저장값"
    : source === "PROPERTIES"
      ? "환경변수 초기값"
      : "기본값";
}

export function AdminAiSettingsPage() {
  const { canUpdate } = useAdminDomainAuthorization("AI");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);
  const [uploadSetting, setUploadSetting] = useState<AdminJobPostingUploadLimitSetting | null>(null);
  const [uploadMb, setUploadMb] = useState("");
  const [uploadSaving, setUploadSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    setSavedMessage(null);
    try {
      const uploadResponse = await getJobPostingUploadLimitSetting();
      setUploadSetting(uploadResponse);
      setUploadMb(String(Math.round(uploadResponse.maxBytes / (1024 * 1024))));
    } catch (err) {
      setError(err instanceof Error ? err.message : "설정을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const uploadMinMb = uploadSetting ? Math.round(uploadSetting.minBytes / (1024 * 1024)) : 1;
  const uploadMaxMb = uploadSetting ? Math.round(uploadSetting.maxAllowedBytes / (1024 * 1024)) : 20;
  const uploadCurrentMb = uploadSetting ? Math.round(uploadSetting.maxBytes / (1024 * 1024)) : 0;
  const uploadDirty = uploadSetting != null && uploadMb !== "" && Number(uploadMb) !== uploadCurrentMb;

  const saveUpload = async () => {
    if (!canUpdate) return;
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

  return (
    <AdminShell
      active="ai-settings"
      breadcrumb="공고 업로드 설정"
      title="공고 업로드 설정"
      icon={SlidersHorizontal}
      desc="공고(PDF/이미지) 업로드 파일 크기 한도를 관리합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading || uploadSaving}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-5">
        {error && <div className="break-words rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {savedMessage && <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{savedMessage}</div>}

        <Card className="border-slate-200 bg-card">
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
                    disabled={!canUpdate}
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
                  {canUpdate && (
                    <Button
                      className="bg-blue-600 text-white hover:bg-blue-700"
                      disabled={uploadSaving || loading || !uploadDirty}
                      onClick={() => void saveUpload()}
                    >
                      {uploadSaving ? <RefreshCw className="size-4 animate-spin" /> : <Save className="size-4" />}
                      저장
                    </Button>
                  )}
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </AdminShell>
  );
}
