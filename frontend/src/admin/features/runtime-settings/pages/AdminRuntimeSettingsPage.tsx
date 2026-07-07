import { useCallback, useEffect, useRef, useState } from "react";
import { Download, RotateCw, SlidersHorizontal, Upload } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import {
  getRuntimeSettings,
  getRuntimeSettingHistory,
  saveRuntimeSetting,
  type RuntimeSetting,
  type RuntimeSettingHistory,
} from "../api";
import {
  exportSettings,
  getSettingsSections,
  importSettings,
  type SettingsExport,
  type SettingsImportResult,
} from "../settingsIoApi";

const VALUE_TYPES = ["STRING", "NUMBER", "BOOLEAN", "URL", "SECRET"];

function fmt(value: string | null): string {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

const EMPTY = {
  settingKey: "",
  settingGroup: "GENERAL",
  displayName: "",
  settingValue: "",
  fallbackValue: "",
  valueType: "STRING",
  active: true,
  description: "",
};

export function AdminRuntimeSettingsPage() {
  const [rows, setRows] = useState<RuntimeSetting[]>([]);
  const [keyword, setKeyword] = useState("");
  const [includeInactive, setIncludeInactive] = useState(false);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [draft, setDraft] = useState({ ...EMPTY });
  const [history, setHistory] = useState<RuntimeSettingHistory[]>([]);
  const [historyKey, setHistoryKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await getRuntimeSettings("", keyword, includeInactive));
    } finally {
      setLoading(false);
    }
  }, [keyword, includeInactive]);

  useEffect(() => {
    void load();
  }, [load]);

  const flash = (m: string) => {
    setMsg(m);
    setTimeout(() => setMsg(null), 2500);
  };

  const saveValue = async (row: RuntimeSetting, value: string) => {
    await saveRuntimeSetting({ ...row, settingValue: value });
    flash(`'${row.settingKey}' 저장됨`);
    await load();
  };

  const toggleActive = async (row: RuntimeSetting) => {
    await saveRuntimeSetting({ ...row, active: !row.active });
    await load();
  };

  const createNew = async () => {
    if (!draft.settingKey.trim()) {
      flash("설정 키를 입력해 주세요.");
      return;
    }
    await saveRuntimeSetting(draft);
    flash(`'${draft.settingKey}' 생성됨`);
    setDraft({ ...EMPTY });
    await load();
  };

  const showHistory = async (key: string) => {
    setHistoryKey(key);
    setHistory(await getRuntimeSettingHistory(key, 50));
  };

  return (
    <AdminShell
      active="runtime-settings"
      breadcrumb="운영 / 설정"
      title="런타임 설정"
      icon={SlidersHorizontal}
      desc="코드가 실시간 참조하는 key-value 설정을 DB에서 관리합니다(값 → fallback → 코드 기본값 순). 모든 변경은 사유·전후값·버전으로 이력에 남습니다."
      actions={(
        <button type="button" className="av-btn" onClick={() => void load()} disabled={loading}>
          <RotateCw className={loading ? "animate-spin" : ""} /> 새로고침
        </button>
      )}
    >
      {msg && <div className="mb-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{msg}</div>}

      <SettingsBackupSection flash={flash} onImported={() => void load()} />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <input
          className="av-input"
          placeholder="키·이름·설명 검색"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <label className="flex items-center gap-1 text-sm text-slate-600">
          <input type="checkbox" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
          비활성 포함
        </label>
      </div>

      <div className="max-h-[60vh] overflow-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full min-w-[900px] text-sm">
          <thead className="sticky top-0 z-10 bg-white">
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">키 / 이름</th>
              <th className="px-3 py-2">그룹</th>
              <th className="px-3 py-2">값</th>
              <th className="px-3 py-2">타입</th>
              <th className="px-3 py-2">상태</th>
              <th className="px-3 py-2">수정</th>
              <th className="px-3 py-2">동작</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr><td colSpan={7} className="px-3 py-8 text-center text-slate-400">설정이 없습니다.</td></tr>
            )}
            {rows.map((r) => (
              <SettingRow key={r.id} row={r} onSaveValue={saveValue} onToggle={toggleActive} onHistory={showHistory} />
            ))}
          </tbody>
        </table>
      </div>

      {/* 신규 설정 */}
      <section className="mt-5 rounded-xl border border-slate-200 bg-white p-4">
        <h3 className="text-sm font-semibold text-slate-800">신규 설정 추가</h3>
        <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
          <input className="av-input" placeholder="설정 키(예: community.report.blur-threshold)" value={draft.settingKey}
                 onChange={(e) => setDraft({ ...draft, settingKey: e.target.value })} />
          <input className="av-input" placeholder="그룹" value={draft.settingGroup}
                 onChange={(e) => setDraft({ ...draft, settingGroup: e.target.value })} />
          <input className="av-input" placeholder="표시명" value={draft.displayName}
                 onChange={(e) => setDraft({ ...draft, displayName: e.target.value })} />
          <input className="av-input" placeholder="값" value={draft.settingValue}
                 onChange={(e) => setDraft({ ...draft, settingValue: e.target.value })} />
          <input className="av-input" placeholder="fallback" value={draft.fallbackValue}
                 onChange={(e) => setDraft({ ...draft, fallbackValue: e.target.value })} />
          <select className="av-input" value={draft.valueType} onChange={(e) => setDraft({ ...draft, valueType: e.target.value })}>
            {VALUE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <div className="mt-3">
          <button type="button" className="av-btn bg-slate-900 text-white" onClick={() => void createNew()}>추가</button>
        </div>
      </section>

      {/* 변경 이력 */}
      {historyKey && (
        <section className="mt-5 rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-800">변경 이력 — <span className="font-mono">{historyKey}</span></h3>
            <button type="button" className="av-btn text-xs" onClick={() => setHistoryKey(null)}>닫기</button>
          </div>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                  <th className="px-3 py-2">시각</th>
                  <th className="px-3 py-2">버전</th>
                  <th className="px-3 py-2">유형</th>
                  <th className="px-3 py-2">이전값 → 이후값</th>
                  <th className="px-3 py-2">처리자</th>
                </tr>
              </thead>
              <tbody>
                {history.length === 0 && <tr><td colSpan={5} className="px-3 py-4 text-center text-slate-400">이력이 없습니다.</td></tr>}
                {history.map((h) => (
                  <tr key={h.id} className="border-b border-slate-100">
                    <td className="px-3 py-2 text-xs text-slate-500">{fmt(h.createdAt)}</td>
                    <td className="px-3 py-2 font-mono">v{h.versionNo}</td>
                    <td className="px-3 py-2 text-xs">{h.changeType}</td>
                    <td className="px-3 py-2 font-mono text-xs">{h.beforeValue ?? "∅"} → {h.afterValue ?? "∅"}</td>
                    <td className="px-3 py-2 font-mono text-xs">{h.actorUserId ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </AdminShell>
  );
}

const SECTION_LABELS: Record<string, string> = {
  runtimeSettings: "런타임 설정",
  moderation: "콘텐츠 중재 정책",
};

function SettingsBackupSection({ flash, onImported }: { flash: (m: string) => void; onImported: () => void }) {
  const [sections, setSections] = useState<string[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<SettingsImportResult | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    getSettingsSections()
      .then((s) => { setSections(s); setSelected(new Set(s)); })
      .catch(() => setSections([]));
  }, []);

  const toggle = (s: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(s)) next.delete(s); else next.add(s);
      return next;
    });
  };

  const doExport = async () => {
    setBusy(true);
    try {
      const data = await exportSettings([...selected]);
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `careertuner-settings-${data.exportedAt?.slice(0, 10) ?? "export"}.json`;
      a.click();
      URL.revokeObjectURL(url);
      flash("설정을 JSON 파일로 내보냈어요.");
    } catch {
      flash("내보내기 실패 — 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const onFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setBusy(true);
    setResult(null);
    try {
      const text = await file.text();
      const payload = JSON.parse(text) as SettingsExport;
      const r = await importSettings(payload);
      setResult(r);
      flash(`가져오기 완료 — 적용 ${r.totalApplied}건 / 스킵 ${r.totalSkipped}건`);
      onImported();
    } catch {
      flash("가져오기 실패 — JSON 형식 또는 서버 오류를 확인해 주세요.");
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  return (
    <section className="mb-4 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-slate-800">설정 백업 / 복원</h3>
          <p className="text-xs text-slate-500">런타임 설정·중재 정책을 JSON으로 내보내고 되돌립니다(환경 이관·복구용). 없는 항목은 건너뛰고 결과를 리포팅해요.</p>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        {sections.map((s) => (
          <label key={s} className="flex items-center gap-1 text-sm text-slate-600">
            <input type="checkbox" checked={selected.has(s)} onChange={() => toggle(s)} />
            {SECTION_LABELS[s] ?? s}
          </label>
        ))}
        <button type="button" className="av-btn" disabled={busy || selected.size === 0} onClick={() => void doExport()}>
          <Download size={14} /> 내보내기
        </button>
        <button type="button" className="av-btn" disabled={busy} onClick={() => fileRef.current?.click()}>
          <Upload size={14} /> 가져오기(JSON)
        </button>
        <input ref={fileRef} type="file" accept="application/json,.json" hidden onChange={onFile} />
      </div>

      {result && (
        <div className="mt-3 rounded-lg bg-slate-50 p-3 text-xs">
          <div className="font-semibold text-slate-700">
            적용 {result.totalApplied}건 · 스킵 {result.totalSkipped}건
          </div>
          <ul className="mt-1 space-y-0.5 text-slate-500">
            {result.sections.map((sec) => (
              <li key={sec.section}>
                <span className="font-mono">{SECTION_LABELS[sec.section] ?? sec.section}</span> — 적용 {sec.applied} / 스킵 {sec.skipped}
                {sec.messages.length > 0 && <span className="text-amber-600"> ({sec.messages.join("; ")})</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function SettingRow({
  row, onSaveValue, onToggle, onHistory,
}: {
  row: RuntimeSetting;
  onSaveValue: (row: RuntimeSetting, value: string) => Promise<void>;
  onToggle: (row: RuntimeSetting) => Promise<void>;
  onHistory: (key: string) => Promise<void>;
}) {
  const [value, setValue] = useState(row.settingValue ?? "");
  const dirty = value !== (row.settingValue ?? "");
  return (
    <tr className="border-b border-slate-100">
      <td className="px-3 py-2">
        <div className="font-mono text-xs font-semibold text-slate-800">{row.settingKey}</div>
        <div className="text-xs text-slate-400">{row.displayName}</div>
      </td>
      <td className="px-3 py-2"><span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{row.settingGroup}</span></td>
      <td className="px-3 py-2">
        {row.secret ? (
          <span className="text-xs text-slate-400">••••••(비밀)</span>
        ) : row.editable ? (
          <input className="av-input w-full" value={value} onChange={(e) => setValue(e.target.value)}
                 placeholder={row.fallbackValue ?? "(비어있음)"} />
        ) : (
          <span className="font-mono text-xs">{row.settingValue ?? row.fallbackValue ?? "-"}</span>
        )}
      </td>
      <td className="px-3 py-2 text-xs">{row.valueType}</td>
      <td className="px-3 py-2">
        <button type="button"
                className={`rounded-full px-2 py-0.5 text-xs font-semibold ${row.active ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"}`}
                onClick={() => void onToggle(row)}>
          {row.active ? "활성" : "비활성"}
        </button>
      </td>
      <td className="px-3 py-2 text-xs text-slate-500">{fmt(row.updatedAt)}</td>
      <td className="px-3 py-2">
        <div className="flex gap-1">
          {row.editable && !row.secret && (
            <button type="button" className="av-btn text-xs" disabled={!dirty} onClick={() => void onSaveValue(row, value)}>저장</button>
          )}
          <button type="button" className="av-btn text-xs" onClick={() => void onHistory(row.settingKey)}>이력</button>
        </div>
      </td>
    </tr>
  );
}
