# 독립 이중 LLM 판정 프로토콜

면접 답변 채점의 골든 기준을 자체 3B 모델의 자기참조 점수가 아니라 **서로의 답을 보지 않는 두 외부
판정자**의 합의로 만든다. 특정 사용자 계정, 장비 주소, SSH 키 이름 또는 일시적인 모델 버전에 의존하지
않는 재현 절차만 이 문서에 둔다.

4090 접속, Tailscale/OpenSSH, 전원, Ollama 같은 환경별 운영 정보는
[CareerTunerAI 운영 문서 인덱스](../../../docs/ai-artifacts/docs/ops/README.md)를 따른다. 실제 호스트·계정·키와
토큰은 승인된 secret 저장소에서만 주입하고 Git에 기록하지 않는다.

## 입력과 출력 계약

입력:

- 블라인드 케이스: `{id, questionType, question, referenceModelAnswer, answer}`
- 공통 루브릭: [`eval/judge_rubric.md`](../eval/judge_rubric.md)
- 실행 manifest: 판정자 provider/model ID, 실행 시각, 입력 SHA-256, 루브릭 SHA-256

각 판정자 출력은 한 줄에 JSON 하나인 JSONL로 고정한다.

```json
{"id":"case-id","score":72,"reason":"핵심 개념은 맞지만 구체적 근거가 부족함"}
```

필수 조건:

- `id`는 입력 케이스와 정확히 일치하고 중복·누락이 없어야 한다.
- `score`는 0~100 정수다.
- `reason`은 답변에 근거한 짧은 한국어 판정 사유다.
- 한 판정자의 입력에 다른 판정자의 점수·사유·모델 출력이 들어가면 해당 실행은 무효다.

## 실행 순서

1. `expectedScore`, 기존 panel 점수, 모델별 결과를 제거한 블라인드 입력을 만든다.
2. 입력과 루브릭의 SHA-256을 기록하고 두 판정자에게 동일한 파일을 전달한다.
3. 판정자 A와 B를 서로 독립된 세션에서 실행한다. 순차 실행해도 되지만 첫 결과를 두 번째 프롬프트에
   포함하지 않는다.
4. 두 JSONL을 schema 검증하고 입력 ID 집합과 정확히 일치하는지 확인한다.
5. `panel = round((judgeA + judgeB) / 2)`로 골든 점수를 만든다.
6. `|judgeA - judgeB| > 10`인 케이스는 자동 평균을 확정하지 않고 재검토 큐로 분리한다.
7. 판정자 점수와 확정 panel만 `eval/panel_scores.jsonl`에 반영한다. 원문 응답과 실행 로그는 본체에
   커밋하지 않고 `docs/ai-artifacts/` 경계를 따른다.

## 원격 실행이 필요한 경우

네트워크 전송 방식은 운영 환경에 맞게 선택한다. SSH를 쓸 때도 저장소 문서에는 아래와 같은 placeholder만
남기고 실제 값을 셸 환경이나 secret manager에서 주입한다. 아래 예시는 `ml/interview-finetune`에서
실행한다.

```powershell
$judgeHost = $env:CAREERTUNER_JUDGE_HOST
$judgeUser = $env:CAREERTUNER_JUDGE_USER
$judgeKey = $env:CAREERTUNER_JUDGE_SSH_KEY
$remoteDir = $env:CAREERTUNER_JUDGE_WORKDIR

scp -i $judgeKey eval/judge_rubric.md eval/blind_cases.jsonl "${judgeUser}@${judgeHost}:${remoteDir}/"
ssh -n -i $judgeKey "${judgeUser}@${judgeHost}" "<approved-judge-command>"
scp -i $judgeKey "${judgeUser}@${judgeHost}:${remoteDir}/scores.jsonl" eval/judge-b.scores.jsonl
```

- 원격 명령은 stdin 대기를 막고 비대화형으로 종료돼야 한다.
- JSON과 긴 프롬프트는 셸 인자에 중첩하지 말고 파일로 전달한다.
- 실행 전 호스트 지문을 검증하고 최소 권한 키를 사용한다.
- 키, 토큰, 내부 IP, 사용자 홈 경로가 로그나 manifest에 들어가지 않았는지 산출물 저장 전에 검사한다.

## 2026-07-07 기준 검증 결과

60케이스에서 두 판정자의 평균 점수차는 5.07, 10점 이내 일치는 0.967이었다. 이 panel을 기준으로
F16과 Q4_K_M을 다시 평가했고 Q4를 운영 후보에서 제외했다. 모델 버전, 밴드별 수치와 최종 결정은
[LIVE_AB_RESULT](../eval/LIVE_AB_RESULT.md)를 정본으로 본다.
