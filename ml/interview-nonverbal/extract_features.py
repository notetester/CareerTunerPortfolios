"""
비언어 면접 평가 — 특징 추출 (학습·추론 공용).

ADR-006: 추론도 학습과 같은 이 코드로 수행해 train/serve skew 를 차단한다.
음성 피처는 frontend `voiceAnalysis.ts` 의 수식·상수를 그대로 옮겼다(같은 정의 = 같은 숫자).

영상 피처(MediaPipe 표정/자세)는 2026-06-19 이후 추가 — 현재는 stub.
"""

import math
import os
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


# LightGBM 입력 벡터 순서(학습·추론 공통). None 은 결측 → LightGBM 기본 결측 분기.
# 음향 피처만 사용 — speechRate/filler 는 언어 의존이라 학습(ChaLearn 영어)과 추론(한국어)의
# 분포가 달라 train/serve skew 가 된다(영어 군말 토큰은 0). 이 둘은 규칙 점수(compute_voice_score)
# 에서 한국어 metrics 로 그대로 평가하고, 모델 입력에서는 뺀다.
VOICE_FEATURE_KEYS = [
    "avgPitchHz", "pitchStdevHz", "avgVolume", "speakingSec",
]


def voice_feature_vector(metrics: dict) -> list:
    """피처 dict → LightGBM 입력 벡터(키 순서 고정)."""
    return [metrics.get(k) for k in VOICE_FEATURE_KEYS]


# ── 규칙 점수 (LightGBM 모델 없을 때 폴백) ─────────────────────
# voiceAnalysis.ts computeVoiceScore 복제. Inworld(profile) 보정은 제외(자체화로 제거).
NEUTRAL = 70


def _band(v, hard_min, ideal_min, ideal_max, hard_max):
    if ideal_min <= v <= ideal_max:
        return 100
    if v <= hard_min or v >= hard_max:
        return 20
    if v < ideal_min:
        return round(20 + 80 * (v - hard_min) / (ideal_min - hard_min))
    return round(20 + 80 * (hard_max - v) / (hard_max - ideal_max))


def _clamp(v, lo, hi):
    return max(lo, min(hi, v))


def compute_voice_score(m: dict, latency_sec=None) -> dict:
    """규칙 기반 항목 점수(0~100). voiceAnalysis.ts computeVoiceScore 와 동일 기준."""
    pace = NEUTRAL if m.get("speechRateSpm") is None else _band(m["speechRateSpm"], 120, 250, 400, 550)
    fluency = NEUTRAL if m.get("fillerPerMin") is None else _clamp(round(100 - m["fillerPerMin"] * 10), 20, 100)
    stability = NEUTRAL
    if m.get("avgPitchHz") and m.get("pitchStdevHz") and m["avgPitchHz"] > 0:
        stability = _band(m["pitchStdevHz"] / m["avgPitchHz"], 0.02, 0.1, 0.35, 0.7)
    confidence = NEUTRAL if m.get("avgVolume") is None else _band(m["avgVolume"], 0.005, 0.03, 0.15, 0.4)
    responsiveness = NEUTRAL if latency_sec is None else _clamp(round(100 - max(0, latency_sec - 1.5) * 10.8), 30, 100)
    overall = round(pace * 0.2 + fluency * 0.25 + stability * 0.2 + confidence * 0.2 + responsiveness * 0.15)
    return {
        "pace": pace, "fluency": fluency, "stability": stability,
        "confidence": confidence, "responsiveness": responsiveness, "overall": overall,
    }


# ── 영상 피처 (MediaPipe — visualAnalysis.ts 1:1 이관) ─────────
# JS(visualAnalysis.ts)와 같은 .task 모델 + 같은 수식·상수 → train/serve skew 0.
# (음성과 별 모델 = late fusion: 영상 LightGBM 은 visual_model.joblib, 음성은 model.joblib)
VISUAL_SAMPLE_INTERVAL_MS = 400  # visualAnalysis.ts SAMPLE_INTERVAL_MS 와 동일
GAZE_OFF_THRESHOLD = 0.55        # visualAnalysis.ts GAZE_OFF_THRESHOLD 와 동일

