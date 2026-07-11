import { useEffect, useState } from "react";
import {
  AlertTriangle,
  Ban,
  Globe2,
  HeartPulse,
  RotateCw,
  Save,
  ShieldAlert,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import {
  AdminListFooter,
  AdminListToolbar,
  AdminSortableHeader,
  useAdminListTools,
  type AdminListColumn,
} from "../../../components/AdminListTools";
import * as securityApi from "../api";
import { BlockEnginePanel } from "../components/BlockEnginePanel";
import type {
  SecurityAppeal,
  SecurityAppealPolicy,
  SecurityBlockRule,
  SecurityOpsSummary,
  SecurityProviderConfig,
  SecurityProviderHealthHistory,
  SecurityReview,
  WafSyncEvent,
} from "../types";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

type TabKey = "blocks" | "engine" | "reviews" | "providers" | "providerHealth" | "appeals" | "waf";

const BLOCK_COLUMNS: AdminListColumn<SecurityBlockRule>[] = [
  { id: "type", label: "유형", getText: (row) => row.ruleType, sortable: true },
  { id: "value", label: "대상", getText: (row) => row.ruleValue, sortable: true },
  { id: "scope", label: "범위", getText: (row) => row.scope, sortable: true },
  { id: "category", label: "분류", getText: (row) => row.category, sortable: true },
  { id: "action", label: "조치", getText: (row) => row.actionType, sortable: true },
  { id: "active", label: "상태", getText: (row) => (row.active ? "활성" : "비활성"), sortable: true },
  { id: "waf", label: "WAF", getText: (row) => row.wafSyncStatus, sortable: true },
  { id: "updated", label: "수정", getText: (row) => formatDate(row.updatedAt), sortable: true },
];

const REVIEW_COLUMNS: AdminListColumn<SecurityReview>[] = [
  { id: "type", label: "검토", getText: (row) => row.reviewType, sortable: true },
  { id: "subject", label: "대상", getText: (row) => `${row.subjectType} ${row.subjectValue}`, sortable: true },
  { id: "score", label: "점수", getText: (row) => row.riskScore, sortable: true },
  { id: "level", label: "위험도", getText: (row) => row.riskLevel, sortable: true },
  { id: "status", label: "상태", getText: (row) => row.status, sortable: true },
  { id: "created", label: "접수", getText: (row) => formatDate(row.createdAt), sortable: true },
];

const PROVIDER_COLUMNS: AdminListColumn<SecurityProviderConfig>[] = [
  { id: "code", label: "Provider", getText: (row) => row.providerCode, sortable: true },
  { id: "name", label: "이름", getText: (row) => row.displayName, sortable: true },
  { id: "type", label: "유형", getText: (row) => row.providerType, sortable: true },
  { id: "mode", label: "모드", getText: (row) => row.mode, sortable: true },
  { id: "enabled", label: "상태", getText: (row) => (row.enabled ? "사용" : "중지"), sortable: true },
  { id: "health", label: "헬스", getText: (row) => row.healthStatus, sortable: true },
];

const PROVIDER_HEALTH_COLUMNS: AdminListColumn<SecurityProviderHealthHistory>[] = [
  { id: "checked", label: "점검", getText: (row) => formatDate(row.checkedAt), sortable: true },
  { id: "provider", label: "Provider", getText: (row) => row.providerCode, sortable: true },
  { id: "type", label: "유형", getText: (row) => row.providerType, sortable: true },
  { id: "source", label: "출처", getText: (row) => row.checkSource, sortable: true },
  { id: "before", label: "이전", getText: (row) => row.statusBefore ?? "-", sortable: true },
  { id: "after", label: "결과", getText: (row) => row.statusAfter, sortable: true },
  { id: "actor", label: "처리자", getText: (row) => row.actorEmail ?? "-", sortable: true },
  { id: "detail", label: "상세", getText: (row) => row.detailMessage ?? "-", sortable: true },
];

const APPEAL_COLUMNS: AdminListColumn<SecurityAppeal>[] = [
  { id: "request", label: "요청", getText: (row) => row.publicRequestId, sortable: true },
  { id: "subject", label: "대상", getText: (row) => `${row.subjectType} ${row.subjectValue}`, sortable: true },
  { id: "email", label: "신청자", getText: (row) => row.submitterEmail, sortable: true },
  { id: "status", label: "상태", getText: (row) => row.status, sortable: true },
  { id: "created", label: "접수", getText: (row) => formatDate(row.createdAt), sortable: true },
];

const WAF_COLUMNS: AdminListColumn<WafSyncEvent>[] = [
  { id: "id", label: "ID", getText: (row) => row.id, sortable: true },
  { id: "provider", label: "Provider", getText: (row) => row.providerCode, sortable: true },
  { id: "operation", label: "작업", getText: (row) => row.operationType, sortable: true },
  { id: "status", label: "상태", getText: (row) => row.status, sortable: true },
  { id: "requested", label: "요청", getText: (row) => formatDate(row.requestedAt), sortable: true },
  { id: "processed", label: "처리", getText: (row) => formatDate(row.processedAt), sortable: true },
];

const NEW_BLOCK = {
  ruleType: "IP",
  ruleValue: "",
  scope: "GLOBAL",
  actionType: "BLOCK",
  category: "MANUAL",
  reason: "",
  memo: "",
  active: true,
  wafSyncEnabled: false,
};

const NEW_REVIEW = {
  reviewType: "LOGIN_RISK",
  subjectType: "IP",
  subjectValue: "",
  riskScore: 50,
  riskLevel: "MEDIUM",
  status: "OPEN",
  reason: "",
  evidenceJson: "{}",
};

export function AdminSecurityOpsPage() {
  const { role, canCreate, canUpdate } = useAdminDomainAuthorization("SECURITY");
  const canManageProviders = role === "SUPER_ADMIN";
  const [tab, setTab] = useState<TabKey>("blocks");
  const [summary, setSummary] = useState<SecurityOpsSummary | null>(null);
  const [blocks, setBlocks] = useState<SecurityBlockRule[]>([]);
  const [reviews, setReviews] = useState<SecurityReview[]>([]);
  const [providers, setProviders] = useState<SecurityProviderConfig[]>([]);
  const [providerHealth, setProviderHealth] = useState<SecurityProviderHealthHistory[]>([]);
  const [appeals, setAppeals] = useState<SecurityAppeal[]>([]);
  const [appealPolicy, setAppealPolicy] = useState<SecurityAppealPolicy | null>(null);
  const [wafEvents, setWafEvents] = useState<WafSyncEvent[]>([]);
  const [blockForm, setBlockForm] = useState({ ...NEW_BLOCK });
  const [reviewForm, setReviewForm] = useState({ ...NEW_REVIEW });
  const [policyReason, setPolicyReason] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const flash = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(null), 2200);
  };

  const load = async () => {
    setLoading(true);
    try {
      const [nextSummary, nextBlocks, nextReviews, nextProviders, nextProviderHealth, nextAppeals, nextPolicy, nextWafEvents] = await Promise.all([
        securityApi.getSecuritySummary(),
        securityApi.getBlockRules(),
        securityApi.getReviews(),
        canManageProviders
          ? securityApi.getProviders().catch(() => [] as SecurityProviderConfig[])
          : Promise.resolve([] as SecurityProviderConfig[]),
        securityApi.getProviderHealthHistory(),
        securityApi.getAppeals(),
        securityApi.getAppealPolicy().catch(() => null),
        securityApi.getWafEvents(),
      ]);
      setSummary(nextSummary);
      setBlocks(nextBlocks);
      setReviews(nextReviews);
      setProviders(nextProviders);
      setProviderHealth(nextProviderHealth);
      setAppeals(nextAppeals);
      setAppealPolicy(nextPolicy);
      setWafEvents(nextWafEvents);
    } catch {
      flash("보안 운영 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [canManageProviders]);

  useEffect(() => {
    if (!canManageProviders) {
      setProviders([]);
      if (tab === "providers") setTab("blocks");
    }
  }, [canManageProviders, tab]);

  return (
    <AdminShell
      active="security-ops"
      breadcrumb="보안 / 차단"
      title="보안 운영 센터"
      icon={ShieldAlert}
      desc="차단 규칙, 위험 검토, Provider, 이의제기, WAF 동기화 큐를 통합 관리합니다."
      actions={(
        <button type="button" className="av-btn" onClick={() => void load()} disabled={loading}>
          <RotateCw className={loading ? "animate-spin" : ""} /> 새로고침
        </button>
      )}
    >
      <div className="grid gap-3 md:grid-cols-5">
        <Metric icon={Ban} label="활성 차단" value={summary?.activeBlockRules ?? 0} />
        <Metric icon={Globe2} label="WAF 대기" value={summary?.pendingWafEvents ?? 0} />
        <Metric icon={AlertTriangle} label="검토 큐" value={summary?.openReviews ?? 0} />
        <Metric icon={ShieldCheck} label="이의제기" value={summary?.openAppeals ?? 0} />
        <Metric icon={HeartPulse} label="Provider" value={summary?.enabledProviders ?? 0} />
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        {([
          ["blocks", "차단 규칙"],
          ["engine", "차단 엔진"],
          ["reviews", "위험 검토"],
          ...(canManageProviders ? [["providers", "Provider 설정"]] : []),
          ["providerHealth", "헬스체크 이력"],
          ["appeals", "이의제기"],
          ["waf", "WAF 동기화"],
        ] as [TabKey, string][]).map(([key, label]) => (
          <button key={key} type="button" className={`av-btn ${tab === key ? "bg-slate-900 text-white" : ""}`} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </div>

      <div className="mt-5">
        {tab === "blocks" && (
          <BlockRulesPanel
            rows={blocks}
            form={blockForm}
            onFormChange={setBlockForm}
            onCreate={async () => {
              if (!canCreate) return;
              const created = await securityApi.createBlockRule(blockForm);
              setBlocks((prev) => [created, ...prev]);
              setBlockForm({ ...NEW_BLOCK });
              await load();
              flash("차단 규칙을 추가했습니다.");
            }}
            onToggle={async (row) => {
              if (!canUpdate) return;
              const updated = await securityApi.updateBlockRule(row.id, { ...row, active: !row.active });
              setBlocks((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
              flash(updated.active ? "차단 규칙을 활성화했습니다." : "차단 규칙을 비활성화했습니다.");
            }}
            onQueueWaf={async (row) => {
              if (!canUpdate) return;
              const updated = await securityApi.queueWafSync(row.id);
              setBlocks((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
              await load();
              flash("WAF 동기화 큐에 등록했습니다.");
            }}
          />
        )}
        {tab === "reviews" && (
          <ReviewsPanel
            rows={reviews}
            form={reviewForm}
            onFormChange={setReviewForm}
            onCreate={async () => {
              if (!canCreate) return;
              const created = await securityApi.createReview(reviewForm);
              setReviews((prev) => [created, ...prev]);
              setReviewForm({ ...NEW_REVIEW });
              await load();
              flash("검토 항목을 추가했습니다.");
            }}
            onDecision={async (row, status, decisionAction) => {
              if (!canUpdate) return;
              const updated = await securityApi.updateReview(row.id, { ...row, status, decisionAction });
              setReviews((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
              await load();
              flash("검토 상태를 변경했습니다.");
            }}
          />
        )}
        {tab === "providers" && canManageProviders && (
          <ProvidersPanel
            rows={providers}
            onToggle={async (row) => {
              if (!canManageProviders) return;
              const updated = await securityApi.updateProvider(row.providerCode, { ...row, enabled: !row.enabled });
              setProviders((prev) => prev.map((item) => (item.providerCode === updated.providerCode ? updated : item)));
              await load();
              flash(updated.enabled ? "Provider를 사용 설정했습니다." : "Provider를 중지했습니다.");
            }}
            onHealth={async (row) => {
              if (!canManageProviders) return;
              const updated = await securityApi.runProviderHealthCheck(row.providerCode);
              setProviders((prev) => prev.map((item) => (item.providerCode === updated.providerCode ? updated : item)));
              await load();
              flash("Provider 헬스체크를 기록했습니다.");
            }}
          />
        )}
        {tab === "providerHealth" && <ProviderHealthPanel rows={providerHealth} />}
        {tab === "appeals" && (
          <AppealsPanel
            rows={appeals}
            policy={appealPolicy}
            reason={policyReason}
            onReasonChange={setPolicyReason}
            onPolicyChange={setAppealPolicy}
            onSavePolicy={async () => {
              if (!canUpdate || !appealPolicy) return;
              const updated = await securityApi.updateAppealPolicy({ ...appealPolicy, reason: policyReason });
              setAppealPolicy(updated);
              setPolicyReason("");
              flash("이의제기 정책을 저장했습니다.");
            }}
            onDecision={async (row, status) => {
              if (!canUpdate) return;
              const updated = await securityApi.decideAppeal(row.id, { status, decisionReason: `${status} 처리` });
              setAppeals((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
              await load();
              flash("이의제기 상태를 변경했습니다.");
            }}
          />
        )}
        {tab === "engine" && <BlockEnginePanel flash={flash} />}
        {tab === "waf" && (
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs text-slate-500">
                대기(QUEUED/PENDING) 이벤트는 스케줄러가 주기적으로 처리합니다. 활성 WAF Provider(endpoint)가 있으면 실제 HTTP 동기화, 없으면 Mock으로 즉시 SYNCED 처리합니다.
              </p>
              {canUpdate && (
                <button
                  type="button"
                  className="av-btn bg-slate-900 text-white whitespace-nowrap"
                  onClick={async () => {
                    if (!canUpdate) return;
                    const n = await securityApi.processWafSync();
                    flash(`WAF 큐를 처리했습니다. (${n}건)`);
                    await load();
                  }}
                >
                  지금 처리
                </button>
              )}
            </div>
            <WafEventsPanel rows={wafEvents} />
          </div>
        )}
      </div>

      {toast && <div className="rpt-toast">{toast}</div>}
    </AdminShell>
  );
}

function Metric({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: number }) {
  return (
    <section className="rounded-lg border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground">
        <Icon className="size-4" /> {label}
      </div>
      <div className="mt-2 text-2xl font-extrabold">{value}</div>
    </section>
  );
}

function BlockRulesPanel({
  rows,
  form,
  onFormChange,
  onCreate,
  onToggle,
  onQueueWaf,
}: {
  rows: SecurityBlockRule[];
  form: typeof NEW_BLOCK;
  onFormChange: (next: typeof NEW_BLOCK) => void;
  onCreate: () => Promise<void>;
  onToggle: (row: SecurityBlockRule) => Promise<void>;
  onQueueWaf: (row: SecurityBlockRule) => Promise<void>;
}) {
  const { canCreate, canUpdate } = useAdminDomainAuthorization("SECURITY");
  const list = useAdminListTools(rows, { columns: BLOCK_COLUMNS, getRowId: (row) => row.id, defaultSortId: "updated", defaultSortDir: "desc" });
  return (
    <div className={canCreate ? "grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]" : "grid gap-5"}>
      {canCreate && <section className="rounded-lg border border-border bg-card p-4 shadow-sm">
        <h2 className="text-sm font-bold">차단 규칙 추가</h2>
        <div className="mt-3 grid gap-2">
          <Select value={form.ruleType} onChange={(ruleType) => onFormChange({ ...form, ruleType })} options={["USER", "EMAIL", "EMAIL_DOMAIN", "IP", "CIDR", "IP_RANGE", "COUNTRY", "ASN"]} />
          <Input value={form.ruleValue} onChange={(ruleValue) => onFormChange({ ...form, ruleValue })} placeholder="대상 값" />
          <Select value={form.scope} onChange={(scope) => onFormChange({ ...form, scope })} options={["GLOBAL", "LOGIN", "COMMUNITY", "AI", "SUPPORT"]} />
          <Select value={form.category} onChange={(category) => onFormChange({ ...form, category })} options={["MANUAL", "SPAM", "ABUSE", "BRUTE_FORCE", "GEO", "VPN", "SECURITY"]} />
          <Input value={form.reason} onChange={(reason) => onFormChange({ ...form, reason })} placeholder="사유" />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.wafSyncEnabled} onChange={(event) => onFormChange({ ...form, wafSyncEnabled: event.target.checked })} />
            WAF 동기화 대상
          </label>
          <button type="button" className="av-btn justify-center bg-slate-900 text-white" onClick={() => void onCreate()}>
            <Save /> 저장
          </button>
        </div>
      </section>}

      <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
        <AdminListToolbar state={list} fileName="security_block_rules" />
        <table className="av-table">
          <thead>
            <tr>
              <AdminSortableHeader state={list} columnId="type">유형</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="value">대상</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="scope">범위</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="category">분류</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="active">상태</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="waf">WAF</AdminSortableHeader>
              <th className="r">조치</th>
            </tr>
          </thead>
          <tbody>
            {list.visibleRows.map((row) => (
              <tr key={row.id}>
                <td>{row.ruleType}</td>
                <td><div className="av-cell__t">{row.ruleValue}</div><div className="av-cell__m">{row.reason ?? "-"}</div></td>
                <td>{row.scope}</td>
                <td>{row.category}</td>
                <td><StatusPill tone={row.active ? "green" : "slate"} label={row.active ? "활성" : "비활성"} /></td>
                <td><StatusPill tone={row.wafSyncStatus === "PENDING" ? "amber" : "slate"} label={row.wafSyncStatus} /></td>
                <td className="r">
                  {canUpdate && (
                    <div className="rv-actions">
                      <button className="av-btn" onClick={() => void onToggle(row)}>{row.active ? "중지" : "활성"}</button>
                      <button className="av-btn" onClick={() => void onQueueWaf(row)}>WAF</button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <AdminListFooter state={list} />
      </section>
    </div>
  );
}

function ReviewsPanel({
  rows,
  form,
  onFormChange,
  onCreate,
  onDecision,
}: {
  rows: SecurityReview[];
  form: typeof NEW_REVIEW;
  onFormChange: (next: typeof NEW_REVIEW) => void;
  onCreate: () => Promise<void>;
  onDecision: (row: SecurityReview, status: string, decisionAction: string) => Promise<void>;
}) {
  const { canCreate, canUpdate } = useAdminDomainAuthorization("SECURITY");
  const list = useAdminListTools(rows, { columns: REVIEW_COLUMNS, getRowId: (row) => row.id, defaultSortId: "created", defaultSortDir: "desc" });
  return (
    <div className={canCreate ? "grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]" : "grid gap-5"}>
      {canCreate && <section className="rounded-lg border border-border bg-card p-4 shadow-sm">
        <h2 className="text-sm font-bold">검토 큐 추가</h2>
        <div className="mt-3 grid gap-2">
          <Select value={form.reviewType} onChange={(reviewType) => onFormChange({ ...form, reviewType })} options={["LOGIN_RISK", "EXTERNAL_RISK", "SECURITY_RISK", "GENERAL"]} />
          <Select value={form.subjectType} onChange={(subjectType) => onFormChange({ ...form, subjectType })} options={["USER", "IP", "EMAIL", "POST", "COMMENT", "SESSION"]} />
          <Input value={form.subjectValue} onChange={(subjectValue) => onFormChange({ ...form, subjectValue })} placeholder="대상 값" />
          <Input value={String(form.riskScore)} onChange={(riskScore) => onFormChange({ ...form, riskScore: Number(riskScore) || 0 })} placeholder="위험 점수" />
          <Input value={form.reason} onChange={(reason) => onFormChange({ ...form, reason })} placeholder="사유" />
          <button type="button" className="av-btn justify-center bg-slate-900 text-white" onClick={() => void onCreate()}>
            <Save /> 저장
          </button>
        </div>
      </section>}
      <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
        <AdminListToolbar state={list} fileName="security_reviews" />
        <table className="av-table">
          <thead>
            <tr>
              <AdminSortableHeader state={list} columnId="type">검토</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="subject">대상</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="score" className="r">점수</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="level">위험도</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="status">상태</AdminSortableHeader>
              <th className="r">조치</th>
            </tr>
          </thead>
          <tbody>
            {list.visibleRows.map((row) => (
              <tr key={row.id}>
                <td>{row.reviewType}</td>
                <td><div className="av-cell__t">{row.subjectValue}</div><div className="av-cell__m">{row.subjectType} · {row.reason ?? "-"}</div></td>
                <td className="r num">{row.riskScore}</td>
                <td><StatusPill tone={riskTone(row.riskLevel)} label={row.riskLevel} /></td>
                <td>{row.status}</td>
                <td className="r">
                  {canUpdate && (
                    <div className="rv-actions">
                      <button className="av-btn" onClick={() => void onDecision(row, "APPROVED", "BLOCK")}>차단</button>
                      <button className="av-btn" onClick={() => void onDecision(row, "DISMISSED", "ALLOW")}>기각</button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <AdminListFooter state={list} />
      </section>
    </div>
  );
}

function ProvidersPanel({
  rows,
  onToggle,
  onHealth,
}: {
  rows: SecurityProviderConfig[];
  onToggle: (row: SecurityProviderConfig) => Promise<void>;
  onHealth: (row: SecurityProviderConfig) => Promise<void>;
}) {
  const { role } = useAdminDomainAuthorization("SECURITY");
  const list = useAdminListTools(rows, { columns: PROVIDER_COLUMNS, getRowId: (row) => row.providerCode });
  if (role !== "SUPER_ADMIN") return null;
  return (
    <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <AdminListToolbar state={list} fileName="security_providers" />
      <table className="av-table">
        <thead>
          <tr>
            <AdminSortableHeader state={list} columnId="code">Provider</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="name">이름</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="type">유형</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="enabled">상태</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="health">헬스</AdminSortableHeader>
            <th className="r">조치</th>
          </tr>
        </thead>
        <tbody>
          {list.visibleRows.map((row) => (
            <tr key={row.providerCode}>
              <td className="av-id">{row.providerCode}</td>
              <td>{row.displayName}</td>
              <td>{row.providerType} · {row.mode}</td>
              <td><StatusPill tone={row.enabled ? "green" : "slate"} label={row.enabled ? "사용" : "중지"} /></td>
              <td><StatusPill tone={row.healthStatus === "OK" ? "green" : "amber"} label={row.healthStatus} /></td>
              <td className="r">
                <div className="rv-actions">
                  <button className="av-btn" onClick={() => void onToggle(row)}>{row.enabled ? "중지" : "사용"}</button>
                  <button className="av-btn" onClick={() => void onHealth(row)}>헬스체크</button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <AdminListFooter state={list} />
    </section>
  );
}

function ProviderHealthPanel({ rows }: { rows: SecurityProviderHealthHistory[] }) {
  const list = useAdminListTools(rows, {
    columns: PROVIDER_HEALTH_COLUMNS,
    getRowId: (row) => row.id,
    defaultSortId: "checked",
    defaultSortDir: "desc",
  });
  return (
    <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <AdminListToolbar state={list} fileName="security_provider_health_history" />
      <table className="av-table">
        <thead>
          <tr>
            <AdminSortableHeader state={list} columnId="checked">점검</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="provider">Provider</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="source">출처</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="before">이전</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="after">결과</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="actor">처리자</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="detail">상세</AdminSortableHeader>
          </tr>
        </thead>
        <tbody>
          {list.visibleRows.map((row) => (
            <tr key={row.id}>
              <td>{formatDate(row.checkedAt)}</td>
              <td><div className="av-cell__t">{row.providerCode}</div><div className="av-cell__m">{row.providerType}</div></td>
              <td>{row.checkSource}</td>
              <td>{row.statusBefore ?? "-"}</td>
              <td><StatusPill tone={row.statusAfter === "OK" ? "green" : row.statusAfter === "DISABLED" || row.statusAfter === "SKIPPED" ? "slate" : "amber"} label={row.statusAfter} /></td>
              <td>{row.actorEmail ?? "-"}</td>
              <td><div className="av-cell__m">{row.detailMessage ?? "-"}</div></td>
            </tr>
          ))}
        </tbody>
      </table>
      <AdminListFooter state={list} />
    </section>
  );
}

function AppealsPanel({
  rows,
  policy,
  reason,
  onReasonChange,
  onPolicyChange,
  onSavePolicy,
  onDecision,
}: {
  rows: SecurityAppeal[];
  policy: SecurityAppealPolicy | null;
  reason: string;
  onReasonChange: (value: string) => void;
  onPolicyChange: (value: SecurityAppealPolicy | null) => void;
  onSavePolicy: () => Promise<void>;
  onDecision: (row: SecurityAppeal, status: string) => Promise<void>;
}) {
  const { canUpdate } = useAdminDomainAuthorization("SECURITY");
  const list = useAdminListTools(rows, { columns: APPEAL_COLUMNS, getRowId: (row) => row.id, defaultSortId: "created", defaultSortDir: "desc" });
  return (
    <div className="grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]">
      <section className="rounded-lg border border-border bg-card p-4 shadow-sm">
        <h2 className="text-sm font-bold">이의제기 정책</h2>
        {policy ? (
          <div className="mt-3 grid gap-2">
            <Input value={policy.displayName} disabled={!canUpdate} onChange={(displayName) => onPolicyChange({ ...policy, displayName })} />
            <Input value={String(policy.maxOpenPerSubject)} disabled={!canUpdate} onChange={(value) => onPolicyChange({ ...policy, maxOpenPerSubject: Number(value) || 1 })} />
            <Input value={String(policy.submitterDailyLimit)} disabled={!canUpdate} onChange={(value) => onPolicyChange({ ...policy, submitterDailyLimit: Number(value) || 1 })} />
            <Input value={String(policy.tokenTtlHours)} disabled={!canUpdate} onChange={(value) => onPolicyChange({ ...policy, tokenTtlHours: Number(value) || 1 })} />
            {canUpdate && <Input value={reason} onChange={onReasonChange} placeholder="변경 사유" />}
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={policy.enabled} disabled={!canUpdate} onChange={(event) => onPolicyChange({ ...policy, enabled: event.target.checked })} />
              정책 사용
            </label>
            {canUpdate && (
              <button type="button" className="av-btn justify-center bg-slate-900 text-white" onClick={() => void onSavePolicy()}>
                <Save /> 저장
              </button>
            )}
          </div>
        ) : (
          <div className="mt-3 text-sm text-muted-foreground">정책 데이터에 접근할 수 없습니다.</div>
        )}
      </section>
      <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
        <AdminListToolbar state={list} fileName="security_appeals" />
        <table className="av-table">
          <thead>
            <tr>
              <AdminSortableHeader state={list} columnId="request">요청</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="subject">대상</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="email">신청자</AdminSortableHeader>
              <AdminSortableHeader state={list} columnId="status">상태</AdminSortableHeader>
              <th className="r">조치</th>
            </tr>
          </thead>
          <tbody>
            {list.visibleRows.map((row) => (
              <tr key={row.id}>
                <td className="av-id">{row.publicRequestId}</td>
                <td><div className="av-cell__t">{row.subjectValue}</div><div className="av-cell__m">{row.reason ?? "-"}</div></td>
                <td>{row.submitterEmail}</td>
                <td>{row.status}</td>
                <td className="r">
                  {canUpdate && (
                    <div className="rv-actions">
                      <button className="av-btn" onClick={() => void onDecision(row, "APPROVED")}>승인</button>
                      <button className="av-btn" onClick={() => void onDecision(row, "REJECTED")}>거절</button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <AdminListFooter state={list} />
      </section>
    </div>
  );
}

function WafEventsPanel({ rows }: { rows: WafSyncEvent[] }) {
  const list = useAdminListTools(rows, { columns: WAF_COLUMNS, getRowId: (row) => row.id, defaultSortId: "requested", defaultSortDir: "desc" });
  return (
    <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <AdminListToolbar state={list} fileName="security_waf_events" />
      <table className="av-table">
        <thead>
          <tr>
            <AdminSortableHeader state={list} columnId="id">ID</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="provider">Provider</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="operation">작업</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="status">상태</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="requested">요청</AdminSortableHeader>
            <AdminSortableHeader state={list} columnId="processed">처리</AdminSortableHeader>
          </tr>
        </thead>
        <tbody>
          {list.visibleRows.map((row) => (
            <tr key={row.id}>
              <td className="av-id">#{row.id}</td>
              <td>{row.providerCode}</td>
              <td>{row.operationType}</td>
              <td><StatusPill tone={row.status === "QUEUED" ? "amber" : row.status === "SYNCED" ? "green" : "slate"} label={row.status} /></td>
              <td>{formatDate(row.requestedAt)}</td>
              <td>{formatDate(row.processedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <AdminListFooter state={list} />
    </section>
  );
}

function Input({ value, onChange, placeholder, disabled = false }: { value: string; onChange: (value: string) => void; placeholder?: string; disabled?: boolean }) {
  return (
    <input
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      disabled={disabled}
      className="h-10 rounded-md border border-border bg-card px-3 text-sm"
    />
  );
}

function Select({ value, options, onChange }: { value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <select value={value} onChange={(event) => onChange(event.target.value)} className="h-10 rounded-md border border-border bg-card px-3 text-sm">
      {options.map((option) => <option key={option} value={option}>{option}</option>)}
    </select>
  );
}

function StatusPill({ tone, label }: { tone: "green" | "amber" | "red" | "slate"; label: string }) {
  const cls = tone === "green" ? "av-st--ok" : tone === "amber" ? "av-st--warn" : tone === "red" ? "av-st--off" : "av-st--info";
  return <span className={`av-st ${cls}`}>{label}</span>;
}

function riskTone(level: string): "green" | "amber" | "red" | "slate" {
  if (level === "CRITICAL" || level === "HIGH") return "red";
  if (level === "MEDIUM") return "amber";
  if (level === "LOW") return "green";
  return "slate";
}

function formatDate(value: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" }).format(date);
}
