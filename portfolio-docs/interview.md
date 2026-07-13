# AI 가상 면접

지원 건(Application Case)에 묶인 회사·직무 컨텍스트를 바탕으로, 예상 질문 생성부터 답변 평가·꼬리 질문·최종 리포트까지 실제 면접 흐름을 재현하는 도메인입니다. 텍스트 면접뿐 아니라 실시간 음성 면접관(OpenAI Realtime), 아바타 화상 면접, 온디바이스 비언어(음성·영상) 분석을 함께 제공하며, 답변 채점은 멀티에이전트 자율 루프(Evaluator → Critic → 필요 시 재평가·추가 탐색)로 이뤄집니다.

채점의 신뢰도를 높이기 위해 **모범답안을 채점 기준 답안지로 삼는 계층 구조**, 자체 파인튜닝 LLM으로의 평가기 교체, Qdrant 기반 RAG 근거 주입, 학습 데이터 자동 적재까지 하나의 파이프라인으로 연결했습니다. 백엔드는 `com.careertuner.interview` 패키지(사용자 `controller`, 미디어 `media`, RAG `rag`, 실시간 `realtime`, 학습 `training`)에, 사용자 화면은 `frontend/src/features/interview` 에 위치합니다.

## 주요 기능

- **면접 모드 선택 · 세션 생성** — 기본/직무/인성/압박/자소서 기반/기업 맞춤 6개 모드
- **AI 예상 질문 생성** — 공고 원문 + 지원 건 컨텍스트 기반, 모드별 질문 수 정책
- **모범답안(답안지) 생성** — 질문 생성 직후 백그라운드 일괄 생성, 채점 기준으로 재사용
- **답변 제출 · 멀티에이전트 평가** — Evaluator/Critic 자율 루프로 점수·피드백·개선안 산출
- **꼬리(반박) 질문** — 압박 면접 전용, 직전 답변을 파고드는 추가 검증
- **실시간 음성 면접관** — OpenAI Realtime ephemeral key 발급, 준비된 질문으로 대화 진행
- **음성 면접 채점** — 전체 트랜스크립트를 질문별로 분해해 일괄 채점
- **비언어 분석** — 자체 추론 서버로 음성/영상 점수(late fusion), 자체 STT 전사
- **아바타 화상 면접** — 세션 토큰 발급, 온디바이스 녹화 → 점수만 저장
- **면접 리포트** — 카테고리별 점수 + 총점 + 직전 세션 대비 변화
- **에이전트 트레이스 타임라인** — 채점 단계별 판단 근거를 사용자에게 노출
- **관리자 기능** — RAG 지식베이스 관리·재색인, 학습 데이터 통계/추출/평가 하니스/파인튜닝
- **사용자 모델 선택** — 최초 질문 모델을 재시도 기본값으로 유지하되 다른 모델로 바꿔 다시 생성 가능
- **웹·모바일·데스크톱 공통 계약** — 모델 선택, 질문 single-flight, 답변 멱등키와 미디어 정리 규칙 공유

## 핵심 구현

### 세션 · 질문 · 모범답안 파이프라인

세션 생성은 `InterviewController.createSession` → `InterviewServiceImpl.createSession` 으로 이어지며, `ApplicationCaseAccessService.requireOwned` 로 지원 건 소유권을 검증한 뒤 `interview_session` 에 모드와 함께 저장합니다. 모드는 `normalizeMode` 에서 `BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY` 화이트리스트로 정규화합니다.

질문 생성(`generateQuestions`)은 공고 원문(`accessService.sourceText`)과 지원 건을 `InterviewOpenAiClient.generateQuestions` 에 넘겨 생성합니다. 압박 면접은 본질문 3개(이후 답변마다 반박 1개 자동 추가), 그 외 모드는 기본 6개입니다.

동일 세션과 모델의 동시 질문 생성은 같은 Promise/action key로 합쳐 중복 AI 호출을 막습니다. 실행 중 사용자가 다른 모델을 고르면 앞 요청 뒤 새 action key로 실행합니다. 앞 요청이 실패해도 바뀐 모델 의도는 폐기하지 않습니다. 이 계약은 React와 Qt `InterviewSession`에서 동일하게 적용됩니다.

