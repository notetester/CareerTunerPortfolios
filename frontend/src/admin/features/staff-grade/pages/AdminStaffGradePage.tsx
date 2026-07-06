import { useCallback, useEffect, useRef, useState } from "react";
import { BadgeDollarSign, Download, RotateCw, Upload } from "lucide-react";

import AdminShell from "../../../components/AdminShell";
import {
  applyStaffImport,
  downloadStaffGradeExport,
  getStaffCandidates,
  getStaffGrade,
  getStaffGradeHistory,
  getStaffGrades,
  previewStaffImport,
  upsertStaffGrade,
  type ImportPreview,
  type StaffCandidate,
  type StaffGradeHistory,
  type StaffGradeRow,
  type StaffGradeUpsert,
} from "../api";

function fmt(value: string | null): string {
  if (!value) return "-";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString("ko-KR");
}

function money(v: number | null): string {
  return v == null ? "-" : v.toLocaleString("ko-KR");
}

const EMPTY_FORM: StaffGradeUpsert = {
  department: "", seniority: "", jobTier: "", payBand: "", jobGrade: "", payStep: "",
  baseSalary: null, currency: "KRW", effectiveDate: "", memo: "",
};

export function AdminStaffGradePage() {
  const [rows, setRows] = useState<StaffGradeRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [department, setDepartment] = useState("");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ row: StaffGradeRow; form: StaffGradeUpsert } | null>(null);
  const [candidates, setCandidates] = useState<StaffCandidate[]>([]);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [history, setHistory] = useState<{ userId: number; items: StaffGradeHistory[] } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const size = 20;

  const flash = (m: string) => { setMsg(m); setTimeout(() => setMsg(null), 2500); };

  const load = useCallback(async (p: number, kw: string, dept: string) => {
    setLoading(true);
    try {
      const res = await getStaffGrades(kw, dept, p, size);
      setRows(res.items);
      setTotal(res.total);
      setPage(res.page);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(1, "", ""); }, [load]);
  useEffect(() => { getStaffCandidates().then(setCandidates).catch(() => setCandidates([])); }, []);

  const totalPages = Math.max(1, Math.ceil(total / size));

  const openEdit = async (userId: number) => {
    const row = await getStaffGrade(userId);
    setEditing({
      row,
      form: {
        department: row.department ?? "", seniority: row.seniority ?? "", jobTier: row.jobTier ?? "",
        payBand: row.payBand ?? "", jobGrade: row.jobGrade ?? "", payStep: row.payStep ?? "",
        baseSalary: row.baseSalary, currency: row.currency ?? "KRW",
        effectiveDate: row.effectiveDate ?? "", memo: row.memo ?? "",
      },
    });
  };

  const saveEdit = async () => {
    if (!editing) return;
    await upsertStaffGrade(editing.row.userId, {
      ...editing.form,
      baseSalary: editing.form.baseSalary === null || String(editing.form.baseSalary) === "" ? null : Number(editing.form.baseSalary),
      effectiveDate: editing.form.effectiveDate || null,
    });
    flash(`${editing.row.userEmail} 등급/급여 저장됨`);
    setEditing(null);
    await load(page, keyword, department);
    getStaffCandidates().then(setCandidates).catch(() => {});
  };

  const onFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      setPreview(await previewStaffImport(file));
    } catch (err) {
      flash(err instanceof Error ? err.message : "업로드 실패");
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const applyImport = async () => {
    if (!preview) return;
    const res = await applyStaffImport(preview.rows);
    flash(`적용 ${res.appliedCount}건 · 스킵 ${res.skippedCount}건`);
    setPreview(null);
    await load(page, keyword, department);
  };

  const showHistory = async (userId: number) => {
    setHistory({ userId, items: await getStaffGradeHistory(userId) });
  };

  return (
    <AdminShell
      active="staff-grades"
      breadcrumb="정책/감사 / 직원 등급"
      title="직원 등급/급여 관리"
      icon={BadgeDollarSign}
      desc="관리자/직원 계정의 조직 등급(연차·티어·밴드·직급·호봉)과 기본급을 관리합니다. 급여는 민감정보로 최고 관리자 전용이며 모든 변경은 이력에 남습니다."
      actions={(
        <button type="button" className="av-btn" onClick={() => void load(page, keyword, department)} disabled={loading}>
          <RotateCw size={14} className={loading ? "animate-spin" : ""} /> 새로고침
        </button>
      )}
    >
      {msg && <div className="mb-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{msg}</div>}

      {/* 툴바 */}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <input className="av-input" placeholder="이메일·이름 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <input className="av-input" placeholder="부서" value={department} onChange={(e) => setDepartment(e.target.value)} />
        <button type="button" className="av-btn" onClick={() => void load(1, keyword, department)}>검색</button>
        <div className="ml-auto flex items-center gap-2">
          <button type="button" className="av-btn" onClick={() => void downloadStaffGradeExport("excel", keyword, department)}>
            <Download size={14} /> Excel
          </button>
          <button type="button" className="av-btn" onClick={() => void downloadStaffGradeExport("csv", keyword, department)}>
            <Download size={14} /> CSV
          </button>
          <button type="button" className="av-btn" onClick={() => fileRef.current?.click()}>
            <Upload size={14} /> 업로드
          </button>
          <input ref={fileRef} type="file" accept=".xlsx,.xls,.csv" hidden onChange={onFile} />
        </div>
      </div>

      {/* 등급 배정(신규) */}
      <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm">
        <span className="text-slate-600">등급 배정/편집:</span>
        <select className="av-input" defaultValue="" onChange={(e) => { if (e.target.value) void openEdit(Number(e.target.value)); e.target.value = ""; }}>
          <option value="">관리자/직원 선택…</option>
          {candidates.map((c) => (
            <option key={c.userId} value={c.userId}>
              {c.email} ({c.name}) {c.hasGrade ? "· 등급있음" : "· 미배정"}
            </option>
          ))}
        </select>
      </div>

      {/* 목록 */}
      <div className="max-h-[55vh] overflow-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full min-w-[1000px] text-sm">
          <thead className="sticky top-0 z-10 bg-white">
            <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
              <th className="px-3 py-2">사용자</th>
              <th className="px-3 py-2">부서</th>
              <th className="px-3 py-2">연차</th>
              <th className="px-3 py-2">티어</th>
              <th className="px-3 py-2">밴드</th>
              <th className="px-3 py-2">직급</th>
              <th className="px-3 py-2">호봉</th>
              <th className="px-3 py-2">기본급</th>
              <th className="px-3 py-2">적용일</th>
              <th className="px-3 py-2">동작</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.userId} className="border-b border-slate-100">
                <td className="px-3 py-2">
                  <div className="font-medium text-slate-800">{r.userName}</div>
                  <div className="text-xs text-slate-400">{r.userEmail}</div>
                </td>
                <td className="px-3 py-2">{r.department ?? "-"}</td>
                <td className="px-3 py-2">{r.seniority ?? "-"}</td>
                <td className="px-3 py-2">{r.jobTier ?? "-"}</td>
                <td className="px-3 py-2">{r.payBand ?? "-"}</td>
                <td className="px-3 py-2">{r.jobGrade ?? "-"}</td>
                <td className="px-3 py-2">{r.payStep ?? "-"}</td>
                <td className="px-3 py-2 font-mono">{money(r.baseSalary)} {r.currency ?? ""}</td>
                <td className="px-3 py-2 text-xs text-slate-500">{r.effectiveDate ?? "-"}</td>
                <td className="px-3 py-2">
                  <div className="flex gap-1">
                    <button type="button" className="av-btn text-xs" onClick={() => void openEdit(r.userId)}>편집</button>
                    <button type="button" className="av-btn text-xs" onClick={() => void showHistory(r.userId)}>이력</button>
                  </div>
                </td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={10} className="px-3 py-8 text-center text-slate-400">배정된 등급이 없습니다.</td></tr>}
          </tbody>
        </table>
      </div>

      {total > 0 && (
        <div className="mt-3 flex items-center justify-between text-sm">
          <span className="text-slate-500">총 {total.toLocaleString("ko-KR")}건</span>
          <div className="flex items-center gap-2">
            <button type="button" className="av-btn text-xs" disabled={page <= 1} onClick={() => void load(page - 1, keyword, department)}>이전</button>
            <span className="text-xs">{page} / {totalPages}</span>
            <button type="button" className="av-btn text-xs" disabled={page >= totalPages} onClick={() => void load(page + 1, keyword, department)}>다음</button>
          </div>
        </div>
      )}

      {/* Excel 업로드 미리보기 */}
      {preview && (
        <section className="mt-5 rounded-xl border border-slate-200 bg-white p-4">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-800">
              업로드 미리보기 — 총 {preview.totalRows} · <span className="text-emerald-600">정상 {preview.okCount}</span> · <span className="text-rose-600">오류 {preview.errorCount}</span>
            </h3>
            <div className="flex gap-2">
              <button type="button" className="av-btn text-xs" onClick={() => setPreview(null)}>취소</button>
              <button type="button" className="av-btn bg-slate-900 text-xs text-white" disabled={preview.okCount === 0} onClick={() => void applyImport()}>
                정상 {preview.okCount}건 적용
              </button>
            </div>
          </div>
          <div className="max-h-[40vh] overflow-auto rounded-lg border border-slate-100">
            <table className="w-full min-w-[820px] text-sm">
              <thead className="sticky top-0 bg-white">
                <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                  <th className="px-3 py-2">#</th><th className="px-3 py-2">email</th><th className="px-3 py-2">부서</th>
                  <th className="px-3 py-2">기본급</th><th className="px-3 py-2">상태</th><th className="px-3 py-2">메시지</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((r) => (
                  <tr key={r.rowNumber} className="border-b border-slate-100">
                    <td className="px-3 py-2 text-xs">{r.rowNumber}</td>
                    <td className="px-3 py-2 text-xs">{r.email ?? "-"}</td>
                    <td className="px-3 py-2 text-xs">{r.department ?? "-"}</td>
                    <td className="px-3 py-2 font-mono text-xs">{money(r.baseSalary)}</td>
                    <td className="px-3 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${r.status === "OK" ? "bg-emerald-50 text-emerald-600" : "bg-rose-50 text-rose-600"}`}>{r.status}</span>
                    </td>
                    <td className="px-3 py-2 text-xs text-rose-500">{r.message ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-xs text-slate-400">헤더: email, department, seniority, job_tier, pay_band, job_grade, pay_step, base_salary, currency, effective_date (한글 별칭 지원)</p>
        </section>
      )}

      {/* 변경 이력 */}
      {history && (
        <section className="mt-5 rounded-xl border border-slate-200 bg-white p-4">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-800">변경 이력 — 사용자 {history.userId}</h3>
            <button type="button" className="av-btn text-xs" onClick={() => setHistory(null)}>닫기</button>
          </div>
          <div className="max-h-[40vh] overflow-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                  <th className="px-3 py-2">시각</th><th className="px-3 py-2">출처</th><th className="px-3 py-2">이전 → 이후</th><th className="px-3 py-2">처리자</th>
                </tr>
              </thead>
              <tbody>
                {history.items.length === 0 && <tr><td colSpan={4} className="px-3 py-4 text-center text-slate-400">이력이 없습니다.</td></tr>}
                {history.items.map((h) => (
                  <tr key={h.id} className="border-b border-slate-100 align-top">
                    <td className="px-3 py-2 text-xs text-slate-500">{fmt(h.createdAt)}</td>
                    <td className="px-3 py-2 text-xs">{h.source}</td>
                    <td className="px-3 py-2 font-mono text-[11px] text-slate-500">
                      <div className="text-rose-500">{h.oldValuesJson ?? "∅"}</div>
                      <div className="text-emerald-600">{h.newValuesJson ?? "∅"}</div>
                    </td>
                    <td className="px-3 py-2 text-xs">{h.changedBy ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* 편집 모달 */}
      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => setEditing(null)}>
          <div className="w-full max-w-lg rounded-xl bg-white p-5 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-base font-semibold text-slate-800">등급/급여 편집</h3>
            <p className="mt-0.5 text-xs text-slate-400">{editing.row.userName} ({editing.row.userEmail}) · {editing.row.userRole}</p>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <Field label="부서" value={editing.form.department ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, department: v } })} />
              <Field label="연차(JUNIOR/SENIOR…)" value={editing.form.seniority ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, seniority: v } })} />
              <Field label="티어" value={editing.form.jobTier ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, jobTier: v } })} />
              <Field label="밴드" value={editing.form.payBand ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, payBand: v } })} />
              <Field label="직급/등급" value={editing.form.jobGrade ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, jobGrade: v } })} />
              <Field label="호봉/스텝" value={editing.form.payStep ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, payStep: v } })} />
              <Field label="기본급(원)" value={editing.form.baseSalary == null ? "" : String(editing.form.baseSalary)}
                onChange={(v) => setEditing({ ...editing, form: { ...editing.form, baseSalary: v === "" ? null : (Number(v) as number) } })} />
              <Field label="통화" value={editing.form.currency ?? "KRW"} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, currency: v } })} />
              <Field label="적용일(YYYY-MM-DD)" value={editing.form.effectiveDate ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, effectiveDate: v } })} />
              <Field label="메모" value={editing.form.memo ?? ""} onChange={(v) => setEditing({ ...editing, form: { ...editing.form, memo: v } })} />
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button type="button" className="av-btn" onClick={() => setEditing(null)}>취소</button>
              <button type="button" className="av-btn bg-slate-900 text-white" onClick={() => void saveEdit()}>저장</button>
            </div>
          </div>
        </div>
      )}
    </AdminShell>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block text-xs">
      <span className="mb-1 block text-slate-500">{label}</span>
      <input className="av-input w-full" value={value} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}
