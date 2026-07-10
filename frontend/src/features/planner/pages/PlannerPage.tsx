import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import {
  Bell,
  CalendarClock,
  CheckCircle2,
  Clock3,
  Edit3,
  Eye,
  EyeOff,
  Layers,
  Link as LinkIcon,
  Loader2,
  Pin,
  Plus,
  Save,
  StickyNote,
  Trash2,
  X,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Slider } from "@/app/components/ui/slider";
import { Switch } from "@/app/components/ui/switch";
import { Textarea } from "@/app/components/ui/textarea";
import { toast } from "@/features/notification/components/toast";
import {
  createPlannerMemo,
  createPlannerScheduleItem,
  deletePlannerMemo,
  deletePlannerScheduleItem,
  getPlannerDashboard,
  updatePlannerMemo,
  updatePlannerScheduleItem,
} from "../api/plannerApi";
import type {
  PlannerDashboard,
  PlannerMemo,
  PlannerMemoRequest,
  PlannerReminderChannel,
  PlannerScheduleItem,
  PlannerScheduleItemRequest,
  PlannerScheduleReminderRequest,
  PlannerTimingPrecision,
} from "../types/planner";
import { REMINDER_CHANNEL_LABELS } from "../types/planner";
import "../styles/planner.css";

type PlannerTab = "schedule" | "memo" | "overlay";

const CHANNELS: PlannerReminderChannel[] = [
  "WEB_TOAST",
  "BROWSER",
  "WEB_PUSH",
  "EMAIL",
  "MOBILE_VIBRATE",
  "MOBILE_SOUND",
  "MOBILE_SOUND_VIBRATE",
  "DESKTOP_SOUND",
  "DESKTOP_TOAST",
  "DESKTOP_TASKBAR",
];

const PRECISIONS: { value: PlannerTimingPrecision; label: string }[] = [
  { value: "YEAR", label: "년 단위" },
  { value: "MONTH", label: "월 단위" },
  { value: "DAY", label: "일/하루종일" },
  { value: "AM_PM", label: "오전/오후" },
  { value: "HOUR", label: "시간" },
  { value: "MINUTE", label: "분" },
  { value: "SECOND", label: "초" },
];

const DEFAULT_CHANNELS: PlannerReminderChannel[] = ["WEB_TOAST", "BROWSER", "WEB_PUSH"];

interface ScheduleForm extends PlannerScheduleItemRequest {
  reminderMode: "offset" | "exact";
}

const emptyMemo: PlannerMemoRequest = {
  title: "",
  content: "",
  color: "yellow",
  pinned: false,
  overlayVisible: false,
  opacity: 1,
  applicationCaseId: null,
  fitAnalysisId: null,
};

function emptySchedule(): ScheduleForm {
  const start = new Date();
  start.setHours(start.getHours() + 1, 0, 0, 0);
  const end = new Date(start);
  end.setHours(end.getHours() + 1);
  return {
    title: "",
    description: "",
    kind: "TASK",
    status: "PLANNED",
    allDay: false,
    timingPrecision: "MINUTE",
    startAt: toInputValue(start),
    endAt: toInputValue(end),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Seoul",
    applicationCaseId: null,
    fitAnalysisId: null,
    sourceType: "MANUAL",
    sourceRef: null,
    sourceSnapshotJson: null,
    overlayVisible: false,
    opacity: 1,
    pinned: false,
    clickThrough: false,
    reminders: [defaultReminder(60)],
    reminderMode: "offset",
  };
}

function parsePlannerItemId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

