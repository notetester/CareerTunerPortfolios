import { useState, useRef, useEffect } from "react";
import { Link } from "react-router";
import {
  Search, HelpCircle, Megaphone, MessagesSquare, BookOpen,
  ArrowRight,
} from "lucide-react";
import { FAQ_CATEGORIES } from "../types/support";
import { useSupportStore } from "../hooks/useSupportStore";
import FaqAccordion from "../components/FaqAccordion";
import "../styles/support.css";

const QUICK_LINKS = [
  { key: "faq", icon: HelpCircle, label: "자주 묻는 질문", desc: "궁금한 점을 빠르게 찾아보세요", bg: "var(--cat-job-bg)", fg: "var(--cat-job-fg)" },
  { key: "notice", icon: Megaphone, label: "공지사항", desc: "업데이트·점검·이벤트 소식", bg: "var(--cat-role-bg)", fg: "var(--cat-role-fg)", href: "/support/notices" },
  { key: "contact", icon: MessagesSquare, label: "문의하기", desc: "1:1로 직접 문의해주세요", bg: "var(--cat-interview-bg)", fg: "var(--cat-interview-fg)", href: "/support/contact" },
  { key: "guide", icon: BookOpen, label: "사용 가이드", desc: "기능별 사용법과 팁 모음", bg: "var(--cat-portfolio-bg)", fg: "var(--cat-portfolio-fg)", href: "/support/guide" },
];

const POPULAR = ["환불 규정", "비밀번호 변경", "모의면접 음성 분석", "무료 횟수"];

export default function SupportHomePage() {
  const [cat, setCat] = useState("all");
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const faqRef = useRef<HTMLDivElement>(null);
  const { faqs, faqLoading, fetchFaqs } = useSupportStore();

  useEffect(() => {
    fetchFaqs(cat === "all" ? undefined : cat);
  }, [fetchFaqs, cat]);

  const faqItems = appliedQuery
    ? faqs.filter((item) => `${item.question} ${item.answer}`.toLocaleLowerCase("ko-KR")
        .includes(appliedQuery.toLocaleLowerCase("ko-KR")))
    : faqs;

  const scrollToFaq = () => faqRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });

  const searchFaq = (value = query) => {
    const normalized = value.trim();
    setQuery(normalized);
    setAppliedQuery(normalized);
    requestAnimationFrame(scrollToFaq);
  };

  const handleQuickClick = (key: string) => {
    if (key === "faq") scrollToFaq();
  };

  return (
    <div className="ct-page ct-support">
      {/* Hero */}
      <div className="ct-hero">
        <h1>무엇을 도와드릴까요?</h1>
        <p>궁금한 점을 검색하거나 아래에서 빠르게 찾아보세요.</p>
        <div className="ct-hero__search">
          <div className="ct-hero__field">
            <Search />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") searchFaq();
              }}
              placeholder="예) 환불, 모의면접, 비밀번호 변경"
              aria-label="고객센터 FAQ 검색"
            />
          </div>
          <button
            type="button"
            onClick={() => searchFaq()}
            className="av-btn av-btn--ink"
            style={{ height: 48, paddingLeft: 24, paddingRight: 24 }}
          >검색</button>
        </div>
        <div className="ct-hero__tags">
          {POPULAR.map((t) => (
            <button key={t} className="ct-hero__tag" onClick={() => searchFaq(t)}>{t}</button>
          ))}
        </div>
      </div>

      {/* Quick links */}
      <div className="ct-quick">
        {QUICK_LINKS.map((q) => {
          const inner = (
            <div className="ct-quick__card" onClick={() => handleQuickClick(q.key)}>
              <div className="ct-quick__ic" style={{ background: q.bg }}>
                <q.icon style={{ color: q.fg }} />
              </div>
              <h4>{q.label} <ArrowRight className="arr" /></h4>
              <p>{q.desc}</p>
            </div>
          );
          return q.href ? (
            <Link key={q.key} to={q.href} style={{ textDecoration: "none" }}>{inner}</Link>
          ) : (
            <div key={q.key}>{inner}</div>
          );
        })}
      </div>

      {/* FAQ */}
      <div ref={faqRef}>
        <h2 className="ct-faq__h">자주 묻는 질문</h2>
        <div className="ct-faq__tabs" style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {FAQ_CATEGORIES.map((c) => (
            <button
              key={c.value}
              onClick={() => setCat(c.value)}
              style={
                cat === c.value
                  ? { background: "var(--av-ink)", color: "var(--av-bg)", border: "1px solid transparent", borderRadius: 9999, padding: "7px 15px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
                  : { background: "var(--av-card)", color: "var(--av-ink-3)", border: "1px solid var(--av-line)", borderRadius: 9999, padding: "7px 15px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
              }
            >
              {c.label}
            </button>
          ))}
        </div>
        {faqLoading ? (
          <div className="ct-faq__empty">질문을 불러오는 중입니다.</div>
        ) : faqItems.length > 0 ? (
          <FaqAccordion items={faqItems} />
        ) : (
          <div className="ct-faq__empty">
            {appliedQuery ? `“${appliedQuery}” 검색 결과가 없습니다.` : "해당 카테고리에 등록된 질문이 없습니다."}
          </div>
        )}
      </div>

      {/* CTA */}
      <div className="ct-cta">
        <div className="ct-cta__t">
          <h3>원하는 답변을 못 찾으셨나요?</h3>
          <p>운영팀이 평일 10:00~18:00에 1:1로 답변드려요. 보통 하루 안에 회신됩니다.</p>
        </div>
        <Link to="/support/contact">
          <button className="av-btn av-btn--ink" style={{ height: 48, paddingLeft: 24, paddingRight: 24 }}>
            문의하기 <ArrowRight />
          </button>
        </Link>
      </div>
    </div>
  );
}
