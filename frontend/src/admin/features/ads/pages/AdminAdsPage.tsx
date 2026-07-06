import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Megaphone, MousePointerClick, Pencil, Plus, RefreshCw, Trash2, Upload } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Switch } from "@/app/components/ui/switch";
import {
  AdminListFooter,
  AdminListToolbar,
  useAdminListTools,
  type AdminListColumn,
} from "@/admin/components/AdminListTools";
import {
  createAd,
  deleteAd,
  listAds,
  toggleAdActive,
  updateAd,
  uploadAdImage,
} from "../api";
import {
  PLACEMENT_LABELS,
  PLATFORM_LABELS,
  type AdminAd,
  type AdminAdPayload,
  type AdPlacement,
  type AdTargetPlatform,
} from "../types/adminAd";

const PLACEMENTS = Object.keys(PLACEMENT_LABELS) as AdPlacement[];
const PLATFORMS = Object.keys(PLATFORM_LABELS) as AdTargetPlatform[];
const selectClass = "h-10 rounded-md border border-slate-200 bg-white px-3 text-sm";

const EMPTY_FORM: AdminAdPayload = {
  title: "",
  imageFileId: null,
  linkUrl: "",
  placement: "HOME_BANNER",
  targetPlatform: "ALL",
  startAt: null,
  endAt: null,
  active: true,
  priority: 0,
  weight: 1,
};