export function PlannerPage() {
  const [searchParams] = useSearchParams();
  const requestedItemId = parsePlannerItemId(searchParams.get("item"));
  const [tab, setTab] = useState<PlannerTab>("schedule");
  const [data, setData] = useState<PlannerDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [memoForm, setMemoForm] = useState<PlannerMemoRequest>(emptyMemo);
  const [editingMemoId, setEditingMemoId] = useState<number | null>(null);
  const [scheduleForm, setScheduleForm] = useState<ScheduleForm>(emptySchedule);
  const [editingScheduleId, setEditingScheduleId] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const now = new Date();
      const from = new Date(now);
      from.setDate(from.getDate() - 30);
      const to = new Date(now);
      to.setDate(to.getDate() + 120);
      setData(await getPlannerDashboard(toApiDateTime(from), toApiDateTime(to)));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "플래너 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  useEffect(() => {
    const requested = searchParams.get("tab");
    if (requestedItemId != null) {
      setTab("schedule");
    } else if (requested === "memo" || requested === "overlay" || requested === "schedule") {
      setTab(requested);
    }
  }, [searchParams, requestedItemId]);

  // 알림의 /planner?item={id} 딥링크가 해당 일정을 실제로 보여주도록 렌더 후 스크롤한다.
  useEffect(() => {
    if (loading || tab !== "schedule" || requestedItemId == null) return;
    if (!data?.scheduleItems.some((item) => item.id === requestedItemId)) return;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById(`planner-schedule-item-${requestedItemId}`)?.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [loading, tab, requestedItemId, data?.scheduleItems]);

  const grouped = useMemo(() => groupByDay(data?.scheduleItems ?? []), [data?.scheduleItems]);
  const overlayCount = (data?.memos.filter((memo) => memo.overlayVisible).length ?? 0)
    + (data?.scheduleItems.filter((item) => item.overlayVisible).length ?? 0);

  const saveMemo = async () => {
    setSaving(true);
    try {
      if (editingMemoId) await updatePlannerMemo(editingMemoId, memoForm);
      else await createPlannerMemo(memoForm);
      toast.success(editingMemoId ? "메모를 수정했습니다." : "메모를 추가했습니다.");
      setMemoForm(emptyMemo);
      setEditingMemoId(null);
      await load();
    } catch (requestError) {
      toast.error(requestError instanceof Error ? requestError.message : "메모를 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const saveSchedule = async () => {
    if (!scheduleForm.title.trim()) return;
    setSaving(true);
    try {
      const request: PlannerScheduleItemRequest = {
        ...scheduleForm,
        title: scheduleForm.title.trim(),
        description: scheduleForm.description?.trim() || null,
        endAt: scheduleForm.endAt || null,
        reminders: scheduleForm.reminders.map((reminder) =>
          scheduleForm.reminderMode === "offset" ? { ...reminder, remindAt: null } : { ...reminder, offsetMinutes: null },
        ),
      };
      if (editingScheduleId) await updatePlannerScheduleItem(editingScheduleId, request);
      else await createPlannerScheduleItem(request);
      toast.success(editingScheduleId ? "일정을 수정했습니다." : "일정을 추가했습니다.");
      setScheduleForm(emptySchedule());
      setEditingScheduleId(null);
      await load();
    } catch (requestError) {
      toast.error(requestError instanceof Error ? requestError.message : "일정을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const editMemo = (memo: PlannerMemo) => {
    setEditingMemoId(memo.id);
    setMemoForm({
      title: memo.title ?? "",
      content: memo.content ?? "",
      color: memo.color,
      pinned: memo.pinned,
      overlayVisible: memo.overlayVisible,
      opacity: memo.opacity,
      applicationCaseId: memo.applicationCaseId,
      fitAnalysisId: memo.fitAnalysisId,
    });
    setTab("memo");
  };

  const editSchedule = (item: PlannerScheduleItem) => {
    setEditingScheduleId(item.id);
    setScheduleForm({
      ...itemToRequest(item),
      reminderMode: item.reminders.some((reminder) => reminder.remindAt) ? "exact" : "offset",
    });
    setTab("schedule");
  };

  const removeMemo = async (memoId: number) => {
    await deletePlannerMemo(memoId);
    toast.success("메모를 삭제했습니다.");
    await load();
  };

  const removeSchedule = async (itemId: number) => {
    await deletePlannerScheduleItem(itemId);
    toast.success("일정을 삭제했습니다.");
    await load();
  };

  const toggleScheduleStatus = async (item: PlannerScheduleItem) => {
    await updatePlannerScheduleItem(item.id, {
      ...itemToRequest(item),
      status: item.status === "DONE" ? "PLANNED" : "DONE",
    });
    await load();
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1400px] space-y-5 px-4 py-8 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <CalendarClock className="size-6 text-blue-600" />
              메모와 일정 플래너
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              수동 일정, C 전략 추천 일정, 고정 메모와 알림을 한 곳에서 관리합니다.
            </p>
          </div>
          <Badge className="bg-blue-100 text-blue-700">오버레이 {overlayCount}개 표시 중</Badge>
        </div>

        <div className="flex overflow-x-auto rounded-lg border border-slate-200 bg-card p-1">
          {[
            { key: "schedule" as const, label: "일정", icon: CalendarClock },
            { key: "memo" as const, label: "메모", icon: StickyNote },
            { key: "overlay" as const, label: "오버레이", icon: Layers },
          ].map((item) => (
            <button
              key={item.key}
              onClick={() => setTab(item.key)}
              className={`flex shrink-0 items-center gap-1.5 rounded-md px-3 py-2 text-sm font-semibold ${
                tab === item.key ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-50"
              }`}
            >
              <item.icon className="size-4" />
              {item.label}
            </button>
          ))}
        </div>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
        {loading && (
          <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-card p-4 text-sm text-slate-600">
            <Loader2 className="size-4 animate-spin text-blue-600" />
            플래너를 불러오는 중입니다.
          </div>
        )}

        {!loading && tab === "schedule" && (
          <div className="grid gap-5 lg:grid-cols-[420px_minmax(0,1fr)]">
            <ScheduleEditor
              form={scheduleForm}
              setForm={setScheduleForm}
              editing={editingScheduleId != null}
              saving={saving}
              onSave={() => void saveSchedule()}
              onCancel={() => { setEditingScheduleId(null); setScheduleForm(emptySchedule()); }}
            />
            <ScheduleTimeline
              grouped={grouped}
              focusItemId={requestedItemId}
              onEdit={editSchedule}
              onDelete={(id) => void removeSchedule(id)}
              onToggleDone={(item) => void toggleScheduleStatus(item)}
            />
          </div>
        )}

        {!loading && tab === "memo" && (
          <div className="grid gap-5 lg:grid-cols-[420px_minmax(0,1fr)]">
            <MemoEditor
              form={memoForm}
              setForm={setMemoForm}
              editing={editingMemoId != null}
              saving={saving}
              onSave={() => void saveMemo()}
              onCancel={() => { setEditingMemoId(null); setMemoForm(emptyMemo); }}
            />
            <MemoList memos={data?.memos ?? []} onEdit={editMemo} onDelete={(id) => void removeMemo(id)} />
          </div>
        )}

        {!loading && tab === "overlay" && (
          <OverlayManager
            data={data}
            onEditMemo={editMemo}
            onEditSchedule={editSchedule}
            onRefresh={() => void load()}
          />
        )}
      </div>
    </div>
  );
}

function ScheduleEditor({
  form,
  setForm,
  editing,
  saving,
  onSave,
  onCancel,
}: {
  form: ScheduleForm;
  setForm: (updater: ScheduleForm | ((current: ScheduleForm) => ScheduleForm)) => void;
  editing: boolean;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Plus className="size-4 text-blue-600" />
          {editing ? "일정 수정" : "새 일정"}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <label className="block text-xs font-semibold text-slate-600">
          제목
          <Input value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} className="mt-1" />
        </label>
        <label className="block text-xs font-semibold text-slate-600">
          내용
          <Textarea rows={4} value={form.description ?? ""} onChange={(event) => setForm({ ...form, description: event.target.value })} className="mt-1" />
        </label>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-xs font-semibold text-slate-600">
            시작
            <Input type="datetime-local" value={form.startAt} onChange={(event) => setForm({ ...form, startAt: event.target.value })} className="mt-1" />
          </label>
          <label className="text-xs font-semibold text-slate-600">
            종료
            <Input type="datetime-local" value={form.endAt ?? ""} onChange={(event) => setForm({ ...form, endAt: event.target.value || null })} className="mt-1" />
          </label>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-xs font-semibold text-slate-600">
            정밀도
            <select value={form.timingPrecision} onChange={(event) => setForm({ ...form, timingPrecision: event.target.value })} className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-input-background px-3 text-sm">
              {PRECISIONS.map((precision) => <option key={precision.value} value={precision.value}>{precision.label}</option>)}
            </select>
          </label>
          <label className="text-xs font-semibold text-slate-600">
            유형
            <select value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value })} className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-input-background px-3 text-sm">
              <option value="TASK">할 일</option>
              <option value="EVENT">이벤트</option>
              <option value="DEADLINE">마감</option>
              <option value="STRATEGY">전략 일정</option>
            </select>
          </label>
        </div>
        <div className="grid gap-3 rounded-lg border border-slate-100 p-3">
          {[
            ["하루종일", "allDay"],
            ["오버레이 표시", "overlayVisible"],
            ["핀 고정", "pinned"],
            ["클릭 무시", "clickThrough"],
          ].map(([label, key]) => (
            <label key={key} className="flex items-center justify-between text-sm font-medium text-slate-700">
              {label}
              <Switch checked={Boolean(form[key as keyof ScheduleForm])} onCheckedChange={(checked) => setForm({ ...form, [key]: checked })} />
            </label>
          ))}
          <label className="text-xs font-semibold text-slate-600">
            투명도 {Math.round(form.opacity * 100)}%
            <Slider value={[form.opacity]} min={0.2} max={1} step={0.05} onValueChange={([value]) => setForm({ ...form, opacity: value })} className="mt-2" />
          </label>
        </div>
        <ReminderEditor form={form} setForm={setForm} />
        <div className="flex gap-2">
          <Button onClick={onSave} disabled={saving || !form.title.trim()} className="h-9">
            <Save className="size-4" />
            저장
          </Button>
          {editing && <Button variant="outline" onClick={onCancel} className="h-9"><X className="size-4" />취소</Button>}
        </div>
      </CardContent>
    </Card>
  );
}

