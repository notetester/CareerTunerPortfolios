# 4090 연결 하드닝 계획 (Connectivity Hardening Plan)

> **사건(2026-06-26):** 4090 재부팅 후 `administrators_authorized_keys`/ACL 무효화로 SSH 가 **publickey 거부**(sshd 는 응답). 단일 SSH 경로 의존이 재부팅 한 번에 무너진 것을 *작업 안정성 기반 사건*으로 취급한다.
> **이 문서의 목표:** 4090 연결을 단일 SSH 가 아니라 **재부팅 이후에도 유지되는 다층 구조**로 재설계. 이번 PR 은 **설계 + key-less 부트스트랩 템플릿 + 검증 체크리스트**까지(실제 4090 변경·secret 커밋은 안 함).
> **전제 교정:** 4090 은 **복원형(Deep Freeze) PC 가 아니다** — 모델 pull·Ollama 모델·eval 결과·git checkout 이 계속 누적돼 옴(리셋형이면 불가능). ⇒ 원인은 디스크 초기화가 아니라 **OpenSSH 키/ACL/서비스/Tailscale 영구화 실패**이고, OS 수준 self-healing 으로 해결된다.

## 1. SSH 장애 원인 분류
| 코드 | 원인 | 현재 증거 |
| --- | --- | --- |
| A | sshd 서비스 죽음 | **배제** — SSH 가 handshake 응답(post-quantum 경고). sshd Running. |
| B | Tailscale 단절 | 배제 가능성 높음 — TCP 도달됨(인증 단계까지 감). |
| C | authorized_keys 파일 유실 | **후보** |
| D | administrators_authorized_keys **ACL 오류** | **유력** — Windows OpenSSH 는 Admin/SYSTEM 외 쓰기권한이면 키 파일을 *조용히 무시* → 키 전체 거부. |
| E | sshd_config AuthorizedKeysFile/Pubkey 정책 | 후보 |
| F | 접속 계정/관리자 그룹 권한 | 후보 |
| G | forced-command wrapper 경로 | 트리거 키 한정(풀세션 키엔 무관) |
| H | Windows/OpenSSH 업데이트 후 정책 변화 | 후보 |
| I | 키 미등록/형식 오류 | 후보 |
**판정:** sshd 응답 + **publickey 만 거부** ⇒ A/B 가 아니라 **C·D·E·F·I(특히 D ACL)**. 4090 직접 접속 시 `Test-4090OpenSshHealth.ps1` 로 확정.

## 2. 권장 계층형 연결 구조
SSH 단일 의존을 버리고 **쌓는 구조**(서로 배타 아님):
```text
Layer 0  Windows OpenSSH self-healing      ← 재부팅마다 키/ACL/서비스/방화벽 재적용 (진짜 fix · NOW)
Layer 1  SSH 경로 (full-session / trigger) ← 키 2종 분리, forced-command 트리거 (현 base)
Layer 2  GitHub Actions + Tailscale        ← A 안 켜져도 push 시 4090 실행 (push-driven 백업 · NEXT)
Layer 3  4090 MCP 서버                       ← run_job/status/log/gpu 구조화 도구 (에이전트 네이티브 · 병행 설계)
Layer 4  Codex plugin/hooks/skills          ← 상위 UX 글루(연결성 자체는 못 줌 · later/skip)
Layer 5  운영 Runbook / 장애 복구            ← 분류·복구 절차(이 문서 §11 + health check)
```
**핵심 결론(리서치 §8 근거):** Codex Remote/MCP/hooks/plugins/skills 는 전부 *실존*하지만 **재부팅·연결 문제를 직접 풀지 않는다**(OS 수준 키/ACL/서비스 영구화가 근본). 연결 안정성에 실제로 기여하는 건 **L0(self-healing) + L2(GitHub Actions self-hosted runner = Windows 서비스로 재부팅 자동기동) + L3(자체 MCP 서버, Secure MCP Tunnel 은 outbound-only 라 인바운드 ACL 무의존)** 이다.

