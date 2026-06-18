import { useState } from "react";
import { AlertTriangle, CalendarDays, FileType, Loader2, Pencil, RefreshCw, Save, Star, Trash2, X } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/app/components/ui/alert-dialog";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import type {
  ApplicationCase,
  ApplicationCaseExtraction,
  ApplicationSourceType,
  ApplicationStatus,
  UpdateApplicationCaseRequest,
} from "../types/applicationCase";
import {
  APPLICATION_SOURCE_OPTIONS,
  APPLICATION_STATUS_OPTIONS,
  getApplicationSourceLabel,
} from "../types/applicationCase";
import { formatKoreaDate } from "../utils/dateFormat";
import { ApplicationExtractionBadge } from "./ApplicationExtractionBadge";
import { ApplicationStatusBadge } from "./ApplicationStatusBadge";

interface ApplicationOverviewPanelProps {
  applicationCase: ApplicationCase;
  extraction?: ApplicationCaseExtraction | null;
  retryingExtraction?: boolean;
  onUpdate(request: UpdateApplicationCaseRequest): Promise<void>;
  onRetryExtraction?(): Promise<ApplicationCaseExtraction | null>;
  onDelete?(): Promise<void>;
}

function formatDate(value: string | null): string {
  return formatKoreaDate(value);
}

interface BasicFormState {
  companyName: string;
  jobTitle: string;
  deadlineDate: string;
  sourceType: ApplicationSourceType;
}

