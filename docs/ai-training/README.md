# A파트 프로필 AI 학습 진행 가이드

이 문서는 CareerTuner A파트의 프로필 AI 기능을 실제 학습 모델로 확장하기 위한 진행 순서입니다.
처음 배우는 사람 기준으로, 각 단계가 왜 필요한지와 어떤 파일을 만들면 되는지 함께 정리합니다.

## 0. 큰 그림

우리가 만들려는 것은 단순 챗봇이 아니라 `CareerTuner 전용 프로필 평가 모델`입니다.

```text
사용자 프로필 입력
-> AI 데이터 동의 확인
-> 서버가 직무군 분류
-> 직무군별 가중치 적용
-> 학습 모델이 분석 JSON 생성
-> 서버가 JSON 형식과 점수 범위 검증
-> 실패 시 규칙 기반 fallback
-> ai_usage_log에 모델명/토큰/상태 저장
-> 프론트 화면에 요약, 강점, 보완점, 추천 액션 표시
```

팀뷰어는 학습을 해주는 도구가 아닙니다. 팀뷰어는 원격 PC를 조작하는 도구입니다.
즉, 팀뷰어로 GPU가 있는 PC에 접속해서 아래 학습 명령을 실행하는 구조입니다.

## 1. 데이터 설계

학습 데이터는 `문제 + 모범답안` 형식입니다.

문제는 사용자 프로필입니다.

```json
{
  "desiredJob": "마케팅 콘텐츠 기획자",
  "desiredIndustry": "IT 서비스",
  "education": [],
  "career": [],
  "projects": [],
  "skills": [],
  "resumeText": "...",
  "selfIntro": "..."
}
```

모범답안은 CareerTuner가 원하는 AI 응답 JSON입니다.
현재 백엔드의 `ProfileAiJsonValidator`는 아래 형식을 기대합니다.

```json
{
  "summary": "프로필 전체 요약",
  "extractedSkills": ["추출된 역량"],
  "strengths": ["강점"],
  "gaps": ["보완점"],
  "recommendations": ["추천 액션"],
  "criterionScores": [
    {
      "criterion": "GOAL_CLARITY",
      "rawScore": 80,
      "evidence": "점수 근거",
      "improvement": "개선 방향"
    }
  ]
}
```

중요한 점은 `criterionScores` 안에 아래 6개 기준이 모두 들어가야 한다는 것입니다.

| 기준 | 쉬운 의미 |
| --- | --- |
| `GOAL_CLARITY` | 희망 직무와 목표가 명확한가 |
| `EXPERIENCE_SPECIFICITY` | 경험 설명이 구체적인가 |
| `ACHIEVEMENT_EVIDENCE` | 성과 수치나 결과 근거가 있는가 |
| `JOB_SKILL_ALIGNMENT` | 보유 역량이 희망 직무와 맞는가 |
| `DOCUMENT_CONSISTENCY` | 이력서, 자기소개, 프로젝트 내용이 서로 자연스럽게 연결되는가 |
| `IMPROVEMENT_READINESS` | 부족한 부분을 보완하기 쉬운 형태로 정리되어 있는가 |

직무군은 현재 서버 코드 기준으로 아래 값을 사용합니다.

| 직무군 | 쉬운 의미 |
| --- | --- |
| `DEVELOPMENT_DATA` | 개발, 데이터, 인프라, QA |
| `SALES_MARKETING` | 영업, 마케팅, 광고, CRM |
| `DESIGN_CONTENT` | 디자인, 콘텐츠, 영상, UX/UI |
| `BUSINESS_OFFICE` | 기획, 인사, 회계, 총무, 운영 |
| `HEALTHCARE_SERVICE` | 의료, 간호, 상담, 고객 서비스 |
| `EDUCATION_PUBLIC` | 교육, 강의, 행정, 공공 |
| `PRODUCTION_LOGISTICS` | 생산, 품질, 물류, 구매, 현장 운영 |
| `ENGINEERING_TECHNICAL` | 기계, 전기전자, 건설, 설비, 화학, 바이오, 환경안전, 연구개발 |
| `GENERAL` | 직무군 판단이 애매한 일반 프로필 |