모범답안은 **채점 기준 답안지**이지만 6개 일괄 생성이 느려 질문 표시를 막지 않도록, 트랜잭션 커밋 이후(`TransactionSynchronization.afterCommit`) `InterviewBackgroundExecutor` 로 비동기 생성합니다. 저장은 `updateQuestionModelAnswer` 매퍼가 `WHERE model_answer IS NULL OR = ''` 조건으로 **first-writer-wins** 를 보장하여, 백그라운드 일괄 생성과 평가 시점 지연 생성이 경쟁해도 **채점 기준 = 화면 표시 = 복기 값**이 항상 일치합니다.

관련 매퍼: `InterviewMapper.insertSession/insertQuestion/updateQuestionModelAnswer`, 테이블: `interview_session`, `interview_question`.

### 멀티에이전트 자율 평가 루프

답변 제출(`submitAnswer`)은 `InterviewAgentOrchestrator.evaluateAnswer` 로 위임합니다. 정책(`AgentPolicy`)이 현재 상태를 보고 다음 액션을 고르는 루프이며, `careertuner.interview.agent.max-turns`(기본 6) 까지 반복합니다.

- **RETRIEVE** — RAG 지식베이스에서 근거를 가져와 프롬프트에 주입(best-effort)
- **EVALUATE** — 답변을 채점(0~100 점수·피드백·개선안)
- **CRITIC** — 채점을 적대적으로 재검증하고 점수 조정(`유지`/`조정`)
- **REEVALUATE** — Critic과 원 채점의 차이가 임계(`REEVAL_THRESHOLD=20`) 이상이면 1회 재평가 후 두 값의 중간으로 수렴
- **PROBE** — 답변이 약하면(`WEAK_ANSWER_LEN=40`, `WEAK_SCORE=50`) 꼬리 질문으로 추가 검증 권장
- **FINISH** — 더 할 일이 없으면 종료

정책은 두 가지를 병행합니다. `RulePolicy`(운영 기본값)는 상태를 규칙으로 판단하고, `LlmPolicy`(시연 모드, `agent.planner=llm`)는 매 턴 LLM이 가용 액션 중 하나를 고릅니다. 선택지가 하나뿐이면 LLM을 호출하지 않고(비용 절감), LLM 호출이 실패하면 자동으로 규칙 정책으로 폴백합니다. 어떤 액션이 실패해도 면접 흐름을 끊지 않고 원 결과로 폴백하는 것이 설계 원칙입니다.

각 단계는 `InterviewMapper.insertAgentStep` 으로 `interview_agent_step` 테이블에 trace(에이전트·액션·상태·요약·소요시간·상세 JSON)로 기록되어, 사용자 화면 `AgentTimeline.tsx` 에서 채점 판단 과정을 그대로 확인할 수 있습니다.

### 평가기 교체 (OpenAI ↔ 자체 파인튜닝 모델)

오케스트레이터는 구체 평가기를 직접 알지 않고 `InterviewEvaluatorProvider` 만 바라봅니다. `careertuner.interview.eval.provider=oss` 이고 자체 평가기 빈이 있으면 자체 모델을, 아니면 OpenAI를 씁니다.

자체 모델(`OssAnswerEvaluator`)은 OpenAI 호환 `/v1/chat/completions` 엔드포인트로 호출하는 vLLM/TGI 서빙 모델(예: Qwen 계열 + LoRA)입니다. 소형 모델이 JSON 앞뒤에 붙이는 잡설은 `OssLlmGateway.extractJsonSpan` 으로 제거합니다. 자체 모델이 미서빙(예: GPU 미접근)이면 `FallbackInterviewAnswerEvaluator` 가 이를 감싸 **Claude → OpenAI → Mock** 으로 폴백하므로, 자체 서버가 없어도 채점이 통째로 실패하지 않습니다.

### 실시간 음성 면접관 (OpenAI Realtime)