export function ApplicationOverviewPanel({
  applicationCase,
  extraction,
  retryingExtraction = false,
  onUpdate,
  onRetryExtraction,
  onDelete,
}: ApplicationOverviewPanelProps) {
  const [updating, setUpdating] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<BasicFormState>({
    companyName: applicationCase.companyName,
    jobTitle: applicationCase.jobTitle,
    deadlineDate: applicationCase.deadlineDate ?? "",
    sourceType: applicationCase.sourceType,
  });
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setForm({
      companyName: applicationCase.companyName,
      jobTitle: applicationCase.jobTitle,
      deadlineDate: applicationCase.deadlineDate ?? "",
      sourceType: applicationCase.sourceType,
    });
    setError(null);
  };

  const setField = <Key extends keyof BasicFormState>(key: Key, value: BasicFormState[Key]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const update = async (request: UpdateApplicationCaseRequest) => {
    setUpdating(true);
    setError(null);
    try {
      await onUpdate(request);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 수정하지 못했습니다.");
    } finally {
      setUpdating(false);
    }
  };

  const handleSaveBasicInfo = async () => {
    const companyName = form.companyName.trim();
    const jobTitle = form.jobTitle.trim();

    if (!companyName || !jobTitle) {
      setError("기업명과 직무명을 입력해주세요.");
      return;
    }

    await update({
      companyName,
      jobTitle,
      deadlineDate: form.deadlineDate || null,
      clearDeadlineDate: !form.deadlineDate,
      sourceType: form.sourceType,
    });
    setEditing(false);
  };

  const handleDelete = async () => {
    if (!onDelete) return;

    setDeleting(true);
    setError(null);
    try {
      await onDelete();
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 삭제함으로 이동하지 못했습니다.");
      setDeleting(false);
    }
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
      <Card className="border-slate-200 bg-card">
        <CardHeader className="gap-3">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="space-y-2">
              <CardTitle className="text-xl font-bold text-slate-950">
                {applicationCase.companyName}
              </CardTitle>
              <div className="text-sm font-medium text-slate-600">{applicationCase.jobTitle}</div>
              <div className="flex flex-wrap gap-2">
                <ApplicationStatusBadge status={applicationCase.status} />
                <ApplicationExtractionBadge extraction={extraction} />
                {applicationCase.favorite && (
                  <Badge variant="outline" className="border-amber-200 bg-amber-50 text-amber-700">
                    <Star className="size-3 fill-amber-500 text-amber-500" />
                    즐겨찾기
                  </Badge>
                )}
                {applicationCase.archived && (
                  <Badge variant="outline" className="border-slate-200 bg-slate-100 text-slate-600">
                    보관됨
                  </Badge>
                )}
              </div>
            </div>
            <div className="flex shrink-0 gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={updating || deleting}
                onClick={() => {
                  if (editing) {
                    resetForm();
                    setEditing(false);
                    return;
                  }
                  resetForm();
                  setEditing(true);
                }}
              >
                {editing ? <X className="size-4" /> : <Pencil className="size-4" />}
                {editing ? "취소" : "기본 정보 수정"}
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {editing ? (
            <div className="space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="detail-company-name">
                    기업명
                  </label>
                  <Input
                    id="detail-company-name"
                    value={form.companyName}
                    onChange={(event) => setField("companyName", event.target.value)}
                    disabled={updating}
                    autoComplete="organization"
                    className="bg-card"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="detail-job-title">
                    직무명
                  </label>
                  <Input
                    id="detail-job-title"
                    value={form.jobTitle}
                    onChange={(event) => setField("jobTitle", event.target.value)}
                    disabled={updating}
                    className="bg-card"
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700" htmlFor="detail-deadline-date">
                    마감일
                  </label>
                  <Input
                    id="detail-deadline-date"
                    type="date"
                    value={form.deadlineDate}
                    onChange={(event) => setField("deadlineDate", event.target.value)}
                    disabled={updating}
                    className="bg-card"
                  />
                  <p className="text-xs leading-5 text-slate-500">
                    마감일이 없거나 상시채용이면 비워두세요.
                  </p>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700">등록 방식</label>
                  <Select
                    value={form.sourceType}
                    disabled={updating}
                    onValueChange={(value) => setField("sourceType", value as ApplicationSourceType)}
                  >
                    <SelectTrigger className="bg-card">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {APPLICATION_SOURCE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button
                  type="button"
                  variant="outline"
                  disabled={updating}
                  onClick={() => {
                    resetForm();
                    setEditing(false);
                  }}
                >
                  취소
                </Button>
                <Button
                  type="button"
                  className="bg-blue-600 text-white hover:bg-blue-700"
                  disabled={updating}
                  onClick={() => void handleSaveBasicInfo()}
                >
                  {updating ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                  저장
                </Button>
              </div>
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-500">
                  <CalendarDays className="size-4" />
                  등록일
                </div>
                <div className="text-sm font-semibold text-slate-900">{formatDate(applicationCase.createdAt)}</div>
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-500">
                  <CalendarDays className="size-4" />
                  마감일
                </div>
                <div className="text-sm font-semibold text-slate-900">
                  {formatKoreaDate(applicationCase.deadlineDate, "마감일 없음/상시채용")}
                </div>
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-500">
                  <FileType className="size-4" />
                  등록 방식
                </div>
                <div className="text-sm font-semibold text-slate-900">
                  {getApplicationSourceLabel(applicationCase.sourceType)}
                </div>
              </div>
            </div>
          )}

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="border-slate-200 bg-card">
        <CardHeader>
          <CardTitle className="text-base font-bold text-slate-900">지원 건 상태</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-500">상태</label>
            <Select
              value={applicationCase.status}
              disabled={updating}
              onValueChange={(value) => void update({ status: value as ApplicationStatus })}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {APPLICATION_STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {extraction && (
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <div className="mb-2 text-xs font-semibold text-slate-500">공고문 추출</div>
              <div className="flex flex-wrap items-center gap-2">
                <ApplicationExtractionBadge extraction={extraction} />
                {extraction.status === "FAILED" && onRetryExtraction && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-7 border-red-200 px-2 text-xs text-red-700 hover:bg-red-50 hover:text-red-800"
                    disabled={retryingExtraction}
                    onClick={() => void onRetryExtraction()}
                  >
                    {retryingExtraction ? <Loader2 className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
                    다시 추출
                  </Button>
                )}
              </div>
              {extraction.status === "FAILED" && extraction.errorMessage && (
                <p className="mt-2 text-xs leading-5 text-red-600">{extraction.errorMessage}</p>
              )}
            </div>
          )}

          <label className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
            <Checkbox
              checked={applicationCase.favorite}
              disabled={updating}
              onCheckedChange={(checked) => void update({ favorite: Boolean(checked) })}
            />
            즐겨찾기
          </label>

          <label className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
            <Checkbox
              checked={applicationCase.archived}
              disabled={updating}
              onCheckedChange={(checked) => void update({ archived: Boolean(checked) })}
            />
            보관
          </label>

          <div className="rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-500">
            최종 수정: {formatDate(applicationCase.updatedAt)}
          </div>

          {onDelete && (
            <div className="rounded-lg border border-red-100 bg-red-50 p-3">
              <div className="flex items-start gap-2">
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-red-600" />
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-red-700">삭제함 이동</div>
                  <p className="mt-1 text-xs leading-5 text-red-600">
                    삭제함으로 이동하면 이 지원 건은 활성 목록에서 숨겨집니다. 30일 동안 삭제함에서 복원할 수 있고 연결된 공고문과 분석 결과는 즉시 물리 삭제되지 않습니다.
                  </p>
                </div>
              </div>
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="mt-3 w-full border-red-200 text-red-600 hover:bg-red-100 hover:text-red-700"
                    disabled={deleting || updating}
                  >
                    <Trash2 className="size-4" />
                    {deleting ? "이동 중" : "삭제함으로 이동"}
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>지원 건을 삭제함으로 이동할까요?</AlertDialogTitle>
                    <AlertDialogDescription>
                      이 지원 건은 활성 목록에서 숨겨지고 30일 동안 삭제함 목록에서 복원할 수 있습니다. 연결된 공고문, 공고 분석, 기업 분석 결과는 즉시 물리 삭제되지 않습니다.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel disabled={deleting}>취소</AlertDialogCancel>
                    <AlertDialogAction
                      className="bg-red-600 text-white hover:bg-red-700"
                      disabled={deleting}
                      onClick={() => void handleDelete()}
                    >
                      {deleting ? "이동 중" : "삭제함으로 이동"}
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
