// 음성 모의면접/아바타 화상 면접의 온디바이스 음성 분석.
// 원본 오디오는 서버에 저장하지 않는다 — 지표·점수(JSON)만 저장 (ADR-002).
//
// 구성:
//  - VoiceMetricsTracker: 라이브 마이크 스트림에서 Web Audio 로 발화/침묵/피치/성량 샘플링
//  - countFillers / computeVoiceScore: 트랜스크립트·지표 → 항목별 점수(0~100)
//  - blobToPcm16Base64: 녹음(webm 등) → 16kHz mono PCM16(LINEAR16) base64 (Inworld 전송용)

import type { VoiceMetrics, VoiceProfile, VoiceScoreDetail } from "../types/interview";

// ───── 라이브 지표 샘플링 ─────

const SAMPLE_INTERVAL_MS = 100;
/** RMS 이 값 이상이면 발화 프레임으로 간주 (마이크 기본 감도 기준 경험값) */
const SPEECH_RMS_THRESHOLD = 0.015;
/** 피치 탐색 범위 (Hz) — 사람 목소리 기본 주파수 */
const PITCH_MIN_HZ = 60;
const PITCH_MAX_HZ = 400;

/**
 * 마이크 MediaStream 에 AnalyserNode 를 붙여 100ms 간격으로
 * 발화 시간 · 성량(RMS) · 피치(자기상관) · 질문→답변 반응 지연을 누적한다.
 * AI 쪽 음성은 원격 스트림(스피커)이라 마이크 지표에 섞이지 않는다.
 */
export class VoiceMetricsTracker {
  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private buf: Float32Array | null = null;

  private startedAt = 0;
  private endedAt: number | null = null;
  private speechFrames = 0;
  private totalFrames = 0;
  private rmsSum = 0;
  private rmsCount = 0;
  private pitches: number[] = [];

  /** AI 질문이 끝난 시각 (ms). 다음 사용자 발화까지의 지연 측정용. */
  private pendingQuestionEndAt: number | null = null;
  private latenciesSec: number[] = [];

  start(stream: MediaStream) {
    this.stopSampling();
    this.startedAt = performance.now();
    this.endedAt = null;
    this.audioContext = new AudioContext();
    const source = this.audioContext.createMediaStreamSource(stream);
    this.analyser = this.audioContext.createAnalyser();
    this.analyser.fftSize = 2048;
    source.connect(this.analyser);
    this.buf = new Float32Array(this.analyser.fftSize);

    this.timer = setInterval(() => this.sample(), SAMPLE_INTERVAL_MS);
  }

  /** AI 발화(질문)가 끝났을 때 호출 — 다음 사용자 발화까지 지연을 잰다. */
  markAiSpeechEnd() {
    this.pendingQuestionEndAt = performance.now();
  }

  /**
   * 샘플링만 먼저 멈춘다 (지표 확정은 finish).
   * 면접 종료 후 비동기 분석(Inworld 등)이 끝나길 기다렸다가 finish 를 불러도
   * 분석 대기 시간이 totalSec 에 섞이지 않게 한다.
   */
  pause() {
    if (this.endedAt == null) this.endedAt = performance.now();
    this.stopSampling();
  }

  /** 샘플링을 멈추고 누적 지표를 돌려준다. 트랜스크립트 글자 수로 말 속도를 계산한다. */
  finish(userTranscriptChars: number, fillerCount: number): VoiceMetrics {
    this.pause();
    const totalSec = this.startedAt > 0 ? ((this.endedAt ?? performance.now()) - this.startedAt) / 1000 : 0;
    const speakingSec = (this.speechFrames * SAMPLE_INTERVAL_MS) / 1000;
    const speakingMin = speakingSec / 60;
    const totalMin = totalSec / 60;

    const avgPitch = average(this.pitches);
    return {
      totalSec: round1(totalSec),
      speakingSec: round1(speakingSec),
      speechRateSpm: speakingMin > 0.05 ? Math.round(userTranscriptChars / speakingMin) : null,
      fillerCount,
      fillerPerMin: totalMin > 0.05 ? round1(fillerCount / totalMin) : null,
      avgPitchHz: avgPitch != null ? Math.round(avgPitch) : null,
      pitchStdevHz: stdev(this.pitches) != null ? Math.round(stdev(this.pitches)!) : null,
      avgResponseLatencySec: average(this.latenciesSec) != null ? round1(average(this.latenciesSec)!) : null,
      maxResponseLatencySec: this.latenciesSec.length > 0 ? round1(Math.max(...this.latenciesSec)) : null,
      avgVolume: this.rmsCount > 0 ? round3(this.rmsSum / this.rmsCount) : null,
    };
  }

