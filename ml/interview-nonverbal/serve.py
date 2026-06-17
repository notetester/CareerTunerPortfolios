"""
비언어 추론 FastAPI 서버 (ADR-006: 서버 Python 추론).  (TODO: 2026-06-19 연결)

음성/영상 원본 업로드 → extract_features 로 피처 추출 → LightGBM → 종합 인상 점수.
Spring 백엔드가 HTTP 로 호출한다(자체 모델 로컬 서빙, Ollama 와 같은 결).

엔드포인트(2026-06-19):
  POST /score/voice   : 오디오 파일 + 트랜스크립트(글자수/필러) + 라이브 latency → 인상 점수
  GET  /health        : 모델 로드 상태

흐름:
  1) train_nonverbal.py 산출 model.joblib + 정규화 통계 로드
  2) 업로드 오디오 16kHz 변환 → extract_voice_features → voice_feature_vector
  3) 정규화 → LightGBM predict → 0~100 종합 인상 점수 + 항목 점수
  4) 모델 미로드/실패 시 규칙 점수(voiceAnalysis computeVoiceScore 동등)로 폴백
"""

# from fastapi import FastAPI, UploadFile
# from extract_features import extract_voice_features, voice_feature_vector
# import joblib, uvicorn

if __name__ == "__main__":
    print("TODO(2026-06-19): FastAPI 추론 엔드포인트 (POST /score/voice) + 모델 로드 + 폴백")
