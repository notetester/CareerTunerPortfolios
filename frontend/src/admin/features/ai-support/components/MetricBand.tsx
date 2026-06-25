import Sparkline from "./Sparkline";
import type { ChatbotMetrics, MetricCard } from "../types/adminChatbotPanel";

type CardKind = "ratio" | "count";

interface MetricDef {
  key: keyof ChatbotMetrics;
  label: string;
  kind: CardKind;
  /** 보조 설명(델타 옆 문구). */
  note: string;
  /** true 면 빨강 강조(FAQ 공백). */
  danger?: boolean;
}

const DEFS: MetricDef[] = [
  { key: "autoResolveRate", label: "자동 해결률", kind: "ratio", note: "상담사 전환 없이 종료" },
  { key: "faqReferenceCount", label: "FAQ 참조 응답", kind: "count", note: "RAG가 FAQ를 인용한 횟수" },
  { key: "faqGap", label: "FAQ 공백", kind: "count", note: "답할 FAQ가 없던 질문 군집", danger: true },
  { key: "handoffRate", label: "상담사 전환율", kind: "ratio", note: "사람 상담사로 넘어간 비율" },
];

/** 비율(0~1) → "72.4%". 건수 → "1,284". */
function formatValue(value: number, kind: CardKind): { main: string; unit: string | null } {
  if (kind === "ratio") return { main: (value * 100).toFixed(1), unit: "%" };
  return { main: Math.round(value).toLocaleString("ko-KR"), unit: null };
}

/** 델타 강조색: 공백 카드는 항상 빨강, 그 외는 상승 green·하락 amber. */
function deltaClass(danger: boolean, up: boolean): string {
  if (danger) return "bad";
  return up ? "up" : "down";
}

/** 델타를 ▲/▼ + 단위로. ratio 는 %p, count 는 건. */
function formatDelta(delta: number, kind: CardKind): { text: string; up: boolean } {
  const up = delta >= 0;
  const arrow = up ? "▲" : "▼";
  const abs = Math.abs(delta);
  if (kind === "ratio") return { text: `${arrow} ${(abs * 100).toFixed(1)}%p`, up };
  return { text: `${arrow} ${Math.round(abs).toLocaleString("ko-KR")}건`, up };
}

function Card({ def, card }: { def: MetricDef; card: MetricCard | null }) {
  // 카드 자체가 null = 데이터 수집 중(가짜 숫자 금지).
  if (!card) {
    return (
      <div className="ais-met">
        <div className="ais-met__l">{def.label}</div>
        <div className="ais-met__row">
          <span className="ais-met__pending">수집 중</span>
        </div>
        <div className="ais-met__d">데이터를 모으는 중입니다 · {def.note}</div>
      </div>
    );
  }

  const color = def.danger ? "var(--red)" : "var(--blue)";
  const hasValue = card.value != null;
  const v = hasValue ? formatValue(card.value as number, def.kind) : null;
  const d = card.deltaVsPrev != null ? formatDelta(card.deltaVsPrev, def.kind) : null;
  // 부분 응답(value만 채우고 series 누락/null) 방어 — 런타임 검증 없는 env.data as T 경로라 정규화.
  const series = card.series ?? [];

  return (
    <div className="ais-met">
      <div className="ais-met__l">{def.label}</div>
      <div className="ais-met__row">
        {v ? (
          <span className={`ais-met__n num${def.danger ? " danger" : ""}`}>
            {v.main}
            {v.unit && <small>{v.unit}</small>}
          </span>
        ) : (
          <span className="ais-met__n num ais-met__n--dash">—</span>
        )}
        {series.length >= 2 && (
          <span className="ais-met__spark">
            <Sparkline series={series} color={color} />
          </span>
        )}
      </div>
      <div className="ais-met__d num">
        {def.note}
        {d && (
          <>
            {" · "}
            <b className={deltaClass(def.danger ?? false, d.up)}>{d.text}</b>
          </>
        )}
      </div>
    </div>
  );
}

/** 메트릭 밴드(4열). 각 카드 value+delta+스파크라인. */
export default function MetricBand({ metrics }: { metrics: ChatbotMetrics | null }) {
  return (
    <div className="ais-metrics">
      {DEFS.map((def) => (
        <Card key={def.key} def={def} card={metrics ? metrics[def.key] : null} />
      ))}
    </div>
  );
}
