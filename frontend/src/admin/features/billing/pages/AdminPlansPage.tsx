import { useEffect, useMemo, useState, type ReactNode } from "react";
import { CalendarClock, Package, RefreshCw, X } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  cancelBillingPolicyChange,
  createBillingPolicyChange,
  getAdminPlans,
  getBillingPolicyChanges,
  type AdminPlans,
  type BillingPolicyChange,
  type BillingPolicyTargetType,
} from "../api";

type Snapshot = Record<string, string | number | boolean | null>;

const won = (n: number | null | undefined) => `${(n ?? 0).toLocaleString("ko-KR")}원`;

const targetLabels: Record<BillingPolicyTargetType, string> = {
  SUBSCRIPTION_PLAN: "구독 요금제",
  CREDIT_PRODUCT: "크레딧 상품",
  SUBSCRIPTION_BENEFIT_POLICY: "플랜 사용권",
  AI_FEATURE_BENEFIT_POLICY: "기능 차감 정책",
};

const applyModeByTarget: Record<BillingPolicyTargetType, string> = {
  SUBSCRIPTION_PLAN: "NEXT_SUBSCRIPTION_PERIOD",
  CREDIT_PRODUCT: "NEW_PURCHASE_FROM_EFFECTIVE_AT",
  SUBSCRIPTION_BENEFIT_POLICY: "NEXT_BENEFIT_PERIOD",
  AI_FEATURE_BENEFIT_POLICY: "NEXT_BENEFIT_PERIOD",
};

const applyModeLabel: Record<string, string> = {
  NEXT_SUBSCRIPTION_PERIOD: "활성 구독자는 현재 기간 종료 후 적용",
  NEW_PURCHASE_FROM_EFFECTIVE_AT: "적용 시각 이후 신규 구매부터 적용",
  NEXT_BENEFIT_PERIOD: "다음 사용권 기간부터 적용",
};

