@echo off
chcp 65001 > nul
REM 비언어 추론 서버(serve.py) 로컬 기동 — 백엔드(careertuner.interview.nonverbal.serve-url)가 호출.
REM 음성 모의면접 "정밀 분석(자체 AI)" 사용 시 이 서버가 떠 있어야 한다 (없으면 브라우저 지표로 폴백).
cd /d %~dp0
python -m uvicorn serve:app --host 127.0.0.1 --port 8500
