# 4090 grounding validator 회귀 평가 (E1 라이브)

> 목적: grounding validator(#114)가 **실제 Ollama + 백엔드 경로**에서 정상 작동하는지 확인. 로직은 단위테스트 29/29로 검증됨 — 이건 **라이브 통합 + 회귀**(특히 정상 응답을 잘못 막지 않는지)를 본다.
> 전제: **dev 에 #114 merge 완료**(`3daf387`). 4090 에 `careertuner-c-career-strategy-3b` + `qwen2.5:3b-instruct`. provider=oss.

## 확인 항목 (6) — 검증 수준 표기
| # | 항목 | 라이브에서 보는 법 | 비고 |
| --- | --- | --- | --- |
| 1 | 정상 LoRA 응답이 validator 통과 | 반복 호출 success(model=자체모델) 비율이 가드 전과 유사(거짓 차단 없음) | ★핵심(라이브) |
| 2 | missing 을 보유로 서술하면 retry | 로그에 `grounding 위반 ... 재호출` 출현(발생 시) | 확률적 — 발생하면 기록 |
| 3 | risks/strategyActions/learningTaskReasons 의 missing 은 안 막음 | 정상 응답이 막히지 않음(success 유지) | 보수적 검사 + 단위테스트가 보장 |
| 4 | "즉시 지원하기보다는" 등 부정/제한 문맥 false-positive 없음 | HOLD 케이스 정상 통과 | 단위테스트 보장 + 라이브 확인 |
| 5 | retry 소진 시 fallback 정상 | Ollama 중단/위반 지속 시 model=mock, 화면 안 깨짐 | ★핵심(라이브) |
| 6 | 점수/판단 = rule engine | 응답 fitScore/applyDecision 이 규칙엔진 값 유지 | ★핵심 |

## 실행 명령 (4090, PowerShell)
```powershell
git checkout dev; git pull            # #114 포함 확인
cd backend
$env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
$env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://localhost:11434/v1"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
$env:CAREERTUNER_ANALYSIS_AI_OSS_GROUNDING_RETRIES="1"
.\gradlew.bat bootRun *> ..\ml\career-strategy-llm\out\eval\grounding_bootrun.log   # 다른 터미널에서 호출
```
별도 터미널(시드 실계정 로그인 후) — 여러 지원 건을 각 5회 호출(확률적 위반 유발 + 정상 통과 확인):
```powershell
# 로그인 → accessToken; HOLD/COMPLEMENT 섞어서(예: case 2, 14, 35) 각 5회 POST
foreach ($id in 2,14,35) { 1..5 | % { Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/fit-analyses/application-cases/$id" -Headers @{Authorization="Bearer $tok"} -TimeoutSec 120 | Out-Null } }
```
로그/DB 확인:
```powershell
Select-String -Path ..\ml\career-strategy-llm\out\eval\grounding_bootrun.log -Pattern "grounding 위반|재호출|OSS 자체모델 실패|폴백" | Select-Object -First 40
# ai_usage_log model 분포(자체모델 vs mock)는 DB 조회(reports/15 §3 쿼리)
```
폴백 확인:
```powershell
ollama stop careertuner-c-career-strategy-3b   # 잠깐 언로드 → 재호출 시 mock 폴백 확인 → 이후 자동 재로드
```

## 기대 결과
```text
1. 정상 호출 다수가 model=careertuner-c-career-strategy-3b 로 성공(가드가 정상 응답을 막지 않음).
2. (발생 시) 'grounding 위반 ... 재호출' WARN 로그 — field/missingSkill/phrase 포함.
3·4. HOLD/COMPLEMENT 정상 응답이 false-positive 로 막히지 않음(success 비율 유지).
5. Ollama 중단/위반 지속 시 model=mock 폴백, 화면 안 깨짐(로그 'OSS 자체모델 실패 → 폴백').
6. 모든 응답에서 fitScore/applyDecision 은 규칙엔진 값(자체모델이 만들지 않음).
```
fallback 확인 방법: 응답 JSON 의 `model` 필드(자체모델 vs mock) + 로그의 폴백 문구 + `ai_usage_log` 의 model 분포 + 점수/판단이 규칙엔진 값으로 동일.

## 산출물 규칙
- 로그/결과 raw 는 `out/eval/`(미커밋) → **artifact repo `CareerTunerAI`** push. 메인 repo 엔 **요약 수치만**(이 문서/후속 요약).
- D/F 모델·설정 미수정. 7B/재학습/RAG 금지. `ollama stop` 은 언로드(삭제 아님).
