# interview-nonverbal — 비언어 면접 평가 자체 모델 (음성/영상 → LightGBM)

D 파트 비언어 평가. **1단 특징추출(음성/영상) + 2단 LightGBM 종합 인상 점수** 구조.
텍스트 면접관(Qwen) 트랙과는 별개 트랙이다.

## 방식 (ADR-006 · 2026-06-18 결정)

- **추론도 학습과 같은 서버 Python 코드로 수행** → train/serve skew 원천 차단.
- 음성/영상 원본을 종료 후 서버로 전송(시작 화면 **동의 체크박스**) → Python 이 피처 추출 + LightGBM 추론 → 종합 인상 점수. 분석 후 원본은 폐기.
- 피처 추출기 **한 벌(`extract_features.py`)** 을 학습·추론이 공유한다. (JS 추출/ONNX 온디바이스 안 씀)
- 서빙: 로컬 Python 추론 서비스(FastAPI)를 Spring 이 호출 — 자체 모델 로컬 서빙(Ollama)과 일관.
- 모델 없으면 **규칙 점수 폴백**(`voiceAnalysis.ts`/`visualAnalysis.ts` 동일 수식) → 학습 전에도 점수가 나온다.
- **음성·영상 별 모델(late fusion, ADR-007)**: 음성=`model.joblib`, 영상=`visual_model.joblib`. 음성 모의면접은 음성 모델만, 아바타 화상면접은 둘 다 채점해 가중 결합. 모달 결측(음성만/영상만)에 강건.
- 포트폴리오용. 라이선스: ChaLearn CC BY-NC 4.0 (비상업, 발표/포트폴리오 한정).

## 진행 상황

- [x] **2026-06-18** 스캐폴드 + 음성 피처 추출(`extract_features.py`, frontend `voiceAnalysis.ts` 수식 그대로 이관)
- [x] **2026-06-18** ③ 학습 파이프라인(`prepare_chalearn.py` · `train_nonverbal.py`) 구현 + 더미로 train→serve lightgbm 경로 검증
- [x] **2026-06-18** ④ `serve.py`(규칙/lightgbm 폴백) + 백엔드 연결(`InterviewNonverbalClient` · `POST /voice-score`) + 프론트(`RealtimeInterviewTab` serve 전환 + 동의 체크박스)
- [ ] 사용자 chalearnlap 데이터 다운 → ③ 음성 LightGBM 학습(`model.joblib`)
- [x] **2026-06-18** 영상 트랙 코드(late fusion, ADR-007): `extract_visual_features`(MediaPipe blendshape/pose, `visualAnalysis.ts` 이관) + serve `/score/avatar-base64`(음성+영상 결합) + 백엔드 `avatar-score` + 프론트 `AvatarTab` serve 전환·동의 체크박스
- [x] **2026-06-18** 영상 LightGBM 학습 완료 — ChaLearn 2000개 → `visual_model.joblib`(836KB) **MAE 12.5**(음성 12.4와 동등). serve 가 자동 로드해 `source=lightgbm`. ★학원컴용 OneDrive 동기화는 `model.joblib` 패턴(git `*.joblib` 제외).

## 사용법

### serve 기동 (백엔드가 호출)

```bat
run-serve.bat     REM = python -m uvicorn serve:app --host 127.0.0.1 --port 8500
```

백엔드 `careertuner.interview.nonverbal.serve-url`(기본 `http://127.0.0.1:8500`)가 호출한다.
serve 가 떠 있으면 음성 모의면접 "정밀 분석(자체 AI)"이 동작하고, 없으면 브라우저 지표로 폴백한다.

### 학습 (chalearnlap 데이터 받은 뒤)

```bash
# 음성 트랙: mp4 → 음성 피처 + 라벨 CSV → model.joblib
python prepare_chalearn.py --features voice --video-dir <mp4폴더> --annotation annotation_training.pkl --out data/voice.csv [--limit 500]
python train_nonverbal.py --features voice --csv data/voice.csv      # → model.joblib

# 영상 트랙(아바타): mp4 → 영상 피처(MediaPipe) → visual_model.joblib
python prepare_chalearn.py --features visual --video-dir <mp4폴더> --annotation annotation_training.pkl --out data/visual.csv [--limit 500]
python train_nonverbal.py --features visual --csv data/visual.csv    # → visual_model.joblib
```

`serve.py` 가 같은 폴더의 `model.joblib`/`visual_model.joblib` 을 자동 로드한다(없으면 규칙 폴백).

## 파일

| 파일 | 상태 | 내용 |
| --- | --- | --- |
| `extract_features.py` | 음성·영상 ✅ | 학습·추론 공용 피처 추출 + 규칙 폴백. 음성=`voiceAnalysis.ts`, 영상=`visualAnalysis.ts` 와 동일 정의 |
| `prepare_chalearn.py` | ✅ | ChaLearn mp4 + annotation pkl → `--features voice\|visual` 피처 + `interview` 라벨 CSV |
| `train_nonverbal.py` | ✅ | CSV → LightGBM 회귀(0~100) → `--features` 별 `model.joblib`/`visual_model.joblib` |
| `serve.py` | ✅ | FastAPI 추론 (`/score/voice-base64` · `/score/avatar-base64`[음성+영상 late fusion] · `/transcribe`) |
| `run-serve.bat` | ✅ | serve 로컬 기동 |
| `requirements.txt` | ✅ | numpy/soundfile/lightgbm/scikit-learn/fastapi/uvicorn · 영상: mediapipe/opencv-python · joblib |

## 데이터 (★DB 안 거침)

- **라벨**: 공식 **chalearnlap `dataset/24`** annotation pickle — `{interview, Big5 5종} × {영상파일명: 0~1}`. SIGN UP 필요, 압축비번 페이지 공개.
  ⚠️ HF 미러 `yeray142/first-impressions-v2` 는 라벨 미업로드(video/transcription/age_group 만) → 라벨은 공식에서 받는다.
- **타겟**: `interview`(면접 호감도) × 100 = 0~100 인상 점수.
- ChaLearn 공개 데이터는 DB 에 넣지 않고 파일(`data/`)로만 다룬다. 운영 team1_db 는 실사용 데이터 축적용.

## 음성 피처 (frontend `voiceAnalysis.ts` 와 동일 정의 — skew 방지)

`speechRateSpm`(말속도) · `fillerPerMin`(군말) · `avgPitchHz`/`pitchStdevHz`(톤) · `avgVolume`(성량) · `speakingSec`/`totalSec`.
같은 수식·상수(RMS 임계 0.015, ACF 피치 60~400Hz)를 써서 어느 쪽에서 뽑아도 같은 숫자가 나오게 한다.

## 영상 피처 (frontend `visualAnalysis.ts` 와 동일 정의 — skew 방지)

표정 `avgSmile`/`avgBrowTension` · 시선 `gazeOffRatio` · 자세 `avgShoulderTilt`/`movementLevel` · 화면유지 `faceDetectedRatio`.
MediaPipe FaceLandmarker(blendshape) + PoseLandmarker 로 **400ms 간격** 샘플. JS 와 **같은 `.task` 모델**(`models/` 에 1회 자동 다운로드)·같은 blendshape 키·어깨 랜드마크 인덱스(11/12)를 써서 train/serve skew 를 0 으로 만든다.

## 학습/서빙 환경

- 학습: 로컬 노트북 **CPU** (LightGBM 은 GPU 불필요).
- 서빙: 로컬 FastAPI (추론) + Spring 이 HTTP 호출.
