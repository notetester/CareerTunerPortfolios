import { useEffect, useRef, useState } from "react";
import { GitCompareArrows, Eye } from "lucide-react";
import { ApiError } from "@/app/lib/api";
import { previewThreshold } from "../api/adminChatbotPanelApi";
import type { ThresholdPreview } from "../types/adminChatbotPanel";

interface ThresholdStripProps {
  /** 슬라이더 정수값(50~95). */
  value: number;
  /** 드래그 시 부모로 통지(상세 막대 임계선 동기화). */
  onChange: (v: number) => void;
}

/**
 * RAG 설정 스트립 — 유사도 임계값 미리보기.
 * ★ 읽기 전용: 실제 챗봇 매칭은 바뀌지 않는다(저장/적용 아님).
 * 슬라이더(50~95, 표시 0.{v}) 드래그 → 디바운스 → /threshold/preview → gapCount/total + 히스토그램.
 */
export default function ThresholdStrip({ value, onChange }: ThresholdStripProps) {
  const [preview, setPreview] = useState<ThresholdPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | null>(null);

  useEffect(() => {
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      const threshold = value / 100;
      setLoading(true);
      setError(null);
      previewThreshold(threshold)
        .then((p) => setPreview(p))
        .catch((e) => setError(e instanceof ApiError ? e.message : "미리보기를 불러오지 못했습니다."))
        .finally(() => setLoading(false));
    }, 280);
    return () => {
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, [value]);

  const maxBucket = preview ? Math.max(1, ...preview.histogram.map((b) => b.count)) : 1;
  const cutoff = value / 100;

  return (
    <div className="ais-cfg">
      <div className="ais-cfg__h">
        <GitCompareArrows />
        FAQ 참조 임계값 (코사인 유사도)
        <span className="ais-cfg__sub">이 값 미만이면 답변 보류 → 공백으로 수집</span>
      </div>

      <div className="ais-thr">
        <div className="ais-thr__track">
          <span className="ais-thr__lo" style={{ width: `${value}%` }} />
          <span className="ais-thr__hi" style={{ width: `${100 - value}%` }} />
          <span className="ais-thr__knob" style={{ left: `${value}%` }} />
          <input
            type="range"
            min={50}
            max={95}
            step={1}
            value={value}
            onChange={(e) => onChange(Number(e.target.value))}
            aria-label="FAQ 참조 임계값"
          />
        </div>
        <span className="ais-thr__val num">0.{value}</span>
      </div>

      <div className="ais-legend">
        <span><b style={{ color: "var(--amber)" }}>← 낮춤</b> 더 자주 답함 · 오답 위험</span>
        <span><b style={{ color: "var(--blue)" }}>높임 →</b> 정확하지만 공백 증가</span>
      </div>

      {/* ★ 미리보기 명시 — 저장/적용 아님 */}
      <div className="ais-thr__notice">
        <Eye />
        미리보기 — 실제 챗봇 매칭은 바뀌지 않습니다.
      </div>

      <div className="ais-thr__result">
        {error ? (
          <span className="ais-thr__err">{error}</span>
        ) : preview ? (
          <span>
            <b className="num">0.{value}</b> 미만이면{" "}
            <b className="num ais-thr__gap">{preview.gapCount.toLocaleString("ko-KR")}건</b>
            {" "}공백 · 전체 <b className="num">{preview.total.toLocaleString("ko-KR")}건</b> 기준
            {loading && <span className="ais-thr__busy"> 계산 중…</span>}
          </span>
        ) : loading ? (
          <span className="ais-thr__busy">분포를 계산하는 중…</span>
        ) : (
          <span className="ais-thr__busy">—</span>
        )}
      </div>

      {/* 유사도 분포 히스토그램 — cutoff(임계값) 미만 막대는 amber, 이상은 blue */}
      {preview && preview.histogram.length > 0 && (
        <div className="ais-hist">
          {preview.histogram.map((b, i) => {
            // 구간 상한이 임계값 이하 = 공백으로 잡히는 분포(amber 강조).
            const below = b.to <= cutoff;
            return (
              <div className="ais-hist__col" key={i} title={`0.${Math.round(b.from * 100)}–0.${Math.round(b.to * 100)} · ${b.count}건`}>
                <span
                  className={`ais-hist__bar${below ? " below" : ""}`}
                  style={{ height: `${(b.count / maxBucket) * 100}%` }}
                />
                <span className="ais-hist__x num">{b.from.toFixed(1)}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
