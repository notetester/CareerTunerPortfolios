import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import {
  CalendarClock,
  CircleAlert,
  Coins,
  Package,
  RefreshCw,
  Save,
  ShieldCheck,
  Ticket,
  X,
} from "lucide-react";
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
import { RefundPolicySection } from "../components/RefundPolicySection";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

type SnapshotValue = string | number | boolean | null;
type Snapshot = Record<string, SnapshotValue>;

interface TargetOption {
  code: string;
  label: string;
  helper: string;
  snapshot: Snapshot;
}

interface EditableField {
  key: string;
  label: string;
  type: "text" | "number" | "textarea" | "boolean" | "select";
  options?: string[];
  helper?: string;
}

interface TargetMeta {
  label: string;
  shortLabel: string;
  icon: LucideIcon;
  applyMode: string;
  applyCopy: string;
}

const targetMeta: Record<BillingPolicyTargetType, TargetMeta> = {
  SUBSCRIPTION_PLAN: {
    label: "구독 요금제",
    shortLabel: "요금제",
    icon: Package,
    applyMode: "NEXT_SUBSCRIPTION_PERIOD",
    applyCopy: "활성 구독자는 현재 구독 기간 종료 후 다음 구독 기간부터 적용됩니다.",
  },
  CREDIT_PRODUCT: {
    label: "크레딧 상품",
    shortLabel: "크레딧",
    icon: Coins,
    applyMode: "NEW_PURCHASE_FROM_EFFECTIVE_AT",
    applyCopy: "적용 시작 시각 이후 새로 생성되는 크레딧 구매 건부터 적용됩니다.",
  },
  SUBSCRIPTION_BENEFIT_POLICY: {
    label: "플랜 사용권",
    shortLabel: "사용권",
    icon: Ticket,
    applyMode: "NEXT_BENEFIT_PERIOD",
    applyCopy: "이미 발급된 사용권 잔여량은 유지하고 다음 사용권 기간부터 적용합니다.",
  },
  AI_FEATURE_BENEFIT_POLICY: {
    label: "기능 차감 정책",
    shortLabel: "차감 정책",
    icon: ShieldCheck,
    applyMode: "NEXT_BENEFIT_PERIOD",
    applyCopy: "새 사용권 기간 또는 적용 시각 이후의 신규 이용 판단부터 반영됩니다.",
  },
};

const fieldConfig: Record<BillingPolicyTargetType, EditableField[]> = {
  SUBSCRIPTION_PLAN: [
    { key: "name", label: "요금제명", type: "text" },
    { key: "monthlyPrice", label: "월 가격", type: "number" },
    { key: "yearlyPrice", label: "연 가격", type: "number" },
    { key: "description", label: "설명", type: "textarea" },
    { key: "sortOrder", label: "정렬 순서", type: "number" },
    { key: "active", label: "노출 활성화", type: "boolean" },
  ],
  CREDIT_PRODUCT: [
    { key: "name", label: "상품명", type: "text" },
    { key: "price", label: "가격", type: "number" },
    { key: "creditAmount", label: "지급 크레딧", type: "number" },
    { key: "badge", label: "배지", type: "text" },
    { key: "description", label: "설명", type: "textarea" },
    { key: "sortOrder", label: "정렬 순서", type: "number" },
    { key: "enabled", label: "구매 활성화", type: "boolean" },
  ],
  SUBSCRIPTION_BENEFIT_POLICY: [
    { key: "benefitName", label: "사용권명", type: "text" },
    { key: "quantity", label: "제공 수량", type: "number" },
    { key: "resetCycle", label: "초기화 주기", type: "select", options: ["MONTHLY", "NONE"] },
    { key: "overagePolicy", label: "초과 정책", type: "select", options: ["CREDIT", "BLOCK", "UPGRADE"] },
    { key: "creditCost", label: "초과 크레딧 비용", type: "number" },
    { key: "sortOrder", label: "정렬 순서", type: "number" },
    { key: "active", label: "정책 활성화", type: "boolean" },
  ],
  AI_FEATURE_BENEFIT_POLICY: [
    { key: "benefitCode", label: "사용권 코드", type: "text" },
    { key: "chargeUnit", label: "차감 단위", type: "select", options: ["REQUEST", "APPLICATION_CASE", "DOCUMENT", "QUESTION", "REPORT"] },
    { key: "defaultCreditCost", label: "기본 크레딧 비용", type: "number" },
    { key: "minCreditCost", label: "최소 크레딧 비용", type: "number" },
    { key: "maxCreditCost", label: "최대 크레딧 비용", type: "number" },
    { key: "creditUnitTokens", label: "크레딧 단위 토큰", type: "number" },
    { key: "includedInTicket", label: "사용권 포함", type: "boolean" },
    { key: "active", label: "정책 활성화", type: "boolean" },
  ],
};

