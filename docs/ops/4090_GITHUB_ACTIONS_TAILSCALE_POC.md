# 4090 Layer 2 — GitHub Actions + Tailscale 실행 경로 PoC

> **위치:** [4090 연결 하드닝 계획](./4090_CONNECTIVITY_HARDENING_PLAN.md) §5(Layer 2) 의 **상세 PoC 설계**. 상위 계층 구조·보안 원칙·장애 분류는 그 문서를 기준으로 한다.
> **목적:** SSH 단일 경로 의존을 끊는다. 사람/Codex 세션이 안 떠 있어도 **GitHub Actions 가 4090 의 eval job 을 실행**시키는 두 번째 실행 경로(Layer 2)를 만든다.
> **이번 PR 범위:** 설계 문서 + **수동 dispatch 전용 workflow 초안**(secret 미설정 시 동작 안 함). 실제 secret 생성·4090 runner 설치·자동 트리거 활성화는 **하지 않는다**.

## 0. 왜 Layer 2 인가 (배경)

2026-06-26 4090 재부팅으로 OpenSSH `administrators_authorized_keys` ACL 이 무효화되어 **SSH publickey 가 통째로 거부**됐다. self-heal(L0)로 복구·영구화했지만, 사건의 본질은 **"실행 경로가 SSH 하나뿐"** 이라는 점이다. SSH 한 경로가 죽으면 eval 큐 전체가 멈춘다.

Layer 2 는 그 단일점을 없앤다. **job 의 source-of-truth 는 CareerTunerAI 의 `jobs/open/*.json` 큐**이고, GitHub Actions 가 그 큐를 4090 에서 돌리는 **백업 실행 경로**가 된다. SSH 를 완전히 대체하는 게 아니라, SSH 가 살아있을 때도 쓸 수 있는 **푸시/디스패치 기반 보조 경로**다.

## 1. 두 가지 방식 비교 (방식 A vs 방식 B)

| 구분 | **방식 A — Hosted runner + Tailscale + forced-command SSH** | **방식 B — Self-hosted runner (4090 = Windows 서비스)** |
| --- | --- | --- |
| 러너 위치 | GitHub-hosted `ubuntu-latest` (매 실행 새 VM) | 4090 자체에 GitHub Actions runner 설치 |
| tailnet 합류 | `tailscale/github-action` 으로 **임시 합류**(ephemeral key) | 불필요 — 4090 은 이미 tailnet/로컬에서 실행 |
| 4090 도달 방식 | tailnet 진입 후 **forced-command SSH** 트리거 | runner 프로세스가 4090 위에서 **직접** `run_latest_job.ps1` 실행 |
| **4090 SSH 생존 의존** | **필수** — L0 self-heal 이 SSH/ACL 을 살려둬야 동작 | **불필요** — SSH 우회. runner 는 outbound long-poll |
| 인바운드 포트 | 없음(SSH 도 Tailscale 내부 22, ACL 한정) | 없음(runner 가 GitHub 으로 outbound 연결) |
| 재부팅 생존성 | 4090 self-heal + Tailscale 자동기동에 의존 | **runner 를 Windows 서비스(`svc.sh`)로 등록 → 재부팅 자동 기동·자동 재연결** |
| 4090 추가 설치 | 없음(노트북/현장 키만) | runner 패키지 + 서비스 등록(현장 1회) |
| 비밀값 | `TAILSCALE_AUTHKEY` + trigger SSH private key + 4090 IP | runner registration token(설치 시 1회) |
| 보안 표면 | hosted VM 이 tailnet 에 잠깐 들어옴(ephemeral·ACL 태그로 축소) | 4090 이 GitHub 작업을 받아 실행(러너 권한·repo allowlist 로 축소) |
| 디버깅 | hosted 로그 + 4090 SSH 로그 양쪽 | 4090 runner 로그 한 곳 |
| **장점** | 4090 무설치로 **지금 바로 PoC**. SSH 자산 재사용 | SSH 완전 우회. 재부팅 생존성 가장 강함. 4090 로컬 실행이라 단순 |
| **단점** | **SSH 가 살아있어야 함**(L0 미복구면 무용) · tailnet 임시합류 키 관리 | 4090 에 self-hosted runner 설치·서비스화 필요 · 러너 보안(=4090 에서 임의 워크플로 실행 위험) 관리 |
| 권장 시점 | **단기 PoC** | **중기 정착** |

## 2. 권장 로드맵

