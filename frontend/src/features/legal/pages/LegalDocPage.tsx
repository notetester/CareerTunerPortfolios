import { useState, useEffect, useRef, useCallback } from "react";
import { useSearchParams } from "react-router";
import { Info, FileText, CalendarCheck, History, ArrowRight } from "lucide-react";
import {
  legalDocs,
  LEGAL_DOC_KEYS,
  LEGAL_DOC_LABELS,
  type LegalDocKey,
} from "../data/mockLegal";
import type { LegalBlock, LegalSection } from "../types/legal";
import "../styles/legal.css";

function isValidDocKey(key: string): key is LegalDocKey {
  return (LEGAL_DOC_KEYS as readonly string[]).includes(key);
}

function BlockRenderer({ block }: { block: LegalBlock }) {
  switch (block.t) {
    case "p":
      return <p>{block.text}</p>;
    case "ol":
      return (
        <ol className="ct-legal__ol">
          {block.items.map((item, i) => <li key={i}>{item}</li>)}
        </ol>
      );
    case "dl":
      return (
        <dl className="ct-legal__dl">
          {block.items.map((item, i) => (
            <div key={i}>
              <dt>{item.term}</dt>
              <dd>{item.desc}</dd>
            </div>
          ))}
        </dl>
      );
    case "note":
      return (
        <div className="ct-legal__note">
          <Info /><div>{block.text}</div>
        </div>
      );
  }
}

function SectionRenderer({ section }: { section: LegalSection }) {
  return (
    <section className="ct-legal__sec" id={section.id}>
      <h2><span className="num">{section.num}</span>{section.title}</h2>
      {section.blocks.map((block, i) => (
        <BlockRenderer key={i} block={block} />
      ))}
    </section>
  );
}

export default function LegalDocPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const docKey = searchParams.get("doc") ?? "terms";
  const currentKey: LegalDocKey = isValidDocKey(docKey) ? docKey : "terms";
  const doc = legalDocs[currentKey];

  const [active, setActive] = useState<string>(doc.sections[0]?.id ?? "");
  const docRef = useRef<HTMLElement>(null);

  const switchDoc = useCallback(
    (key: LegalDocKey) => {
      setSearchParams({ doc: key });
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
    [setSearchParams],
  );

  const goTo = (id: string) => {
    const el = document.getElementById(id);
    if (el) window.scrollTo({ top: window.scrollY + el.getBoundingClientRect().top - 84, behavior: "smooth" });
  };

  // Scroll spy
  useEffect(() => {
    const onScroll = () => {
      const atBottom =
        window.innerHeight + window.scrollY >=
        document.documentElement.scrollHeight - 100;

      if (atBottom) {
        setActive(doc.sections[doc.sections.length - 1]?.id ?? "");
        return;
      }

      let cur = doc.sections[0]?.id;
      for (const s of doc.sections) {
        const el = document.getElementById(s.id);
        if (el && el.getBoundingClientRect().top <= 100) cur = s.id;
      }
      if (cur) setActive(cur);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [currentKey, doc.sections]);

  useEffect(() => {
    setActive(doc.sections[0]?.id ?? "");
  }, [doc]);

  const otherDocs = LEGAL_DOC_KEYS.filter((k) => k !== currentKey);

  return (
    <div className="ct-page ct-legal">
      {/* Header */}
      <div className="ct-legal__top">
        <div className="ct-legal__eyebrow">약관 및 정책</div>
        <h1 className="ct-legal__h1">{doc.title}</h1>
        <div className="ct-legal__metarow">
          <span className="ct-legal__chip"><CalendarCheck /> 시행일 {doc.effective}</span>
          <span className="ct-badge" style={{ background: "var(--info-50)", color: "var(--brand-blue)" }}>{doc.version}</span>
          <span className="ct-legal__chip"><History /> 최종 개정 {doc.updated}</span>
        </div>
        {doc.intro && <p className="ct-legal__intro">{doc.intro}</p>}
      </div>

      {/* Mobile TOC dropdown */}
      <div className="ct-legal__mobiletoc">
        <select value={active} onChange={(e) => goTo(e.target.value)}>
          {doc.sections.map((s) => (
            <option key={s.id} value={s.id}>{s.num} {s.title}</option>
          ))}
        </select>
      </div>

      {/* Desktop TOC */}
      <nav className="ct-legal__toc">
        <div className="ct-legal__toclabel">목차</div>
        {doc.sections.map((s) => (
          <a
            key={s.id}
            href={`#${s.id}`}
            className={active === s.id ? "active" : ""}
            onClick={(e) => { e.preventDefault(); goTo(s.id); }}
          >
            <span className="n">{s.num.replace("제", "").replace("조", "")}</span>
            {s.title}
          </a>
        ))}
      </nav>

      {/* Document body */}
      <article className="ct-legal__doc" ref={docRef}>
        {doc.sections.map((section) => (
          <SectionRenderer key={section.id} section={section} />
        ))}

        {/* Related documents */}
        <div className="ct-legal__related">
          <h4>관련 문서</h4>
          <div className="ct-legal__relgrid">
            {otherDocs.map((key) => (
              <button key={key} className="ct-legal__relcard" onClick={() => switchDoc(key)}>
                <span className="ct-legal__relic"><FileText /></span>
                <div>
                  <b>{LEGAL_DOC_LABELS[key]}</b><br />
                  <span>시행일 {legalDocs[key].effective} · {legalDocs[key].version}</span>
                </div>
                <span className="go"><ArrowRight /></span>
              </button>
            ))}
          </div>
        </div>
      </article>
    </div>
  );
}
