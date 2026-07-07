import { useEffect, useRef } from "react";

/**
 * 마이크 입력 레벨 시각화 — MediaStream 의 실제 음량(RMS)을 따라 움직이는 바.
 * 오른쪽이 최신인 스크롤 파형. React 상태 없이 rAF 로 바 높이를 직접 갱신해
 * 60fps 리렌더 없이 동작한다. 스트림이 없거나 Web Audio 미지원이면 최소 높이로 정지.
 *
 * 높이·간격은 className 으로 준다 (예: "h-6 gap-[3px] text-rose-500").
 * 바 색은 기본 currentColor 라 text-* 로 제어하거나 barClassName 으로 교체한다.
 */
export function MicLevelMeter({
  stream,
  bars = 16,
  className = "",
  barClassName = "w-[3px] rounded-full bg-current",
  minRatio = 0.16,
}: {
  stream: MediaStream | null;
  bars?: number;
  className?: string;
  barClassName?: string;
  /** 무음일 때 바의 최소 높이 비율 (0~1) */
  minRatio?: number;
}) {
  const barRefs = useRef<(HTMLSpanElement | null)[]>([]);

  useEffect(() => {
    const paint = (levels: number[]) => {
      for (let i = 0; i < levels.length; i++) {
        const el = barRefs.current[i];
        if (!el) continue;
        el.style.height = `${(minRatio + (1 - minRatio) * levels[i]) * 100}%`;
        el.style.opacity = `${0.45 + 0.55 * levels[i]}`;
      }
    };
    const history = new Array<number>(bars).fill(0);
    paint(history);
    if (!stream) return;

    let ctx: AudioContext;
    let analyser: AnalyserNode;
    try {
      const Ctx =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      ctx = new Ctx();
      analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      ctx.createMediaStreamSource(stream).connect(analyser);
    } catch {
      return; // Web Audio 미지원 — 정적 표시로 남긴다
    }
    void ctx.resume().catch(() => undefined);

    const buf = new Float32Array(analyser.fftSize);
    let smoothed = 0;

    // rAF 대신 setInterval — 백그라운드 탭·WebView 의 rAF 스로틀링에도 멈추지 않는다.
    const timer = setInterval(() => {
      analyser.getFloatTimeDomainData(buf);
      let sumSq = 0;
      for (let i = 0; i < buf.length; i++) sumSq += buf[i] * buf[i];
      const rms = Math.sqrt(sumSq / buf.length);
      // 일반 발화 RMS(약 0.01~0.15)를 0~1 로 정규화 + 저음량 지각 보정(pow<1)
      const level = Math.min(1, Math.pow(Math.max(0, rms - 0.004) / 0.12, 0.7));
      // 어택은 즉시, 릴리즈는 천천히 — 말 끊길 때 뚝 떨어지지 않게
      smoothed = level > smoothed ? level : smoothed * 0.6;
      history.shift();
      history.push(smoothed);
      paint(history);
    }, 50);

    return () => {
      clearInterval(timer);
      void ctx.close().catch(() => undefined);
    };
  }, [stream, bars, minRatio]);

  return (
    <div className={`flex items-center ${className}`} aria-hidden>
      {Array.from({ length: bars }).map((_, i) => (
        <span
          key={i}
          ref={(el) => {
            barRefs.current[i] = el;
          }}
          className={barClassName}
          style={{ height: `${minRatio * 100}%`, opacity: 0.45 }}
        />
      ))}
    </div>
  );
}