/** ISO(서버) → datetime-local 입력값. */
function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** datetime-local 입력값 → ISO(서버). 빈 값은 null. */
function toIso(local: string): string | null {
  if (!local) return null;
  const date = new Date(local);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function formatDateTime(value: string | null): string {
  if (!value) return "무제한";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
}

const AD_COLUMNS: AdminListColumn<AdminAd>[] = [
  { id: "title", label: "제목", getText: (row) => row.title, sortable: true },
  { id: "placement", label: "배치", getText: (row) => PLACEMENT_LABELS[row.placement], sortable: true },
  { id: "targetPlatform", label: "플랫폼", getText: (row) => PLATFORM_LABELS[row.targetPlatform], sortable: true },
  { id: "active", label: "활성", getText: (row) => (row.active ? "활성" : "비활성"), sortable: true },
  { id: "priority", label: "우선순위", getText: (row) => row.priority, sortable: true },
  { id: "weight", label: "가중치", getText: (row) => row.weight, sortable: true },
  { id: "impressionCount", label: "노출", getText: (row) => row.impressionCount, sortable: true },
  { id: "clickCount", label: "클릭", getText: (row) => row.clickCount, sortable: true },
  { id: "ctr", label: "CTR", getText: (row) => `${row.ctr}%`, sortable: true },
  { id: "startAt", label: "게재 시작", getText: (row) => formatDateTime(row.startAt), sortValue: (row) => row.startAt, sortable: true },
  { id: "endAt", label: "게재 종료", getText: (row) => formatDateTime(row.endAt), sortValue: (row) => row.endAt, sortable: true },
];

/** 광고 관리 콘솔 — 배치/기간/플랫폼/활성/우선순위 편집 + 노출·클릭 통계. */
export function AdminAdsPage() {
  const [rows, setRows] = useState<AdminAd[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [placementFilter, setPlacementFilter] = useState("");
  const [activeOnly, setActiveOnly] = useState(false);

  /** null=폼 닫힘, {id:null}=신규 등록, {id:number}=수정. */
  const [editing, setEditing] = useState<AdminAd | null | undefined>(undefined);
  const [form, setForm] = useState<AdminAdPayload>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listAds({
        placement: placementFilter || undefined,
        activeOnly: activeOnly || undefined,
      });
      setRows(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "광고 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [placementFilter, activeOnly]);

  useEffect(() => {
    void load();
  }, [load]);

  const totals = useMemo(() => {
    return rows.reduce(
      (acc, ad) => {
        acc.impressions += ad.impressionCount;
        acc.clicks += ad.clickCount;
        return acc;
      },
      { impressions: 0, clicks: 0 },
    );
  }, [rows]);

  const list = useAdminListTools(rows, {
    columns: AD_COLUMNS,
    getRowId: (row) => row.id,
    defaultSortId: "priority",
    defaultSortDir: "desc",
  });

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setImagePreview(null);
  };

  const openEdit = (ad: AdminAd) => {
    setEditing(ad);
    setForm({
      title: ad.title,
      imageFileId: ad.imageFileId,
      linkUrl: ad.linkUrl ?? "",
      placement: ad.placement,
      targetPlatform: ad.targetPlatform,
      startAt: ad.startAt,
      endAt: ad.endAt,
      active: ad.active,
      priority: ad.priority,
      weight: ad.weight,
    });
    setImagePreview(ad.imageUrl);
  };

  const closeForm = () => {
    setEditing(undefined);
    setForm(EMPTY_FORM);
    setImagePreview(null);
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    setError(null);
    try {
      const fileId = await uploadAdImage(file);
      setForm((prev) => ({ ...prev, imageFileId: fileId }));
      setImagePreview(URL.createObjectURL(file));
    } catch (e) {
      setError(e instanceof Error ? e.message : "이미지 업로드에 실패했습니다.");
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (!form.title.trim()) {
      setError("제목은 필수입니다.");
      return;
    }
    setSaving(true);
    setError(null);
    const payload: AdminAdPayload = {
      ...form,
      linkUrl: form.linkUrl?.trim() ? form.linkUrl.trim() : null,
    };
    try {
      if (editing) {
        await updateAd(editing.id, payload);
        setMessage("광고를 수정했습니다.");
      } else {
        await createAd(payload);
        setMessage("광고를 등록했습니다.");
      }
      closeForm();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (ad: AdminAd) => {
    try {
      await toggleAdActive(ad.id, !ad.active);
      setRows((prev) => prev.map((r) => (r.id === ad.id ? { ...r, active: !ad.active } : r)));
    } catch (e) {
      setError(e instanceof Error ? e.message : "활성 상태 변경에 실패했습니다.");
    }
  };

  const handleDelete = async (ad: AdminAd) => {
    if (!window.confirm(`'${ad.title}' 광고를 삭제할까요?`)) return;
    try {
      await deleteAd(ad.id);
      setMessage("광고를 삭제했습니다.");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "삭제에 실패했습니다.");
    }
  };

  const formOpen = editing !== undefined;

  return (
    <AdminShell
      active="ads"
      breadcrumb="운영 · 광고 관리"
      title="광고 관리"
      icon={Megaphone}
      desc="배치·기간·플랫폼별 광고를 관리하고 노출·클릭 통계를 확인합니다. 유료플랜 사용자에게는 노출되지 않습니다."
      actions={
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load()}>
            <RefreshCw className="mr-1 h-4 w-4" /> 새로고침
          </Button>
          <Button size="sm" onClick={openCreate}>
            <Plus className="mr-1 h-4 w-4" /> 광고 등록
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* 요약 통계 */}
        <div className="grid grid-cols-3 gap-3">
          <StatCard label="등록 광고" value={rows.length} />
          <StatCard label="총 노출" value={totals.impressions} />
          <StatCard label="총 클릭" value={totals.clicks} />
        </div>

        {/* 필터 */}
        <div className="flex flex-wrap items-center gap-3">
          <select
            className={selectClass}
            value={placementFilter}
            onChange={(e) => setPlacementFilter(e.target.value)}
          >
            <option value="">전체 배치</option>
            {PLACEMENTS.map((p) => (
              <option key={p} value={p}>
                {PLACEMENT_LABELS[p]}
              </option>
            ))}
          </select>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <Switch checked={activeOnly} onCheckedChange={setActiveOnly} />
            활성만 보기
          </label>
        </div>

        {message && (
          <div className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">{message}</div>
        )}
        {error && (
          <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>
        )}

        {/* 등록/수정 폼 */}
        {formOpen && (
          <Card>
            <CardContent className="space-y-4 p-5">
              <h3 className="text-base font-semibold text-slate-800">
                {editing ? "광고 수정" : "광고 등록"}
              </h3>

              <Field label="제목">
                <Input
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="광고 제목(대체 텍스트로도 사용)"
                  maxLength={200}
                />
              </Field>

              <Field label="이미지">
                <div className="flex items-center gap-3">
                  {imagePreview ? (
                    <img
                      src={imagePreview}
                      alt="미리보기"
                      className="h-16 w-28 rounded border border-slate-200 object-cover"
                    />
                  ) : (
                    <div className="flex h-16 w-28 items-center justify-center rounded border border-dashed border-slate-300 text-xs text-slate-400">
                      이미지 없음
                    </div>
                  )}
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) void handleUpload(file);
                      e.target.value = "";
                    }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <Upload className="mr-1 h-4 w-4" />
                    {uploading ? "업로드 중..." : "이미지 선택"}
                  </Button>
                  {form.imageFileId && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setForm({ ...form, imageFileId: null });
                        setImagePreview(null);
                      }}
                    >
                      제거
                    </Button>
                  )}
                </div>
              </Field>

              <Field label="링크 URL">
                <Input
                  value={form.linkUrl ?? ""}
                  onChange={(e) => setForm({ ...form, linkUrl: e.target.value })}
                  placeholder="https://... (비우면 클릭 비활성)"
                  maxLength={1000}
                />
              </Field>

              <div className="grid grid-cols-2 gap-4">
                <Field label="배치">
                  <select
                    className={selectClass}
                    value={form.placement}
                    onChange={(e) => setForm({ ...form, placement: e.target.value as AdPlacement })}
                  >
                    {PLACEMENTS.map((p) => (
                      <option key={p} value={p}>
                        {PLACEMENT_LABELS[p]}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="타겟 플랫폼">
                  <select
                    className={selectClass}
                    value={form.targetPlatform}
                    onChange={(e) =>
                      setForm({ ...form, targetPlatform: e.target.value as AdTargetPlatform })
                    }
                  >
                    {PLATFORMS.map((p) => (
                      <option key={p} value={p}>
                        {PLATFORM_LABELS[p]}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <Field label="게재 시작">
                  <Input
                    type="datetime-local"
                    value={toLocalInput(form.startAt)}
                    onChange={(e) => setForm({ ...form, startAt: toIso(e.target.value) })}
                  />
                </Field>
                <Field label="게재 종료">
                  <Input
                    type="datetime-local"
                    value={toLocalInput(form.endAt)}
                    onChange={(e) => setForm({ ...form, endAt: toIso(e.target.value) })}
                  />
                </Field>
              </div>

              <div className="grid grid-cols-3 gap-4">
                <Field label="우선순위">
                  <Input
                    type="number"
                    value={form.priority}
                    onChange={(e) => setForm({ ...form, priority: Number(e.target.value) || 0 })}
                  />
                </Field>
                <Field label="가중치(≥1)">
                  <Input
                    type="number"
                    min={1}
                    value={form.weight}
                    onChange={(e) => setForm({ ...form, weight: Math.max(1, Number(e.target.value) || 1) })}
                  />
                </Field>
                <Field label="활성">
                  <div className="flex h-10 items-center">
                    <Switch
                      checked={form.active}
                      onCheckedChange={(v) => setForm({ ...form, active: v })}
                    />
                  </div>
                </Field>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button variant="outline" onClick={closeForm} disabled={saving}>
                  취소
                </Button>
                <Button onClick={() => void handleSave()} disabled={saving || uploading}>
                  {saving ? "저장 중..." : "저장"}
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {/* 목록 */}
        {loading ? (
          <div className="py-10 text-center text-sm text-slate-500">불러오는 중...</div>
        ) : rows.length === 0 ? (
          <div className="py-10 text-center text-sm text-slate-500">등록된 광고가 없습니다.</div>
        ) : (
          <div className="space-y-3">
            <AdminListToolbar state={list} fileName="admin_ads" />
            {list.visibleRows.map((ad) => (
              <Card key={ad.id}>
                <CardContent className="flex items-center gap-4 p-4">
                  {ad.imageUrl ? (
                    <img
                      src={ad.imageUrl}
                      alt={ad.title}
                      className="h-14 w-24 shrink-0 rounded border border-slate-200 object-cover"
                    />
                  ) : (
                    <div className="flex h-14 w-24 shrink-0 items-center justify-center rounded border border-dashed border-slate-300 text-[11px] text-slate-400">
                      텍스트 배너
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate font-medium text-slate-800">{ad.title}</span>
                      {!ad.active && <Badge className="bg-slate-200 text-slate-600">비활성</Badge>}
                    </div>
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
                      <span>{PLACEMENT_LABELS[ad.placement]}</span>
                      <span>· {PLATFORM_LABELS[ad.targetPlatform]}</span>
                      <span>· 우선 {ad.priority} / 가중 {ad.weight}</span>
                      <span>
                        · {formatDateTime(ad.startAt)} ~ {formatDateTime(ad.endAt)}
                      </span>
                    </div>
                    <div className="mt-1 flex gap-4 text-xs text-slate-600">
                      <span>노출 {ad.impressionCount.toLocaleString()}</span>
                      <span className="flex items-center gap-1">
                        <MousePointerClick className="h-3 w-3" /> 클릭 {ad.clickCount.toLocaleString()}
                      </span>
                      <span>CTR {ad.ctr}%</span>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <Switch checked={ad.active} onCheckedChange={() => void handleToggle(ad)} />
                    <Button variant="ghost" size="icon" onClick={() => openEdit(ad)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => void handleDelete(ad)}
                      className="text-red-500 hover:text-red-600"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
            {list.visibleRows.length === 0 && (
              <div className="py-10 text-center text-sm text-slate-400">검색 조건에 맞는 광고가 없습니다.</div>
            )}
            <AdminListFooter state={list} />
          </div>
        )}
      </div>
    </AdminShell>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <CardContent className="p-4">
        <div className="text-xs text-slate-500">{label}</div>
        <div className="mt-1 text-2xl font-semibold text-slate-800">{value.toLocaleString()}</div>
      </CardContent>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-600">{label}</span>
      {children}
    </label>
  );
}

export default AdminAdsPage;
