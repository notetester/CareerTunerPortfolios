# 4090 작업 자동화 계획 — CareerTunerAI 작업 큐 (2026-06-22)

> 목표: 사람이 매번 Claude Code 명령을 4090 Codex 에 복붙하고 답변을 수동으로 옮기는 흐름을 없앤다.
> 1단계(이번): **CareerTunerAI 를 작업 큐 + 결과 큐**로 사용. 2·3단계(SSH/runner)는 **문서화만**(즉시 적용 X).

## 1. 작업 큐 구조 (CareerTunerAI, 적용 완료)
```text
jobs/open/      노트북 Claude 가 올린 실행 대기 job(*.json)
jobs/running/   run_latest_job.ps1 이 실행 중 이동(실패 시 open 복귀)
jobs/done/      완료 job
results/<jobId>/ job 별 결과 JSON·로그·SUMMARY
scripts/        run_latest_job.ps1(allowlist 실행기), submit_result.ps1(수동 제출)
STATUS_4090.md  4090 현재 상태(스크립트 자동 갱신)
HANDOFF_4090.md 최신 작업 지시 요약(노트북 Claude 가 갱신)
```
> 메인 repo 엔 raw 결과를 커밋하지 않는다. 큐·결과는 전부 CareerTunerAI 에만. submodule/`.gitmodules` 안 만든다.

## 2. 운영 흐름
```text
노트북 Claude Code : jobs/open 에 job 작성 + HANDOFF 갱신 → CareerTunerAI push
4090 Codex        : CareerTuner dev pull + CareerTunerAI main pull
                    → scripts\run_latest_job.ps1 -CareerTunerRepo <경로>
                    → 결과 results/<jobId>/ 저장 → STATUS 갱신 → push  (스크립트 자동)
노트북 Claude Code : CareerTunerAI pull → 분석 → 요약만 메인 repo reports/ 에 PR
```
4090 Codex 가 앞으로 볼 것: `HANDOFF_4090.md`, `jobs/open/`. 노트북 Claude 가 지시를 올릴 곳: `jobs/open/`(+`HANDOFF_4090.md`).

## 3. 안전 모델 (arbitrary 실행 금지)
- **taskType allowlist**: `eval_e2_observer`, `eval_latency`, `eval_grounding_regression` 만 인식. 그 외 taskType 이면 **중단**.
- **job 파일의 raw 명령을 실행하지 않는다.** 스크립트가 taskType 별 **고정 명령 템플릿**을 쓰고, job 은 **구조화 params**(model/repeat/timeout 등)만 제공.
- **백엔드 필요 작업은 자동 실행 안 함**: `eval_grounding_regression` 은 Spring 기동+로그인이 필요해 자동 실행하지 않고 가이드(reports/28)만 출력 → 수동 실행 후 `submit_result.ps1` 로 제출.
- 실패 시 job 을 `jobs/open` 으로 되돌려 재실행 가능.
- 모델/설정(D/F) 변경·gguf/safetensors·PII/token 업로드 금지(README 명시).

## 4. SSH over Tailscale 검토 (문서화만 — 즉시 적용 X)
노트북 Claude → 4090 원격으로 `pull → run_latest_job → push` 를 돌리는 더 강한 자동화 후보.
Windows 4090 서버에는 **Tailscale SSH 보다 Windows OpenSSH Server over Tailscale** 가 적합.

| 항목 | 방침 |
| --- | --- |
| OpenSSH Server | `Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0` 후 `sshd` 서비스 시작·자동 시작 |
| 인증 | **공개키 인증만**. `~/.ssh/authorized_keys`(또는 관리자용 `C:\ProgramData\ssh\administrators_authorized_keys`)에 노트북 공개키 등록 |
| password 로그인 | `sshd_config` 에서 `PasswordAuthentication no` (키 등록·검증 후 비활성화) |
| 방화벽 | 22번 인바운드를 **Tailscale 인터페이스(CGNAT 100.64.0.0/10)에서만** 허용. 공용/사설 프로파일에서 22 차단 |
| 노출 | Tailnet 내부에서만 접근(공인 IP 노출 금지). Tailscale ACL 로 노트북 노드만 4090:22 허용 가능 |
| 실행 범위 | 원격에서도 `run_latest_job.ps1`(allowlist)만 호출. 임의 명령 셸 자동화 금지 |
| 롤백/비활성화 | `Stop-Service sshd; Set-Service sshd -StartupType Disabled`, 방화벽 규칙 삭제, `Remove-WindowsCapability` |

> 적용 전 점검: 4090 이 공유 PC 인지, 관리자 권한 정책, 키 보관처. 합의 전엔 설치하지 않는다.

## 5. GitHub Actions self-hosted runner 검토 (문서화만 — 즉시 적용 X)
장기 후보. `workflow_dispatch` 수동 트리거로만 4090 runner 가 평가 실행.

| 항목 | 방침 |
| --- | --- |
| 대상 repo | **CareerTunerAI(private) 전용** runner. 메인 repo 엔 붙이지 않음(코드 PR 트리거 위험 차단) |
| 트리거 | `workflow_dispatch` **수동만**. `pull_request`/`push` 트리거 **금지** |
| 실행 범위 | workflow 가 `run_latest_job.ps1`(allowlist taskType)만 호출. arbitrary command step 금지 |
| 권한 | runner 계정 최소 권한(관리자 아님), `GITHUB_TOKEN` 최소 scope, secrets 최소화 |
| 위험 | self-hosted runner + public fork PR 는 임의 코드 실행 위험 → **private repo + dispatch-only** 로 회피 |
| 롤백 | runner 등록 해제(`config.cmd remove`), 서비스 제거 |

> fork PR 자동 실행 위험 때문에 **private repo + 수동 dispatch + allowlist** 가 전제. 합의 전엔 설치하지 않는다.

## 6. 복붙 감소 효과
```text
이전: 사람이 (a) Claude 명령을 4090 에 복붙 → (b) 4090 답변을 노트북에 복붙  (작업마다 2회 수동)
이후: 노트북 Claude 가 jobs/open 에 job push → 4090 은 run_latest_job.ps1 1줄 → 결과 자동 push
      → 사람 개입은 'job 한 번 확인' 수준. 표준 평가(E2/latency)는 복붙 0회.
      (grounding 회귀처럼 백엔드 필요 작업만 수동 절차 + submit_result.ps1)
```
SSH/runner 까지 가면 4090 의 `run_latest_job.ps1` 호출도 노트북/dispatch 에서 원격 트리거 → 사람 개입 0 에 근접(단, 안전 가드 유지가 전제).
