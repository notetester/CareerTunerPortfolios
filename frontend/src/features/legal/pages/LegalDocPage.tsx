import { useState, useEffect, useRef, useCallback } from "react";
import { useLocation, useNavigate } from "react-router";
import { CalendarCheck, History } from "lucide-react";
import {
  getLegalDoc,
  isLegalDocType,
  LEGAL_DOC_TYPES,
  type LegalDocType,
  type LegalClauseDto,
  type LegalDocResponse,
} from "../api/legalApi";
import "../styles/legal.css";

const DOC_LABELS: Record<LegalDocType, string> = {
  terms: "이용약관",
  privacy: "개인정보처리방침",
  marketing: "마케팅 수신 동의",
};

/** `/legal/<docType>` 라우트가 실제로 존재하는 docType만 — app/routes.ts 와 동기화.
 *  routes.ts(팀장 소유)에는 legal/terms·legal/privacy 만 있고 legal/marketing 라우트가 없다.
 *  marketing 카드를 누르면 매칭되는 라우트가 없어 빈 화면으로 떨어지므로, 라우트가 생기기 전까지
 *  '관련 문서' 카드에서 제외해 죽은 링크를 만들지 않는다. (routes.ts 에 marketing 추가 시 여기 포함) */
const ROUTED_DOC_TYPES: ReadonlySet<LegalDocType> = new Set<LegalDocType>(["terms", "privacy"]);

/** routes.ts 의 `legal/<seg>` 마지막 세그먼트 → 지원 docType. 미지원이면 null. */
function pathToDocType(pathname: string): LegalDocType | null {
  const seg = pathname.split("/").filter(Boolean).pop() ?? "";
  return isLegalDocType(seg) ? seg : null;
}

function clauseDomId(seq: number): string {
  return `clause-${seq}`;
}

/** 조항 본문을 줄바꿈으로 쪼개 "1. 2. 3."로 시작하는 줄은 <ol>, 아니면 <p> 로 평탄화 렌더한다. */
function ClauseBody({ body }: { body: string }) {
  const lines = body
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l.length > 0);

  const blocks: ({ kind: "p"; text: string } | { kind: "ol"; items: string[] })[] = [];
  for (const line of lines) {
    const isNumbered = /^\d+\.\s*/.test(line);
    if (isNumbered) {
      const text = line.replace(/^\d+\.\s*/, "");
      const last = blocks[blocks.length - 1];
      if (last && last.kind === "ol") last.items.push(text);
      else blocks.push({ kind: "ol", items: [text] });
    } else {
      blocks.push({ kind: "p", text: line });
    }
  }

  return (
    <>
      {blocks.map((b, i) =>
        b.kind === "ol" ? (
          <ol key={i} className="ct-legal__ol">
            {b.items.map((item, j) => (
              <li key={j}>{item}</li>
            ))}
          </ol>
        ) : (
          <p key={i}>{b.text}</p>
        ),
      )}
    </>
  );
}

function ClauseSection({ clause }: { clause: LegalClauseDto }) {
  return (
    <section className="ct-legal__sec" id={clauseDomId(clause.seq)}>
      <h2>
        <span className="num">제{clause.seq}조</span>
        {clause.title}
      </h2>
      <ClauseBody body={clause.body} />
    </section>
  );
}

function formatDate(value: string | null): string {
  if (!value) return "-";
  // ISO/DATETIME 문자열의 날짜 부분만 표시.
  return value.slice(0, 10);
}

