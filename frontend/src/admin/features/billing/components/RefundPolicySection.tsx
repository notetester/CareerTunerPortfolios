import { useEffect, useMemo, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";
import { Megaphone, RefreshCw, Save, Send, ShieldCheck } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { toast } from "@/features/notification/components/toast";
import {
  defaultRefundPolicyRules,
  parseRefundPolicyRules,
  type RefundPolicyRules,
} from "@/features/billing/api/refundPolicyApi";
import {
  getAdminRefundPolicies,
  publishAdminRefundPolicy,
  saveAdminRefundPolicyDraft,
  type AdminRefundPolicy,
} from "../api";

interface RefundPolicyForm {
  title: string;
  summary: string;
  content: string;
  adverse: boolean;
  effectiveAt: string;
  rules: RefundPolicyRules;
}

export function RefundPolicySection() {
  const [policies, setPolicies] = useState<AdminRefundPolicy[]>([]);
  const [form, setForm] = useState<RefundPolicyForm>(emptyForm());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const draft = useMemo(() => policies.find((policy) => policy.status === "DRAFT") ?? null, [policies]);
  const current = useMemo(() => policies
    .filter((policy) => policy.status === "PUBLISHED" && policy.effectiveAt && new Date(policy.effectiveAt) <= new Date())
    .sort((a, b) => b.version - a.version)[0] ?? null, [policies]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const rows = await getAdminRefundPolicies();
      setPolicies(rows);
      setForm(fromPolicy(rows.find((policy) => policy.status === "DRAFT")
        ?? rows.find((policy) => policy.status === "PUBLISHED")
        ?? null));
    } catch (e) {
      setError(e instanceof Error ? e.message : "환불 정책을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const saveDraft = async () => {
    setSaving(true);
    setError(null);
    try {
      const saved = await saveAdminRefundPolicyDraft(toRequest(form));
      setPolicies(await getAdminRefundPolicies());
      setForm(fromPolicy(saved));
      toast.success("환불 정책 초안을 저장했습니다.");
      return saved;
    } catch (e) {
      setError(e instanceof Error ? e.message : "환불 정책 초안을 저장하지 못했습니다.");
      toast.error("환불 정책 초안 저장에 실패했습니다.");
      return null;
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    if (!window.confirm("환불 정책을 게시하고 전체 공지사항에 고정하시겠습니까?")) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await saveAdminRefundPolicyDraft(toRequest(form));
      const published = await publishAdminRefundPolicy(saved.id);
      await load();
      toast.success(`환불 정책 v${published.version}을 게시하고 전체 공지에 고정했습니다.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "환불 정책 게시에 실패했습니다.");
      toast.error("환불 정책 게시 또는 공지 생성에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="gap-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle className="flex items-center gap-2 text-base">
            <ShieldCheck className="size-5 text-blue-600" />
            환불 정책 관리
          </CardTitle>
          <div className="flex items-center gap-2">
            {current && <Badge className="border-green-200 bg-green-50 text-green-700">시행 v{current.version}</Badge>}
            {draft && <Badge className="border-amber-200 bg-amber-50 text-amber-700">초안 v{draft.version}</Badge>}
            <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading || saving}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
              새로고침
            </Button>
          </div>
        </div>
        <p className="text-sm text-slate-500">
          게시 시 새 정책 버전이 생성되고 환불정책 공지가 자동 게시·고정됩니다. 게시된 버전은 수정되지 않습니다.
        </p>
      </CardHeader>
      <CardContent className="space-y-5">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="grid gap-4 lg:grid-cols-2">
          <Field label="정책 제목">
            <input className={inputClass} value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })} />
          </Field>
          <Field label="시행 시각">
            <input className={inputClass} type="datetime-local" value={form.effectiveAt}
              onChange={(event) => setForm({ ...form, effectiveAt: event.target.value })} />
          </Field>
          <Field label="미사용 청약철회 기간">
            <input className={inputClass} type="number" min={7} max={365}
              value={form.rules.withdrawalDays}
              onChange={(event) => updateRules(setForm, form, { withdrawalDays: Number(event.target.value) })} />
          </Field>
          <Field label="사용 후 처리">
            <select className={inputClass} value={form.rules.usedPolicy}
              onChange={(event) => updateRules(setForm, form, {
                usedPolicy: event.target.value as RefundPolicyRules["usedPolicy"],
              })}>
              <option value="MANUAL_REVIEW">관리자 검토</option>
              <option value="PRORATED_REFUND">잔여분 비례 환불</option>
              <option value="NO_REFUND">환불 불가</option>
            </select>
          </Field>
        </div>

        <Field label="변경 요약">
          <input className={inputClass} maxLength={500} value={form.summary}
            onChange={(event) => setForm({ ...form, summary: event.target.value })} />
        </Field>
        <Field label="사용자 고지 본문">
          <textarea className={`${inputClass} min-h-36 py-3`} value={form.content}
            onChange={(event) => setForm({ ...form, content: event.target.value })} />
        </Field>

        <label className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
          <input className="mt-1 size-4" type="checkbox" checked={form.adverse}
            onChange={(event) => setForm({ ...form, adverse: event.target.checked })} />
          <span>
            <strong className="block text-slate-800">사용자에게 불리한 변경</strong>
            <span className="text-slate-500">불리한 변경 여부를 표시하고 공지 시 운영자가 시행일을 재확인합니다.</span>
          </span>
        </label>

        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={() => void saveDraft()} disabled={saving || loading}>
            <Save className="size-4" /> 초안 저장
          </Button>
          <Button onClick={() => void publish()} disabled={saving || loading}>
            <Megaphone className="size-4" /> 게시하고 전체공지 고정
          </Button>
        </div>

        {current?.noticeId && (
          <div className="flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800">
            <Send className="size-4" /> 최신 정책 공지 #{current.noticeId}가 전체 공지사항에 연결되어 있습니다.
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="space-y-2 text-sm font-medium text-slate-700"><span>{label}</span>{children}</label>;
}

const inputClass = "h-10 w-full rounded-md border border-slate-200 bg-background px-3 text-sm outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100";

function emptyForm(): RefundPolicyForm {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return {
    title: "환불 정책",
    summary: "전자상거래 관련 법령과 서비스 운영 기준에 따른 환불 정책입니다.",
    content: "결제 후 7일 이내이며 유료 기능을 사용하지 않은 경우 전액 환불을 신청할 수 있습니다.",
    adverse: false,
    effectiveAt: now.toISOString().slice(0, 16),
    rules: { ...defaultRefundPolicyRules },
  };
}

function fromPolicy(policy: AdminRefundPolicy | null): RefundPolicyForm {
  if (!policy) return emptyForm();
  return {
    title: policy.title,
    summary: policy.summary ?? "",
    content: policy.content,
    adverse: policy.adverse,
    effectiveAt: policy.effectiveAt?.slice(0, 16) ?? emptyForm().effectiveAt,
    rules: parseRefundPolicyRules(policy.rulesJson),
  };
}

function toRequest(form: RefundPolicyForm) {
  return {
    title: form.title.trim(),
    summary: form.summary.trim(),
    content: form.content.trim(),
    adverse: form.adverse,
    effectiveAt: form.effectiveAt,
    rules: form.rules,
  };
}

function updateRules(
  setForm: Dispatch<SetStateAction<RefundPolicyForm>>,
  form: RefundPolicyForm,
  patch: Partial<RefundPolicyRules>,
) {
  setForm({ ...form, rules: { ...form.rules, ...patch } });
}
