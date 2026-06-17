"""
비언어 면접 평가 — 특징 추출 (학습·추론 공용).

ADR-006: 추론도 학습과 같은 이 코드로 수행해 train/serve skew 를 차단한다.
음성 피처는 frontend `voiceAnalysis.ts` 의 수식·상수를 그대로 옮겼다(같은 정의 = 같은 숫자).

영상 피처(MediaPipe 표정/자세)는 2026-06-19 이후 추가 — 현재는 stub.
"""

import re

import numpy as np

try:
    import soundfile as sf
except ImportError:  # 스캐폴드 단계: 의존성 미설치 환경에서도 import 가능하게
    sf = None

# ── voiceAnalysis.ts 와 동일 상수 ──────────────────────────────
SAMPLE_INTERVAL_MS = 100
SPEECH_RMS_THRESHOLD = 0.015
PITCH_MIN_HZ = 60
PITCH_MAX_HZ = 400

# voiceAnalysis.ts FILLER_TOKENS 와 동일
FILLER_TOKENS = {
    "음", "어", "어어", "음음", "그", "저", "저기",
    "그니까", "그러니까", "이제", "막", "뭐랄까", "약간",
}


def detect_pitch_hz(frame: np.ndarray, sr: int):
    """시간영역 자기상관(ACF) 피치 추정. voiceAnalysis.ts detectPitchHz 복제. 신뢰도 낮으면 None."""
    n = len(frame)
    min_lag = int(sr / PITCH_MAX_HZ)
    max_lag = min(int(sr / PITCH_MIN_HZ), n - 1)
    zero_lag = float(np.sum(frame * frame))
    if zero_lag == 0:
        return None
    best_lag, best_corr = -1, 0.0
    for lag in range(min_lag, max_lag + 1):
        corr = float(np.sum(frame[: n - lag] * frame[lag:])) / zero_lag
        if corr > best_corr:
            best_corr, best_lag = corr, lag
    if best_lag < 0 or best_corr < 0.5:  # voiceAnalysis: corr < 0.5 면 잡음 → 버림
        return None
    return sr / best_lag


def count_fillers(user_lines) -> int:
    """트랜스크립트(지원자 발화 리스트) → 군말 개수. voiceAnalysis.ts countFillers 복제."""
    count = 0
    for line in user_lines:
        for raw in re.split(r"[\s,.!?…]+", line):
            if raw.strip() in FILLER_TOKENS:
                count += 1
    return count


def extract_voice_features(audio_path: str, user_transcript_chars: int, filler_count: int) -> dict:
    """
    오디오 파일 → voiceAnalysis.ts VoiceMetrics 와 동일한 음성 피처 dict.

    16kHz mono 입력을 권장한다(prepare/serve 단계에서 리샘플). 100ms 프레임마다
    RMS(성량)·발화여부·ACF 피치를 누적한다. 말속도/군말은 트랜스크립트 기반.

    NOTE: avgResponseLatencySec(질문→발화 지연)는 라이브에서만 측정되므로 녹음 파일만으론
          알 수 없다. 추론 시 프론트가 라이브 측정값을 함께 전달한다(serve 에서 합류).
    """
    if sf is None:
        raise RuntimeError("soundfile 미설치 — requirements.txt 설치 필요")
    audio, sr = sf.read(audio_path)
    if audio.ndim > 1:
        audio = audio.mean(axis=1)  # mono
    audio = audio.astype(np.float64)

    frame_len = int(sr * SAMPLE_INTERVAL_MS / 1000)
    speech_frames = 0
    rms_sum, rms_count = 0.0, 0
    pitches = []
    for start in range(0, len(audio) - frame_len, frame_len):
        frame = audio[start : start + frame_len]
        rms = float(np.sqrt(np.mean(frame * frame)))
        if rms >= SPEECH_RMS_THRESHOLD:
            speech_frames += 1
            rms_sum += rms
            rms_count += 1
            p = detect_pitch_hz(frame, sr)
            if p is not None:
                pitches.append(p)

    total_sec = len(audio) / sr if sr else 0.0
    speaking_sec = speech_frames * SAMPLE_INTERVAL_MS / 1000
    speaking_min = speaking_sec / 60
    total_min = total_sec / 60
    avg_pitch = float(np.mean(pitches)) if pitches else None
    pitch_std = float(np.std(pitches)) if len(pitches) >= 2 else None

    return {
        "totalSec": round(total_sec, 1),
        "speakingSec": round(speaking_sec, 1),
        "speechRateSpm": round(user_transcript_chars / speaking_min) if speaking_min > 0.05 else None,
        "fillerCount": filler_count,
        "fillerPerMin": round(filler_count / total_min, 1) if total_min > 0.05 else None,
        "avgPitchHz": round(avg_pitch) if avg_pitch else None,
        "pitchStdevHz": round(pitch_std) if pitch_std else None,
        "avgVolume": round(rms_sum / rms_count, 3) if rms_count else None,
    }


# LightGBM 입력 벡터 순서(학습·추론 공통). None 은 결측 → 학습 시 중앙값 대체 권장.
VOICE_FEATURE_KEYS = [
    "speechRateSpm", "fillerPerMin", "avgPitchHz", "pitchStdevHz", "avgVolume", "speakingSec",
]


def voice_feature_vector(metrics: dict) -> list:
    """피처 dict → LightGBM 입력 벡터(키 순서 고정)."""
    return [metrics.get(k) for k in VOICE_FEATURE_KEYS]


# ── 영상 피처 (2026-06-19 이후) ────────────────────────────────
def extract_visual_features(video_path: str) -> dict:
    raise NotImplementedError("영상 피처(MediaPipe 표정·자세)는 2026-06-19 이후 추가 예정")
