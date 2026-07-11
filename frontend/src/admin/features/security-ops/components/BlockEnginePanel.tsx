import { useCallback, useEffect, useRef, useState } from "react";
import { DatabaseZap, RefreshCw, Upload } from "lucide-react";

import {
  getBlockBatches,
  getBlockCacheStatus,
  syncBlockCache,
  toggleBlockBatch,
  uploadPolicyFeed,
} from "../api";
import type { BlockCacheStatus, IpBlockBatch, PolicyFeedImportResult } from "../types";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

function formatDate(value: string | null): string {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

/**
 * 차단 집행 엔진 콘솔 — 런타임 캐시 상태·수동 동기화, 정책기관 피드 대량 업로드, IP 정책 배치 제어.
 * 규칙은 이 캐시를 기준으로 실제 요청 차단에 적용된다(DB 직접 조회 아님).
 */
export function BlockEnginePanel({ flash }: { flash: (message: string) => void }) {
  const { canCreate, canUpdate } = useAdminDomainAuthorization("SECURITY");
  const [cache, setCache] = useState<BlockCacheStatus | null>(null);
  const [batches, setBatches] = useState<IpBlockBatch[]>([]);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<PolicyFeedImportResult | null>(null);
  const [action, setAction] = useState("BLOCK");
  const fileRef = useRef<HTMLInputElement>(null);

  const reload = useCallback(async () => {
    const [status, batchList] = await Promise.all([getBlockCacheStatus(), getBlockBatches()]);
    setCache(status);
    setBatches(batchList);
  }, []);

  useEffect(() => {
    reload().catch(() => flash("차단 엔진 상태를 불러오지 못했습니다."));
  }, [reload, flash]);

  const onSync = async () => {
    if (!canUpdate) return;
    setBusy(true);
    try {
      const status = await syncBlockCache();
      setCache(status);
      flash(`차단 캐시를 동기화했습니다. (활성 규칙 ${status.ruleCount}건)`);
    } finally {
      setBusy(false);
    }
  };

  const onUpload = async () => {
    if (!canCreate) return;
    const file = fileRef.current?.files?.[0];
    if (!file) {
      flash("업로드할 CSV/JSON 파일을 선택해 주세요.");
      return;
    }
    setBusy(true);
    try {
      const res = await uploadPolicyFeed(file, { action });
      setResult(res);
      flash(`정책 피드 import 완료 — 생성 ${res.created} / 중복 ${res.skipped} / 실패 ${res.failed}`);
      if (fileRef.current) fileRef.current.value = "";
      await reload();
    } finally {
      setBusy(false);
    }
  };

  const onToggleBatch = async (batch: IpBlockBatch) => {
    if (!canUpdate) return;
    const strategy = batch.active ? "CASCADE_ACTIVE_RULES" : "FORCE_ENABLE_ALL";
    const updated = await toggleBlockBatch(batch.id, !batch.active, strategy);
    setBatches((prev) => prev.map((b) => (b.id === updated.id ? updated : b)));
    flash(updated.active ? "배치를 활성화했습니다." : "배치를 비활성화했습니다.");
    await reload();
  };

  return (
    <div className="space-y-6">
      {/* 런타임 캐시 */}
      <section className="rounded-xl border border-slate-200 bg-card p-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <DatabaseZap className="h-5 w-5 text-slate-500" />
            <h3 className="text-sm font-semibold text-slate-800">런타임 차단 캐시</h3>
          </div>
          {canUpdate && (
            <button type="button" className="av-btn" onClick={onSync} disabled={busy}>
              <RefreshCw className="mr-1 inline h-4 w-4" /> 지금 동기화
            </button>
          )}
        </div>
        <p className="mt-1 text-xs text-slate-500">
          요청 차단은 DB 직접 조회가 아니라 이 메모리·파일 캐시를 기준으로 처리됩니다. 규칙 변경 시 자동 동기화되며, 필요 시 수동 동기화할 수 있습니다.
        </p>
        <div className="mt-3 grid grid-cols-3 gap-3 text-sm">
          <div className="rounded-lg bg-slate-50 px-3 py-2">
            <div className="text-xs text-slate-500">출처</div>
            <div className="font-semibold text-slate-800">{cache?.source ?? "-"}</div>
          </div>
          <div className="rounded-lg bg-slate-50 px-3 py-2">
            <div className="text-xs text-slate-500">활성 규칙</div>
            <div className="font-mono text-lg font-bold text-slate-800">{cache?.ruleCount ?? 0}</div>
          </div>
          <div className="rounded-lg bg-slate-50 px-3 py-2">
            <div className="text-xs text-slate-500">적재 시각</div>
            <div className="font-semibold text-slate-800">{formatDate(cache?.loadedAt ?? null)}</div>
          </div>
        </div>
      </section>

      {/* 정책 피드 업로드 */}
      {canCreate && <section className="rounded-xl border border-slate-200 bg-card p-4">
        <div className="flex items-center gap-2">
          <Upload className="h-5 w-5 text-slate-500" />
          <h3 className="text-sm font-semibold text-slate-800">정책기관 피드 업로드 (CSV / JSON)</h3>
        </div>
        <p className="mt-1 text-xs text-slate-500">
          IP·CIDR·국가·ASN 규칙을 대량 등록합니다. 매치 타입을 비워두면 값 패턴으로 자동 추론합니다(‘/’→CIDR, 2글자→국가, ‘AS…’→ASN). ‘#’ 주석·빈 줄은 건너뜁니다.
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <input ref={fileRef} type="file" accept=".csv,.json,.txt" className="text-sm" />
          <select className="av-input" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="BLOCK">BLOCK(차단)</option>
            <option value="ALLOWLIST">ALLOWLIST(허용)</option>
            <option value="REVIEW">REVIEW(검토)</option>
          </select>
          <button type="button" className="av-btn bg-slate-900 text-white" onClick={onUpload} disabled={busy}>
            업로드 & 등록
          </button>
        </div>
        {result && (
          <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
            <div className="font-semibold text-slate-800">
              배치 <span className="font-mono">{result.batchCode}</span> — 총 {result.total}건 중
              <span className="text-emerald-600"> 생성 {result.created}</span> ·
              <span className="text-amber-600"> 중복 {result.skipped}</span> ·
              <span className="text-red-600"> 실패 {result.failed}</span>
            </div>
            {result.messages.length > 0 && (
              <ul className="mt-2 max-h-40 space-y-0.5 overflow-y-auto text-xs text-slate-500">
                {result.messages.map((m, i) => (
                  <li key={i}>· {m}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </section>}

      {/* IP 정책 배치 */}
      <section className="rounded-xl border border-slate-200 bg-card p-4">
        <h3 className="text-sm font-semibold text-slate-800">IP 정책 배치</h3>
        <p className="mt-1 text-xs text-slate-500">
          배치를 끄면 하위 규칙이 캐시에서 제외되어 즉시 미적용됩니다. 켤 때는 하위 규칙을 함께 활성화(cascade)합니다.
        </p>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                <th className="py-2 pr-3">배치 코드</th>
                <th className="py-2 pr-3">이름 / 출처</th>
                <th className="py-2 pr-3">규칙</th>
                <th className="py-2 pr-3">상태</th>
                <th className="py-2 pr-3">생성</th>
                <th className="py-2">동작</th>
              </tr>
            </thead>
            <tbody>
              {batches.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-6 text-center text-slate-400">
                    아직 배치가 없습니다. 위에서 정책 피드를 업로드하면 배치가 생성됩니다.
                  </td>
                </tr>
              )}
              {batches.map((b) => (
                <tr key={b.id} className="border-b border-slate-100">
                  <td className="py-2 pr-3 font-mono text-xs">{b.batchCode}</td>
                  <td className="py-2 pr-3">
                    <div className="font-medium text-slate-800">{b.batchName}</div>
                    <div className="text-xs text-slate-400">{b.sourceType}{b.sourceName ? ` · ${b.sourceName}` : ""}</div>
                  </td>
                  <td className="py-2 pr-3 font-mono">{b.activeRuleCount}/{b.totalRuleCount}</td>
                  <td className="py-2 pr-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${b.active ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"}`}>
                      {b.active ? "활성" : "비활성"}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-xs text-slate-500">{formatDate(b.createdAt)}</td>
                  <td className="py-2">
                    {canUpdate && (
                      <button type="button" className="av-btn text-xs" onClick={() => onToggleBatch(b)} disabled={busy}>
                        {b.active ? "끄기" : "켜기"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
