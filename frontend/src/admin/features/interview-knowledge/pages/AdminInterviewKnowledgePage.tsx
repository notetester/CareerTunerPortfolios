import { useEffect, useState } from "react";
import { BookMarked, RefreshCw, Plus, DatabaseZap, Pencil, Trash2, X } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/app/components/ui/select";
import {
  addKnowledge, deleteKnowledge, listKnowledge, reindexKnowledge, updateKnowledge,
  type InterviewKnowledge, type KnowledgeKind,
} from "../api";
import { useAdminDomainAuthorization } from "@/admin/auth/useAdminAuthorization";

const KINDS: { value: KnowledgeKind; label: string }[] = [
  { value: "RUBRIC", label: "평가 기준(Rubric)" },
  { value: "QUESTION_BANK", label: "질문 은행" },
  { value: "COMPANY", label: "기업 정보" },
  { value: "GENERAL", label: "일반" },
];

const kindLabel = (k: string) => KINDS.find((x) => x.value === k)?.label ?? k;
const fmt = (v: string) => new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" }).format(new Date(v));

export function AdminInterviewKnowledgePage() {
  const { canCreate, canUpdate, canDelete } = useAdminDomainAuthorization("AI");
  const [rows, setRows] = useState<InterviewKnowledge[]>([]);
  const [loading, setLoading] = useState(true);
  const [kind, setKind] = useState<KnowledgeKind>("RUBRIC");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [source, setSource] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [flash, setFlash] = useState<{ msg: string; tone: "ok" | "err" } | null>(null);

  const toast = (msg: string, tone: "ok" | "err") => {
    setFlash({ msg, tone });
    setTimeout(() => setFlash(null), 2600);
  };

  const load = async () => {
    setLoading(true);
    try {
      setRows(await listKnowledge(200));
    } catch (e) {
      toast(e instanceof Error ? e.message : "목록을 불러오지 못했습니다.", "err");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const resetForm = () => {
    setEditingId(null);
    setKind("RUBRIC");
    setTitle("");
    setContent("");
    setSource("");
  };

  const startEdit = (r: InterviewKnowledge) => {
    if (!canUpdate) return;
    setEditingId(r.id);
    setKind(r.kind);
    setTitle(r.title ?? "");
    setContent(r.content);
    setSource(r.source ?? "");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const submit = async () => {
    if ((editingId == null && !canCreate) || (editingId != null && !canUpdate)) return;
    if (content.trim().length < 5) {
      toast("내용을 5자 이상 입력해 주세요.", "err");
      return;
    }
    setSaving(true);
    const payload = { kind, title: title.trim() || undefined, content: content.trim(), source: source.trim() || undefined };
    try {
      if (editingId != null) {
        await updateKnowledge(editingId, payload);
        toast("지식 문서를 수정했습니다.", "ok");
      } else {
        await addKnowledge(payload);
        toast("지식 문서를 추가했습니다.", "ok");
      }
      resetForm();
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "저장에 실패했습니다.", "err");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (r: InterviewKnowledge) => {
    if (!canDelete) return;
    if (!window.confirm(`"${r.title || r.content.slice(0, 20)}" 문서를 삭제할까요?`)) return;
    try {
      await deleteKnowledge(r.id);
      if (editingId === r.id) resetForm();
      toast("지식 문서를 삭제했습니다.", "ok");
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "삭제에 실패했습니다.", "err");
    }
  };

  const reindex = async () => {
    if (!canUpdate) return;
    setReindexing(true);
    try {
      const { reindexed } = await reindexKnowledge();
      toast(`${reindexed}개 문서를 재색인했습니다.`, "ok");
      await load();
    } catch {
      toast("재색인 실패 — Qdrant 연결을 확인해 주세요(미가동 시 색인은 건너뜁니다).", "err");
    } finally {
      setReindexing(false);
    }
  };

  return (
    <AdminShell
      active="interviews"
      breadcrumb="면접 RAG 지식"
      title="면접 RAG 지식베이스"
      icon={BookMarked}
      desc="면접 평가 근거로 주입할 지식 문서를 등록하고 Qdrant에 재색인합니다."
      actions={
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
          {canUpdate && <Button onClick={() => void reindex()} disabled={reindexing}>
            <DatabaseZap className={`size-4 ${reindexing ? "animate-pulse" : ""}`} /> 재색인
          </Button>}
        </div>
      }
    >
      {flash && (
        <div className={`mb-3 rounded-lg px-4 py-2.5 text-sm ${flash.tone === "ok" ? "border border-green-200 bg-green-50 text-green-700" : "border border-red-200 bg-red-50 text-red-700"}`}>
          {flash.msg}
        </div>
      )}

      <div className={`grid gap-5 ${(editingId != null ? canUpdate : canCreate) ? "lg:grid-cols-[380px_minmax(0,1fr)]" : ""}`}>
        {(editingId != null ? canUpdate : canCreate) && <Card className="border-slate-200 bg-card">
          <CardHeader><CardTitle className="text-base">{editingId != null ? "지식 문서 수정" : "지식 문서 추가"}</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Select value={kind} onValueChange={(v) => setKind(v as KnowledgeKind)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {KINDS.map((k) => <SelectItem key={k.value} value={k.value}>{k.label}</SelectItem>)}
              </SelectContent>
            </Select>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목(선택)" maxLength={200} />
            <Textarea value={content} onChange={(e) => setContent(e.target.value)} placeholder="지식 내용(평가 기준, 모범 답변 포인트 등)" style={{ minHeight: 160 }} />
            <Input value={source} onChange={(e) => setSource(e.target.value)} placeholder="출처(선택)" maxLength={200} />
            <Button className="w-full" onClick={() => void submit()} disabled={saving}>
              {editingId != null ? <Pencil className="size-4" /> : <Plus className="size-4" />}
              {saving ? "저장 중…" : editingId != null ? "수정 저장" : "추가"}
            </Button>
            {editingId != null && (
              <Button variant="outline" className="w-full" onClick={resetForm} disabled={saving}>
                <X className="size-4" /> 수정 취소
              </Button>
            )}
          </CardContent>
        </Card>}

        <Card className="border-slate-200 bg-card">
          <CardHeader><CardTitle className="text-base">등록된 지식 ({rows.length})</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {rows.map((r) => (
              <div key={r.id} className="rounded-lg border border-slate-100 p-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Badge className="bg-slate-100 text-slate-700">{kindLabel(r.kind)}</Badge>
                    <span className="font-semibold text-slate-800">{r.title || "(제목 없음)"}</span>
                  </div>
                  <Badge className={r.indexed ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}>
                    {r.indexed ? "색인됨" : "미색인"}
                  </Badge>
                </div>
                <p className="mt-1.5 line-clamp-2 text-xs leading-5 text-slate-600">{r.content}</p>
                <div className="mt-1 flex items-center justify-between gap-2">
                  <div className="text-[11px] text-slate-400">{r.source ? `${r.source} · ` : ""}{fmt(r.createdAt)}</div>
                  {(canUpdate || canDelete) && <div className="flex gap-1">
                    {canUpdate && <Button variant="ghost" size="sm" className="h-7 px-2" onClick={() => startEdit(r)} title="수정">
                      <Pencil className="size-3.5" />
                    </Button>}
                    {canDelete && <Button variant="ghost" size="sm" className="h-7 px-2" onClick={() => void remove(r)} title="삭제">
                      <Trash2 className="size-3.5 text-red-500" />
                    </Button>}
                  </div>}
                </div>
              </div>
            ))}
            {rows.length === 0 && !loading && (
              <div className="rounded-lg bg-slate-50 p-6 text-center text-sm text-slate-400">등록된 지식 문서가 없습니다.</div>
            )}
          </CardContent>
        </Card>
      </div>
    </AdminShell>
  );
}
