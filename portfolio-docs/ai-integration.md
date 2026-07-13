# AI 통합 · 자체 모델 · RAG

CareerTuner는 AI를 한 개의 전역 fallback 함수로 처리하지 않습니다. 공고 OCR, 적합도 설명, 면접 질문, 답변 평가, 첨삭, 커뮤니티 검열은 실패 비용과 정확성 요구가 다르므로 도메인별 provider 정책을 갖습니다. 공통으로 공유하는 것은 모델 선택 enum, 사용량·과금 계약, 구조화 결과 검증, provenance입니다.

## 왜 자체 LLM을 처음부터 만들지 않았나

기반 LLM을 처음부터 사전학습하려면 대규모 말뭉치, 여러 GPU, 긴 학습·정렬 기간이 필요합니다. 6인 프로젝트의 목표는 범용 언어 능력 자체를 재현하는 것이 아니라 취업 도메인의 입력·출력 형식과 판단 보조를 제품에 연결하는 것입니다. 그래서 공개 기반 모델의 언어 능력을 재사용하고 LoRA/QLoRA로 작은 수의 adapter 파라미터만 학습했습니다.

전체 파라미터 파인튜닝보다 LoRA를 택한 이유도 같습니다.

- RTX 4090 한 장에서 반복 실험할 수 있는 메모리·시간 범위
- 기반 가중치를 보존해 실험 실패 시 원복이 쉬움
- 도메인별 adapter와 버전을 독립 관리
- 3B 모델의 JSON 형식·한국어 설명 품질을 제품 검증기와 함께 개선

## 왜 RAG만 사용하지 않았나

RAG는 최신 근거를 주입하는 데 강하지만 출력 형식, 설명 스타일, task 수행 습관 자체를 안정화하지는 않습니다. CareerTuner는 LoRA와 RAG 중 하나를 고르지 않았습니다.

- LoRA/QLoRA: 반복되는 도메인 task와 구조화 응답 습관 학습
- RAG: 면접 rubric·질문 은행·기업 문서 등 검색 가능한 근거 주입
- 규칙/evidence gate: 점수와 필수 근거를 결정론적으로 통제

C 적합도 RAG 실험처럼 안정적 우위가 확인되지 않은 경로는 기본 비활성으로 남깁니다. "RAG가 있다"는 이유만으로 모든 기능에 강제하지 않습니다.

## 공통 요청과 사용자 선택

사용자는 `AUTO` 또는 제공된 모델을 고를 수 있습니다. AUTO는 해당 도메인의 정책에 따라 가용 provider로 내려가지만, 특정 모델로 strict 재실행한 요청은 성공 결과가 마음에 들지 않는다는 사용자의 의도이므로 임의로 다른 모델로 바꾸지 않습니다. 재시도 UI는 최초 선택을 기본값으로 유지하되 다른 모델 선택을 허용합니다.

결과에는 가능한 범위에서 다음을 남깁니다.

- 요청 모델과 실제 provider/model
- 성공·fallback·실패 상태
- 시도 경로와 검증 실패 이유
- 입력 revision 또는 profile version
- token/사용량, action key, 과금 정산 상태

## 도메인별 실제 경로

| 영역 | 자체 경로 | 자동 fallback·최종 안전망 | 중요한 경계 |
| --- | --- | --- | --- |
| A 프로필 | 선택적 profile fine-tuned service | Claude, OpenAI, 규칙 기반 | 기본 비활성, 서버 점수 재계산, 결과 영속 |
| B 공고/OCR | PaddleOCR worker, B 분석 LoRA | 단계별 Claude/OpenAI 또는 `self-rules-v1` | strict 재추출은 선택 provider 고정, 업로드·SSRF·OCR 검수 |
| C 적합도 | Qwen2.5-3B QLoRA + Ollama | Claude, OpenAI, mock/규칙 | 점수·지원 판단은 규칙 엔진 소유, E1/R3 evidence gate |
| D 면접 | Qwen2.5-3B LoRA, 자체 평가기 | task별 Claude/OpenAI/mock | 질문 생성 single-flight, 답변 평가는 별도 dispatcher, RAG 조건부 |
| E 첨삭 | versioned 3B/8B correction serving | Claude, OpenAI | 10-key schema, 실행 전 preview와 성공 후 멱등 정산 |
| F 커뮤니티 | Ollama 검열·태깅·챗봇 | 파이프라인별로 다름 | runtime tag만 확인된 모델을 직접 학습 성과로 과장하지 않음 |

