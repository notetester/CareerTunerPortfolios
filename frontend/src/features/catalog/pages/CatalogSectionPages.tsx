import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
import { ArrowRight, Award, BookOpen, GraduationCap, Layers3, Loader2, RefreshCw, Search, X } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { getCertificateDetail, getNcsDetail, searchCertificates, searchNcs } from "../api/catalogApi";
import {
  CERT_TYPE_LABELS,
  type CertDetailResponse,
  type CertSearchItem,
  type CertType,
  type NcsDetail,
  type NcsSearchItem,
  type NcsUnit,
} from "../types/catalog";

const certTypeOptions: Array<{ value: CertType | ""; label: string }> = [
  { value: "", label: "전체" },
  { value: "NATIONAL_TECH", label: "국가기술" },
  { value: "NATIONAL_PROF", label: "국가전문" },
  { value: "PRIVATE", label: "민간" },
];

/** 직무·자격 대분류 전용 허브. 두 검색 기능의 상태와 독립 진입점을 제공한다. */
export function CatalogHubPage() {
  const [ncs, setNcs] = useState<NcsSearchItem[]>([]);
  const [certificates, setCertificates] = useState<CertSearchItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    const [ncsResult, certResult] = await Promise.allSettled([searchNcs("", 6), searchCertificates("", "", 6)]);
    if (ncsResult.status === "fulfilled") setNcs(ncsResult.value);
    if (certResult.status === "fulfilled") setCertificates(certResult.value);
    if (ncsResult.status === "rejected" && certResult.status === "rejected") {
      setError("카탈로그 요약을 불러오지 못했습니다. 각 검색 화면에서 다시 시도해 주세요.");
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <CatalogCanvas>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <CatalogHeading icon={<Layers3 className="size-6" />} title="직무·자격 카탈로그" description="NCS 직무능력표준과 국가·민간 자격 정보를 목적에 맞는 독립 검색 화면에서 확인합니다." />
        <Button variant="outline" onClick={() => void load()} disabled={loading}><RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />새로고침</Button>
      </div>

      {error && <ErrorNotice>{error}</ErrorNotice>}

      <div className="grid gap-5 lg:grid-cols-2">
        <CatalogHubCard
          icon={<BookOpen className="size-6" />}
          title="NCS 직무능력표준"
          description="직무 분류와 능력단위, 수행에 필요한 지식·기술·태도를 탐색합니다."
          href="/catalog/ncs"
          count={ncs.length}
          loading={loading}
          samples={ncs.map((item) => item.subName)}
        />
        <CatalogHubCard
          icon={<GraduationCap className="size-6" />}
          title="자격증 검색"
          description="자격 유형, 발급 기관, 연계 직무와 국가자격 시험 일정을 조회합니다."
          href="/catalog/certificates"
          count={certificates.length}
          loading={loading}
          samples={certificates.map((item) => item.name)}
        />
      </div>

      <Card className="border-border bg-card">
        <CardContent className="grid gap-4 pt-6 md:grid-cols-3">
          <SummaryItem label="활용 1" title="직무 구조 파악" text="NCS 능력단위로 목표 직무가 요구하는 실제 수행 능력을 확인합니다." />
          <SummaryItem label="활용 2" title="보유 스펙 검토" text="자격증의 유형과 시행 기관을 확인하고 내 프로필과 대조합니다." />
          <SummaryItem label="활용 3" title="지원 준비 연결" text="부족 역량과 연결되는 직무·자격 정보를 지원 전략에 활용합니다." />
        </CardContent>
      </Card>
    </CatalogCanvas>
  );
}