## 2. 데이터 생성

처음에는 실제 사용자 데이터가 없으므로 직접 만든 더미 프로필로 시작합니다.

추천 수량은 아래와 같습니다.

| 목적 | 추천 데이터 수 |
| --- | --- |
| 구조 테스트 | 8개에서 20개 |
| 발표용 최소 시연 | 30개에서 50개 |
| 파인튜닝 1차 시도 | 100개 이상 |
| 조금 더 안정적인 실험 | 300개 이상 |
| 1차 QLoRA 기본 학습 | 500개 이상 |

처음부터 1000개를 만들 필요는 없습니다. 먼저 작은 데이터로 학습 흐름을 끝까지 성공시키는 것이 중요합니다.

데이터를 만들 때는 좋은 프로필만 만들면 안 됩니다.

- 잘 작성된 프로필
- 경력은 있지만 성과 수치가 없는 프로필
- 기술은 많지만 프로젝트 설명이 약한 프로필
- 직무 목표가 불명확한 프로필
- 신입 프로필
- 경력 전환자 프로필
- 비개발 직무 프로필
- 개발 직무 프로필

이렇게 다양한 케이스가 있어야 모델이 한쪽으로 치우치지 않습니다.

## 3. 학습 데이터 파일 형식

학습용 파일은 JSONL을 사용합니다.
JSONL은 `한 줄에 JSON 하나`가 들어가는 형식입니다.

```jsonl
{"messages":[{"role":"system","content":"..."},{"role":"user","content":"..."},{"role":"assistant","content":"..."}]}
{"messages":[{"role":"system","content":"..."},{"role":"user","content":"..."},{"role":"assistant","content":"..."}]}
```

이 저장소에는 시작용 샘플 파일을 만들어두었습니다.

- `profile_ai_seed_samples.jsonl`
- `profile_ai_training_samples_300.jsonl`
- `profile_ai_training_samples_500.jsonl`
- `generate_profile_ai_seed_samples.py`
- `generate_profile_ai_expanded_samples.py`
- `profile_ai_data_quality_checklist.md`
- `profile_ai_dataset_manual_review.md`
- `validate_profile_ai_dataset.py`

현재 `profile_ai_seed_samples.jsonl`은 30개 샘플로 구성되어 있습니다.
처음 학습 흐름을 검증하기 위한 최소 데이터셋이며, 실제 품질을 높이려면 사람이 점수와 추천 문장을 검수한 뒤 500개 이상으로 확장하는 것이 좋습니다.
현재 30개 샘플은 `profile_ai_dataset_manual_review.md` 기준으로 1차 수동 검수를 완료했습니다.

현재 실제 QLoRA 학습 기본 입력은 `profile_ai_training_samples_500.jsonl`입니다.
이 파일은 기존 30개 seed 데이터와 70개 1차 확장 데이터에 자동 생성 데이터를 더한 500개 데이터셋이며, 추가분은 직무/산업/경험/기술 조합이 겹치지 않도록 생성합니다.
검증 스크립트는 프로필 서명이 중복되면 실패하도록 되어 있어, 앞으로 데이터를 늘릴 때도 중복을 먼저 차단합니다.

샘플을 다시 생성하려면 아래 명령을 실행합니다.

```bash
python docs/ai-training/generate_profile_ai_seed_samples.py
```

500개 학습용 데이터셋을 다시 생성하려면 아래 명령을 실행합니다.

```bash
python docs/ai-training/generate_profile_ai_expanded_samples.py
```

## 4. 학습

학습 방식은 두 가지 중 하나로 선택합니다.

### 선택지 A. OpenAI 파인튜닝 API

초보자와 발표 안정성을 생각하면 가장 쉽습니다.

흐름은 다음과 같습니다.