## C 적합도 모델

Qwen2.5-3B-Instruct를 4-bit QLoRA로 학습하고 adapter merge, GGUF 변환, Ollama 등록, 평가와 백엔드 연결까지 재현합니다. 합성 distillation과 공개 가능한 직무·자격 근거를 사용하며 실제 사용자 이력서를 학습 데이터로 쓰지 않습니다.

LLM은 `fitScore`, `applyDecision`, 매칭·부족 역량을 마음대로 결정하지 않습니다. 규칙 엔진이 값을 계산하고 모델은 설명을 생성합니다. `ProfileQualityGuard`, skill alias 정규화, E1 grounding hard guard, R3 review-first evidence gate가 모델 출력과 입력 근거의 불일치를 차단합니다.

## D 면접과 RAG

질문 생성, 꼬리질문, 모범답안, 답변 평가를 하나로 묶지 않습니다. 질문 생성은 동일 세션·모델 동시 요청을 single-flight로 합치고 action key를 바꿔 다른 모델 재시도를 분리합니다. 답변 평가기는 생성 gateway와 별도 선택 경로를 갖습니다.

면접 지식 원문은 MySQL, 벡터는 Qdrant에 둡니다. RUBRIC, QUESTION_BANK, COMPANY, GENERAL 문서를 색인하고 조건이 맞을 때 top-k 문맥을 평가에 주입합니다. Qdrant 장애나 검색 결과 없음은 면접 전체를 중단하지 않으며, 색인 실패 문서는 `indexed=false`로 남겨 재색인합니다.

## E 첨삭과 과금

자기소개서, 면접 답변, 이력서, 포트폴리오 첨삭은 같은 10-key 구조화 계약을 사용합니다. 모델 응답은 schema validator를 통과해야 결과로 저장됩니다. 실행 전에는 예상 사용권·크레딧을 preview하고 사용자가 확인한 뒤 실행하며, 실제 성공 결과만 정산합니다. request/action key로 중복 첨삭과 중복 과금을 막습니다.

## 구조화 출력

- OpenAI: JSON schema를 API 요청에 전달
- Claude/Ollama: schema를 프롬프트에 포함하고 JSON span 추출·validator 적용
- 도구 호출 챗봇: 구조화 출력 옵션이 tool call과 충돌하는 경로는 프롬프트 지시와 controller parser로 분리

유효한 JSON이라는 사실만으로 도메인적으로 안전한 것은 아닙니다. enum, 필수 키, 점수 범위, 입력 근거 일치, 소유권을 서버가 다시 검사합니다.

## 사용량과 비용

`ai_usage_log`는 기능, 상태, 실제 모델, 토큰, 크레딧과 오류를 기록합니다. 과금 기능은 로그 한 줄만 믿지 않고 preview, 사용권/크레딧 선택, 결과 저장, 멱등 정산을 하나의 업무 흐름으로 묶습니다. 자체 GPU 호출도 비용이 0이라는 뜻이 아니라 외부 토큰 과금과 다른 비용 모델이라는 점을 구분합니다.

## 상태를 말하는 방법

학습 완료, 제품 연결, 코드 기본값, 배포 설정, 라이브 가용성은 서로 다릅니다. 자세한 현재 상태와 검증 기준은 [모델 증거 매트릭스](./model-evidence.md)에 정리했습니다.

관련 문서: [적합도](./fit-analysis.md), [면접](./interview.md), [첨삭](./correction.md), [보안](./security.md)
