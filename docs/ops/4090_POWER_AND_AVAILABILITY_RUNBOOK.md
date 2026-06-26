# 4090 전원·가용성 Runbook (Power & Availability)

> **사건(2026-06-26 후속):** SSH self-heal(Layer 0) 복구 후에도 4090 이 **Tailscale offline(last seen 표시)** 으로 빠짐.
> 이번엔 SSH 인증이 아니라 **PC 자체가 꺼짐/절전/네트워크 단절**. self-heal 은 PC 가 *켜져 있어야* 동작하므로,
> **전원·가용성**이 Layer 0 의 더 아래 전제다. 이 runbook 은 offline 원인 진단·복구·예방을 정리한다.

## 1. 4090 offline 가능 원인
| 원인 | 신호 | 비고 |
| --- | --- | --- |
| **절전/슬립(sleep/hibernate)** | Tailscale "last seen N분 전", SSH `Connection timed out` | 가장 흔함. 전원 정책으로 예방(§4). |
| 전원 꺼짐/재부팅 중 | last seen 오래됨, ping 불가 | 부팅 후 self-heal 이 SSH 복구. |
| Tailscale 서비스 중단/로그아웃 | PC 는 켜졌는데 tailnet 에 안 보임 | Tailscale Automatic + 로그인 유지(§4). |
| 네트워크 단절(공용망 WiFi 끊김) | last seen 갱신 안 됨 | DERP 릴레이도 도달 불가. |
| WireGuard 직결 차단(공용망) | ping 되는데 TCP timeout | 과거 사례 — force-DERP 로 우회(reports/40). 단 이번은 *완전 timeout* 이라 해당 아님. |

## 2. Tailscale "Last seen" 해석
- admin 콘솔(또는 `tailscale status`)에서 `chanssick` 이 **회색 + "X분 전"** = 해당 시점 이후 tailnet 에 신호 없음 → **PC offline/절전 추정**.
- 초록 **Connected** 로 바뀌면 PC 가 온라인 — 그때부터 SSH/GPU 작업 가능.
- **무한 SSH 재시도 금지** — offline 이면 timeout 만 반복. last seen 으로 판단하고 대기.

## 3. 전원 복구 후 확인 순서 (online 되면)
```text
1. Tailscale admin: chanssick = Connected 확인
2. full-session SSH:  ssh -i ~/.ssh/careertuner_4090_full_ed25519 hsy82@<IP> hostname
3. trigger SSH:       ssh -i ~/.ssh/careertuner_4090_ed25519 hsy82@<IP> run   (forced-command dry)
4. SSH self-heal:     Get-Service sshd / schtasks 'CareerTuner-SSH-Persist'(+Hourly)
5. Ollama GPU:        ollama ps  +  nvidia-smi --query-gpu=memory.used,utilization.gpu --format=csv
   - PROCESSOR 가 '100% CPU' 면 Ollama 가 GPU 못 잡음 → Ollama 재시작(taskkill ollama.exe → tray 재생성) 후 재확인
6. 작업 재개:         R2b job 실행 / Layer 2 workflow dispatch
```

## 4. 절전 방지 (예방 — 현장 관리자, 소유자 동의 전제)
4090 이 헤드리스 배치 서버로 쓰이므로 **슬립/최대절전을 끄는 게** 가용성에 직결. 단 **공용 PC 라 소유자 동의 필수.**
- 점검만: `scripts\ops\4090\Set-4090PowerPolicy.ps1` (현재 powercfg 상태 출력).
- 적용: `Set-4090PowerPolicy.ps1 -Apply` (관리자) — `standby-timeout`/`hibernate-timeout`/`monitor-timeout` = 0(안 함).
- Tailscale 서비스 Automatic + 로그인 유지(self-heal 스크립트가 `Set-Service Tailscale Automatic` 포함).
- (선택) BIOS/NIC **Wake-on-LAN** — 절전·종료 상태에서 원격 기상. BIOS 설정 + NIC "매직 패킷 허용" 필요. 같은 tailnet 의 다른 PC(elevenpc 등)에서 매직패킷 전송 가능. **BIOS 접근이 필요해 현장 작업**이며, 공용 PC 정책상 소유자 협의 대상.

## 5. 장기 — 단일 PC 가용성 의존 줄이기
4090 한 대에 의존하는 한 그 PC 의 전원·네트워크가 SPOF 다. 연결 다중화(Layer 2/3, `4090_CONNECTIVITY_HARDENING_PLAN.md`)와 별개로:
- **전원/네트워크 모니터링**: Tailscale last-seen 을 주기 점검(사람/스크립트)해 offline 을 빨리 인지.
- **WoL + 자동 깨우기**: online 전제 작업(GitHub Actions 트리거 등) 전에 WoL 로 기상 시도(설계 후보).
- (대안) GPU 작업을 가용성 높은 환경으로 옮기는 것은 비용/소유 문제라 현 단계 범위 밖.

## 6. 지금(4090 offline) 할 수 있는 것 / 없는 것
| 가능(4090 불필요) | 불가(4090 필요) |
| --- | --- |
| 코드/하니스 품질 개선·검증(validator/summarizer/test), 문서·runbook, 워크플로 preflight 보강 | GPU 실측(R2b A/B), Ollama 상태, workflow 실제 4090 trigger 검증, MCP 서버 실구동 |

관련: [4090_CONNECTIVITY_HARDENING_PLAN.md](4090_CONNECTIVITY_HARDENING_PLAN.md) · [scripts/ops/4090/README.md](../../scripts/ops/4090/README.md).
