import { useState } from "react";
import {
  Scale, Eye, Check, CalendarClock, Plus,
  ChevronUp, ChevronDown, X,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import "./admin-terms.css";

const DOCS = ["이용약관", "개인정보처리방침", "마케팅 수신 동의"];

interface Clause { t: string; b: string; }

const INIT_CLAUSES: Clause[] = [
  {
    t: "목적",
    b: "이 약관은 커리어튜너(이하 \"회사\")가 제공하는 AI 기반 취업 준비 서비스(이하 \"서비스\")의 이용 조건 및 절차, 회사와 회원 간의 권리·의무를 규정함을 목적으로 합니다.",
  },
  {
    t: "정의",
    b: "1. \"회원\"이란 이 약관에 동의하고 서비스 이용 계약을 체결한 자를 말합니다.\n2. \"크레딧\"이란 AI 분석 기능을 이용하기 위해 차감되는 이용권을 말합니다.\n3. \"AI 분석 결과\"란 회원이 제공한 자료를 기반으로 서비스가 생성한 리포트를 말합니다.",
  },
  {
    t: "AI 분석 결과의 성격과 책임",
    b: "1. AI 분석 결과는 취업 준비를 돕기 위한 참고 자료이며, 채용 결과를 보장하지 않습니다.\n2. 회사는 분석 결과의 정확성을 높이기 위해 노력하나, 회원의 최종 의사결정에 대한 책임은 회원 본인에게 있습니다.",
  },
];

const VERSIONS = [
  { v: "v2.4", st: "next" as const, note: "AI 분석 결과 책임 조항 신설", d: "시행 예정 2026.06.20" },
  { v: "v2.3", st: "live" as const, note: "개인정보 위탁 업체 변경 반영", d: "시행 2026.05.20" },
  { v: "v2.2", st: "old" as const, note: "크레딧 환불 규정 명확화", d: "시행 2026.02.01" },
  { v: "v2.1", st: "old" as const, note: "커뮤니티 운영 정책 조항 추가", d: "시행 2025.11.10" },
];

const BADGE_LABEL: Record<string, string> = { live: "게시중", next: "예정", old: "종료" };

export default function AdminTerms() {
  const [doc, setDoc] = useState("이용약관");
  const [clauses, setClauses] = useState<Clause[]>(INIT_CLAUSES);
  const [summary, setSummary] = useState("AI 분석 결과 책임 조항 신설 (제3조)");
  const [when, setWhen] = useState<"즉시" | "예약">("예약");

  const update = (i: number, k: keyof Clause, v: string) =>
    setClauses((p) => p.map((c, j) => (j === i ? { ...c, [k]: v } : c)));
  const add = () => setClauses((p) => [...p, { t: "", b: "" }]);
  const remove = (i: number) => setClauses((p) => p.filter((_, j) => j !== i));
  const moveUp = (i: number) => {
    if (i === 0) return;
    setClauses((p) => {
      const next = [...p];
      [next[i - 1], next[i]] = [next[i], next[i - 1]];
      return next;
    });
  };
  const moveDown = (i: number) => {
    setClauses((p) => {
      if (i >= p.length - 1) return p;
      const next = [...p];
      [next[i], next[i + 1]] = [next[i + 1], next[i]];
      return next;
    });
  };

  return (
    <AdminShell
      active="terms"
      breadcrumb="약관 관리"
      title="약관 관리"
      icon={Scale}
      desc="법적 문서 작성·개정 — 게시 7일 전 공지 의무"
      actions={
        <div className="av-seg">
          {DOCS.map((d) => (
            <button key={d} className={doc === d ? "on" : ""} onClick={() => setDoc(d)}>
              {d}
            </button>
          ))}
        </div>
      }
    >
      <div className="av-form">
        {/* 본문 영역 */}
        <section className="av-panel">
          <div className="av-mod__h" style={{ paddingBottom: 12 }}>
            <span className="av-mod__t">{doc} — v2.4 작성 중</span>
            <span className="av-mod__s">기준: v2.3 (게시중)</span>
          </div>

          <div className="av-field" style={{ borderTop: "1px solid var(--av-line-soft)" }}>
            <div className="av-flabel">
              개정 요약 <span className="opt">— 공지사항·이메일 고지에 그대로 사용됩니다</span>
            </div>
            <input
              className="av-input"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
              placeholder="예: 크레딧 환불 규정 명확화 (제12조)"
            />
          </div>

          <div className="av-field">
            <div className="av-flabel">조항</div>
            {clauses.map((c, i) => (
              <div className="tv-clause" key={i}>
                <div className="tv-clause__h">
                  <span className="tv-clause__no num">제{i + 1}조</span>
                  <input
                    className="tv-clause__t"
                    value={c.t}
                    onChange={(e) => update(i, "t", e.target.value)}
                    placeholder="조항 제목"
                  />
                  <div className="tv-clause__tools">
                    <button aria-label="위로" onClick={() => moveUp(i)}><ChevronUp /></button>
                    <button aria-label="아래로" onClick={() => moveDown(i)}><ChevronDown /></button>
                    <button aria-label="삭제" onClick={() => remove(i)}><X /></button>
                  </div>
                </div>
                <textarea
                  className="tv-clause__b"
                  value={c.b}
                  onChange={(e) => update(i, "b", e.target.value)}
                  placeholder="조항 본문 — 줄바꿈으로 항(1. 2. 3.)을 구분하세요"
                />
              </div>
            ))}
            <button className="tv-add" onClick={add}>
              <Plus /> 조항 추가
            </button>
          </div>

          <div className="av-field">
            <div className="av-flabel">시행 시점</div>
            <div className="av-choices">
              <div
                className={`av-choice${when === "즉시" ? " on" : ""}`}
                onClick={() => setWhen("즉시")}
              >
                <div className="t">즉시 시행</div>
                <div className="s">게시와 동시에 효력</div>
              </div>
              <div
                className={`av-choice${when === "예약" ? " on" : ""}`}
                onClick={() => setWhen("예약")}
              >
                <div className="t">예약 시행</div>
                <div className="s num">2026.06.20 00:00</div>
              </div>
            </div>
            <div className="av-hint">
              개정 약관은 시행 7일 전(불리한 변경은 30일 전) 공지해야 합니다. 게시 시 공지사항 초안이 자동 생성됩니다.
            </div>
          </div>
        </section>

        {/* 우측 레일 */}
        <aside className="av-rail">
          <section className="av-panel">
            <div className="av-mod__h">
              <span className="av-mod__t">버전 이력</span>
              <span className="av-mod__s">{doc}</span>
            </div>
            <div className="av-list tv-ver">
              {VERSIONS.map((ver) => (
                <a key={ver.v} href="#" onClick={(e) => e.preventDefault()}>
                  <span style={{ minWidth: 0, flex: 1 }}>
                    <span className="av-list__t">
                      <b className="num" style={{ fontWeight: 700, marginRight: 6 }}>{ver.v}</b>
                      {ver.note}
                    </span>
                    <span className="av-list__s num" style={{ display: "block", marginTop: 2 }}>
                      {ver.d}
                    </span>
                  </span>
                  <span className={`tv-ver__badge ${ver.st}`}>{BADGE_LABEL[ver.st]}</span>
                </a>
              ))}
            </div>
          </section>

          <section className="av-panel">
            <div className="av-mod__h"><span className="av-mod__t">개정 체크리스트</span></div>
            <div className="av-note" style={{ marginTop: 12 }}>
              <b>법무 검토</b> 완료 후 게시하세요. 게시하면 <b>전 회원 NOTICE 알림</b>이 발송되고,
              미동의 회원은 다음 로그인 시 동의 절차를 거칩니다.
            </div>
          </section>
        </aside>
      </div>

      {/* 스티키 푸터 */}
      <div className="av-composefoot">
        <div className="av-composefoot__in">
          <span className="av-composefoot__draft num">
            <Check /> 임시저장됨 · 2분 전
          </span>
          <div className="av-composefoot__r">
            <button className="av-btn"><Eye /> 미리보기</button>
            <button className="av-btn">임시저장</button>
            <button className="av-btn av-btn--ink">
              <CalendarClock /> 게시 예약
            </button>
          </div>
        </div>
      </div>
    </AdminShell>
  );
}