const numberKeys = new Set([
  "monthlyPrice",
  "yearlyPrice",
  "price",
  "creditAmount",
  "quantity",
  "creditCost",
  "sortOrder",
  "defaultCreditCost",
  "minCreditCost",
  "maxCreditCost",
  "creditUnitTokens",
]);

const won = (value: number | null | undefined) => `${(value ?? 0).toLocaleString("ko-KR")}원`;
const numberLabel = (value: number | null | undefined, unit = "") => `${(value ?? 0).toLocaleString("ko-KR")}${unit}`;

function localDateTimeValue(date = new Date()) {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function parseSnapshot(json: string): Snapshot {
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    return Object.fromEntries(
      Object.entries(parsed).map(([key, value]) => [key, normalizeUnknownValue(value)]),
    );
  } catch {
    return {};
  }
}

function normalizeUnknownValue(value: unknown): SnapshotValue {
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean" || value === null) {
    return value;
  }
  return value == null ? null : String(value);
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace("T", " ");
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function AdminPlansPage() {
  const { canCreate, canUpdate } = useAdminDomainAuthorization("BILLING");
  const [data, setData] = useState<AdminPlans | null>(null);
  const [changes, setChanges] = useState<BillingPolicyChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [targetType, setTargetType] = useState<BillingPolicyTargetType>("SUBSCRIPTION_PLAN");
  const [targetCode, setTargetCode] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState(localDateTimeValue());
  const [draft, setDraft] = useState<Snapshot>({});

  const targets = useMemo(() => buildTargets(data, targetType), [data, targetType]);
  const selectedTarget = useMemo(
    () => targets.find((target) => target.code === targetCode) ?? targets[0] ?? null,
    [targetCode, targets],
  );
  const diffRows = useMemo(
    () => buildDiffRows(targetType, selectedTarget?.snapshot ?? {}, draft),
    [draft, selectedTarget, targetType],
  );
  const changedRows = diffRows.filter((row) => !sameSnapshotValue(row.current, row.next));
  const scheduledCount = changes.filter((change) => change.status === "SCHEDULED").length;
  const meta = targetMeta[targetType];

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

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const next = targets.find((target) => target.code === targetCode) ?? targets[0];
    setTargetCode(next?.code ?? "");
    setDraft(next?.snapshot ?? {});
  }, [targetCode, targets]);

  const selectTarget = (code: string) => {
    const next = targets.find((target) => target.code === code);
    setTargetCode(code);
    setDraft(next?.snapshot ?? {});
    setNotice(null);
  };

  const changeTargetType = (nextType: BillingPolicyTargetType) => {
    setTargetType(nextType);
    setNotice(null);
  };

  const updateDraft = (key: string, value: string | boolean) => {
    setDraft((current) => ({ ...current, [key]: normalizeDraftValue(key, value) }));
    setNotice(null);
  };

  const resetDraft = () => {
    setDraft(selectedTarget?.snapshot ?? {});
    setNotice(null);
  };

  const submit = async () => {
    if (!canCreate || !selectedTarget || changedRows.length === 0) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      await createBillingPolicyChange({
        targetType,
        applyMode: meta.applyMode,
        effectiveFrom,
        nextSnapshot: draft,
      });
      const [plans, policyChanges] = await Promise.all([getAdminPlans(), getBillingPolicyChanges()]);
      setData(plans);
      setChanges(policyChanges);
      setNotice("변경 예약을 저장했습니다. 기존 결제 대기 건과 이미 발급된 잔여량은 변경되지 않습니다.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "변경 예약을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const cancelChange = async (id: number) => {
    if (!canUpdate) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      await cancelBillingPolicyChange(id);
      setChanges(await getBillingPolicyChanges());
      setNotice("예약 변경을 취소했습니다.");
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
      desc="가격, 지급 크레딧, 사용권 정책을 적용 시점이 있는 정책 변경으로 관리합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading} title="새로고침">
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      }
    >
      <div className="space-y-5">
        <PolicyNotice />
        {error && <Alert tone="error">{error}</Alert>}
        {notice && <Alert tone="success">{notice}</Alert>}

        <SummaryStrip data={data} scheduledCount={scheduledCount} loading={loading} />

        <RefundPolicySection />

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_440px]">
          <div className="space-y-5">
            <CurrentPolicies data={data} loading={loading} />
            <PolicyChangeList changes={changes} saving={saving} canCancel={canUpdate} onCancel={(id) => void cancelChange(id)} />
          </div>

          {canCreate && <Card className="border-slate-200 bg-card">
            <CardHeader className="gap-2">
              <div className="flex items-center justify-between gap-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <CalendarClock className="size-4 text-blue-600" />
                  변경 예약
                </CardTitle>
                <Badge className="border-blue-200 bg-blue-50 text-blue-700">{meta.shortLabel}</Badge>
              </div>
              <p className="text-sm text-slate-500">{meta.applyCopy}</p>
            </CardHeader>
            <CardContent className="space-y-5">
              <TargetTypeTabs value={targetType} onChange={changeTargetType} />

              <div className="space-y-3">
                <Field label="대상">
                  <select
                    className="h-10 w-full rounded-md border border-slate-200 bg-background px-3 text-sm outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
                    value={targetCode}
                    onChange={(event) => selectTarget(event.target.value)}
                    disabled={targets.length === 0}
                  >
                    {targets.map((target) => (
                      <option key={target.code} value={target.code}>{target.label}</option>
                    ))}
                  </select>
                </Field>
                {selectedTarget && (
                  <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                    {selectedTarget.helper}
                  </div>
                )}
                <Field label="적용 시작 시각">
                  <input
                    className="h-10 w-full rounded-md border border-slate-200 bg-background px-3 text-sm outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
                    type="datetime-local"
                    value={effectiveFrom}
                    onChange={(event) => setEffectiveFrom(event.target.value)}
                  />
                </Field>
              </div>

              <DraftEditor targetType={targetType} draft={draft} onChange={updateDraft} />

              <DiffPreview rows={diffRows} changedCount={changedRows.length} />

              <div className="grid gap-2 sm:grid-cols-2">
                <Button variant="outline" onClick={resetDraft} disabled={saving || !selectedTarget || changedRows.length === 0}>
                  <RefreshCw className="size-4" />
                  되돌리기
                </Button>
                <Button onClick={() => void submit()} disabled={saving || !selectedTarget || changedRows.length === 0 || !effectiveFrom}>
                  <Save className="size-4" />
                  변경 예약 저장
                </Button>
              </div>
            </CardContent>
          </Card>}
        </div>
      </div>
    </AdminShell>
  );
}