  dispose() {
    this.stopSampling();
  }

  private stopSampling() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.audioContext?.close().catch(() => undefined);
    this.audioContext = null;
    this.analyser = null;
  }

  private sample() {
    if (!this.analyser || !this.buf) return;
    this.analyser.getFloatTimeDomainData(this.buf);
    let sumSq = 0;
    for (let i = 0; i < this.buf.length; i++) sumSq += this.buf[i] * this.buf[i];
    const rms = Math.sqrt(sumSq / this.buf.length);
    this.totalFrames += 1;

    if (rms >= SPEECH_RMS_THRESHOLD) {
      this.speechFrames += 1;
      this.rmsSum += rms;
      this.rmsCount += 1;
      if (this.pendingQuestionEndAt != null) {
        this.latenciesSec.push((performance.now() - this.pendingQuestionEndAt) / 1000);
        this.pendingQuestionEndAt = null;
      }
      const pitch = detectPitchHz(this.buf, this.audioContext?.sampleRate ?? 48000);
      if (pitch != null) this.pitches.push(pitch);
    }
  }
}

/** 시간영역 자기상관(ACF) 기반 피치 추정. 신뢰도 낮으면 null. */
function detectPitchHz(buf: Float32Array, sampleRate: number): number | null {
  const minLag = Math.floor(sampleRate / PITCH_MAX_HZ);
  const maxLag = Math.min(Math.floor(sampleRate / PITCH_MIN_HZ), buf.length - 1);
  let bestLag = -1;
  let bestCorr = 0;
  let zeroLag = 0;
  for (let i = 0; i < buf.length; i++) zeroLag += buf[i] * buf[i];
  if (zeroLag === 0) return null;

  for (let lag = minLag; lag <= maxLag; lag++) {
    let corr = 0;
    for (let i = 0; i + lag < buf.length; i++) corr += buf[i] * buf[i + lag];
    corr /= zeroLag;
    if (corr > bestCorr) {
      bestCorr = corr;
      bestLag = lag;
    }
  }
  // 상관계수가 낮으면 잡음으로 보고 버린다.
  if (bestLag < 0 || bestCorr < 0.5) return null;
  return sampleRate / bestLag;
}

// ───── 필러(군말) 카운트 ─────

// 한국어 면접에서 흔한 군말. 단독 토큰일 때만 센다(일반 단어 오검출 방지 휴리스틱).
const FILLER_TOKENS = new Set(["음", "어", "어어", "음음", "그", "저", "저기", "그니까", "그러니까", "이제", "막", "뭐랄까", "약간"]);

export function countFillers(userLines: string[]): number {
  let count = 0;
  for (const line of userLines) {
    for (const raw of line.split(/[\s,.!?…]+/)) {
      const token = raw.trim();
      if (token && FILLER_TOKENS.has(token)) count += 1;
    }
  }
  return count;
}

// ───── 점수 산출 ─────

/** 측정 불가 항목에 주는 중립 점수 */
const NEUTRAL = 70;

/**
 * 브라우저 지표 + (있으면) Inworld voice profile → 항목별 점수.
 * 기준은 실제 면접 표준(ADR-001/002): 또박또박한 속도, 군말 최소화, 안정된 톤, 자신감, 빠른 반응.
 */
