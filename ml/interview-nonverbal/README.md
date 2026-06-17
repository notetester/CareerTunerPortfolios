# interview-nonverbal — 비언어 면접 평가 자체 모델 (음성/영상 → LightGBM)

D 파트 비언어 평가. **1단 특징추출(음성/영상) + 2단 LightGBM 종합 인상 점수** 구조.
텍스트 면접관(Qwen) 트랙과는 별개 트랙이다.

## 방식 (ADR-006 · 2026-06-18 결정)

- **추론도 학습과 같은 서버 Python 코드로 수행** → train/serve skew 원천 차단.
- 음성/영상 원본을 종료 후 서버로 전송(시작 전 **동의 모달**) → Python 이 피처 추출 + LightGBM 추론 → 종합 인상 점수.
- 피처 추출기 **한 벌(`extract_features.py`)** 을 학습·추론이 공유한다. (JS 추출/ONNX 온디바이스 안 씀)
- 서빙: 로컬 Python 추론 서비스(FastAPI)를 Spring 이 호출 — 자체 모델 로컬 서빙(Ollama)과 일관.
- 포트폴리오용. 라이선스: ChaLearn CC BY-NC 4.0 (비상업, 발표/포트폴리오 한정).

## 진행 상황

- [x] **2026-06-18**: 스캐폴드 + **음성 피처 추출**(`extract_features.py`, frontend `voiceAnalysis.ts` 수식 그대로 이관)
- [ ] **2026-06-19**: ③ ChaLearn 다운(`prepare_chalearn.py`) → 음성 LightGBM 학습(`train_nonverbal.py`) → ④ FastAPI 추론(`serve.py`) + Spring 연결 + Inworld 제거
- [ ] **(이후)**: 영상 피처(MediaPipe) 추가 → 영상+음성 통합 (아바타 화상면접)

## 파일

| 파일 | 상태 | 내용 |
| --- | --- | --- |
| `extract_features.py` | 음성 ✅ / 영상 stub | 학습·추론 공용 피처 추출. 음성은 `voiceAnalysis.ts`와 동일 정의 |
| `prepare_chalearn.py` | TODO 2026-06-19 | ChaLearn 서브셋 → 음성 피처 + 라벨 CSV |
| `train_nonverbal.py` | TODO 2026-06-19 | LightGBM(MultiOutputRegressor) 학습 |
| `serve.py` | TODO 2026-06-19 | FastAPI 추론 서버 (Spring 이 호출) |
| `requirements.txt` | ✅ | 의존성 |

## 음성 피처 (frontend `voiceAnalysis.ts` 와 동일 정의 — skew 방지)

`speechRateSpm`(말속도) · `fillerPerMin`(군말) · `avgPitchHz`/`pitchStdevHz`(톤) · `avgVolume`(성량) · `speakingSec`/`totalSec`.
같은 수식·상수(RMS 임계 0.015, ACF 피치 60~400Hz)를 써서 어느 쪽에서 뽑아도 같은 숫자가 나오게 한다.

## 데이터 경로 (★DB 안 거침)

ChaLearn 합성/공개 데이터는 DB 에 넣지 않고 파일로만 다룬다. 운영 team1_db 는 실사용 데이터 축적용.

## 학습/서빙 환경

- 학습: 로컬 노트북 **CPU** (LightGBM 은 GPU 불필요).
- 서빙: 로컬 FastAPI (추론) + Spring 이 HTTP 호출.
