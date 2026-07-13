# 프로필 · 스펙

프로필은 적합도 분석, 공고 비교, 면접 질문, 첨삭이 공유하는 원천 입력입니다. 현재 값만 저장하는 단순 폼에서 끝내지 않고 버전 스냅샷, AI 분석 결과, 문서 가져오기, 포트폴리오 파일까지 한 생명주기로 묶습니다.

## 사용자 기능

- 희망 직무·산업, 학력, 경력, 프로젝트, 스킬, 자격증, 어학, 자기소개와 선호 조건 저장
- 프로필 저장 시 `user_profile_version` 스냅샷 생성
- 버전 목록·상세 조회와 관리자 버전 조회
- 이력서/문서 분석 작업과 구조화 결과 가져오기
- 포트폴리오 파일 업로드, 기존 파일 연결, 목록, soft delete
- AI 요약, 스킬 추출, 완성도 진단
- 사용자가 AI 모델을 선택하고 결과 provider·모델·상태 확인

## 데이터 모델과 버전

`user_profile`은 사용자당 현재 행을 upsert합니다. 저장 전후에는 `ProfileMapper`가 버전 스냅샷을 보장하고, AI 분석은 실제로 사용한 `user_profile_version.id`를 기록합니다. 따라서 프로필을 수정한 뒤에도 "어떤 버전을 입력으로 이 결과가 나왔는가"를 추적할 수 있습니다.

`profile_ai_analysis`는 요약·스킬·완성도 결과와 quality warning, 모델, 상태를 저장합니다. 같은 분석을 화면 응답으로만 버리던 과거 구조와 달리, 최신 결과를 다시 읽고 분석 근거를 연결할 수 있습니다.

주요 API는 다음과 같습니다.

- `GET/PUT /api/profile`
- `GET /api/profile/versions`, `GET /api/profile/versions/{versionId}`
- `POST /api/profile/import`, `/import/analyze`, `GET /import/analyze/{jobId}`
- `POST /api/profile/portfolio-files/upload`, `/link`, `GET /portfolio-files`, `DELETE /portfolio-files/{fileId}`
- `POST /api/profile/ai/{summary|skills|completeness}?model=...`
- `GET /api/profile/ai-analysis`

## AI 품질 경계

LLM에는 구조화 계약을 요구하고 `ProfileAiJsonValidator`가 필수 기준, 점수 범위, JSON 형식을 검증합니다. 최종 가중 점수는 서버 `ProfileScoreCalculator`가 다시 계산해 모델이 점수 정책을 소유하지 못하게 합니다. 알려진 스킬 scanner와 resume post-processor가 누락·노이즈를 보완합니다.

직무군에 따라 목표 명확성, 경험 구체성, 성과 근거, 직무 역량 적합성, 문서 완성도, 개선 실행성의 가중치를 달리합니다. 서버 규칙을 고정하면 provider가 바뀌어도 점수 해석을 비교할 수 있습니다.

## 모델 선택과 fallback

파인튜닝 프로필 모델은 학습 서버와 설정이 있을 때 선택적으로 활성화됩니다. 기본 비활성이라는 점과 미구현이라는 말은 다릅니다. 사용자가 특정 모델을 요청하면 그 의도를 요청 계약에 담고, 자동 모드에서는 가용성과 검증 결과에 따라 Claude, OpenAI, 규칙 기반으로 내려갑니다.

응답과 영속 결과에 실제 모델·상태를 남겨 자체 모델 성공, 외부 provider fallback, 규칙 기반 결과를 구분합니다. 외부 LLM이 없어도 규칙 기반 완성도·스킬 결과로 화면이 중단되지 않습니다.

## 문서와 파일의 소유권

가져오기 작업은 원본 파일과 추출/구조화 상태를 분리합니다. 포트폴리오 파일은 `file_asset` 소유권을 확인하고 프로필에 연결하며, 삭제 시 다른 사용자의 파일이나 다른 도메인의 참조를 건드리지 않습니다. 분석·첨삭으로 넘길 때는 파일 경로가 아니라 소유권 검증된 ID를 사용합니다.

## 트레이드오프

- 모든 저장에 버전을 남기면 저장량은 늘지만 분석 재현성과 관리자 지원이 좋아집니다.
- AI 결과 영속화는 schema migration이 필요하지만, 같은 결과를 다시 표시하고 provenance를 감사할 수 있습니다.
- 자체 모델을 기본 강제하지 않아 GPU가 없는 개발 환경에서도 동작하지만, 배포 설정과 라이브 가용성을 별도로 표시해야 합니다.
- AI 분석은 최신 AI 데이터 동의를 확인한 뒤 실행합니다.

관련 문서: [적합도·취업 전략](./fit-analysis.md), [AI 통합](./ai-integration.md), [데이터 생명주기](./data-lifecycle.md)
