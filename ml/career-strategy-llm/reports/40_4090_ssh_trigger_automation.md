# 4090 원격 작업 자동화 — SSH trigger + job queue + guarded auto-progress (2026-06-25)

> 사람이 TeamViewer로 매번 "GitHub 갱신됐으니 확인해라"를 중계하던 비효율 제거. A(노트북 Claude Code)와
> B(4090 Codex)가 **CareerTunerAI 큐 + Tailscale SSH trigger** 로 자동으로 주고받는다.
> 상세 스크립트·문서는 CareerTunerAI(artifact repo). 여기엔 요약. raw 결과는 메인 repo 미커밋.

## 1. 구조
```text
A(노트북 Claude Code) ──jobs/open push──▶ CareerTunerAI(GitHub) ──pull──▶ B(4090) run_latest_job → results push
   ▲ SSH trigger(Tailscale, forced-command)  ──ssh→ run_careertuner_job.ps1 ──┘
   └ watch_and_progress(결과 감지·임계 게이트) → Claude Code 가 정책 내에서 다음 job 자동 진행
```
- **메시지 버스 = CareerTunerAI GitHub repo**: `jobs/open`(A→B 명령), `results/`·`STATUS_4090`(B→A 응답), `HANDOFF_4090`(지시).
- **채널 모델(2026-06-25 결정)**:
  - 기본 운영/디버그 = **앱 SSH 풀세션**(Codex/Claude 앱 "연결>SSH")로 4090에 직접 접속 — 경로·로그·실패·unknown job 처리.
  - 반복 자동 실행 = **forced-command SSH wrapper**(known job 트리거, 좁고 안전).
  - 백업 = GitHub polling · 최후 fallback = TeamViewer.
- **키 2개 분리**: 트리거 키 `careertuner_4090_ed25519`(forced-command, wrapper 한정) ↔ 앱 풀세션 키(forced-command 없음, 신뢰 머신). `setup_4090_ssh.ps1 -NotebookPubKey <트리거> -AppFullKey <앱>`.

## 2. 역할
| 주체 | 하는 일 | 하지 않는 일 |
| --- | --- | --- |
| A 노트북 Claude Code | job 작성·push, SSH trigger, 결과 pull/분석, 다음 job 자동 진행(정책 내), main repo 요약 PR | PR 직접 merge, raw 결과 main repo 커밋 |
| B 4090 Codex/runner | dev pull→known taskType 자동 실행→results push, unknown/실패는 plan/needs_review | main repo 수정, D/F 모델 변경, raw 명령 실행 |

## 3. 하이브리드 실행 (B측)
- **known taskType**(`eval_e2_observer`/`eval_latency`/`eval_golden_set`): `run_latest_job.ps1` 가 allowlist로 자동 실행.
- **unknown/실패**: wrapper 가 `jobs/needs_review/` 마커 작성, 자동 재실행 안 함 → Codex 가 원인 분류·plan.
- 장기: guarded Codex agent loop(allowlist·lock·max runtime·dirty check·kill switch 전제).

## 4. Guarded auto-progress (A측)
`watch_and_progress.ps1` 가 결과 감지 후 **기계적 임계 게이트**만 판정(`progress_policy.json`):
json_parse<0.95 · success<0.85 · cjk>0.05 · e2>0.02 · timeout>0 · 동일 job 2연속 실패 → 중단.
통과 시 Claude Code가 분석→다음 job 생성→SSH trigger 반복. merge 필요·새 코드·재학습·보안변경·D/F 영향 등은
멈추고 사용자 보고. kill switch = CareerTunerAI 루트 `STOP_AUTO_PROGRESS`.

## 5. 보안
- 4090 Windows OpenSSH Server, **password 로그인 비활성**, 키 인증만.
- **트리거 키 = forced command** 로 wrapper 한 줄에 고정(키가 새도 다른 명령 불가). **앱 풀세션 키 = 넓은 권한**이라 신뢰 머신(앱)에서만, 사람이 의도적으로 사용. 둘은 별도 키.
- 방화벽: Tailscale 대역(100.64.0.0/10)만 22번 허용(트리거 머신 2대 대응), 일반 LAN/공인망 차단.
- 노트북 개인키 `~/.ssh/careertuner_4090_ed25519` 는 어디에도 커밋 안 함.

## 6. 산출물
- **CareerTunerAI**: `docs/SSH_TRIGGER_SETUP.md`·`CODEX_SSH_CONNECTION_SETUP.md`·`AUTO_PROGRESS_POLICY.md`·`POLLING_BACKUP_SETUP.md`,
  `scripts/run_remote_job_wrapper.example.ps1`·`paths.example.ps1`·`watch_and_progress.ps1`·`progress_policy.json`·`install_polling_task.example.ps1`, `HANDOFF_4090.md` 갱신.
- **메인 repo**: 이 문서(요약).

## 7. 노트북 트리거 명령
```powershell
ssh -i $env:USERPROFILE\.ssh\careertuner_4090_ed25519 hsy82@<4090_Tailscale_IP> run
```
(forced command 라 인자 무시되고 `C:\Users\careertuner\CareerTunerOps\run_careertuner_job.ps1` 실행)

## 8. 적용 전 남은 준비물 (4090에서, 팀장 승인 후)
1. 4090 Tailscale IP 확인. 2. OpenSSH Server 설치·자동시작. 3. authorized_keys 에 노트북 공개키 forced-command 등록.
4. PasswordAuthentication no. 5. 방화벽 Tailscale-only. 6. wrapper/paths 배치 + 로컬 테스트. 7. 노트북에서 SSH 연결 테스트.
→ 절차: CareerTunerAI `docs/SSH_TRIGGER_SETUP.md`.