# JS 가 CDN 에서 쓰는 .task 와 동일 모델(같은 가중치여야 skew 0). visualAnalysis.ts:9-12 참조.
FACE_MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/"
    "face_landmarker/float16/1/face_landmarker.task"
)
POSE_MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
    "pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
)
_MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")

# LightGBM 입력 벡터 순서(학습·추론 공통). 음향 VOICE_FEATURE_KEYS 와 동일하게 키 순서 고정.
VISUAL_FEATURE_KEYS = [
    "avgSmile", "avgBrowTension", "gazeOffRatio",
    "avgShoulderTilt", "movementLevel", "faceDetectedRatio",
]


def visual_feature_vector(metrics: dict) -> list:
    """영상 피처 dict → LightGBM 입력 벡터(키 순서 고정)."""
    return [metrics.get(k) for k in VISUAL_FEATURE_KEYS]


def _ensure_model(url: str) -> str:
    """models/ 에 .task 가 없으면 1회 다운로드(JS CDN 과 동일 파일) 후 경로 반환."""
    import urllib.request

    os.makedirs(_MODEL_DIR, exist_ok=True)
    path = os.path.join(_MODEL_DIR, url.rsplit("/", 1)[-1])
    if not os.path.exists(path):
        urllib.request.urlretrieve(url, path)
    return path