function PolicyNotice() {
  return (
    <div className="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-900">
      <div className="flex gap-2">
        <CircleAlert className="mt-0.5 size-4 shrink-0" />
        <div className="space-y-1">
          <p className="font-semibold">정책 변경 적용 기준</p>
          <p>활성 구독자는 현재 구독 기간 종료 후 다음 구독 기간부터 적용됩니다.</p>
          <p>구독 중이 아닌 사용자는 적용 시작 시각 이후 신규 구독/구매부터 적용됩니다.</p>
          <p>이미 생성된 결제 대기 건과 이미 발급된 사용권 잔여량은 변경하지 않습니다.</p>
        </div>
      </div>
    </div>
  );
}

function SummaryStrip({ data, scheduledCount, loading }: { data: AdminPlans | null; scheduledCount: number; loading: boolean }) {
  const cards = [
    { label: "활성 요금제", value: `${data?.plans.filter((plan) => plan.active).length ?? 0}개`, sub: "공개 구독 플랜" },
    { label: "판매 상품", value: `${data?.creditProducts.filter((product) => product.enabled).length ?? 0}개`, sub: "구매 가능한 크레딧" },
    { label: "사용권 정책", value: `${data?.benefitPolicies.length ?? 0}개`, sub: "플랜별 제공량" },
    { label: "예약 변경", value: `${scheduledCount}건`, sub: "취소 전 예약" },
  ];

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {cards.map((card) => (
        <Card key={card.label} className="border-slate-200 bg-card">
          <CardContent className="p-4">
            <div className="text-xs font-semibold text-slate-500">{card.label}</div>
            <div className={`mt-1 text-2xl font-black ${loading ? "text-slate-300" : "text-slate-900"}`}>{loading ? "-" : card.value}</div>
            <div className="mt-1 text-xs text-slate-400">{card.sub}</div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function CurrentPolicies({ data, loading }: { data: AdminPlans | null; loading: boolean }) {
  return (
    <div className="space-y-5">
      <div className="grid gap-5 lg:grid-cols-2">
        <Card className="border-slate-200 bg-card">
          <CardHeader><CardTitle className="text-base">구독 요금제</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {(data?.plans ?? []).map((plan) => (
              <PolicyRow
                key={plan.code}
                title={plan.name}
                code={plan.code}
                disabled={!plan.active}
                meta={plan.description}
                value={`${won(plan.monthlyPrice)} / 월`}
                subValue={plan.yearlyPrice ? `연 ${won(plan.yearlyPrice)} / 월 환산` : "연 결제 없음"}
              />
            ))}
            {(data?.plans?.length ?? 0) === 0 && !loading && <EmptyState text="요금제가 없습니다." />}
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-card">
          <CardHeader><CardTitle className="text-base">크레딧 충전 상품</CardTitle></CardHeader>
          <CardContent className="space-y-2">
            {(data?.creditProducts ?? []).map((product) => (
              <PolicyRow
                key={product.code}
                title={product.name}
                code={product.code}
                disabled={!product.enabled}
                badge={product.badge ?? undefined}
                meta={product.description}
                value={won(product.price)}
                subValue={`${numberLabel(product.creditAmount, "개")} 지급`}
              />
            ))}
            {(data?.creditProducts?.length ?? 0) === 0 && !loading && <EmptyState text="크레딧 상품이 없습니다." />}
          </CardContent>
        </Card>
      </div>

      <Card className="border-slate-200 bg-card">
        <CardHeader><CardTitle className="text-base">플랜 사용권 정책</CardTitle></CardHeader>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                  <th className="px-4 py-3">플랜</th>
                  <th className="px-4 py-3">사용권</th>
                  <th className="px-4 py-3">제공량</th>
                  <th className="px-4 py-3">초과 정책</th>
                  <th className="px-4 py-3">상태</th>
                </tr>
              </thead>
              <tbody>
                {(data?.benefitPolicies ?? []).map((policy) => (
                  <tr key={`${policy.planCode}:${policy.benefitCode}`} className="border-b border-slate-100">
                    <td className="px-4 py-3 font-semibold text-slate-900">{policy.planCode}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-800">{policy.benefitName}</div>
                      <div className="text-xs text-slate-400">{policy.benefitCode}</div>
                    </td>
                    <td className="px-4 py-3 text-slate-700">{numberLabel(policy.quantity, "회")} / {policy.resetCycle}</td>
                    <td className="px-4 py-3 text-slate-700">{policy.overagePolicy} · {numberLabel(policy.creditCost, "크레딧")}</td>
                    <td className="px-4 py-3"><StatusBadge active={policy.active} /></td>
                  </tr>
                ))}
                {(data?.benefitPolicies?.length ?? 0) === 0 && !loading && (
                  <tr><td colSpan={5}><EmptyState text="사용권 정책이 없습니다." /></td></tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <Card className="border-slate-200 bg-card">
        <CardHeader><CardTitle className="text-base">기능 차감 정책</CardTitle></CardHeader>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                  <th className="px-4 py-3">기능</th>
                  <th className="px-4 py-3">사용권</th>
                  <th className="px-4 py-3">차감</th>
                  <th className="px-4 py-3">비용 범위</th>
                  <th className="px-4 py-3">상태</th>
                </tr>
              </thead>
              <tbody>
                {(data?.featureBenefitPolicies ?? []).map((policy) => (
                  <tr key={policy.featureType} className="border-b border-slate-100">
                    <td className="px-4 py-3 font-semibold text-slate-900">{policy.featureType}</td>
                    <td className="px-4 py-3 text-slate-700">{policy.benefitCode}</td>
                    <td className="px-4 py-3 text-slate-700">{policy.includedInTicket ? "사용권 우선" : "크레딧 직접 차감"} · {policy.chargeUnit}</td>
                    <td className="px-4 py-3 text-slate-700">
                      {numberLabel(policy.minCreditCost, "크레딧")}~{numberLabel(policy.maxCreditCost, "크레딧")}
                      {policy.creditUnitTokens > 0 && <div className="text-xs text-slate-500">{policy.creditUnitTokens.toLocaleString("ko-KR")}토큰당</div>}
                    </td>
                    <td className="px-4 py-3"><StatusBadge active={policy.active} /></td>
                  </tr>
                ))}
                {(data?.featureBenefitPolicies?.length ?? 0) === 0 && !loading && (
                  <tr><td colSpan={5}><EmptyState text="기능 차감 정책이 없습니다." /></td></tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function PolicyRow({
  title,
  code,
  meta,
  value,
  subValue,
  disabled,
  badge,
}: {
  title: string;
  code: string;
  meta?: string | null;
  value: string;
  subValue: string;
  disabled?: boolean;
  badge?: string;
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-3">
      <div className="min-w-0">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <span className="font-semibold text-slate-900">{title}</span>
          <span className="text-xs text-slate-400">{code}</span>
          {badge && <Badge className="border-amber-200 bg-amber-50 text-amber-700">{badge}</Badge>}
          {disabled && <Badge className="bg-slate-200 text-slate-600">비활성</Badge>}
        </div>
        <div className="mt-1 truncate text-xs text-slate-500">{meta ?? "-"}</div>
      </div>
      <div className="shrink-0 text-right">
        <div className="font-black text-slate-900">{value}</div>
        <div className="text-xs text-slate-400">{subValue}</div>
      </div>
    </div>
  );
}

function PolicyChangeList({
  changes,
  saving,
  canCancel,
  onCancel,
}: {
  changes: BillingPolicyChange[];
  saving: boolean;
  canCancel: boolean;
  onCancel: (id: number) => void;
}) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader><CardTitle className="text-base">예약 변경</CardTitle></CardHeader>
      <CardContent className="space-y-2">
        {changes.map((change) => {
          const current = parseSnapshot(change.currentSnapshotJson);
          const next = parseSnapshot(change.nextSnapshotJson);
          const rows = buildDiffRows(change.targetType, current, next).filter((row) => !sameSnapshotValue(row.current, row.next));
          return (
            <div key={change.id} className="rounded-lg border border-slate-100 p-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <ChangeStatusBadge status={change.status} />
                    <span className="font-semibold text-slate-900">{targetMeta[change.targetType].label}</span>
                    <span className="text-xs text-slate-400">{change.targetCode}</span>
                  </div>
                  <div className="mt-1 text-xs text-slate-500">적용 시작: {formatDateTime(change.effectiveFrom)}</div>
                  <div className="text-xs text-slate-400">{applyModeText(change.applyMode)}</div>
                </div>
                {canCancel && change.status === "SCHEDULED" && (
                  <Button variant="outline" size="icon" disabled={saving} onClick={() => onCancel(change.id)} title="예약 취소">
                    <X className="size-4" />
                  </Button>
                )}
              </div>
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                {(rows.length > 0 ? rows.slice(0, 4) : buildDiffRows(change.targetType, current, next).slice(0, 2)).map((row) => (
                  <div key={row.key} className="rounded-md bg-slate-50 px-3 py-2 text-xs">
                    <div className="font-semibold text-slate-500">{row.label}</div>
                    <div className="mt-0.5 text-slate-700">{displayValue(row.current)} → {displayValue(row.next)}</div>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
        {changes.length === 0 && <EmptyState text="예약 변경이 없습니다." />}
      </CardContent>
    </Card>
  );
}

function TargetTypeTabs({
  value,
  onChange,
}: {
  value: BillingPolicyTargetType;
  onChange: (value: BillingPolicyTargetType) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-2">
      {(Object.keys(targetMeta) as BillingPolicyTargetType[]).map((type) => {
        const meta = targetMeta[type];
        const Icon = meta.icon;
        const selected = value === type;
        return (
          <button
            key={type}
            type="button"
            className={`flex h-11 items-center justify-center gap-2 rounded-md border px-3 text-sm font-semibold transition-colors ${
              selected
                ? "border-blue-600 bg-blue-50 text-blue-700"
                : "border-slate-200 bg-background text-slate-600 hover:bg-slate-50"
            }`}
            onClick={() => onChange(type)}
          >
            <Icon className="size-4" />
            {meta.shortLabel}
          </button>
        );
      })}
    </div>
  );
}

function DraftEditor({
  targetType,
  draft,
  onChange,
}: {
  targetType: BillingPolicyTargetType;
  draft: Snapshot;
  onChange: (key: string, value: string | boolean) => void;
}) {
  return (
    <div className="space-y-3">
      {fieldConfig[targetType].map((field) => (
        <DraftField key={field.key} field={field} value={draft[field.key]} onChange={(value) => onChange(field.key, value)} />
      ))}
    </div>
  );
}

function DraftField({
  field,
  value,
  onChange,
}: {
  field: EditableField;
  value: SnapshotValue | undefined;
  onChange: (value: string | boolean) => void;
}) {
  if (field.type === "boolean") {
    return (
      <label className="flex min-h-10 items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2 text-sm">
        <span className="font-medium text-slate-700">{field.label}</span>
        <input type="checkbox" checked={Boolean(value)} onChange={(event) => onChange(event.target.checked)} />
      </label>
    );
  }

  if (field.type === "textarea") {
    return (
      <Field label={field.label}>
        <textarea
          className="min-h-20 w-full resize-y rounded-md border border-slate-200 bg-background px-3 py-2 text-sm outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
          value={value == null ? "" : String(value)}
          onChange={(event) => onChange(event.target.value)}
        />
      </Field>
    );
  }

  if (field.type === "select") {
    const stringValue = value == null ? "" : String(value);
    const options = field.options?.includes(stringValue) || !stringValue
      ? field.options ?? []
      : [stringValue, ...(field.options ?? [])];
    return (
      <Field label={field.label}>
        <select
          className="h-10 w-full rounded-md border border-slate-200 bg-background px-3 text-sm outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
          value={stringValue}
          onChange={(event) => onChange(event.target.value)}
        >
          {options.map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      </Field>
    );
  }

  return (
    <Field label={field.label}>
      <input
        className="h-10 w-full rounded-md border border-slate-200 bg-background px-3 text-sm outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
        type={field.type}
        min={field.type === "number" ? 0 : undefined}
        value={value == null ? "" : String(value)}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}

function DiffPreview({ rows, changedCount }: { rows: ReturnType<typeof buildDiffRows>; changedCount: number }) {
  return (
    <div className="rounded-lg border border-slate-200">
      <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-3 py-2">
        <span className="text-sm font-semibold text-slate-800">변경 미리보기</span>
        <Badge className={changedCount > 0 ? "bg-slate-900 text-white" : "bg-slate-200 text-slate-600"}>
          {changedCount}개 변경
        </Badge>
      </div>
      <div className="max-h-64 overflow-auto p-2">
        {rows.map((row) => {
          const changed = !sameSnapshotValue(row.current, row.next);
          return (
            <div key={row.key} className={`grid gap-2 rounded-md px-2 py-2 text-xs sm:grid-cols-[96px_minmax(0,1fr)] ${changed ? "bg-blue-50" : ""}`}>
              <div className="font-semibold text-slate-500">{row.label}</div>
              <div className={changed ? "font-semibold text-blue-900" : "text-slate-500"}>
                {displayValue(row.current)} → {displayValue(row.next)}
              </div>
            </div>
          );
        })}
        {rows.length === 0 && <div className="p-4 text-center text-sm text-slate-400">대상을 선택하세요.</div>}
      </div>
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

function Alert({ tone, children }: { tone: "error" | "success"; children: ReactNode }) {
  const className = tone === "error"
    ? "border-red-200 bg-red-50 text-red-700"
    : "border-green-200 bg-green-50 text-green-700";
  return <div className={`rounded-lg border px-4 py-3 text-sm ${className}`}>{children}</div>;
}

function EmptyState({ text }: { text: string }) {
  return <div className="rounded-lg bg-slate-50 p-4 text-center text-sm text-slate-400">{text}</div>;
}

function StatusBadge({ active }: { active: boolean }) {
  return <Badge className={active ? "border-green-200 bg-green-50 text-green-700" : "bg-slate-200 text-slate-600"}>{active ? "활성" : "비활성"}</Badge>;
}

function ChangeStatusBadge({ status }: { status: string }) {
  const className = status === "SCHEDULED"
    ? "bg-slate-900 text-white"
    : status === "CANCELED"
      ? "bg-slate-200 text-slate-600"
      : "border-green-200 bg-green-50 text-green-700";
  return <Badge className={className}>{statusLabel(status)}</Badge>;
}

function buildTargets(data: AdminPlans | null, targetType: BillingPolicyTargetType): TargetOption[] {
  if (!data) return [];
  if (targetType === "SUBSCRIPTION_PLAN") {
    return data.plans.map((plan) => ({
      code: plan.code,
      label: `${plan.name} (${plan.code})`,
      helper: `${won(plan.monthlyPrice)} / 월 · ${(plan.active ?? true) ? "노출 중" : "비활성"}`,
      snapshot: {
        code: plan.code,
        name: plan.name,
        monthlyPrice: plan.monthlyPrice,
        yearlyPrice: plan.yearlyPrice ?? null,
        description: plan.description ?? null,
        active: plan.active ?? true,
        sortOrder: plan.sortOrder,
      },
    }));
  }
  if (targetType === "CREDIT_PRODUCT") {
    return data.creditProducts.map((product) => ({
      code: product.code,
      label: `${product.name} (${product.code})`,
      helper: `${won(product.price)} · 크레딧 ${numberLabel(product.creditAmount, "개")} 지급`,
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
      helper: `${numberLabel(policy.quantity, "회")} · 초과 ${policy.creditCost}크레딧 · ${policy.overagePolicy}`,
      snapshot: { ...policy },
    }));
  }
  return (data.featureBenefitPolicies ?? []).map((policy) => ({
    code: policy.featureType,
    label: `${policy.featureType} → ${policy.benefitCode}`,
    helper: `${policy.includedInTicket ? "사용권 우선" : "크레딧 직접 차감"} · ${policy.minCreditCost}~${policy.maxCreditCost}크레딧`,
    snapshot: { ...policy },
  }));
}

function buildDiffRows(targetType: BillingPolicyTargetType, current: Snapshot, next: Snapshot) {
  return fieldConfig[targetType].map((field) => ({
    key: field.key,
    label: field.label,
    current: current[field.key] ?? null,
    next: next[field.key] ?? null,
  }));
}

function normalizeDraftValue(key: string, value: string | boolean): SnapshotValue {
  if (typeof value === "boolean") return value;
  if (numberKeys.has(key)) return value === "" ? 0 : Number(value);
  return value === "" ? null : value;
}

function sameSnapshotValue(a: SnapshotValue | undefined, b: SnapshotValue | undefined) {
  return String(a ?? "") === String(b ?? "");
}

function displayValue(value: SnapshotValue | undefined) {
  if (typeof value === "boolean") return value ? "예" : "아니오";
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function statusLabel(status: string) {
  if (status === "SCHEDULED") return "예약됨";
  if (status === "CANCELED") return "취소됨";
  if (status === "APPLIED") return "적용됨";
  return status;
}

function applyModeText(applyMode: string) {
  if (applyMode === "NEXT_SUBSCRIPTION_PERIOD") return "다음 구독 기간부터 적용";
  if (applyMode === "NEW_PURCHASE_FROM_EFFECTIVE_AT") return "적용 시각 이후 신규 구매부터 적용";
  if (applyMode === "NEXT_BENEFIT_PERIOD") return "다음 사용권 기간부터 적용";
  return applyMode;
}