export function computeVoiceScore(metrics: VoiceMetrics, profile: VoiceProfile | null): VoiceScoreDetail {
  // 말 속도: 한국어 발표 적정 250~400자/분, 그 밖은 거리에 비례해 감점.
  const pace =
    metrics.speechRateSpm == null
      ? NEUTRAL
      : bandScore(metrics.speechRateSpm, 120, 250, 400, 550);

  // 군말: 분당 0회 = 100, 8회 이상 = 20.
  const fluency =
    metrics.fillerPerMin == null
      ? NEUTRAL
      : clamp(Math.round(100 - metrics.fillerPerMin * 10), 20, 100);

  // 톤 안정감: 피치 변동계수(stdev/avg) 0.10~0.35 가 자연스러운 억양.
  let stability = NEUTRAL;
  if (metrics.avgPitchHz != null && metrics.pitchStdevHz != null && metrics.avgPitchHz > 0) {
    const cov = metrics.pitchStdevHz / metrics.avgPitchHz;
    stability = bandScore(cov, 0.02, 0.1, 0.35, 0.7);
  }
  if (hasLabel(profile?.vocalStyle, "monotone")) stability = clamp(stability - 15, 0, 100);

  // 자신감: 성량 + 감정·보컬스타일 라벨 보정.
  let confidence =
    metrics.avgVolume == null ? NEUTRAL : bandScore(metrics.avgVolume, 0.005, 0.03, 0.15, 0.4);
  if (hasLabel(profile?.emotion, "calm") || hasLabel(profile?.emotion, "happy") || hasLabel(profile?.emotion, "neutral")) {
    confidence = clamp(confidence + 10, 0, 100);
  }
  if (hasLabel(profile?.emotion, "fearful") || hasLabel(profile?.emotion, "sad")) {
    confidence = clamp(confidence - 15, 0, 100);
  }
  if (hasLabel(profile?.vocalStyle, "whispering") || hasLabel(profile?.vocalStyle, "mumbling")) {
    confidence = clamp(confidence - 20, 0, 100);
  }

  // 반응 속도: 질문 후 1.5초 내 첫 발화 = 100, 8초 = 30.
  const responsiveness =
    metrics.avgResponseLatencySec == null
      ? NEUTRAL
      : clamp(Math.round(100 - Math.max(0, metrics.avgResponseLatencySec - 1.5) * 10.8), 30, 100);

  const overall = Math.round(
    pace * 0.2 + fluency * 0.25 + stability * 0.2 + confidence * 0.2 + responsiveness * 0.15,
  );
  return { pace, fluency, stability, confidence, responsiveness, overall };
}

/** [hardMin, idealMin, idealMax, hardMax] 구간 점수: 이상 구간 100, 밖은 선형 감점. */
function bandScore(value: number, hardMin: number, idealMin: number, idealMax: number, hardMax: number): number {
  if (value >= idealMin && value <= idealMax) return 100;
  if (value <= hardMin || value >= hardMax) return 20;
  if (value < idealMin) return Math.round(20 + (80 * (value - hardMin)) / (idealMin - hardMin));
  return Math.round(20 + (80 * (hardMax - value)) / (hardMax - idealMax));
}

function hasLabel(labels: { label: string }[] | undefined, name: string): boolean {
  return !!labels?.some((l) => l.label === name);
}

// ───── 오디오 변환 (Inworld LINEAR16 전송용) ─────

/** Inworld 전송 오디오 상한(초) — 라벨형 프로필이라 마지막 구간만으로 충분, 페이로드 폭주 방지. */
const MAX_ANALYSIS_SEC = 120;
const TARGET_SAMPLE_RATE = 16000;

/**
 * MediaRecorder 녹음(webm/opus 등)을 16kHz mono PCM16(raw LINEAR16) base64 로 변환한다.
 * 길면 마지막 {@link MAX_ANALYSIS_SEC}초만 잘라 보낸다.
 */
export async function blobToPcm16Base64(blob: Blob): Promise<string> {
  const arrayBuffer = await blob.arrayBuffer();
  const decodeCtx = new AudioContext();
  let decoded: AudioBuffer;
  try {
    decoded = await decodeCtx.decodeAudioData(arrayBuffer);
  } finally {
    decodeCtx.close().catch(() => undefined);
  }

  const keepSec = Math.min(decoded.duration, MAX_ANALYSIS_SEC);
  const offline = new OfflineAudioContext(1, Math.ceil(keepSec * TARGET_SAMPLE_RATE), TARGET_SAMPLE_RATE);
  const source = offline.createBufferSource();
  source.buffer = decoded;
  source.connect(offline.destination);
  // 마지막 keepSec 구간부터 재생되도록 offset 을 준다.
  source.start(0, Math.max(0, decoded.duration - keepSec));
  const rendered = await offline.startRendering();

  const samples = rendered.getChannelData(0);
  const pcm = new Int16Array(samples.length);
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    pcm[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return bytesToBase64(new Uint8Array(pcm.buffer));
}

/** MediaRecorder 녹음(webm 등) 원본을 변환 없이 base64 로 — 서버(serve)가 ffmpeg 로 16kHz 변환한다. */
export async function blobToBase64(blob: Blob): Promise<string> {
  return bytesToBase64(new Uint8Array(await blob.arrayBuffer()));
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

// ───── 공통 헬퍼 ─────

function average(values: number[]): number | null {
  if (values.length === 0) return null;
  return values.reduce((a, b) => a + b, 0) / values.length;
}

function stdev(values: number[]): number | null {
  if (values.length < 2) return null;
  const avg = average(values)!;
  return Math.sqrt(values.reduce((sum, v) => sum + (v - avg) ** 2, 0) / values.length);
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

function round1(v: number): number {
  return Math.round(v * 10) / 10;
}

function round3(v: number): number {
  return Math.round(v * 1000) / 1000;
}