| 시점 | 선택 | 이유 |
| --- | --- | --- |
| **단기 PoC (NOW/NEXT)** | **방식 A** — hosted runner + Tailscale + forced-command SSH | 4090 에 아무것도 추가 설치하지 않고, 이미 있는 SSH trigger 자산(forced-command wrapper) 을 그대로 재사용. 가장 빨리 "세션 없이도 job 실행" 을 증명. |
| **중기 정착** | **방식 B** — self-hosted runner(Windows 서비스) | 재부팅 자동 기동·SSH 우회로 생존성이 가장 강하다. PoC 로 흐름이 검증되면 4090 에 runner 를 서비스로 박는다. |
| **병행** | **L3 MCP server** ([계획](./4090_MCP_SERVER_POC_PLAN.md)) | 에이전트 네이티브 도구(run_job/status/log/gpu). Layer 2 와 배타가 아니라 **함께 쌓는** 구조. |

> **핵심:** A 와 B 는 둘 중 하나가 아니라 **단계**다. A 로 흐름을 증명하고, B 로 생존성을 굳히고, MCP 로 에이전트 인터페이스를 얹는다.

## 3. source-of-truth 와 실행 흐름

- **job 큐 source-of-truth = CareerTunerAI repo 의 `jobs/open/*.json`.** 메인 CareerTuner repo 는 결과/큐를 보관하지 않는다.
- **결과 push 대상 = CareerTunerAI** (`results/` 등 운영 repo 규약). 메인 repo 에 raw 결과를 커밋하지 않는다.

### 방식 A 흐름 (이번 workflow 초안이 구현하는 것)

```text
사용자가 GitHub UI 에서 workflow_dispatch 실행 (inputs.jobId)
  → ubuntu-latest 러너 기동
  → actions/checkout (메인 repo, 워크플로 파일만 필요)
  → tailscale up  (tailscale/github-action, authkey = secret, ephemeral, ACL 태그)
  → SSH private key 설치 (secret → ~/.ssh, chmod 600)
  → known_hosts pinning (4090 호스트키 secret/절차로 고정)
  → jobId 정규식 검증 (^[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9-]+$)  ← 임의 입력 차단
  → forced-command SSH: ssh -i key trigger@<4090-IP> run   (wrapper 가 jobId 로 run_latest_job.ps1 호출)
  → 4090: run_latest_job.ps1 이 CareerTunerAI jobs/open 큐 실행
  → 4090: 결과를 CareerTunerAI 로 push
  → exit code 수집 → 워크플로 성공/실패 판정
```

여기서 **forced-command** 가 핵심 안전장치다. trigger SSH 키는 `command="...run_careertuner_job.ps1",no-pty` 로 묶여 있어, 워크플로가 4090 에 **임의 셸 명령을 못 보낸다**. 워크플로가 넘기는 건 검증된 jobId 뿐이고, 실제로 무엇을 실행할지는 4090 의 wrapper 가 결정한다.

### 방식 B 흐름 (중기)

```text
workflow_dispatch / (선택) jobs/open 변경 push
  → 4090 self-hosted runner (Windows 서비스, 재부팅 자동기동)
  → runner 가 4090 위에서 run_latest_job.ps1 직접 실행 (SSH 불필요)
  → 결과 CareerTunerAI push
```

## 4. 방식 A 의 4090 SSH 생존 의존성 (명시)

방식 A 는 **반드시 4090 의 SSH 가 살아있어야** 동작한다. 구체적으로:

1. **L0 self-heal 선행 필수** — `Install-4090OpenSshSelfHeal.ps1` 로 sshd Automatic·`administrators_authorized_keys` ACL 교정·trigger 키 등록·방화벽 22(Tailscale 한정) 가 되어 있어야 한다. 2026-06-26 사건처럼 ACL 이 깨지면 방식 A 는 publickey 거부로 전부 실패한다.
2. **Tailscale 가 4090 에서 자동 기동** 돼 있어야 hosted runner 가 tailnet 으로 4090 에 도달한다.
3. **trigger forced-command wrapper(`run_careertuner_job.ps1`)** 가 4090 에 존재해야 한다.

→ 즉 방식 A 는 L0 의 **위에** 쌓이는 경로다. L0 가 무너지면 같이 무너진다. 이 약점을 없애는 게 방식 B(SSH 우회)다. **그래서 중기 목표가 B 다.**

## 5. 필요한 GitHub secret 목록 (값 없이 placeholder·설명만)

