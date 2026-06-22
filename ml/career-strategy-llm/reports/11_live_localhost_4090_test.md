# 4090 localhost 라이브 테스트 (C OSS 적합도 연동)

> 목표: 공유 4090 PC **내부에서** Spring 백엔드가 **localhost Ollama**(`careertuner-c-career-strategy-3b`)를 호출해
> C 적합도 설명을 자체모델로 생성하는지만 검증한다. **Tailscale/LAN/포트개방은 배제**(원격 경로 결정 전 단계).

## 전제
- 4090 PC에 Ollama 모델 `careertuner-c-career-strategy-3b` 이미 등록(서빙 완료).
- 백엔드도 **같은 4090 PC에서** 실행 → base-url = `http://localhost:11434/v1`.
- 브랜치 = `feature/c-fit-oss-llm-integration`(이 PR).

## 1. env 설정 (PowerShell)
```powershell
$env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
$env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://localhost:11434/v1"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
$env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
```
> 이 env 는 yaml 없이도 Spring `@ConfigurationProperties(careertuner.analysis.ai)`에 바인딩된다.
> ⚠️ `max-tokens`를 **1024 미만으로 낮추지 말 것** — 512는 truncation으로 JSON parse 실패(4090 검증).

## 2. Ollama 확인 (백엔드 기동 전)
```bat
ollama list
curl http://localhost:11434/v1/models
```
PowerShell에서 `curl` 별칭 문제가 있으면:
```powershell
Invoke-RestMethod http://localhost:11434/v1/models
```
응답 목록에 `careertuner-c-career-strategy-3b` 가 보여야 한다.

## 3. ★ DB 스키마 drift 선행 확인 (백엔드 부팅 전제)
백엔드 부팅 시 F 검열 빈 `ModerationSettingService.init()`(@PostConstruct)이 `ai_moderation_setting.sanction_threshold` 를
조회한다. **이 컬럼이 DB에 없으면 부팅 자체가 실패**한다(BadSqlGrammar). 이는 **C OSS 문제가 아니라 DB 스키마 drift**다.

공식 해결 경로(F 코드 수정 아님 — 마이그레이션 SQL 실행):
```bat
:: 사용 중인 DB 에 패치 적용 (sanction_threshold / block_days 컬럼 추가, 재실행 안전 가드 포함)
mysql -h <DB_HOST> -u <DB_USER> -p <DB_NAME> < backend\src\main\resources\db\patches\20260619_f_moderation_sanction.sql
```
- 기본 DB 는 `application.yaml` 의 `team1_db @ localhost`. **공유 DB 변경은 팀 합의 필요**(패치 주석 명시). 단독 테스트는 로컬 MySQL 권장.
- 적용 후 확인: `SHOW COLUMNS FROM ai_moderation_setting LIKE 'sanction_threshold';`
- ⚠️ 임의로 F 도메인 Java 코드를 수정하지 말 것. 누락 시 **오류 위치·누락 컬럼·DB명·부팅 중단 여부만 보고**.

## 4. 백엔드 실행
```bat
cd backend
.\gradlew.bat bootRun
```
(또는 repo 루트에서 `backend\gradlew.bat -p backend bootRun`.)

## 5. 라이브 호출 확인 기준
백엔드가 뜨면 적합도 분석 API 또는 프런트 화면에서 적합도 분석을 실행하고 확인한다.
```text
1. provider=oss 로 동작(로그)
2. CareerAnalysisOssClient 가 localhost:11434/v1/chat/completions 호출
3. Ollama 응답 JSON parse 성공
4. 금지키(fitScore/score/applyDecision/decision)가 최종 결과에 섞이지 않음
5. fitScore/applyDecision 은 서버 규칙엔진 값
6. strategy(설명) 는 자체모델 출력 반영
7. 화면이 깨지지 않음
8. OSS 실패(예: Ollama 종료) 시 OpenAI 또는 Mock 으로 폴백
```

## 6. 실패 시 유형 분류
```text
A. Ollama 모델 없음        → ollama list 확인
B. localhost:11434 접근 실패 → Ollama 서비스 기동 확인
C. JSON parse 실패         → 응답 본문 로그
D. max token truncation    → MAX_TOKENS 1280 이상인지
E. Spring 설정 바인딩 실패  → env 이름/철자 확인
F. DB 스키마 drift          → §3 패치 적용
G. API/화면 호출 경로 문제
```
작은 설정/경로 문제는 수정 가능. **학습 데이터·모델·D/F 담당 코드·DB 스키마(SQL 외) 는 임의 수정 금지.**

## 7. 4090 PC Codex 에게 줄 첫 지시문
```text
이 PC에는 Ollama 모델 careertuner-c-career-strategy-3b 가 등록돼 있다. 현재 repo는 C OSS provider 연동 브랜치(feature/c-fit-oss-llm-integration)다.
목표: Spring 백엔드가 localhost Ollama를 호출해 C 적합도 설명을 자체모델로 생성하는지 검증.
절대 금지: 7B/추가학습/GGUF 재변환/Ollama 모델 재생성/Tailscale/방화벽·포트개방/백엔드 대규모 수정/D·F 파일 수정/DB 스키마 임의(SQL 외) 수정.
순서:
 1) ml/career-strategy-llm/reports/11_live_localhost_4090_test.md 를 먼저 읽는다.
 2) §2 로 Ollama 모델 확인.
 3) §1 env 설정(특히 MAX_TOKENS=1280, 1024 미만 금지).
 4) gradlew -p backend compileTestJava test --tests com.careertuner.fitanalysis.ai.* 로 C OSS 단위테스트 재확인.
 5) cd backend; .\gradlew.bat bootRun. 부팅이 ai_moderation_setting.sanction_threshold 로 막히면 §3 DB drift(패치 적용, 공유DB면 팀 합의) — C OSS 문제 아님.
 6) §5 기준으로 라이브 호출(provider=oss 로그·OSS localhost 호출·JSON·금지키 제거·점수=규칙엔진·설명=OSS·폴백·화면).
끝나면 §8 형식으로 보고.
```

## 8. 보고 형식
```text
1. 현재 브랜치/커밋
2. ollama list 결과
3. /v1/models 호출 결과
4. env 설정값(provider/base-url/model/max-tokens/temperature)
5. C OSS 단위테스트 결과
6. bootRun 성공 여부(+ DB drift 발생 시 §3 항목)
7. 적합도 분석 API/화면 호출 성공 여부
8. 자체모델 설명 출력 확인 여부
9. 금지키/JSON/CJK 문제 여부
10. 폴백 테스트 결과
11. 실패 시 전체 로그와 원인 분류(A~G)
```
