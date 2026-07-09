import {
  isWebFact,
  parseVerifiedFactViews,
  webFactLinkUrl,
  type VerifiedFactView,
} from "../types/analysis";

/**
 * 검증된 사실(verifiedFacts) 표시 전용 리스트(읽기 전용 · D-4d).
 *
 * <p>WEB 근거 fact(sourceKind="WEB")에는 "웹 근거" 배지와 sourceRef 링크(http/https 일 때만 클릭 가능,
 * 아니면 텍스트)를 붙인다. JOB_POSTING 등 그 외 fact 는 배지·링크 없이 "사실 (출처: …)" 형태로만 표시해
 * 기존 표시와 동일하다. 컨테이너/제목은 호출부가 감싸므로 여기서는 목록만 그린다(사용자/관리자 공용).
 */
export function VerifiedFactsList({ value }: { value: string | null }) {
  const facts = parseVerifiedFactViews(value);

  if (facts.length === 0) {
    return <div className="mt-2 text-sm text-slate-400">내용 없음</div>;
  }

  return (
    <ul className="mt-2 space-y-1.5 text-sm leading-6 text-slate-600">
      {facts.map((fact, index) => (
        <li key={index} className="flex gap-2">
          <span className="mt-2 size-1.5 shrink-0 rounded-full bg-current" />
          <span className="min-w-0 break-words">
            <VerifiedFactItem fact={fact} />
          </span>
        </li>
      ))}
    </ul>
  );
}

function VerifiedFactItem({ fact }: { fact: VerifiedFactView }) {
  const web = isWebFact(fact);
  const linkUrl = web ? webFactLinkUrl(fact) : null;

  return (
    <>
      <span>
        {fact.fact}
        {fact.source ? ` (출처: ${fact.source})` : ""}
      </span>
      {web && (
        <span className="ml-1.5 inline-flex items-center rounded-full border border-blue-200 bg-blue-50 px-1.5 py-0.5 align-middle text-[11px] font-semibold text-blue-700">
          웹 근거
        </span>
      )}
      {web && fact.sourceRef && (
        linkUrl ? (
          <a
            href={linkUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="ml-1.5 inline break-all text-blue-600 underline hover:text-blue-800"
          >
            {fact.sourceRef}
          </a>
        ) : (
          <span className="ml-1.5 break-all text-slate-500">{fact.sourceRef}</span>
        )
      )}
    </>
  );
}