export function NcsCatalogPage() {
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") ?? "");
  const [items, setItems] = useState<NcsSearchItem[]>([]);
  const [detail, setDetail] = useState<NcsDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const initialized = useRef(false);

  const run = useCallback(async (nextQuery = query) => {
    setLoading(true);
    setSearched(true);
    setError(null);
    setDetail(null);
    try {
      setItems(await searchNcs(nextQuery.trim()));
      setParams(nextQuery.trim() ? { q: nextQuery.trim() } : {}, { replace: true });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "NCS 검색에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [query, setParams]);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;
    const initialQuery = params.get("q");
    if (initialQuery) void run(initialQuery);
  }, [params, run]);

  const openDetail = async (id: number) => {
    setDetailLoading(true);
    setError(null);
    try {
      setDetail(await getNcsDetail(id));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "NCS 상세를 불러오지 못했습니다.");
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <CatalogCanvas>
      <CatalogHeading icon={<BookOpen className="size-6" />} title="NCS 직무능력표준" description="세분류명, 능력단위와 기술 키워드로 직무를 검색하고 수행 기준을 확인합니다." />
      <CatalogBreadcrumb current="NCS 직무능력표준" />
      <SearchBox query={query} setQuery={setQuery} run={() => void run()} loading={loading} placeholder="직무·기술 키워드 (예: 데이터, 3D프린터, 회계)" />
      {error && <ErrorNotice>{error}</ErrorNotice>}
      <div className="grid min-h-[480px] gap-5 lg:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]">
        <ResultColumn loading={loading} searched={searched} empty={items.length === 0} initialText="검색어를 입력하면 NCS 세분류와 능력단위를 찾습니다." emptyText="검색 결과가 없습니다. 다른 직무나 기술 키워드로 검색해 보세요.">
          {items.length > 0 && <p className="text-sm text-muted-foreground">검색 결과 {items.length}건</p>}
          {items.map((item) => (
            <button key={item.id} type="button" onClick={() => void openDetail(item.id)} className={`w-full rounded-xl border p-4 text-left transition ${detail?.id === item.id ? "border-primary bg-primary/5" : "border-border bg-card hover:border-primary/50"}`}>
              <div className="text-xs text-muted-foreground">{item.majorName} › {item.middleName} › {item.minorName}</div>
              <div className="mt-1 font-bold text-foreground">{item.subName}</div>
              <div className="mt-3 flex flex-wrap gap-2"><Badge variant="secondary">능력단위 {item.unitCount ?? 0}</Badge><Badge variant="secondary">요소 {item.elementCount ?? 0}</Badge>{item.minLevel != null && <Badge>수준 {item.minLevel}{item.maxLevel != null && item.maxLevel !== item.minLevel ? `~${item.maxLevel}` : ""}</Badge>}</div>
            </button>
          ))}
        </ResultColumn>
        <DetailColumn loading={detailLoading} empty={!detail} emptyText="왼쪽 검색 결과를 선택하면 능력단위와 지식·기술·태도를 확인할 수 있습니다.">
          {detail && <NcsDetailView detail={detail} onClose={() => setDetail(null)} />}
        </DetailColumn>
      </div>
    </CatalogCanvas>
  );
}

export function CertificateCatalogPage() {
  const [params, setParams] = useSearchParams();
  const initialType = normalizeCertType(params.get("type"));
  const [query, setQuery] = useState(params.get("q") ?? "");
  const [type, setType] = useState<CertType | "">(initialType);
  const [items, setItems] = useState<CertSearchItem[]>([]);
  const [detail, setDetail] = useState<CertDetailResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const initialized = useRef(false);

  const run = useCallback(async (nextQuery = query, nextType = type) => {
    setLoading(true);
    setSearched(true);
    setError(null);
    setDetail(null);
    try {
      setItems(await searchCertificates(nextQuery.trim(), nextType));
      const nextParams: Record<string, string> = {};
      if (nextQuery.trim()) nextParams.q = nextQuery.trim();
      if (nextType) nextParams.type = nextType;
      setParams(nextParams, { replace: true });
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "자격증 검색에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }, [query, setParams, type]);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;
    const initialQuery = params.get("q");
    if (initialQuery || initialType) void run(initialQuery ?? "", initialType);
  }, [initialType, params, run]);

  const chooseType = (nextType: CertType | "") => {
    setType(nextType);
    if (searched) void run(query, nextType);
  };

  const openDetail = async (id: number) => {
    setDetailLoading(true);
    setError(null);
    try {
      setDetail(await getCertificateDetail(id));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "자격증 상세를 불러오지 못했습니다.");
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <CatalogCanvas>
      <CatalogHeading icon={<GraduationCap className="size-6" />} title="자격증 검색" description="국가·민간 자격을 유형별로 검색하고 발급 정보와 시험 일정을 확인합니다." />
      <CatalogBreadcrumb current="자격증 검색" />
      <SearchBox query={query} setQuery={setQuery} run={() => void run()} loading={loading} placeholder="자격증명·직무 키워드 (예: 정보처리, 지게차)">
        <div className="flex flex-wrap gap-2">{certTypeOptions.map((option) => <button key={option.value} type="button" onClick={() => chooseType(option.value)} className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${type === option.value ? "border-primary bg-primary/10 text-primary" : "border-border text-muted-foreground hover:border-primary/50"}`}>{option.label}</button>)}</div>
      </SearchBox>
      {error && <ErrorNotice>{error}</ErrorNotice>}
      <div className="grid min-h-[480px] gap-5 lg:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]">
        <ResultColumn loading={loading} searched={searched} empty={items.length === 0} initialText="자격증명이나 관련 직무를 입력해 자격 정보를 검색하세요." emptyText="검색 결과가 없습니다. 다른 키워드나 자격 유형을 선택해 보세요.">
          {items.length > 0 && <p className="text-sm text-muted-foreground">검색 결과 {items.length}건</p>}
          {items.map((item) => (
            <button key={item.id} type="button" onClick={() => void openDetail(item.id)} className={`w-full rounded-xl border p-4 text-left transition ${detail?.certificate.id === item.id ? "border-primary bg-primary/5" : "border-border bg-card hover:border-primary/50"}`}>
              <div className="flex flex-wrap gap-2"><Badge>{CERT_TYPE_LABELS[item.certType]}</Badge>{item.hasSchedule === 1 && <Badge variant="secondary">시험 일정</Badge>}{item.grade && <Badge variant="secondary">{item.grade}</Badge>}</div>
              <div className="mt-2 font-bold text-foreground">{item.name}</div>
              {item.authority && <div className="mt-1 text-xs text-muted-foreground">{item.authority}</div>}
              {item.descriptionSnippet && <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{item.descriptionSnippet}</p>}
            </button>
          ))}
        </ResultColumn>
        <DetailColumn loading={detailLoading} empty={!detail} emptyText="왼쪽 검색 결과를 선택하면 시행 기관, 관련 직무와 시험 일정을 확인할 수 있습니다.">
          {detail && <CertificateDetailView data={detail} onClose={() => setDetail(null)} />}
        </DetailColumn>
      </div>
    </CatalogCanvas>
  );
}

function NcsDetailView({ detail, onClose }: { detail: NcsDetail; onClose: () => void }) {
  const units = Array.isArray(detail.units) ? detail.units as NcsUnit[] : [];
  return (
    <Card className="border-primary/30 bg-card"><CardHeader><div className="flex items-start justify-between gap-3"><div><div className="text-xs text-muted-foreground">{detail.majorName} › {detail.middleName} › {detail.minorName}</div><CardTitle className="mt-2 text-xl">{detail.subName}</CardTitle><div className="mt-1 text-xs text-muted-foreground">NCS 코드 {detail.ncsCode}</div></div><CloseButton onClick={onClose} /></div></CardHeader><CardContent className="max-h-[64vh] space-y-3 overflow-y-auto">
      {units.length ? units.map((unit, index) => <details key={`${unit.unitNo ?? index}`} className="rounded-lg border border-border bg-muted/30 p-3" open={index === 0}><summary className="cursor-pointer font-semibold text-foreground">{unit.unitName ?? `능력단위 ${index + 1}`}{unit.level != null && <Badge className="ml-2">수준 {String(unit.level)}</Badge>}</summary>{Array.isArray(unit.elements) && <div className="mt-3 space-y-3">{unit.elements.map((element, elementIndex) => <div key={`${element.elementNo ?? elementIndex}`} className="rounded-lg border border-border bg-card p-3"><div className="font-medium text-foreground">{element.elementName ?? `능력단위요소 ${elementIndex + 1}`}</div><KeywordRows label="지식" values={element.knowledge} /><KeywordRows label="기술" values={element.skills} /><KeywordRows label="태도" values={element.attitudes} /></div>)}</div>}</details>) : <p className="text-sm text-muted-foreground">등록된 능력단위 상세가 없습니다.</p>}
    </CardContent></Card>
  );
}

function CertificateDetailView({ data, onClose }: { data: CertDetailResponse; onClose: () => void }) {
  const certificate = data.certificate;
  return (
    <Card className="border-primary/30 bg-card"><CardHeader><div className="flex items-start justify-between gap-3"><div><Badge>{CERT_TYPE_LABELS[certificate.certType]}</Badge><CardTitle className="mt-3 text-xl">{certificate.name}</CardTitle></div><CloseButton onClick={onClose} /></div></CardHeader><CardContent className="max-h-[64vh] space-y-5 overflow-y-auto">
      <dl className="grid gap-3 text-sm sm:grid-cols-2"><Info label="등급" value={certificate.grade} /><Info label="주무 기관" value={certificate.authority} /><Info label="발급·시행" value={certificate.issuerOrg} /><Info label="관련 NCS" value={certificate.ncsSubName} /><Info label="계열" value={certificate.series} /><Info label="상태" value={certificate.status} /></dl>
      {certificate.description && <section><h3 className="text-sm font-bold text-foreground">자격 설명</h3><p className="mt-2 whitespace-pre-line text-sm leading-6 text-muted-foreground">{certificate.description}</p></section>}
      <section><h3 className="text-sm font-bold text-foreground">시험 일정</h3>{data.schedules.length ? <div className="mt-2 space-y-2">{data.schedules.map((schedule, index) => <div key={index} className="rounded-lg border border-border bg-muted/30 p-3 text-sm"><div className="font-semibold text-foreground">{schedule.roundName || `${schedule.year ?? ""}년`}</div><div className="mt-2 grid gap-1 text-xs text-muted-foreground sm:grid-cols-2"><Schedule label="필기 접수" value={dateRange(schedule.docRegStart, schedule.docRegEnd)} /><Schedule label="필기 시험" value={schedule.docExam} /><Schedule label="필기 합격" value={schedule.docPass} /><Schedule label="실기 시험" value={dateRange(schedule.pracExamStart, schedule.pracExamEnd)} /><Schedule label="실기 합격" value={schedule.pracPass} /></div></div>)}</div> : <p className="mt-2 text-sm text-muted-foreground">등록된 시험 일정이 없습니다.</p>}</section>
    </CardContent></Card>
  );
}

function CatalogCanvas({ children }: { children: ReactNode }) { return <main className="min-h-[calc(100vh-72px)] bg-background"><div className="mx-auto w-full max-w-[1440px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">{children}</div></main>; }
function CatalogHeading({ icon, title, description }: { icon: ReactNode; title: string; description: string }) { return <div><h1 className="flex items-center gap-2 text-2xl font-black text-foreground"><span className="text-primary">{icon}</span>{title}</h1><p className="mt-1 text-sm text-muted-foreground">{description}</p></div>; }
function CatalogBreadcrumb({ current }: { current: string }) { return <div className="flex items-center gap-2 text-sm text-muted-foreground"><Link to="/catalog" className="font-medium text-primary hover:underline">직무·자격 카탈로그</Link><span>›</span><span>{current}</span></div>; }
function CatalogHubCard({ icon, title, description, href, count, loading, samples }: { icon: ReactNode; title: string; description: string; href: string; count: number; loading: boolean; samples: string[] }) { return <Card className="border-border"><CardHeader><div className="flex items-start justify-between gap-3"><span className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">{icon}</span><Badge variant="secondary">{loading ? "조회 중" : `미리보기 ${count}건`}</Badge></div><CardTitle className="mt-3 text-xl">{title}</CardTitle><p className="text-sm text-muted-foreground">{description}</p></CardHeader><CardContent><div className="min-h-28 space-y-2 rounded-lg border border-border bg-muted/20 p-3">{loading ? <div className="flex h-20 items-center justify-center"><Loader2 className="size-5 animate-spin text-muted-foreground" /></div> : samples.length ? samples.slice(0, 4).map((sample) => <div key={sample} className="truncate text-sm text-foreground">• {sample}</div>) : <div className="flex h-20 items-center justify-center text-sm text-muted-foreground">검색 화면에서 최신 데이터를 조회하세요.</div>}</div><Button asChild className="mt-4 w-full"><Link to={href}>독립 검색 화면 열기<ArrowRight className="size-4" /></Link></Button></CardContent></Card>; }
function SummaryItem({ label, title, text }: { label: string; title: string; text: string }) { return <div><Badge variant="secondary">{label}</Badge><h3 className="mt-3 font-bold text-foreground">{title}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{text}</p></div>; }
function SearchBox({ query, setQuery, run, loading, placeholder, children }: { query: string; setQuery: (value: string) => void; run: () => void; loading: boolean; placeholder: string; children?: ReactNode }) { return <Card className="border-border"><CardContent className="space-y-4 pt-6"><div className="flex flex-col gap-2 sm:flex-row"><Input value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") run(); }} placeholder={placeholder} className="h-11" /><Button onClick={run} disabled={loading} className="h-11 sm:px-7">{loading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}검색</Button></div>{children}</CardContent></Card>; }
function ResultColumn({ loading, searched, empty, initialText, emptyText, children }: { loading: boolean; searched: boolean; empty: boolean; initialText: string; emptyText: string; children: ReactNode }) { if (loading) return <EmptyPanel><Loader2 className="size-6 animate-spin" />검색 결과를 불러오는 중입니다.</EmptyPanel>; if (!searched) return <EmptyPanel><Search className="size-6" />{initialText}</EmptyPanel>; if (empty) return <EmptyPanel><Search className="size-6" />{emptyText}</EmptyPanel>; return <div className="space-y-3">{children}</div>; }
function DetailColumn({ loading, empty, emptyText, children }: { loading: boolean; empty: boolean; emptyText: string; children: ReactNode }) { if (loading) return <EmptyPanel><Loader2 className="size-6 animate-spin" />상세 정보를 불러오는 중입니다.</EmptyPanel>; if (empty) return <EmptyPanel><Award className="size-6" />{emptyText}</EmptyPanel>; return <div className="lg:sticky lg:top-24">{children}</div>; }
function EmptyPanel({ children }: { children: ReactNode }) { return <div className="flex min-h-64 flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border bg-card px-6 text-center text-sm text-muted-foreground">{children}</div>; }
function ErrorNotice({ children }: { children: ReactNode }) { return <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{children}</div>; }
function CloseButton({ onClick }: { onClick: () => void }) { return <Button type="button" variant="ghost" size="icon" onClick={onClick} aria-label="상세 닫기"><X className="size-4" /></Button>; }
function KeywordRows({ label, values }: { label: string; values?: string[] }) { if (!values?.length) return null; return <div className="mt-2 flex flex-wrap items-start gap-1.5"><span className="text-xs font-semibold text-muted-foreground">{label}</span>{values.slice(0, 16).map((value) => <Badge key={value} variant="secondary">{value}</Badge>)}</div>; }
function Info({ label, value }: { label: string; value: string | null }) { if (!value) return null; return <div><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 font-medium text-foreground">{value}</dd></div>; }
function Schedule({ label, value }: { label: string; value: string | null }) { if (!value) return null; return <div><span className="font-medium text-foreground">{label}</span> {value}</div>; }
function dateRange(start: string | null, end: string | null): string | null { if (!start && !end) return null; return start && end && start !== end ? `${start} ~ ${end}` : start || end; }
function normalizeCertType(value: string | null): CertType | "" { return value === "NATIONAL_TECH" || value === "NATIONAL_PROF" || value === "PRIVATE" ? value : ""; }