## 3. Layer 0 — Windows OpenSSH self-healing (NOW · 핵심)
재부팅마다 SSH 설정을 재적용하는 **OnStart + 매시간 SYSTEM 스케줄 작업**. 스크립트(key-less 템플릿): [`scripts/ops/4090/`](../../scripts/ops/4090/README.md).
- `Install-4090OpenSshSelfHeal.ps1` (현장 1회, 공개키 **파라미터 placeholder** 로만): OpenSSH 설치·sshd Automatic·`administrators_authorized_keys` 작성 + **ACL 강제 교정**(`icacls /inheritance:r` + Administrators/SYSTEM:F) · sshd_config pubkey-only · 방화벽 22 Tailscale 한정 · Tailscale Automatic · self-heal 스크립트 생성 + 스케줄 작업 등록.
- `Test-4090OpenSshHealth.ps1`: §1 의 A~I 분류 건강검진(키/토큰 미출력).
- 로그: `C:\ProgramData\CareerTuner4090\logs\ssh-self-heal.log` / `ssh-health.log`.
> 운영 repo(CareerTunerAI)에는 공개키를 채운 실행본(`scripts/harden_4090_ssh.ps1` + `install_ssh_persistence_task.ps1`, `docs/SSH_PERSISTENCE_SETUP.md`)이 이미 있음. 메인 repo 는 **키 없는 템플릿**만 둔다.

## 4. Layer 1 — SSH 경로 (키 2종 분리)
| 키 | 용도 | 권한 | 비고 |
| --- | --- | --- | --- |
| **full-session key** | 사람/Codex 가 원격 직접 작업·디버깅 | 전체 셸(넓음) | 별도 키·별도 로그, 신뢰 머신에서만 |
| **trigger key** | job 실행 전용 | `command="...run_careertuner_job.ps1",no-pty` (wrapper 만) | 일반 셸 불가, 평상시 자동 트리거 |
- 두 키는 목적·권한 분리 유지. trigger 키는 **forced-command 만**. (private key 는 노트북에만, 어디에도 커밋 금지.)

## 5. Layer 2 — GitHub Actions + Tailscale push-trigger (NEXT)
> **상세 PoC 설계·secret 명세·workflow 초안:** [`4090_GITHUB_ACTIONS_TAILSCALE_POC.md`](./4090_GITHUB_ACTIONS_TAILSCALE_POC.md) (+ 수동 dispatch 전용 워크플로 [`.github/workflows/4090-job-trigger.yml`](../../.github/workflows/4090-job-trigger.yml)).

"A(Claude Code)/Codex 가 안 켜져 있어도 job push 시 4090 을 깨우는" 경로.
```text
GitHub push / workflow_dispatch / jobs/open *.json 변경
  → GitHub Actions
  → (A) self-hosted runner(4090, Windows 서비스)  또는  (B) hosted runner + tailscale/github-action 으로 tailnet 합류 → 4090 SSH(forced-command) 트리거
  → run_latest_job.ps1 → 결과 CareerTunerAI results push
```
- **권장 = (A) self-hosted runner 를 4090 에 Windows 서비스로 설치**(공식 `svc.sh`/서비스 등록): **재부팅 후 자동 기동·자동 재연결**(리서치: codex-action 이 아니라 *self-hosted runner* 경로가 SSH 트리거 대체 + 생존성에 크게 유리). 인입 포트 불필요(러너가 outbound 로 GitHub 에 long-poll).
- (B) hosted runner + Tailscale auth key 는 4090 에 추가 설치 없이 되지만 **여전히 4090 sshd/키 생존 전제** → L0 선행 필수.
- 검토 항목: `TAILSCALE_AUTHKEY`/SSH key는 **GitHub secret**(이번 PR 미생성), `known_hosts` pinning, `workflow_dispatch` 수동, jobs 변경 감지, 실패 시 exit code/로그, 동시 실행 lock.
- **SSH 완전 대체 아님** — 세션이 죽어도 도는 *푸시 기반 백업*.

## 6. Layer 3 — 4090 MCP 서버 (병행 설계)
> **상세 PoC 계획(구현 금지·도구 표면·보안·transport):** [`4090_MCP_SERVER_POC_PLAN.md`](./4090_MCP_SERVER_POC_PLAN.md).

