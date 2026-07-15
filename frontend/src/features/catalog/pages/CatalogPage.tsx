import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { BookOpen, GraduationCap, Layers, Search, X } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  getCertificateDetail,
  getNcsDetail,
  searchCertificates,
  searchNcs,
} from "../api/catalogApi";
import {
  CERT_TYPE_LABELS,
  type CertDetailResponse,
  type CertSearchItem,
  type CertType,
  type NcsDetail,
  type NcsSearchItem,
  type NcsUnit,
} from "../types/catalog";

type Tab = "ncs" | "cert";

const CERT_TYPE_FILTERS: Array<{ value: CertType | ""; label: string }> = [
  { value: "", label: "전체" },
  { value: "NATIONAL_TECH", label: "국가기술" },
  { value: "NATIONAL_PROF", label: "국가전문" },
  { value: "PRIVATE", label: "민간" },
];

const certBadgeClass: Record<CertType, string> = {
  NATIONAL_TECH: "bg-blue-100 text-blue-700",
  NATIONAL_PROF: "bg-emerald-100 text-emerald-700",
  PRIVATE: "bg-slate-100 text-slate-600",
};

/** NCS·자격증 통합 카탈로그 검색 — 비로그인도 참고할 수 있는 공개 레퍼런스. */
export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get("tab") === "cert" ? "cert" : "ncs";
  const setTab = (next: Tab) => setParams(next === "cert" ? { tab: "cert" } : {}, { replace: true });

  return (
    <div className="mx-auto max-w-6xl space-y-4 px-4 py-8">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold text-slate-900">
          <Layers className="size-5" />
          직무·자격 카탈로그
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          NCS 국가직무능력표준과 국가·민간 자격증을 한곳에서 검색합니다. 지원 건의 스펙·자격 전략을 세울 때 참고하세요.
        </p>
      </div>

      <div className="flex gap-2">
        <TabButton active={tab === "ncs"} onClick={() => setTab("ncs")} icon={<BookOpen className="size-4" />}>
          NCS 직무능력표준
        </TabButton>
        <TabButton active={tab === "cert"} onClick={() => setTab("cert")} icon={<GraduationCap className="size-4" />}>
          자격증
        </TabButton>
      </div>

      {tab === "ncs" ? <NcsSearchPanel /> : <CertSearchPanel />}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  icon,
  children,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-2 rounded-lg border px-4 py-2 text-sm font-medium transition-colors ${
        active
          ? "border-blue-300 bg-blue-50 text-blue-700"
          : "border-slate-200 bg-card text-slate-600 hover:border-slate-300"
      }`}
    >
      {icon}
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ NCS */

function NcsSearchPanel() {
  const [query, setQuery] = useState("");
  const [items, setItems] = useState<NcsSearchItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [detail, setDetail] = useState<NcsDetail | null>(null);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSearched(true);
    try {
      setItems(await searchNcs(query.trim()));
    } catch (e) {
      setError(e instanceof Error ? e.message : "검색에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [query]);

  return (
    <>
      <SearchBar
        value={query}
        onChange={setQuery}
        onSubmit={run}
        placeholder="세분류명·직무·기술 키워드 (예: 데이터, 3D프린터, 회계)"
      />
      {error && <ErrorBox message={error} />}
      <ResultLayout
        loading={loading}
        empty={searched && !loading && items.length === 0}
        emptyText="검색 결과가 없습니다. 다른 키워드로 시도해 보세요."
        placeholder={!searched}
        placeholderText="키워드를 입력하면 NCS 세분류를 찾아드립니다."
        list={
          <>
            {items.length > 0 && <p className="text-sm text-slate-500">총 {items.length}건</p>}
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => void openNcs(item.id, setDetail, setError)}
                className={`w-full rounded-lg border p-4 text-left transition-colors ${
                  detail?.id === item.id
                    ? "border-blue-300 bg-blue-50/50"
                    : "border-slate-200 bg-card hover:border-blue-300 hover:bg-blue-50/30"
                }`}
              >
                <div className="text-xs text-slate-500">
                  {item.majorName} › {item.middleName} › {item.minorName}
                </div>
                <div className="mt-0.5 font-semibold text-slate-900">{item.subName}</div>
                <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs text-slate-500">
                  <Badge className="bg-slate-100 text-slate-600">능력단위 {item.unitCount ?? 0}</Badge>
                  <Badge className="bg-slate-100 text-slate-600">요소 {item.elementCount ?? 0}</Badge>
                  {item.minLevel != null && (
                    <Badge className="bg-indigo-100 text-indigo-700">
                      수준 {item.minLevel}
                      {item.maxLevel != null && item.maxLevel !== item.minLevel ? `~${item.maxLevel}` : ""}
                    </Badge>
                  )}
                </div>
              </button>
            ))}
          </>
        }
        detail={detail && <NcsDetailPanel detail={detail} onClose={() => setDetail(null)} />}
      />
    </>
  );
}

async function openNcs(
  id: number,
  setDetail: (d: NcsDetail | null) => void,
  setError: (e: string | null) => void,
) {
  try {
    setDetail(await getNcsDetail(id));
  } catch (e) {
    setError(e instanceof Error ? e.message : "상세를 불러오지 못했습니다.");
  }
}

function NcsDetailPanel({ detail, onClose }: { detail: NcsDetail; onClose: () => void }) {
  const units = Array.isArray(detail.units) ? (detail.units as NcsUnit[]) : [];
  return (
    <DetailShell onClose={onClose}>
      <div className="text-xs text-slate-500">
        {detail.majorName} › {detail.middleName} › {detail.minorName}
      </div>
      <h2 className="mt-1 text-lg font-bold text-slate-900">{detail.subName}</h2>
      <div className="mt-1 text-xs text-slate-400">NCS 코드 {detail.ncsCode}</div>

      {units.length === 0 ? (
        <p className="mt-4 text-sm text-slate-500">능력단위 상세가 없습니다.</p>
      ) : (
        <div className="mt-4 space-y-3">
          {units.map((unit, i) => (
            <details key={i} className="rounded-lg border border-slate-200 bg-slate-50/50 p-3">
              <summary className="cursor-pointer text-sm font-semibold text-slate-800">
                {unit.unitName ?? `능력단위 ${i + 1}`}
                {unit.level != null && (
                  <span className="ml-2 rounded bg-indigo-100 px-1.5 py-0.5 text-xs text-indigo-700">
                    수준 {String(unit.level)}
                  </span>
                )}
              </summary>
              {Array.isArray(unit.elements) && unit.elements.length > 0 && (
                <ul className="mt-2 space-y-2">
                  {unit.elements.map((el, j) => (
                    <li key={j} className="rounded-md border border-slate-200 bg-card p-2.5 text-sm">
                      <div className="font-medium text-slate-800">{el.elementName ?? `요소 ${j + 1}`}</div>
                      <ChipRow label="지식" items={el.knowledge} tone="amber" />
                      <ChipRow label="기술" items={el.skills} tone="blue" />
                      <ChipRow label="태도" items={el.attitudes} tone="emerald" />
                    </li>
                  ))}
                </ul>
              )}
            </details>
          ))}
        </div>
      )}
    </DetailShell>
  );
}

function ChipRow({
  label,
  items,
  tone,
}: {
  label: string;
  items?: string[];
  tone: "amber" | "blue" | "emerald";
}) {
  if (!items || items.length === 0) return null;
  const toneClass = {
    amber: "bg-amber-50 text-amber-700",
    blue: "bg-blue-50 text-blue-700",
    emerald: "bg-emerald-50 text-emerald-700",
  }[tone];
  return (
    <div className="mt-1.5 flex flex-wrap items-start gap-1">
      <span className="mt-0.5 text-xs font-medium text-slate-400">{label}</span>
      {items.slice(0, 12).map((it, k) => (
        <span key={k} className={`rounded px-1.5 py-0.5 text-xs ${toneClass}`}>
          {it}
        </span>
      ))}
    </div>
  );
}

/* ----------------------------------------------------------------- CERT */

function CertSearchPanel() {
  const [query, setQuery] = useState("");
  const [type, setType] = useState<CertType | "">("");
  const [items, setItems] = useState<CertSearchItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [detail, setDetail] = useState<CertDetailResponse | null>(null);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSearched(true);
    try {
      setItems(await searchCertificates(query.trim(), type));
    } catch (e) {
      setError(e instanceof Error ? e.message : "검색에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [query, type]);

  // 유형 필터를 바꾸면 이미 검색한 경우 즉시 재조회
  useEffect(() => {
    if (searched) void run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type]);

  return (
    <>
      <SearchBar
        value={query}
        onChange={setQuery}
        onSubmit={run}
        placeholder="자격증명·직무 키워드 (예: 정보처리, 3D프린터, 지게차)"
      >
        <div className="flex flex-wrap gap-1.5">
          {CERT_TYPE_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              onClick={() => setType(f.value)}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                type === f.value
                  ? "border-blue-300 bg-blue-50 text-blue-700"
                  : "border-slate-200 bg-card text-slate-500 hover:border-slate-300"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </SearchBar>
      {error && <ErrorBox message={error} />}
      <ResultLayout
        loading={loading}
        empty={searched && !loading && items.length === 0}
        emptyText="검색 결과가 없습니다. 다른 키워드로 시도해 보세요."
        placeholder={!searched}
        placeholderText="키워드를 입력하면 국가·민간 자격증을 찾아드립니다."
        list={
          <>
            {items.length > 0 && <p className="text-sm text-slate-500">총 {items.length}건</p>}
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => void openCert(item.id, setDetail, setError)}
                className={`w-full rounded-lg border p-4 text-left transition-colors ${
                  detail?.certificate.id === item.id
                    ? "border-blue-300 bg-blue-50/50"
                    : "border-slate-200 bg-card hover:border-blue-300 hover:bg-blue-50/30"
                }`}
              >
                <div className="flex flex-wrap items-center gap-1.5">
                  <Badge className={certBadgeClass[item.certType]}>{CERT_TYPE_LABELS[item.certType]}</Badge>
                  {item.hasSchedule === 1 && <Badge className="bg-amber-100 text-amber-700">시험일정</Badge>}
                  {item.grade && <span className="text-xs text-slate-500">{item.grade}</span>}
                </div>
                <div className="mt-1 font-semibold text-slate-900">{item.name}</div>
                {item.authority && <div className="mt-0.5 text-xs text-slate-500">{item.authority}</div>}
                {item.descriptionSnippet && (
                  <p className="mt-1.5 line-clamp-2 text-sm text-slate-600">{item.descriptionSnippet}</p>
                )}
              </button>
            ))}
          </>
        }
        detail={detail && <CertDetailPanel data={detail} onClose={() => setDetail(null)} />}
      />
    </>
  );
}

async function openCert(
  id: number,
  setDetail: (d: CertDetailResponse | null) => void,
  setError: (e: string | null) => void,
) {
  try {
    setDetail(await getCertificateDetail(id));
  } catch (e) {
    setError(e instanceof Error ? e.message : "상세를 불러오지 못했습니다.");
  }
}

function CertDetailPanel({ data, onClose }: { data: CertDetailResponse; onClose: () => void }) {
  const c = data.certificate;
  return (
    <DetailShell onClose={onClose}>
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge className={certBadgeClass[c.certType]}>{CERT_TYPE_LABELS[c.certType]}</Badge>
        {c.grade && <Badge className="bg-slate-100 text-slate-600">{c.grade}</Badge>}
        {c.official && <Badge className="bg-emerald-100 text-emerald-700">{c.official}</Badge>}
      </div>
      <h2 className="mt-2 text-lg font-bold text-slate-900">{c.name}</h2>

      <dl className="mt-3 space-y-1.5 text-sm">
        {c.authority && <InfoRow label="주무부처/기관" value={c.authority} />}
        {c.issuerOrg && <InfoRow label="발급/시행" value={c.issuerOrg} />}
        {c.series && <InfoRow label="계열" value={c.series} />}
        {c.ncsSubName && <InfoRow label="관련 NCS 직무" value={c.ncsSubName} />}
        {c.status && <InfoRow label="상태" value={c.status} />}
      </dl>

      {c.description && (
        <div className="mt-3">
          <div className="text-xs font-medium text-slate-400">설명</div>
          <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-slate-700">{c.description}</p>
        </div>
      )}

      {data.schedules.length > 0 && (
        <div className="mt-4">
          <div className="text-xs font-medium text-slate-400">시험 일정</div>
          <div className="mt-1.5 space-y-2">
            {data.schedules.map((s, i) => (
              <div key={i} className="rounded-lg border border-slate-200 bg-slate-50/50 p-2.5 text-sm">
                <div className="font-medium text-slate-800">
                  {/* roundName 이 이미 "2026년 1차"처럼 연도를 포함하면 year 를 중복 표기하지 않는다. */}
                  {s.roundName
                    ? s.year != null && !s.roundName.includes(String(s.year))
                      ? `${s.year}년 ${s.roundName}`
                      : s.roundName
                    : `${s.year ?? ""}년`}
                </div>
                <div className="mt-1 grid grid-cols-1 gap-x-4 gap-y-0.5 text-xs text-slate-600 sm:grid-cols-2">
                  <ExamLine label="필기 접수" value={rangeText(s.docRegStart, s.docRegEnd)} />
                  <ExamLine label="필기 시험" value={s.docExam} />
                  <ExamLine label="필기 합격" value={s.docPass} />
                  <ExamLine label="실기 시험" value={rangeText(s.pracExamStart, s.pracExamEnd)} />
                  <ExamLine label="실기 합격" value={s.pracPass} />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </DetailShell>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-2">
      <dt className="w-24 shrink-0 text-slate-400">{label}</dt>
      <dd className="text-slate-700">{value}</dd>
    </div>
  );
}

function ExamLine({ label, value }: { label: string; value: string | null | undefined }) {
  if (!value) return null;
  return (
    <div>
      <span className="text-slate-400">{label} </span>
      <span className="text-slate-700">{value}</span>
    </div>
  );
}

function rangeText(a: string | null | undefined, b: string | null | undefined): string | null {
  if (!a && !b) return null;
  if (a && b && a !== b) return `${a} ~ ${b}`;
  return a || b || null;
}

/* -------------------------------------------------------------- shared */

function SearchBar({
  value,
  onChange,
  onSubmit,
  placeholder,
  children,
}: {
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  placeholder: string;
  children?: React.ReactNode;
}) {
  return (
    <Card className="border-slate-200">
      <CardContent className="space-y-3 pt-6">
        <div className="flex gap-2">
          <Input
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            onKeyDown={(e) => e.key === "Enter" && onSubmit()}
          />
          <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={onSubmit}>
            <Search className="size-4" />
            검색
          </Button>
        </div>
        {children}
      </CardContent>
    </Card>
  );
}

function ResultLayout({
  loading,
  empty,
  emptyText,
  placeholder,
  placeholderText,
  list,
  detail,
}: {
  loading: boolean;
  empty: boolean;
  emptyText: string;
  placeholder: boolean;
  placeholderText: string;
  list: React.ReactNode;
  detail: React.ReactNode;
}) {
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div className="space-y-3">
        {loading ? (
          <div className="py-16 text-center text-sm text-slate-500">불러오는 중...</div>
        ) : placeholder ? (
          <div className="rounded-lg border border-dashed border-slate-200 py-16 text-center text-sm text-slate-400">
            {placeholderText}
          </div>
        ) : empty ? (
          <div className="rounded-lg border border-slate-200 bg-card py-16 text-center text-sm text-slate-500">
            {emptyText}
          </div>
        ) : (
          list
        )}
      </div>
      <div className="lg:sticky lg:top-4 lg:self-start">{detail}</div>
    </div>
  );
}

function DetailShell({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <Card className="border-slate-200">
      <CardContent className="relative max-h-[70vh] overflow-y-auto pt-6">
        <button
          type="button"
          onClick={onClose}
          className="absolute right-3 top-3 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          aria-label="닫기"
        >
          <X className="size-4" />
        </button>
        {children}
      </CardContent>
    </Card>
  );
}

function ErrorBox({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{message}</div>
  );
}
