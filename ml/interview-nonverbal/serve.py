"""
비언어 추론 FastAPI 서버 (ADR-006: 서버 Python 추론).

음성 원본 업로드 → ffmpeg 16kHz mono 변환 → extract_voice_features → 점수.
LightGBM 모델(train_nonverbal.py 산출)이 있으면 종합 인상 점수를 모델로,
없으면 규칙 점수(compute_voice_score)로 폴백한다. Spring 백엔드가 HTTP 로 호출.

실행: python -m uvicorn serve:app --host 127.0.0.1 --port 8500
"""

import os
import subprocess
import tempfile

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile

from extract_features import (
    compute_voice_score,
    count_fillers,
    extract_voice_features,
    voice_feature_vector,
)

app = FastAPI(title="interview-nonverbal")

# LightGBM 모델 (있으면 로드, 없으면 규칙 폴백)
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
    """음성 → 인상 점수. transcript_chars/filler_count 는 프론트가 트랜스크립트에서 계산해 전달."""
    latency = None if latency_sec is None or latency_sec < 0 else latency_sec
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=os.path.splitext(audio.filename or "")[1] or ".bin")
    wav = None
    try:
        tmp.write(await audio.read())
        tmp.close()
        wav = _to_wav16k(tmp.name)
        metrics = extract_voice_features(wav, transcript_chars, filler_count)

        detail = compute_voice_score(metrics, latency)  # 규칙 점수(항목별) — 항상 제공
        overall = detail["overall"]
        source = "rule"
        if _MODEL is not None:
            vec = [v if v is not None else np.nan for v in voice_feature_vector(metrics)]
            pred = _MODEL.predict([vec])[0]
            overall = int(max(0, min(100, round(float(np.ravel(pred)[0])))))
            source = "lightgbm"
        return {"score": overall, "detail": detail, "metrics": metrics, "source": source}
    finally:
        for p in (tmp.name, wav):
            if p and os.path.exists(p):
                try:
                    os.remove(p)
                except OSError:
                    pass


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8500)