> ⚠ **이번 PR 은 어떤 secret 도 생성/커밋하지 않는다.** 아래는 운영자가 GitHub repo Settings → Secrets 에 **수동 등록**할 항목의 명세일 뿐이다. workflow 는 `${{ secrets.XXX }}` 로 참조만 하며, **미설정이면 동작하지 않는다**(가드 step 이 즉시 실패 처리).

| Secret 이름 | 용도 | 형식/획득 | 비고 |
| --- | --- | --- | --- |
| `TAILSCALE_AUTHKEY` | hosted runner 가 tailnet 에 임시 합류 | Tailscale admin → **ephemeral + tagged** auth key (예: `tag:ci-4090`) | **재사용 불가·만료 짧게.** ephemeral 이라 작업 종료 후 노드 자동 삭제 |
| `TRIGGER_SSH_PRIVATE_KEY` | forced-command 트리거 키(개인키) | 노트북에서 만든 trigger 전용 ed25519 **private key 본문** | full-session 키와 **반드시 분리**. forced-command 로만 동작 |
| `H4090_HOST` | 4090 Tailscale IP/호스트 | 예: `100.x.y.z` (또는 MagicDNS 이름) | IP 자체는 비밀 아님이나 노출 최소화 위해 secret 처리 |
| `H4090_SSH_USER` | SSH 접속 계정 | 예: trigger 전용 계정명 | forced-command 키가 묶인 계정 |
| `H4090_KNOWN_HOSTS` | 4090 SSH 호스트키 pinning | `ssh-keyscan` 결과(4090 공개 호스트키 라인) | MITM 방지. 미설정 시 워크플로는 **연결 거부**(StrictHostKeyChecking) |

- 모든 값은 **운영자가 직접 등록**한다. 코드/문서/로그 어디에도 실제 값을 쓰지 않는다.
- private key 는 노트북·GitHub secret 에만 존재. **repo 에 절대 커밋 금지.**
- `H4090_KNOWN_HOSTS` 가 없으면 호스트키 검증을 우회하지 않고 **실패**시킨다(보안 우선).

## 6. 보안 가드 (workflow 초안에 반영)

- **workflow_dispatch 수동 전용.** push/schedule 등 자동 트리거 없음(주석으로 명시).
- **jobId 정규식 + allowlist 검증.** `^[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9-]+$` 만 통과. 셸 메타문자·경로 주입 차단.
- **forced-command 만.** 워크플로는 4090 에 임의 명령을 못 보낸다. jobId 만 전달, 실행 결정은 4090 wrapper.
- **secret 미설정 가드.** 필수 secret 이 비면 step 이 즉시 실패(부분 실행 방지).
- **known_hosts pinning.** StrictHostKeyChecking=yes, 호스트키 secret 으로 고정.
- **최소 권한.** `permissions: contents: read`. tailnet 합류는 ephemeral·tagged.
- **로그에 secret 미출력.** key/authkey/known_hosts 값을 echo 하지 않는다.

## 7. 자체 검증 체크리스트

- ✅ 실제 secret 값·private key 없음 — `${{ secrets.XXX }}` 참조만.
- ✅ workflow_dispatch 수동 전용(자동 트리거 비활성).
- ✅ jobId 가 임의 shell 로 이어지지 않음(정규식 검증 + forced-command).
- ✅ SSH(L1)·Tailscale(L2 transport)·GitHub Actions(실행자)·MCP(L3)·Plugin(L4) 역할 구분 명확.
- ✅ backend/RAG/model 코드 미변경(이 PR 은 docs/ops + .github/workflows 만).
- ✅ source-of-truth = CareerTunerAI `jobs/open`, 결과 push 도 CareerTunerAI.

## 8. 이번 PR 에서 하지 않은 것

실제 GitHub secret 생성/커밋 · Tailscale auth key·private key 커밋 · production workflow 자동 트리거 활성화 · 4090 self-hosted runner 실제 설치 · MCP server 실제 구현 · backend/RAG/model 변경 · raw 결과 커밋 · 워크플로 자가 merge.

## 9. 다음 단계

1. (운영자) 위 secret 등록 → 방식 A workflow 를 **수동 dispatch 로 1회 PoC** → SSH 없이도(=세션 없이도) job 이 도는지 확인.
2. PoC 성공 시 **방식 B(self-hosted runner Windows 서비스)** 설치 → SSH 우회·재부팅 생존성 확보.
3. 병행으로 [L3 MCP server PoC](./4090_MCP_SERVER_POC_PLAN.md) 진행(HTTP over Tailscale).
4. 안정화 후 L4(Codex plugin/hooks/skills) 를 위 경로들의 UX 글루로만 얹는다.