function localDateTimeValue(date = new Date()) {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function parseSnapshot(json: string): Snapshot {
  try {
    return JSON.parse(json) as Snapshot;
  } catch {
    return {};
  }
}

export function AdminPlansPage() {
  const [data, setData] = useState<AdminPlans | null>(null);
  const [changes, setChanges] = useState<BillingPolicyChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [targetType, setTargetType] = useState<BillingPolicyTargetType>("SUBSCRIPTION_PLAN");
  const [targetCode, setTargetCode] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState(localDateTimeValue());
  const [draft, setDraft] = useState<Snapshot>({});

  const targets = useMemo(() => buildTargets(data, targetType), [data, targetType]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [plans, policyChanges] = await Promise.all([getAdminPlans(), getBillingPolicyChanges()]);
      setData(plans);
      setChanges(policyChanges);
    } catch (e) {
      setError(e instanceof Error ? e.message : "요금제 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  useEffect(() => {
    const first = targets[0];
    setTargetCode(first?.code ?? "");
    setDraft(first?.snapshot ?? {});
  }, [targets]);

  const selectTarget = (code: string) => {
    setTargetCode(code);
    setDraft(targets.find((target) => target.code === code)?.snapshot ?? {});
  };

  const updateDraft = (key: string, value: string | boolean) => {
    setDraft((current) => ({ ...current, [key]: normalizeDraftValue(key, value) }));
  };

  const submit = async () => {
    if (!targetCode) return;
    setSaving(true);
    setError(null);
    try {
      await createBillingPolicyChange({
        targetType,
        applyMode: applyModeByTarget[targetType],
        effectiveFrom,
        nextSnapshot: draft,
      });
      setChanges(await getBillingPolicyChanges());
    } catch (e) {
      setError(e instanceof Error ? e.message : "변경 예약을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const cancelChange = async (id: number) => {
    setSaving(true);
    setError(null);
    try {
      await cancelBillingPolicyChange(id);
      setChanges(await getBillingPolicyChanges());
    } catch (e) {
      setError(e instanceof Error ? e.message : "예약 변경을 취소하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      active="plans"
      breadcrumb="요금제 관리"
      title="요금제·크레딧 상품"
      icon={Package}
      desc="가격과 사용권 정책은 즉시 덮어쓰지 않고 변경 예약으로 관리합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      }
    >
      {error && <div className="mb-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <div className="mb-5 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
        활성 구독자에게는 현재 구독 기간 종료 후 다음 구독 기간부터 적용됩니다. 구독 중이 아닌 사용자는 적용 시작 시각 이후 신규 구독/구매부터 적용됩니다.
        이미 생성된 결제 대기 건과 이미 발급된 사용권 잔여량은 변경하지 않습니다.
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_420px]">
        <div className="space-y-5">
          <CurrentPolicyCards data={data} loading={loading} />
          <PolicyChangeList changes={changes} saving={saving} onCancel={(id) => void cancelChange(id)} />
        </div>

        <Card className="border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <CalendarClock className="size-4" />
              변경 예약
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="변경 대상">
              <select className="w-full rounded-md border border-slate-200 bg-background px-3 py-2 text-sm" value={targetType} onChange={(event) => setTargetType(event.target.value as BillingPolicyTargetType)}>
                {Object.entries(targetLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </Field>
            <Field label="대상 코드">
              <select className="w-full rounded-md border border-slate-200 bg-background px-3 py-2 text-sm" value={targetCode} onChange={(event) => selectTarget(event.target.value)}>
                {targets.map((target) => <option key={target.code} value={target.code}>{target.label}</option>)}
              </select>
            </Field>
            <Field label="적용 시작 시각">
              <input className="w-full rounded-md border border-slate-200 bg-background px-3 py-2 text-sm" type="datetime-local" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
            </Field>
            <div className="rounded-md border border-slate-200 px-3 py-2 text-xs text-slate-500">
              {applyModeLabel[applyModeByTarget[targetType]]}
            </div>

            <DraftEditor targetType={targetType} draft={draft} onChange={updateDraft} />

            <Button className="w-full" onClick={() => void submit()} disabled={saving || !targetCode}>
              변경 예약 저장
            </Button>
          </CardContent>
        </Card>
      </div>
    </AdminShell>
  );
}

function CurrentPolicyCards({ data, loading }: { data: AdminPlans | null; loading: boolean }) {
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card className="border-slate-200 bg-card">
        <CardHeader><CardTitle className="text-base">구독 요금제</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {(data?.plans ?? []).map((p) => (
            <div key={p.code} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-slate-900">{p.name}</span>
                  <span className="text-xs text-slate-400">{p.code}</span>
                  {!p.active && <Badge className="bg-slate-200 text-slate-600">비활성</Badge>}
                </div>
                <div className="truncate text-xs text-slate-500">{p.description}</div>
              </div>
              <div className="shrink-0 text-right">
                <div className="font-black text-slate-900">{won(p.monthlyPrice)}</div>
                <div className="text-xs text-slate-400">연 {won(p.yearlyPrice)}/월</div>
              </div>
            </div>
          ))}
          {(data?.plans?.length ?? 0) === 0 && !loading && <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-400">요금제가 없습니다.</div>}
        </CardContent>
      </Card>

      <Card className="border-slate-200 bg-card">
        <CardHeader><CardTitle className="text-base">크레딧 충전 상품</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {(data?.creditProducts ?? []).map((c) => (
            <div key={c.code} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-slate-900">{c.name}</span>
                  {c.badge && <Badge className="bg-amber-100 text-amber-700">{c.badge}</Badge>}
                  {!c.enabled && <Badge className="bg-slate-200 text-slate-600">비활성</Badge>}
                </div>
                <div className="text-xs text-slate-500">크레딧 {c.creditAmount}개</div>
              </div>
              <div className="shrink-0 font-black text-slate-900">{won(c.price)}</div>
            </div>
          ))}
          {(data?.creditProducts?.length ?? 0) === 0 && !loading && <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-400">상품이 없습니다.</div>}
        </CardContent>
      </Card>
    </div>
  );
}

function PolicyChangeList({ changes, saving, onCancel }: { changes: BillingPolicyChange[]; saving: boolean; onCancel: (id: number) => void }) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader><CardTitle className="text-base">예약 변경</CardTitle></CardHeader>
      <CardContent className="space-y-2">
        {changes.map((change) => {
          const next = parseSnapshot(change.nextSnapshotJson);
          return (
            <div key={change.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <Badge className={change.status === "SCHEDULED" ? "bg-slate-900 text-white" : "bg-slate-200 text-slate-600"}>{change.status}</Badge>
                  <span className="font-semibold text-slate-900">{targetLabels[change.targetType]}</span>
                  <span className="text-xs text-slate-400">{change.targetCode}</span>
                </div>
                <div className="mt-1 text-xs text-slate-500">{summarizeChange(change.targetType, next)}</div>
                <div className="text-xs text-slate-400">적용 시작: {change.effectiveFrom.replace("T", " ")}</div>
              </div>
              {change.status === "SCHEDULED" && (
                <Button variant="outline" size="icon" disabled={saving} onClick={() => onCancel(change.id)} title="예약 취소">
                  <X className="size-4" />
                </Button>
              )}
            </div>
          );
        })}
        {changes.length === 0 && <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-400">예약 변경이 없습니다.</div>}
      </CardContent>
    </Card>
  );
}

function DraftEditor({ targetType, draft, onChange }: { targetType: BillingPolicyTargetType; draft: Snapshot; onChange: (key: string, value: string | boolean) => void }) {
  if (targetType === "SUBSCRIPTION_PLAN") {
    return (
      <div className="space-y-3">
        <TextField label="요금제명" value={draft.name} onChange={(value) => onChange("name", value)} />
        <NumberField label="월 가격" value={draft.monthlyPrice} onChange={(value) => onChange("monthlyPrice", value)} />
        <NumberField label="연 가격" value={draft.yearlyPrice} onChange={(value) => onChange("yearlyPrice", value)} />
        <TextField label="설명" value={draft.description} onChange={(value) => onChange("description", value)} />
        <NumberField label="정렬 순서" value={draft.sortOrder} onChange={(value) => onChange("sortOrder", value)} />
        <CheckField label="노출 활성화" checked={Boolean(draft.active)} onChange={(value) => onChange("active", value)} />
      </div>
    );
  }
  if (targetType === "CREDIT_PRODUCT") {
    return (
      <div className="space-y-3">
        <TextField label="상품명" value={draft.name} onChange={(value) => onChange("name", value)} />
        <NumberField label="가격" value={draft.price} onChange={(value) => onChange("price", value)} />
        <NumberField label="지급 크레딧" value={draft.creditAmount} onChange={(value) => onChange("creditAmount", value)} />
        <TextField label="배지" value={draft.badge} onChange={(value) => onChange("badge", value)} />
        <TextField label="설명" value={draft.description} onChange={(value) => onChange("description", value)} />
        <NumberField label="정렬 순서" value={draft.sortOrder} onChange={(value) => onChange("sortOrder", value)} />
        <CheckField label="구매 활성화" checked={Boolean(draft.enabled)} onChange={(value) => onChange("enabled", value)} />
      </div>
    );
  }
  if (targetType === "SUBSCRIPTION_BENEFIT_POLICY") {
    return (
      <div className="space-y-3">
        <TextField label="사용권명" value={draft.benefitName} onChange={(value) => onChange("benefitName", value)} />
        <NumberField label="제공 수량" value={draft.quantity} onChange={(value) => onChange("quantity", value)} />
        <TextField label="초과 정책" value={draft.overagePolicy} onChange={(value) => onChange("overagePolicy", value)} />
        <NumberField label="초과 크레딧 비용" value={draft.creditCost} onChange={(value) => onChange("creditCost", value)} />
        <NumberField label="정렬 순서" value={draft.sortOrder} onChange={(value) => onChange("sortOrder", value)} />
        <CheckField label="정책 활성화" checked={Boolean(draft.active)} onChange={(value) => onChange("active", value)} />
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <TextField label="사용권 코드" value={draft.benefitCode} onChange={(value) => onChange("benefitCode", value)} />
      <TextField label="차감 단위" value={draft.chargeUnit} onChange={(value) => onChange("chargeUnit", value)} />
      <NumberField label="기본 크레딧 비용" value={draft.defaultCreditCost} onChange={(value) => onChange("defaultCreditCost", value)} />
      <CheckField label="사용권 포함" checked={Boolean(draft.includedInTicket)} onChange={(value) => onChange("includedInTicket", value)} />
      <CheckField label="정책 활성화" checked={Boolean(draft.active)} onChange={(value) => onChange("active", value)} />
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-1 text-sm">
      <span className="font-medium text-slate-700">{label}</span>
      {children}
    </label>
  );
}

function TextField({ label, value, onChange }: { label: string; value: unknown; onChange: (value: string) => void }) {
  return (
    <Field label={label}>
      <input className="w-full rounded-md border border-slate-200 bg-background px-3 py-2 text-sm" value={value == null ? "" : String(value)} onChange={(event) => onChange(event.target.value)} />
    </Field>
  );
}

function NumberField({ label, value, onChange }: { label: string; value: unknown; onChange: (value: string) => void }) {
  return (
    <Field label={label}>
      <input className="w-full rounded-md border border-slate-200 bg-background px-3 py-2 text-sm" type="number" value={value == null ? "" : String(value)} onChange={(event) => onChange(event.target.value)} />
    </Field>
  );
}

function CheckField({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <label className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-sm">
      <span className="font-medium text-slate-700">{label}</span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
    </label>
  );
}

function buildTargets(data: AdminPlans | null, targetType: BillingPolicyTargetType) {
  if (!data) return [];
  if (targetType === "SUBSCRIPTION_PLAN") {
    return data.plans.map((plan) => ({
      code: plan.code,
      label: `${plan.name} (${plan.code})`,
      snapshot: {
        code: plan.code,
        name: plan.name,
        monthlyPrice: plan.monthlyPrice,
        yearlyPrice: plan.yearlyPrice ?? null,
        description: plan.description ?? null,
        active: plan.active,
        sortOrder: plan.sortOrder,
      },
    }));
  }
  if (targetType === "CREDIT_PRODUCT") {
    return data.creditProducts.map((product) => ({
      code: product.code,
      label: `${product.name} (${product.code})`,
      snapshot: {
        code: product.code,
        name: product.name,
        price: product.price,
        creditAmount: product.creditAmount,
        description: product.description ?? null,
        badge: product.badge ?? null,
        enabled: product.enabled,
        sortOrder: product.sortOrder,
      },
    }));
  }
  if (targetType === "SUBSCRIPTION_BENEFIT_POLICY") {
    return (data.benefitPolicies ?? []).map((policy) => ({
      code: `${policy.planCode}:${policy.benefitCode}`,
      label: `${policy.planCode} / ${policy.benefitName}`,
      snapshot: { ...policy },
    }));
  }
  return (data.featureBenefitPolicies ?? []).map((policy) => ({
    code: policy.featureType,
    label: `${policy.featureType} → ${policy.benefitCode}`,
    snapshot: { ...policy },
  }));
}

function normalizeDraftValue(key: string, value: string | boolean) {
  if (typeof value === "boolean") return value;
  if (["monthlyPrice", "yearlyPrice", "price", "creditAmount", "quantity", "creditCost", "sortOrder", "defaultCreditCost"].includes(key)) {
    return value === "" ? 0 : Number(value);
  }
  return value === "" ? null : value;
}

function summarizeChange(targetType: BillingPolicyTargetType, next: Snapshot) {
  if (targetType === "SUBSCRIPTION_PLAN") return `월 ${won(Number(next.monthlyPrice ?? 0))}, 연 ${won(Number(next.yearlyPrice ?? 0))}`;
  if (targetType === "CREDIT_PRODUCT") return `${won(Number(next.price ?? 0))}, 크레딧 ${next.creditAmount ?? 0}개`;
  if (targetType === "SUBSCRIPTION_BENEFIT_POLICY") return `${next.benefitName ?? next.benefitCode}: ${next.quantity ?? 0}장, 초과 ${next.creditCost ?? 0}크레딧`;
  return `${next.featureType ?? ""}: ${next.benefitCode ?? ""}, 기본 ${next.defaultCreditCost ?? 0}크레딧`;
}
