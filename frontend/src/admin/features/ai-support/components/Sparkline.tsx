import type { MetricPoint } from "../types/adminChatbotPanel";

interface SparklineProps {
  series: MetricPoint[];
  color?: string;
  width?: number;
  height?: number;
}

/**
 * 인라인 SVG 스파크라인(의존성 0). 디자인 핸드오프 AvSpark 포팅.
 * 점이 2개 미만이면 그리지 않는다.
 */
export default function Sparkline({
  series,
  color = "var(--blue)",
  width = 56,
  height = 18,
}: SparklineProps) {
  if (!series || series.length < 2) return null;

  const pts = series.map((p) => p.count);
  const max = Math.max(...pts);
  const min = Math.min(...pts);
  const span = max - min || 1;

  const xy = pts.map((p, i) => [
    (i / (pts.length - 1)) * (width - 4) + 2,
    height - 2 - ((p - min) / span) * (height - 5),
  ] as const);

  const d = xy
    .map((p, i) => `${i ? "L" : "M"}${p[0].toFixed(1)} ${p[1].toFixed(1)}`)
    .join(" ");
  const last = xy[xy.length - 1];

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      fill="none"
      aria-hidden="true"
    >
      <path d={d} stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={last[0]} cy={last[1]} r="2" fill={color} />
    </svg>
  );
}
