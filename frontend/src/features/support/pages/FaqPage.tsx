import { useState, useEffect } from "react";
import { FAQ_CATEGORIES } from "../types/support";
import { useSupportStore } from "../hooks/useSupportStore";
import FaqAccordion from "../components/FaqAccordion";
import "../styles/support.css";

export default function FaqPage() {
  const [cat, setCat] = useState("all");
  const { faqs, faqLoading, fetchFaqs } = useSupportStore();

  useEffect(() => {
    fetchFaqs(cat === "all" ? undefined : cat);
  }, [fetchFaqs, cat]);

  const items = faqs;

  return (
    <div className="ct-page">
      <div className="ct-pagehead">
        <h1>자주 묻는 질문</h1>
        <p>CareerTuner 이용에 대해 자주 묻는 질문들을 모았습니다.</p>
      </div>

      <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 18 }}>
        {FAQ_CATEGORIES.map((c) => (
          <button
            key={c.value}
            onClick={() => setCat(c.value)}
            style={
              cat === c.value
                ? { background: "var(--av-ink)", color: "#fff", border: "1px solid transparent", borderRadius: 9999, padding: "7px 15px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
                : { background: "var(--av-card)", color: "var(--av-ink-3)", border: "1px solid var(--av-line)", borderRadius: 9999, padding: "7px 15px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
            }
          >
            {c.label}
          </button>
        ))}
      </div>

      {items.length > 0 ? (
        <FaqAccordion items={items} />
      ) : (
        <div className="ct-faq__empty">해당 카테고리에 등록된 질문이 없습니다.</div>
      )}
    </div>
  );
}
