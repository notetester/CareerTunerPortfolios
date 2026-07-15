# D 면접 모델 스크립트 운영 문서

학습·평가 script의 반복 실행 절차와 판정 계약을 둔다. 현재 모델·학습 상태는 상위 [README.md](../README.md)와 [TRAINING.md](../TRAINING.md), 평가 결과는 [eval/README.md](../eval/README.md)를 먼저 확인한다.

- [DUAL_JUDGE_RUNBOOK.md](DUAL_JUDGE_RUNBOOK.md): 두 판정자를 사용한 오프라인 평가 재현 절차

API key와 원본 사용자 데이터는 runbook이나 결과 파일에 기록하지 않는다. 새 raw output은 `docs/ai-artifacts/` 경계를 따른다.