SSH 셸을 에이전트가 직접 만지는 대신 **구조화 도구**를 노출. allowlist 기반, 임의 셸 금지.
| 도구 | 동작 |
| --- | --- |
| `gpu_status()` `ollama_status()` `health_check()` | 읽기 전용 상태 |
| `list_jobs()` `job_status(jobId)` `read_log(jobId, tailLines)` | 큐/로그(tail 만) |
| `run_job(jobId)` `cancel_job(jobId)` | **allowlist jobId 만** |
| `git_status()` `sync_repo()` | 고정 repo/path 만 |
**강한 제한:** 임의 shell 도구 금지 · 허용 jobId/ repo/path 만 · raw secret 출력 금지 · 로그 tail 만 · **모든 tool call 감사 로그**.
**transport 후보:**
| 후보 | NAT/인바운드 | 재부팅 생존성 | 호출자 | 평가 |
| --- | --- | --- | --- | --- |
| **Streamable HTTP over Tailscale** | 인바운드 포트(내부망 한정) | Windows 서비스화 필요 | 임의 MCP 클라(Claude Code/Codex) | **PoC 권장** — tailnet 내부, 공개 URL 없음 |
| **OpenAI Secure MCP Tunnel** | **인바운드 0(outbound-only)** | 서비스화 시 **유리**(ACL 무의존) | **OpenAI 제품 한정**(ChatGPT/Codex/Responses) | 공격면 작으나 호출자 좁음·high 빌드 |
| stdio (`codex mcp-server`/ssh wrapper) | 전송 별도 필요 | 자동부활 없음 | 로컬/ssh 경유 | 원격 트리거엔 부적합 |
- **권장:** PoC = **Streamable HTTP MCP over Tailscale**(외부 공개 URL 없음, tailnet 내부만). 이후 OpenAI 제품에서 직접 호출이 필요하면 **Secure MCP Tunnel**(outbound-only) 검토. 두 경우 모두 **MCP 서버를 Windows 서비스로 자동기동**해야 재부팅 생존.
- 참고(리서치): `codex mcp-server` 는 **stdio 전용**(네트워크 리슨 없음)이라 그 자체로 원격 트리거 불가 — 전송(SSH/Tailscale/Tunnel)이 여전히 필요.

## 7. Layer 4 — Codex plugin / hooks / skills (UX 글루 · later/skip)
리서치 결론: 셋 다 **로컬 Codex 세션 lifecycle 자동화**이고 **외부/원격 이벤트 트리거가 없다** → 연결 안정성을 *제공하지 못한다*. 반드시 **L0~3 위에 얹는 UX 계층**으로만:
- **plugin**: 4090 작업 명령/도구를 패키징·배포(마켓플레이스). MCP/skills/hooks 를 묶음.
- **hooks**: 세션 시작 시 4090 health check, PR 생성 후 job trigger 안내 같은 **로컬 글루**.
- **skills**: 4090 job 작성/결과 해석/장애 분류 절차 표준화(SKILL.md).
> 정직: 우리 job 흐름은 Codex 에이전트가 아니라 자체 PS 러너로 돌아 hooks 가 걸릴 지점이 거의 없다 → **현 단계 skip**, 추후 Codex 를 운영 인터페이스로 쓸 때만 의미.

## 8. 조사 결과 (Codex/OpenAI 8개 · 공식 1차 출처 · 전부 실존)
| 항목 | 실존 | SSH 대비 | 재부팅 생존성 | 난이도/보안 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| Codex Remote connections | yes(GA 26-06-25) | 보완(SSH 위 relay/세션) | 도움 안 됨(같은 키/ACL 의존) | med / OpenAI 계정·relay 종속 | **later** |
| Codex MCP 설정(client) | yes | 무관 | 무관 | low / med | later |
| Codex MCP Server(`codex mcp-server`) | yes(**stdio 전용**) | 무관(전송 별도 필요) | 자동부활 없음 | med / med~high | later |
| **Codex GitHub Action** | yes(codex-action) | codex-action 무관 / **self-hosted runner 는 대체 가능** | **runner=서비스화 시 유리** | med / med | **next** |
| Codex Hooks | yes(로컬) | 무관 | 무관 | low / med | **skip** |
| Codex Plugins | yes(로컬 패키징) | 무관 | 무관 | med / med~high | **skip** |
| Codex Skills | yes(포맷 출처 Anthropic) | 무관 | 무관 | low / med | **skip** |
| OpenAI Remote MCP / Secure Tunnel | yes(outbound-only) | 보완(좁은 MCP 채널) | **유리**(인바운드 0) | high / med | later(L3 transport 후보) |
출처: developers.openai.com/codex/{remote-connections,mcp,github-action,hooks,plugins,skills}, openai/codex-action, developers.openai.com/api/docs/guides/secure-mcp-tunnels, openai/tunnel-client.