export default function LegalDocPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const docType = pathToDocType(location.pathname);

  const [doc, setDoc] = useState<LegalDocResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState<string>("");
  const docRef = useRef<HTMLElement>(null);

  const goTo = useCallback((id: string) => {
    const el = document.getElementById(id);
    if (el)
      window.scrollTo({
        top: window.scrollY + el.getBoundingClientRect().top - 84,
        behavior: "smooth",
      });
  }, []);

  const switchDoc = useCallback(
    (key: LegalDocType) => {
      navigate(`/legal/${key}`);
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
    [navigate],
  );

  useEffect(() => {
    if (!docType) {
      setLoading(false);
      setError("지원하지 않는 문서입니다.");
      setDoc(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    getLegalDoc(docType)
      .then((res) => {
        if (cancelled) return;
        setDoc(res);
        setActive(res.sections[0] ? clauseDomId(res.sections[0].seq) : "");
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setDoc(null);
        setError(e instanceof Error ? e.message : "문서를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [docType]);

  const sections = doc?.sections ?? [];

  // Scroll spy
  useEffect(() => {
    if (sections.length === 0) return;
    const onScroll = () => {
      const atBottom =
        window.innerHeight + window.scrollY >=
        document.documentElement.scrollHeight - 100;
      if (atBottom) {
        setActive(clauseDomId(sections[sections.length - 1].seq));
        return;
      }
      let cur = clauseDomId(sections[0].seq);
      for (const s of sections) {
        const el = document.getElementById(clauseDomId(s.seq));
        if (el && el.getBoundingClientRect().top <= 100) cur = clauseDomId(s.seq);
      }
      setActive(cur);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [sections]);

  const title = doc?.title ?? (docType ? DOC_LABELS[docType] : "약관 및 정책");
  // 라우트가 존재하는 docType만 관련 문서 카드로 노출(라우트 없는 marketing 등은 죽은 링크 방지로 제외).
  const otherDocs = LEGAL_DOC_TYPES.filter((k) => k !== docType && ROUTED_DOC_TYPES.has(k));

  return (
    <div className="ct-page ct-legal">
      {/* Header */}
      <div className="ct-legal__top">
        <div className="ct-legal__eyebrow">약관 및 정책</div>
        <h1 className="ct-legal__h1">{title}</h1>
        {doc && doc.sections.length > 0 && (
          <div className="ct-legal__metarow">
            <span className="ct-legal__chip">
              <CalendarCheck /> 시행일 {formatDate(doc.effectiveDate)}
            </span>
            {doc.versionLabel && (
              <span
                className="ct-badge"
                style={{ background: "var(--info-50)", color: "var(--brand-blue)" }}
              >
                {doc.versionLabel}
              </span>
            )}
            <span className="ct-legal__chip">
              <History /> 최종 개정 {formatDate(doc.updatedAt)}
            </span>
          </div>
        )}
        {doc?.summary && <p className="ct-legal__intro">{doc.summary}</p>}
      </div>

      {/* Mobile TOC dropdown */}
      {sections.length > 0 && (
        <div className="ct-legal__mobiletoc">
          <select value={active} onChange={(e) => goTo(e.target.value)}>
            {sections.map((s) => (
              <option key={s.seq} value={clauseDomId(s.seq)}>
                제{s.seq}조 {s.title}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Desktop TOC */}
      {sections.length > 0 && (
        <nav className="ct-legal__toc">
          <div className="ct-legal__toclabel">목차</div>
          {sections.map((s) => (
            <a
              key={s.seq}
              href={`#${clauseDomId(s.seq)}`}
              className={active === clauseDomId(s.seq) ? "active" : ""}
              onClick={(e) => {
                e.preventDefault();
                goTo(clauseDomId(s.seq));
              }}
            >
              <span className="n">{s.seq}</span>
              {s.title}
            </a>
          ))}
        </nav>
      )}

      {/* Document body */}
      <article className="ct-legal__doc" ref={docRef}>
        {loading && <p className="ct-legal__sec">불러오는 중…</p>}

        {!loading && error && <p className="ct-legal__sec">{error}</p>}

        {!loading && !error && sections.length === 0 && (
          <p className="ct-legal__sec">현재 시행 중인 문서가 없습니다.</p>
        )}

        {!loading && !error &&
          sections.map((clause) => (
            <ClauseSection key={clause.seq} clause={clause} />
          ))}

        {/* Related documents */}
        {!loading && (
          <div className="ct-legal__related">
            <h4>관련 문서</h4>
            <div className="ct-legal__relgrid">
              {otherDocs.map((key) => (
                <button
                  key={key}
                  className="ct-legal__relcard"
                  onClick={() => switchDoc(key)}
                >
                  <div>
                    <b>{DOC_LABELS[key]}</b>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}
      </article>
    </div>
  );
}