```text
JSONL 데이터 준비
-> OpenAI에 파일 업로드
-> 파인튜닝 실행
-> fine-tuned model id 획득
-> Spring Boot에서 해당 model id로 호출
```

장점:

- 내 PC 사양이 낮아도 가능
- 모델 서버를 직접 운영하지 않아도 됨
- 백엔드 연동이 쉬움

단점:

- API 비용이 발생할 수 있음
- "내 서버에서 직접 모델을 돌린다"는 느낌은 약함

### 선택지 B. 오픈소스 모델 QLoRA 학습

팀뷰어로 GPU PC를 조작해서 직접 학습시키는 방식입니다.

흐름은 다음과 같습니다.

```text
GPU PC 접속
-> nvidia-smi로 GPU 확인
-> Python 가상환경 생성
-> transformers, datasets, peft, trl 설치
-> Qwen/Llama/Mistral 계열 모델 선택
-> QLoRA 학습
-> LoRA adapter 저장
-> vLLM/Ollama/FastAPI로 모델 서버 실행
```

장점:

- "직접 학습하고 서빙했다"는 발표 포인트가 강함
- 자체 모델 구조를 설명하기 좋음

단점:

- GPU, CUDA, Python 환경 문제가 생길 수 있음
- 서빙 서버를 따로 관리해야 함

## 5. 테스트

학습 데이터는 세 종류로 나누는 것이 좋습니다.

```text
train: 80%
validation: 10%
test: 10%
```

테스트할 때는 아래를 확인합니다.

- JSON 형식이 깨지지 않는가
- 필수 필드가 모두 있는가
- 6개 평가 기준이 모두 있는가
- `rawScore`가 0에서 100 사이인가
- 직무와 맞지 않는 추천을 하지 않는가
- 너무 일반적인 조언만 반복하지 않는가

자동 검증은 아래 명령으로 실행합니다.

```bash
python docs/ai-training/validate_profile_ai_dataset.py docs/ai-training/profile_ai_training_samples_500.jsonl
```

이 스크립트는 구조 오류는 실패로 처리하고, 데이터 수량/직무군 분포/점수 분포/개인정보 의심 패턴/추천 문장 품질은 경고로 보여줍니다.

## 6. 서빙

서빙은 학습된 모델을 웹서비스에서 호출할 수 있게 켜두는 과정입니다.

권장 구조:

```text
React :5173
Spring Boot :8080
AI Model Server :8000
```

Spring Boot가 직접 모델을 들고 있는 것이 아니라, AI 모델 서버에 HTTP 요청을 보내는 구조가 좋습니다.

```text
ProfileController
-> ProfileService
-> FineTunedProfileAiService
-> AI Model Server
-> ProfileAiJsonValidator
-> ai_usage_log 저장
```

## 7. 백엔드 연동

백엔드 연동 시 새로 만들 클래스 이름은 아래처럼 가는 것이 좋습니다.

```text
FineTunedProfileAiService implements ProfileAiService
```

이렇게 하면 기존 구조를 유지하면서 AI 구현체만 바꿀 수 있습니다.

현재 구조:

```text
ProfileAiService
├─ RuleBasedProfileAiService
└─ OpenAiProfileAiService
```

확장 후:

```text
ProfileAiService
├─ RuleBasedProfileAiService
├─ OpenAiProfileAiService
└─ FineTunedProfileAiService
```

## 8. 지금 단계의 다음 작업

현재는 1단계와 2단계의 시작 파일을 만들었습니다.

다음 순서는 아래와 같습니다.

1. `profile_ai_training_samples_500.jsonl` 형식 확인
2. 직무군별 샘플을 500개 이상으로 확장
3. 사람이 모범답안 품질 검수
4. 학습 방식 선택: OpenAI 파인튜닝 또는 오픈소스 QLoRA
5. 팀뷰어로 학습 PC 접속
6. 학습 실행
7. 테스트 결과 확인
8. 모델 서버 또는 OpenAI 모델 ID를 백엔드에 연결