`InterviewRealtimeService.createSession` 은 회사/직무/모드/준비된 질문으로 면접관 `instructions`(한국어 진행 규칙 + 본질문 최대 6개)를 구성하고, OpenAI Realtime `client_secrets` 엔드포인트로 단기 ephemeral key를 발급받아 프런트에 내려줍니다. API 키는 서버에만 보관하고 프런트로 노출하지 않으며, 응답에는 모델·voice·WebRTC 접속 URL이 함께 담깁니다. 모델·voice는 `careertuner.interview.realtime.*` 에서 설정합니다.

대화 종료 후에는 `scoreVoiceTranscript` 로 전체 트랜스크립트를 `[{role,text}]` → "면접관/지원자" 대화 텍스트로 변환한 뒤, 준비된 본질문(꼬리 제외)과 저장된 모범답안을 만점 기준으로 넘겨 질문별로 일괄 채점하고 `interview_answer` 에 저장합니다(엔드포인트 `POST /api/interview/sessions/{id}/score-voice`).

### RAG 지식베이스 (Qdrant)

`InterviewKnowledgeService` 는 원본을 MySQL(`interview_knowledge`)에, 벡터를 Qdrant에 저장합니다. 문서 추가/수정 시 임베딩(`EmbeddingClient`) 후 `QdrantClient.upsert` 로 색인하며, 문서 종류는 `RUBRIC/QUESTION_BANK/COMPANY/GENERAL` 로 정규화합니다. 색인은 **best-effort** — Qdrant 미가동 시 `indexed=false` 로 남고 이후 재색인이 가능하며, 채점 시 `retrieveContext` 도 비활성/장애/무결과면 빈 컨텍스트를 반환해 면접 평가 흐름을 끊지 않습니다.

관리자는 `POST/GET/PUT/DELETE /api/admin/interview/knowledge` 와 `POST .../reindex`(전체 재색인)로 지식을 관리합니다(`InterviewKnowledgeController`, 화면 `AdminInterviewKnowledgePage.tsx`).

### 비언어(음성·영상) 분석 · 미디어

`InterviewNonverbalClient` 는 자체 추론 서버(Python FastAPI, `ml/interview-nonverbal/serve.py`)를 호출합니다. 원본 음성/영상을 base64로 보내면 서버가 16kHz 변환·피처 추출 후 점수를 산출합니다(LightGBM 모델이 있으면 모델, 없으면 규칙 폴백). 제공 기능은 음성 점수(`/score/voice-base64`), 아바타 음성+영상 late fusion 점수(`/score/avatar-base64`), 자체 STT 전사(`/transcribe`, OpenAI Whisper API 대체)입니다.

동의와 소유권이 확인된 원본은 `file_asset`에 연결하고 인증된 다운로드 경로만 사용합니다. 제출되지 않은 임시 업로드, 교체된 답변 미디어, 로컬 녹화 파일은 정리해 고아 데이터를 남기지 않습니다. 온디바이스·서버 분석 결과(트랜스크립트 + 지표 + 점수 JSON)는 `interview_media_analysis`에 별도로 저장합니다. `GET /api/interview/media/capabilities`로 프런트가 키 노출 없이 기능 활성 여부를 미리 판단합니다.

### 면접 리포트 · 학습 데이터

`getReport` 는 질문·답변으로 트랜스크립트를 구성해 `generateReport` 로 카테고리별 점수와 요약 피드백을 받고, 직전 세션 총점(`findLatestScoredSessionScore`)과 함께 반환합니다. 생성된 리포트는 `interview_session.report` 에 JSON으로 캐시되어 재조회 시 재생성하지 않습니다.

한편 평가가 끝난 답변은 `InterviewTrainingSample` 로 `insert` 되어(질문·답변·점수·피드백·RAG 사용 여부·모델 id) 파인튜닝/평가 하니스의 원천이 됩니다(best-effort — 적재 실패가 채점을 막지 않음). 관리자는 `/api/admin/interview/training` 에서 통계 조회, JSONL 추출(파인튜닝 입력 포맷), 평가 하니스 실행, 파인튜닝 트리거를 사용합니다(`InterviewTrainingController`). 학습·서빙 스크립트는 `ml/interview-finetune`(`finetune_lora.py`, `merge_and_export.py`, `serve_vllm.sh` 등)에 있습니다.

