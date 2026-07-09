import { ChevronLeft, ChevronRight, FileText } from "lucide-react";
import type { ReferencePage } from "../types/adminChatbotPanel";

interface ReferenceTableProps {
  data: ReferencePage | null;
  loading: boolean;
  error: string | null;
  page: number;
  size: number;
  onPage: (next: number) => void;
}

function timeOf(iso: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false });
}

const simText = (s: number | null) => (s == null ? "—" : s.toFixed(2));

/** 참조 대화(답한 대화 로그) 테이블 + 페이저. */
export default function ReferenceTable({
  data, loading, error, page, size, onPage,
}: ReferenceTableProps) {
  const rows = data?.content ?? [];
  const total = data?.total ?? 0;
  const start = total === 0 ? 0 : page * size + 1;
  const end = Math.min(total, page * size + rows.length);
  const hasPrev = page > 0;
  const hasNext = (page + 1) * size < total;

  return (
    <section className="ais-panel">
      <div className="ais-panel__h">
        <span className="ais-panel__t">FAQ를 참조한 대화</span>
        <span className="ais-panel__s">RAG가 임계값 이상 매칭으로 답한 최근 대화</span>
      </div>

      {error ? (
        <div className="ais-empty">{error}</div>
      ) : loading && rows.length === 0 ? (
        <div className="ais-empty">불러오는 중…</div>
      ) : rows.length === 0 ? (
        <div className="ais-empty">표시할 참조 대화가 없습니다.</div>
      ) : (
        <table className="ais-table">
          <thead>
            <tr>
              <th style={{ width: 64 }}>시각</th>
              <th>사용자 질문</th>
              <th>참조한 FAQ</th>
              <th className="r" style={{ width: 92 }}>유사도</th>
              <th style={{ width: 110 }}>결과</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => {
              const ok = r.result === "해결";
              const simOk = (r.similarity ?? 0) >= 0.85;
              return (
                <tr key={`${r.createdAt}-${i}`}>
                  <td className="ais-muted num">{timeOf(r.createdAt)}</td>
                  <td><span className="ais-cell__t">{r.question}</span></td>
                  <td>
                    {r.faqQuestion ? (
                      <span className="ais-faqref"><FileText />{r.faqQuestion}</span>
                    ) : (
                      <span className="ais-muted">—</span>
                    )}
                  </td>
                  <td className="r">
                    <span className={`ais-score num ${simOk ? "ok" : "mid"}`}>{simText(r.similarity)}</span>
                  </td>
                  <td><span className={`ais-st ${ok ? "ais-st--ok" : "ais-st--warn"}`}>{r.result}</span></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <div className="ais-foot">
        <span className="num">
          {total === 0 ? "총 0건" : `${start}–${end} 표시 · 총 ${total.toLocaleString("ko-KR")}건`}
        </span>
        <div className="ais-pager">
          <button disabled={!hasPrev || loading} onClick={() => onPage(page - 1)} aria-label="이전">
            <ChevronLeft />
          </button>
          <button disabled={!hasNext || loading} onClick={() => onPage(page + 1)} aria-label="다음">
            <ChevronRight />
          </button>
        </div>
      </div>
    </section>
  );
}