function ReminderEditor({
  form,
  setForm,
}: {
  form: ScheduleForm;
  setForm: (updater: ScheduleForm | ((current: ScheduleForm) => ScheduleForm)) => void;
}) {
  const updateReminder = (index: number, patch: Partial<PlannerScheduleReminderRequest>) => {
    setForm((current) => ({
      ...current,
      reminders: current.reminders.map((reminder, i) => i === index ? { ...reminder, ...patch } : reminder),
    }));
  };
  return (
    <div className="rounded-lg border border-blue-100 bg-blue-50/40 p-3">
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 text-sm font-semibold text-blue-900">
          <Bell className="size-4" />
          알림
        </div>
        <Button variant="outline" className="h-8 bg-card" onClick={() => setForm((current) => ({ ...current, reminders: [...current.reminders, defaultReminder(10)] }))}>
          <Plus className="size-3.5" />
          추가
        </Button>
      </div>
      <div className="mb-3 grid grid-cols-2 gap-2">
        <button type="button" onClick={() => setForm({ ...form, reminderMode: "offset" })} className={`rounded-md px-3 py-2 text-xs font-semibold ${form.reminderMode === "offset" ? "bg-blue-600 text-white" : "bg-card text-blue-700"}`}>시작 전</button>
        <button type="button" onClick={() => setForm({ ...form, reminderMode: "exact" })} className={`rounded-md px-3 py-2 text-xs font-semibold ${form.reminderMode === "exact" ? "bg-blue-600 text-white" : "bg-card text-blue-700"}`}>정확한 시각</button>
      </div>
      <div className="space-y-3">
        {form.reminders.map((reminder, index) => (
          <div key={index} className="rounded-md bg-card p-2.5">
            <div className="flex items-center gap-2">
              {form.reminderMode === "offset" ? (
                <select value={reminder.offsetMinutes ?? 60} onChange={(event) => updateReminder(index, { offsetMinutes: Number(event.target.value) })} className="h-8 rounded-md border border-slate-200 px-2 text-xs">
                  <option value={0}>정시</option>
                  <option value={10}>10분 전</option>
                  <option value={60}>1시간 전</option>
                  <option value={1440}>1일 전</option>
                  <option value={10080}>1주 전</option>
                </select>
              ) : (
                <Input type="datetime-local" value={reminder.remindAt ?? ""} onChange={(event) => updateReminder(index, { remindAt: event.target.value || null })} className="h-8 text-xs" />
              )}
              <Button variant="ghost" size="icon" className="size-8 text-red-500" onClick={() => setForm((current) => ({ ...current, reminders: current.reminders.filter((_, i) => i !== index) }))}>
                <Trash2 className="size-4" />
              </Button>
            </div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {CHANNELS.map((channel) => {
                const active = reminder.channels.includes(channel);
                return (
                  <button
                    key={channel}
                    type="button"
                    onClick={() => updateReminder(index, { channels: active ? reminder.channels.filter((item) => item !== channel) : [...reminder.channels, channel] })}
                    className={`rounded-full px-2 py-1 text-[11px] font-semibold ${active ? "bg-blue-100 text-blue-700" : "bg-slate-100 text-slate-500"}`}
                  >
                    {REMINDER_CHANNEL_LABELS[channel]}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
        {form.reminders.length === 0 && <div className="text-xs text-blue-600">알림 없이 저장됩니다.</div>}
      </div>
    </div>
  );
}

function ScheduleTimeline({
  grouped,
  focusItemId,
  onEdit,
  onDelete,
  onToggleDone,
}: {
  grouped: { day: string; items: PlannerScheduleItem[] }[];
  focusItemId: number | null;
  onEdit: (item: PlannerScheduleItem) => void;
  onDelete: (id: number) => void;
  onToggleDone: (item: PlannerScheduleItem) => void;
}) {
  return (
    <div className="space-y-4">
      {grouped.map((group) => (
        <section key={group.day} className="rounded-lg border border-slate-200 bg-card">
          <div className="border-b border-slate-100 px-4 py-3 text-sm font-bold text-slate-800">{group.day}</div>
          <div className="divide-y divide-slate-100">
            {group.items.map((item) => (
              <div
                id={`planner-schedule-item-${item.id}`}
                key={item.id}
                className={`scroll-mt-24 grid gap-3 p-4 transition-colors md:grid-cols-[100px_minmax(0,1fr)_auto] ${
                  item.id === focusItemId ? "bg-blue-50/80 outline outline-2 -outline-offset-2 outline-blue-400 dark:bg-blue-500/10" : ""
                }`}
              >
                <div className="text-xs font-semibold text-slate-500">{timeLabel(item)}</div>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`font-bold ${item.status === "DONE" ? "text-slate-400 line-through" : "text-slate-900"}`}>{item.title}</span>
                    <Badge className={kindTone(item.kind)}>{kindLabel(item.kind)}</Badge>
                    {item.applicationCompanyName && <Badge variant="outline"><LinkIcon className="mr-1 size-3" />{item.applicationCompanyName}</Badge>}
                    {item.reminders.length > 0 && <Badge className="bg-blue-50 text-blue-700"><Bell className="mr-1 size-3" />{item.reminders.length}</Badge>}
                  </div>
                  {item.description && <p className="mt-1 text-sm leading-6 text-slate-600">{item.description}</p>}
                </div>
                <div className="flex items-start gap-1">
                  <Button variant="ghost" size="icon" title="완료 전환" onClick={() => onToggleDone(item)}><CheckCircle2 className="size-4" /></Button>
                  <Button variant="ghost" size="icon" title="수정" onClick={() => onEdit(item)}><Edit3 className="size-4" /></Button>
                  <Button variant="ghost" size="icon" title="삭제" className="text-red-500" onClick={() => onDelete(item.id)}><Trash2 className="size-4" /></Button>
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
      {grouped.length === 0 && <EmptyState icon={CalendarClock} text="아직 등록된 일정이 없습니다." />}
    </div>
  );
}

function MemoEditor({
  form,
  setForm,
  editing,
  saving,
  onSave,
  onCancel,
}: {
  form: PlannerMemoRequest;
  setForm: (form: PlannerMemoRequest) => void;
  editing: boolean;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader><CardTitle className="flex items-center gap-2 text-base"><StickyNote className="size-4 text-amber-600" />{editing ? "메모 수정" : "새 메모"}</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <Input value={form.title ?? ""} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="메모 제목" />
        <Textarea rows={8} value={form.content ?? ""} onChange={(event) => setForm({ ...form, content: event.target.value })} placeholder="메모 내용" />
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-xs font-semibold text-slate-600">
            색상
            <select value={form.color} onChange={(event) => setForm({ ...form, color: event.target.value })} className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-input-background px-3 text-sm">
              <option value="yellow">노랑</option>
              <option value="blue">파랑</option>
              <option value="green">초록</option>
              <option value="rose">빨강</option>
              <option value="slate">회색</option>
            </select>
          </label>
          <label className="text-xs font-semibold text-slate-600">
            투명도 {Math.round(form.opacity * 100)}%
            <Slider value={[form.opacity]} min={0.2} max={1} step={0.05} onValueChange={([value]) => setForm({ ...form, opacity: value })} className="mt-3" />
          </label>
        </div>
        <div className="grid gap-3 rounded-lg border border-slate-100 p-3">
          <label className="flex items-center justify-between text-sm font-medium text-slate-700">핀 고정<Switch checked={form.pinned} onCheckedChange={(checked) => setForm({ ...form, pinned: checked })} /></label>
          <label className="flex items-center justify-between text-sm font-medium text-slate-700">오버레이 표시<Switch checked={form.overlayVisible} onCheckedChange={(checked) => setForm({ ...form, overlayVisible: checked })} /></label>
        </div>
        <div className="flex gap-2">
          <Button onClick={onSave} disabled={saving || (!form.title?.trim() && !form.content?.trim())} className="h-9"><Save className="size-4" />저장</Button>
          {editing && <Button variant="outline" onClick={onCancel} className="h-9"><X className="size-4" />취소</Button>}
        </div>
      </CardContent>
    </Card>
  );
}

function MemoList({ memos, onEdit, onDelete }: { memos: PlannerMemo[]; onEdit: (memo: PlannerMemo) => void; onDelete: (id: number) => void }) {
  return (
    <div className="grid gap-3 md:grid-cols-2">
      {memos.map((memo) => (
        <div key={memo.id} className={`planner-memo planner-memo--${memo.color}`} style={{ opacity: memo.opacity }}>
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <div className="truncate text-sm font-bold text-slate-900">{memo.title || "제목 없는 메모"}</div>
              <div className="mt-1 whitespace-pre-wrap text-sm leading-6 text-slate-700">{memo.content || "내용 없음"}</div>
            </div>
            <div className="flex shrink-0 gap-1">
              {memo.pinned && <Pin className="size-4 text-amber-600" />}
              {memo.overlayVisible ? <Eye className="size-4 text-blue-600" /> : <EyeOff className="size-4 text-slate-300" />}
            </div>
          </div>
          <div className="mt-3 flex justify-end gap-1">
            <Button variant="ghost" size="icon" onClick={() => onEdit(memo)}><Edit3 className="size-4" /></Button>
            <Button variant="ghost" size="icon" className="text-red-500" onClick={() => onDelete(memo.id)}><Trash2 className="size-4" /></Button>
          </div>
        </div>
      ))}
      {memos.length === 0 && <EmptyState icon={StickyNote} text="아직 등록된 메모가 없습니다." />}
    </div>
  );
}

function OverlayManager({
  data,
  onEditMemo,
  onEditSchedule,
}: {
  data: PlannerDashboard | null;
  onEditMemo: (memo: PlannerMemo) => void;
  onEditSchedule: (item: PlannerScheduleItem) => void;
  onRefresh: () => void;
}) {
  const memos = data?.memos.filter((memo) => memo.overlayVisible) ?? [];
  const items = data?.scheduleItems.filter((item) => item.overlayVisible) ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <section className="rounded-lg border border-slate-200 bg-card p-4">
        <h2 className="flex items-center gap-2 text-base font-bold text-slate-900"><StickyNote className="size-4 text-amber-600" />표시 중인 메모</h2>
        <div className="mt-3 space-y-2">
          {memos.map((memo) => (
            <button key={memo.id} onClick={() => onEditMemo(memo)} className="flex w-full items-center justify-between rounded-lg border border-slate-100 p-3 text-left hover:bg-slate-50">
              <span className="truncate text-sm font-semibold">{memo.title || memo.content || "제목 없는 메모"}</span>
              <span className="text-xs text-slate-400">{Math.round(memo.opacity * 100)}%</span>
            </button>
          ))}
          {memos.length === 0 && <div className="text-sm text-slate-500">오버레이에 표시 중인 메모가 없습니다.</div>}
        </div>
      </section>
      <section className="rounded-lg border border-slate-200 bg-card p-4">
        <h2 className="flex items-center gap-2 text-base font-bold text-slate-900"><CalendarClock className="size-4 text-blue-600" />표시 중인 일정</h2>
        <div className="mt-3 space-y-2">
          {items.map((item) => (
            <button key={item.id} onClick={() => onEditSchedule(item)} className="flex w-full items-center justify-between rounded-lg border border-slate-100 p-3 text-left hover:bg-slate-50">
              <span className="truncate text-sm font-semibold">{item.title}</span>
              <span className="text-xs text-slate-400">{timeLabel(item)}</span>
            </button>
          ))}
          {items.length === 0 && <div className="text-sm text-slate-500">오버레이에 표시 중인 일정이 없습니다.</div>}
        </div>
      </section>
    </div>
  );
}

function EmptyState({ icon: Icon, text }: { icon: typeof CalendarClock; text: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 bg-card p-8 text-center text-sm text-slate-500">
      <Icon className="mx-auto mb-2 size-6 text-slate-300" />
      {text}
    </div>
  );
}

function defaultReminder(offsetMinutes: number): PlannerScheduleReminderRequest {
  return {
    remindAt: null,
    offsetMinutes,
    channels: DEFAULT_CHANNELS,
    soundEnabled: true,
    vibrationEnabled: true,
  };
}

function itemToRequest(item: PlannerScheduleItem): PlannerScheduleItemRequest {
  return {
    title: item.title,
    description: item.description,
    kind: item.kind,
    status: item.status,
    allDay: item.allDay,
    timingPrecision: item.timingPrecision,
    startAt: toInputValue(item.startAt),
    endAt: item.endAt ? toInputValue(item.endAt) : null,
    timezone: item.timezone,
    applicationCaseId: item.applicationCaseId,
    fitAnalysisId: item.fitAnalysisId,
    sourceType: item.sourceType,
    sourceRef: item.sourceRef,
    sourceSnapshotJson: item.sourceSnapshotJson,
    overlayVisible: item.overlayVisible,
    opacity: item.opacity,
    pinned: item.pinned,
    clickThrough: item.clickThrough,
    reminders: item.reminders.map((reminder) => ({
      remindAt: reminder.remindAt ? toInputValue(reminder.remindAt) : null,
      offsetMinutes: reminder.offsetMinutes,
      channels: reminder.channels,
      soundEnabled: reminder.soundEnabled,
      vibrationEnabled: reminder.vibrationEnabled,
    })),
  };
}

function groupByDay(items: PlannerScheduleItem[]) {
  const sorted = [...items].sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());
  const groups = new Map<string, PlannerScheduleItem[]>();
  for (const item of sorted) {
    const key = new Date(item.startAt).toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "short" });
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  return Array.from(groups, ([day, groupItems]) => ({ day, items: groupItems }));
}

function timeLabel(item: PlannerScheduleItem) {
  if (item.allDay || item.timingPrecision === "DAY") return "하루종일";
  const start = new Date(item.startAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  if (!item.endAt) return start;
  const end = new Date(item.endAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  return `${start} - ${end}`;
}

function kindLabel(kind: string) {
  if (kind === "STRATEGY") return "전략";
  if (kind === "DEADLINE") return "마감";
  if (kind === "EVENT") return "이벤트";
  return "할 일";
}

function kindTone(kind: string) {
  if (kind === "STRATEGY") return "bg-indigo-100 text-indigo-700";
  if (kind === "DEADLINE") return "bg-red-100 text-red-700";
  if (kind === "EVENT") return "bg-green-100 text-green-700";
  return "bg-slate-100 text-slate-700";
}

function toInputValue(value: string | Date) {
  const date = typeof value === "string" ? new Date(value) : value;
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toApiDateTime(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function pad(value: number) {
  return String(value).padStart(2, "0");
}