def extract_visual_features(video_path: str) -> dict:
    """
    면접 영상(webm/mp4) → visualAnalysis.ts VisualMetrics 와 동일한 영상 피처 dict.

    400ms 간격으로 프레임을 샘플해 FaceLandmarker(blendshape)·PoseLandmarker 로
    표정/시선/자세를 누적한다. blendshape 키·어깨 랜드마크 인덱스(11/12)·수식은
    visualAnalysis.ts(sample/finish)와 동일하다. mediapipe/opencv 는 무거우므로 지연 import.
    """
    import cv2  # noqa: PLC0415
    import mediapipe as mp  # noqa: PLC0415

    base_options = mp.tasks.BaseOptions
    running_mode = mp.tasks.vision.RunningMode
    face_opts = mp.tasks.vision.FaceLandmarkerOptions(
        base_options=base_options(model_asset_path=_ensure_model(FACE_MODEL_URL)),
        running_mode=running_mode.VIDEO,
        num_faces=1,
        output_face_blendshapes=True,
    )
    pose_opts = mp.tasks.vision.PoseLandmarkerOptions(
        base_options=base_options(model_asset_path=_ensure_model(POSE_MODEL_URL)),
        running_mode=running_mode.VIDEO,
        num_poses=1,
    )

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"영상을 열 수 없음: {video_path}")

    samples = face_frames = pose_frames = gaze_off_frames = 0
    smile_sum = brow_sum = shoulder_tilt_sum = movement_sum = 0.0
    movement_count = 0
    last_mid = None

    with mp.tasks.vision.FaceLandmarker.create_from_options(face_opts) as face, \
            mp.tasks.vision.PoseLandmarker.create_from_options(pose_opts) as pose:
        next_sample_ms = 0.0
        prev_ts = -1
        while True:
            ok, frame_bgr = cap.read()
            if not ok:
                break
            pos_ms = cap.get(cv2.CAP_PROP_POS_MSEC)
            if pos_ms + 1e-6 < next_sample_ms:
                continue  # 400ms 간격까지 프레임 건너뜀
            next_sample_ms = pos_ms + VISUAL_SAMPLE_INTERVAL_MS
            ts = int(pos_ms)
            if ts <= prev_ts:  # detect_for_video 는 단조증가 ts 요구
                ts = prev_ts + 1
            prev_ts = ts

            rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            samples += 1

            face_res = face.detect_for_video(mp_image, ts)
            shapes = face_res.face_blendshapes[0] if face_res.face_blendshapes else None
            if shapes:
                face_frames += 1
                s = {c.category_name: c.score for c in shapes}
                g = s.get  # blendshape 점수 조회(없으면 0)
                smile_sum += (g("mouthSmileLeft", 0.0) + g("mouthSmileRight", 0.0)) / 2
                brow_sum += (g("browDownLeft", 0.0) + g("browDownRight", 0.0)) / 2
                gaze_off = max(
                    (g("eyeLookOutLeft", 0.0) + g("eyeLookInRight", 0.0)) / 2,
                    (g("eyeLookInLeft", 0.0) + g("eyeLookOutRight", 0.0)) / 2,
                    (g("eyeLookUpLeft", 0.0) + g("eyeLookUpRight", 0.0)) / 2,
                    (g("eyeLookDownLeft", 0.0) + g("eyeLookDownRight", 0.0)) / 2,
                )
                if gaze_off >= GAZE_OFF_THRESHOLD:
                    gaze_off_frames += 1

            pose_res = pose.detect_for_video(mp_image, ts)
            lm = pose_res.pose_landmarks[0] if pose_res.pose_landmarks else None
            if lm and len(lm) > 12:
                pose_frames += 1
                left, right = lm[11], lm[12]  # BlazePose 표준: 11=좌어깨, 12=우어깨
                shoulder_tilt_sum += abs(left.y - right.y)
                mid = ((left.x + right.x) / 2, (left.y + right.y) / 2)
                if last_mid is not None:
                    movement_sum += math.hypot(mid[0] - last_mid[0], mid[1] - last_mid[1])
                    movement_count += 1
                last_mid = mid

    cap.release()
    if samples == 0:  # 프레임 0 = 영상 분석 불가 → 전 피처 결측(JS finish() null 과 동치)
        return {k: None for k in VISUAL_FEATURE_KEYS} | {"samples": 0}

    return {
        "samples": samples,
        "faceDetectedRatio": round(face_frames / samples, 2),
        "avgSmile": round(smile_sum / face_frames, 2) if face_frames else 0.0,
        "avgBrowTension": round(brow_sum / face_frames, 2) if face_frames else 0.0,
        "gazeOffRatio": round(gaze_off_frames / face_frames, 2) if face_frames else 0.0,
        "avgShoulderTilt": round(shoulder_tilt_sum / pose_frames, 2) if pose_frames else 0.0,
        "movementLevel": round(movement_sum / movement_count, 3) if movement_count else 0.0,
    }


# ── 영상 규칙 점수 (LightGBM 없을 때 폴백) ─────────────────────
# visualAnalysis.ts computeVisualScore 복제(상수·가중치 동일).
def compute_visual_score(m: dict) -> dict:
    """규칙 기반 영상 항목 점수(0~100). visualAnalysis.ts computeVisualScore 와 동일 기준."""
    expression = 60
    expression += round(min(m.get("avgSmile") or 0, 0.3) * 100)    # 미소 가산(최대 +30)
    expression -= round(min(m.get("avgBrowTension") or 0, 0.4) * 75)  # 미간 긴장 감점(최대 -30)
    expression = _clamp(expression, 0, 100)

    gaze = _clamp(round(100 - (m.get("gazeOffRatio") or 0) * 140), 30, 100)

    posture = 100
    posture -= round(max(0, (m.get("avgShoulderTilt") or 0) - 0.02) * 800)
    posture -= round(max(0, (m.get("movementLevel") or 0) - 0.004) * 4000)
    posture = _clamp(posture, 20, 100)

    presence = _clamp(round((m.get("faceDetectedRatio") or 0) * 100), 0, 100)

    overall = round(expression * 0.3 + gaze * 0.3 + posture * 0.25 + presence * 0.15)
    return {
        "expression": expression, "gaze": gaze, "posture": posture,
        "presence": presence, "overall": overall,
    }