## 설계 결정과 트레이드오프

- **모범답안을 채점 기준으로 통일** — 텍스트/음성 채점이 같은 답안지를 만점 기준으로 삼도록 `model_answer` 를 first-writer-wins로 고정했습니다. 화면에 보여준 답안과 채점 기준이 달라지는 문제를 원천 차단하는 대신, 백그라운드 생성·지연 생성·경쟁 저장을 매퍼 조건으로 조율하는 복잡도를 감수했습니다.
- **모든 외부 의존은 폴백 가능하게** — RAG(Qdrant), 자체 평가 모델(vLLM), 비언어 서버, LLM 게이트웨이가 없거나 실패해도 면접이 끊기지 않습니다. Evaluator 실패만 흐름을 중단하고, 나머지는 원 결과 유지 또는 빈 컨텍스트로 진행합니다.
- **규칙 정책 기본 / LLM Planner는 시연** — LLM이 매 턴 액션을 고르는 방식은 데모 가치가 크지만 비용·지연이 큽니다. 운영 기본값은 규칙 정책으로 두고, `agent.planner=llm` 로 전환 시에도 선택지가 하나면 LLM을 호출하지 않도록 최적화했습니다.
- **미디어 수명주기 분리** — 원본 보존, 분석 지표, 임시 업로드의 책임을 나누고 소유권·동의·정리 규칙을 각각 적용합니다.
- **평가기 추상화** — 채점 로직을 `InterviewAnswerEvaluator` 인터페이스로 두어 OpenAI와 자체 파인튜닝 모델을 설정만으로 교체할 수 있습니다. 자체 모델 서빙 여부와 무관하게 제품이 동작하도록 폴백 래퍼로 감쌌습니다.

## 데이터 · 연동

- **테이블** — `interview_session`, `interview_question`(모범답안 `model_answer` 포함), `interview_answer`, `interview_agent_step`(에이전트 trace), `interview_media_analysis`, `interview_knowledge`, `interview_training_sample`, `file_asset`, `ai_usage_log`(공통)
- **AI provider** — 답변 평가/질문 생성/리포트: OpenAI 또는 자체 파인튜닝 모델(vLLM/TGI OpenAI 호환), 폴백 체인 Claude → OpenAI → Mock
- **실시간** — OpenAI Realtime(`client_secrets` ephemeral key + WebRTC)
- **벡터 DB** — Qdrant(`interview_knowledge` 컬렉션, 임베딩 `text-embedding-3-small`, dim 1536, top-k 4)
- **자체 추론 서버** — Python FastAPI(음성/영상 점수, STT), LightGBM 비언어 모델
- **AI 사용량** — 모든 AI 호출을 `InterviewAiUsageLogService` 로 `ai_usage_log` 에 성공/실패 기록(기능 타입: 질문 생성/평가/Critic/Planner/꼬리질문/모범답안/리포트/음성 채점)

## 사용 기술

- **백엔드** — Spring Boot 4 / Java 21 / MyBatis / MySQL 8, REST `/api/interview/**` · `/api/admin/interview/**`, `ApiResponse<T>` envelope, `@Transactional` + `TransactionSynchronization.afterCommit` 비동기, Java `HttpClient` 외부 연동
- **AI/ML** — OpenAI(Chat·Realtime·Embeddings), 자체 파인튜닝 LLM(LoRA / vLLM / Ollama), Qdrant 벡터 검색, LightGBM 비언어 분석, faster-whisper STT
- **프런트엔드** — React 19 / Vite 8 / TypeScript / Tailwind v4, 모드별 탭 UI(`features/interview/components`), 에이전트 타임라인·리포트 시각화, WebRTC 실시간 음성, 온디바이스 녹음/녹화(`voiceAnalysis`/`visualAnalysis` 훅), mock 모드 지원