## 9. 권장 최종 구조
```text
1) 즉시(NOW):   L0 OpenSSH self-heal bootstrap + health check  ← 진짜 fix, 현장 1회
2) 다음(NEXT):  L2 GitHub Actions self-hosted runner(Windows 서비스) push-trigger
3) 병행 설계:   L3 4090 MCP 서버(HTTP over Tailscale PoC → 필요 시 Secure Tunnel)
4) UX 글루:     L4 Codex plugin/hooks/skills 는 위 L0~3 를 호출하는 인터페이스로만
```
> **Layer 2/3 PoC 상세 문서:** [`4090_GITHUB_ACTIONS_TAILSCALE_POC.md`](./4090_GITHUB_ACTIONS_TAILSCALE_POC.md)(L2, 방식 A/B 비교 + 수동 workflow 초안), [`4090_MCP_SERVER_POC_PLAN.md`](./4090_MCP_SERVER_POC_PLAN.md)(L3, 도구 표면·보안·transport 계획).

## 10. 보안 원칙 (엄수)
- 모든 키 **목적별 분리**(full-session ↔ trigger), trigger 키는 **forced-command 만**.
- private key·token·**Tailscale auth key·GitHub secret 절대 커밋 금지**(이번 PR 미생성). 템플릿은 placeholder 만.
- GitHub Actions secret 은 **최소 권한**. `known_hosts` pinning.
- MCP 서버: **임의 shell 금지**, tool allowlist, raw secret 미출력, 로그 tail 만, **모든 tool call 감사**.
- 실행 결과는 **jobId 단위** 기록. 실패 원인은 sshd/tailscale/auth/acl/wrapper/repo 로 분류(§1).
- 로그에 secret/token/private path 출력 금지.

## 11. 현장 1회 실행 절차 (4090 직접 접근 — TeamViewer/콘솔/VNC)
> 현재 SSH 죽어 원격 불가 → 4090 에 직접. 관리자 PowerShell.
```powershell
# 1) 진단
.\scripts\ops\4090\Test-4090OpenSshHealth.ps1            # A~I 분류
# 2) self-healing 설치(+ 즉시 복구) — 공개키는 노트북 ~/.ssh/*.pub 값으로
.\scripts\ops\4090\Install-4090OpenSshSelfHeal.ps1 `
   -FullSessionPubKey 'ssh-ed25519 <FULL_SESSION_PUBKEY> careertuner-fullsession-ops' `
   -TriggerPubKey     'ssh-ed25519 <TRIGGER_PUBKEY> careertuner-notebook-to-4090'
# (또는 CareerTunerAI 운영 repo 의 키 포함 install_ssh_persistence_task.ps1)
```
검증: 노트북 `ssh -i ~/.ssh/careertuner_4090_full_ed25519 hsy82@<4090-Tailscale-IP> echo ok` → **재부팅 후 재검증**.

## 12. 이번 PR 에서 하지 않은 것
실제 secret/private key/Tailscale auth key 커밋 · GitHub Actions secret 생성 · production workflow 활성화 · 4090 실제 원격 변경 · backend/RAG/모델 코드 변경 · self-hosted runner/MCP 서버 실제 구축(설계만).

## 13. 다음 단계
1. **(현장 1회) L0 self-heal 부트스트랩** → SSH 복구·재부팅 생존 확인(가장 시급, R2 RAG 실측도 이게 풀려야 진행).
2. **L2 self-hosted runner(Windows 서비스) PoC** — push→자동 실행 백업.
3. **L3 MCP 서버 PoC**(HTTP over Tailscale) — run_job/status/log/gpu allowlist 도구.
4. L4 는 위가 안정된 뒤 UX 글루로.

## 자체 검증
- ✅ 실제 secret/private key/auth key 없음(템플릿은 placeholder 파라미터, install 스크립트가 placeholder 면 거부).
- ✅ backend/RAG/model 파일 미수정.
- ✅ self-healing 스크립트가 키를 placeholder 파라미터로만 받음.
- ✅ 현장 1회 절차·재부팅 검증 명령 명확(§11).
- ✅ MCP/GitHub Actions/SSH 를 배타 아닌 계층형으로 정리(§2·§9).
