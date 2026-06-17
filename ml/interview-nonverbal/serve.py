"""
비언어 추론 FastAPI 서버 (ADR-006: 서버 Python 추론).

음성 원본 → ffmpeg 16kHz mono → extract_voice_features → 점수.
LightGBM 모델(train_nonverbal.py 산출)이 있으면 종합 인상 점수를 모델로,
없으면 규칙 점수(compute_voice_score)로 폴백한다.

엔드포인트:
  POST /score/voice         : multipart (테스트/직접 업로드용)
  POST /score/voice-base64  : base64 JSON (Spring 백엔드 연동용)
  GET  /health

실행: python -m uvicorn serve:app --host 127.0.0.1 --port 8500
"""

import base64
import os
import subprocess
import tempfile

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel

from extract_features import (
    compute_voice_score,
    extract_voice_features,
    voice_feature_vector,
)

app = FastAPI(title="interview-nonverbal")

_MODEL = None
_MODEL_PATH = os.path.join(os.path.dirname(__file__), "model.joblib")
try:
    import joblib

    if os.path.exists(_MODEL_PATH):
        _MODEL = joblib.load(_MODEL_PATH)
except Exception:
    _MODEL = None


def _to_wav16k(src_path: str) -> str:
    """업로드 오디오(webm/wav 등)를 16kHz mono wav 로 변환 (ffmpeg)."""
    dst = src_path + ".16k.wav"
    subprocess.run(
        ["ffmpeg", "-y", "-i", src_path, "-ar", "16000", "-ac", "1", dst],
        check=True,
        capture_output=True,
    )
    return dst


def _score_from_wav(wav: str, transcript_chars: int, filler_count: int, latency_sec) -> dict:
    """16k wav → 피처 → (LightGBM 있으면 모델 / 없으면 규칙) 점수."""
    metrics = extract_voice_features(wav, transcript_chars, filler_count)
    detail = compute_voice_score(metrics, latency_sec)  # 항목별 규칙 점수(항상 제공)
    overall = detail["overall"]
    source = "rule"
    if _MODEL is not None:
        vec = [v if v is not None else np.nan for v in voice_feature_vector(metrics)]
        pred = _MODEL.predict([vec])[0]
        overall = int(max(0, min(100, round(float(np.ravel(pred)[0])))))
        source = "lightgbm"
    return {"score": overall, "detail": detail, "metrics": metrics, "source": source}


def _cleanup(*paths):
    for p in paths:
        if p and os.path.exists(p):
            try:
                os.remove(p)
            except OSError:
                pass


@app.get("/health")
def health():
    return {"status": "UP", "model_loaded": _MODEL is not None, "mode": "lightgbm" if _MODEL else "rule"}


@app.post("/score/voice")
async def score_voice(
    audio: UploadFile = File(...),
    transcript_chars: int = Form(0),
    filler_count: int = Form(0),
    latency_sec: float = Form(-1.0),
):
    """multipart 업로드 (테스트/직접용)."""
    latency = None if latency_sec is None or latency_sec < 0 else latency_sec
    suffix = os.path.splitext(audio.filename or "")[1] or ".bin"
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    wav = None
    try:
        tmp.write(await audio.read())
        tmp.close()
        wav = _to_wav16k(tmp.name)
        return _score_from_wav(wav, transcript_chars, filler_count, latency)
    finally:
        _cleanup(tmp.name, wav)


class VoiceB64Request(BaseModel):
    audio_base64: str
    audio_format: str = "webm"  # 업로드 원본 컨테이너(webm/wav 등) — 확장자로 ffmpeg 가 판별
    transcript_chars: int = 0
    filler_count: int = 0
    latency_sec: float = -1.0


@app.post("/score/voice-base64")
def score_voice_base64(req: VoiceB64Request):
    """base64 JSON (Spring 백엔드 연동용)."""
    latency = None if req.latency_sec < 0 else req.latency_sec
    raw = base64.b64decode(req.audio_base64)
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=f".{req.audio_format.lstrip('.')}")
    wav = None
    try:
        tmp.write(raw)
        tmp.close()
        wav = _to_wav16k(tmp.name)
        return _score_from_wav(wav, req.transcript_chars, req.filler_count, latency)
    finally:
        _cleanup(tmp.name, wav)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8500)
