import { useRef, useState } from "react";
import { Link } from "react-router";
import { Award, Building2, Loader2, Search, ShieldCheck } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";
import { api } from "@/app/lib/api";

interface CertificateSearchResult {
  query: string;
  resolvedAlias: string | null;
  national: { name: string; kind: string; scheduleQueryable: boolean }[];
  nationalUnavailable: boolean;
  privateMatches: { name: string; currentStatus: string; institution: string; registrationNo: string }[];
  privateLookupFailed: boolean;
}

/**
 * 자격증 통합 검색 — 국가자격(오프라인 스냅샷 613종)과 민간자격 등록정보(공공데이터 라이브)를 한 번에 조회한다.
 * 검색은 '조회'이며 추천이 아니다(추천은 적합도 분석의 cert-need-gate 판정이 담당).
 */
export function CertificateSearchPage() {
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<CertificateSearchResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 최신 질의만 반영 — Enter 연타 시 늦게 도착한 이전 응답이 최신 결과를 덮어쓰지 않도록.
  const requestSeq = useRef(0);

  const search = async () => {
    const q = query.trim();
    if (q.length < 2) {
      setError("두 글자 이상 입력하세요.");
      return;
    }
    const seq = ++requestSeq.current;
    setLoading(true);
    setError(null);
    try {
      const data = await api<CertificateSearchResult>(`/certificates/search?q=${encodeURIComponent(q)}`);
      if (seq === requestSeq.current) setResult(data);
    } catch (requestError) {
      if (seq === requestSeq.current) setError(requestError instanceof Error ? requestError.message : "검색에 실패했습니다.");
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[900px] space-y-5 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <Award className="size-6 text-blue-600" />
            자격증 검색
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            국가자격 종목(공식 스냅샷)과 민간자격 등록정보(공공데이터)를 함께 검색합니다. SQLD 같은 통용 약어도 공식 등록명으로 찾아줍니다.
          </p>
        </div>

        <div className="flex gap-2">
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => { if (event.key === "Enter") void search(); }}
            placeholder="자격증명 입력 (예: 정보처리기사, SQLD, 공인노무사)"
            className="bg-card"
          />
          <Button onClick={() => void search()} disabled={loading} className="gap-1.5">
            {loading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
            검색
          </Button>
        </div>

        {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}

        {result && (
          <>
            {result.resolvedAlias && (
              <div className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-xs text-blue-800">
                '{result.query}'는 통용 약어입니다 — 공식 등록명 <b>'{result.resolvedAlias}'</b>(으)로도 함께 검색했습니다.
              </div>
            )}

            <section className="rounded-lg border border-slate-200 bg-card">
              <div className="flex items-center gap-1.5 border-b border-slate-100 px-4 py-3 text-sm font-bold text-slate-800">
                <ShieldCheck className="size-4 text-blue-600" />
                국가자격 <span className="font-semibold text-slate-400">({result.national.length})</span>
              </div>
              {result.nationalUnavailable ? (
                <p className="p-4 text-xs text-amber-700">
                  국가자격 목록을 현재 조회할 수 없습니다 — 결과 없음이 부재를 뜻하지 않습니다.
                </p>
              ) : result.national.length === 0 ? (
                <p className="p-4 text-xs text-slate-500">
                  한국산업인력공단 시행 국가자격 목록에 없습니다. (대한상공회의소 등 타 기관 시행 국가자격은 이 목록에 포함되지 않습니다.)
                </p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {result.national.map((item) => (
                    <li key={item.name} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5">
                      <span className="text-sm font-semibold text-slate-800">{item.name}</span>
                      <span className="flex items-center gap-1.5">
                        <Badge className={item.kind === "NATIONAL_TECHNICAL" ? "bg-blue-100 text-blue-700" : "bg-indigo-100 text-indigo-700"}>
                          {item.kind === "NATIONAL_TECHNICAL" ? "국가기술자격" : "국가전문자격"}
                        </Badge>
                        {item.scheduleQueryable && <Badge className="bg-green-100 text-green-700">일정 조회 지원</Badge>}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="rounded-lg border border-slate-200 bg-card">
              <div className="flex items-center gap-1.5 border-b border-slate-100 px-4 py-3 text-sm font-bold text-slate-800">
                <Building2 className="size-4 text-slate-500" />
                민간자격 등록정보 <span className="font-semibold text-slate-400">({result.privateMatches.length})</span>
              </div>
              {result.privateLookupFailed ? (
                <p className="p-4 text-xs text-amber-700">
                  민간자격 등록정보 조회가 현재 불가능합니다 — 결과 없음이 미등록을 뜻하지 않습니다.
                </p>
              ) : result.privateMatches.length === 0 ? (
                <p className="p-4 text-xs text-slate-500">민간자격 등록정보에서 일치 항목을 찾지 못했습니다.</p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {result.privateMatches.map((item) => (
                    <li key={`${item.name}-${item.registrationNo}`} className="px-4 py-2.5">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-sm font-semibold text-slate-800">{item.name}</span>
                        <Badge className={item.currentStatus?.includes("폐지") || item.currentStatus?.includes("취소")
                          ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}>
                          {item.currentStatus || "상태 미상"}
                        </Badge>
                      </div>
                      <p className="mt-0.5 text-[11px] text-slate-500">
                        {item.institution} · 등록번호 {item.registrationNo}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
              <p className="border-t border-slate-100 px-4 py-2 text-[11px] text-slate-400">
                출처: 한국직업능력연구원 민간자격등록정보. 민간자격 시험일정은 주관기관 공식 페이지에서 확인하세요.
              </p>
            </section>

            <div className="text-xs text-slate-500">
              추천이 필요하면 <Link to="/career-roadmap" className="font-semibold text-indigo-600 underline-offset-2 hover:underline">장기 커리어 로드맵</Link>이나
              지원 건의 적합도 분석(자격증 전략)을 확인하세요 — 검색은 조회일 뿐 추천 판정이 아닙니다.
            </div>
          </>
        )}
      </div>
    </div>
  );
}
