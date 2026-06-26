# scripts/ops/4090 — 4090 연결 Layer 0/1 (OpenSSH self-healing)

4090(RTX 4090, Windows, Tailscale, Ollama) 의 SSH 접속을 **재부팅 이후에도 유지**하기 위한 현장 스크립트.
배경·전체 계층 설계는 [docs/ops/4090_CONNECTIVITY_HARDENING_PLAN.md](../../../docs/ops/4090_CONNECTIVITY_HARDENING_PLAN.md).

> ⚠ 이 디렉터리는 **키 없는 템플릿**이다. private key·token·Tailscale auth key 는 절대 커밋하지 않는다.
> 공개키는 실행 시 **파라미터(placeholder)** 로만 전달한다.

## 파일
| 파일 | 역할 |
| --- | --- |
| `Install-4090OpenSshSelfHeal.ps1` | OpenSSH 키/ACL/서비스/방화벽 적용 + **OnStart+매시간 SYSTEM self-heal 작업** 등록(현장 1회) |
| `Test-4090OpenSshHealth.ps1` | publickey 거부 원인 A~I 분류 건강검진 |

## 현장 1회 실행 (4090 관리자 PowerShell)
> 현재 SSH 가 죽어 원격 불가 → **TeamViewer/콘솔/VNC 로 4090 에 직접** 실행.
```powershell
# CareerTuner 메인 repo clone 위치에서
.\scripts\ops\4090\Install-4090OpenSshSelfHeal.ps1 `
   -FullSessionPubKey 'ssh-ed25519 <FULL_SESSION_PUBKEY> careertuner-fullsession-ops' `
   -TriggerPubKey     'ssh-ed25519 <TRIGGER_PUBKEY> careertuner-notebook-to-4090'
```
(공개키 실제 값은 노트북 `~/.ssh/*.pub` 또는 CareerTunerAI 운영 문서에서. private key 는 노트북에만.)

## 검증
```powershell
.\scripts\ops\4090\Test-4090OpenSshHealth.ps1     # 4090 에서: A~I 분류
```
```bash
# 노트북에서:
ssh -i ~/.ssh/careertuner_4090_full_ed25519 hsy82@<4090-Tailscale-IP> echo ok
```
## 재부팅 검증
재부팅 → 자동 로그인/부팅 후 `CareerTuner-SSH-Persist` 작업이 SSH 를 재적용한다. 다시 노트북에서 `ssh ... echo ok`.

## 동작(self-heal, 멱등)
sshd Automatic+기동 · authorized_keys 작성 + **ACL 교정**(Administrators/SYSTEM only — 이게 핵심) · sshd_config pubkey-only · 방화벽 22 Tailscale 한정 · Tailscale Automatic. 로그 `C:\ProgramData\CareerTuner4090\logs\ssh-self-heal.log`.

## 한계
4090 이 재부팅-복원형 PC 면 무의미하나, **현재 가정은 일반 PC**(모델/결과/체크아웃이 누적돼 옴 → 디스크 유지 확인). 따라서 원인은 디스크 초기화가 아니라 **키/ACL/서비스 영구화 실패**이고, 이 self-heal 로 해결된다.
