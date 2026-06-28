# 4090 Layer 3 — MCP 서버 PoC 계획 (구현 금지 · 계획 전용)

> **위치:** [4090 연결 하드닝 계획](./4090_CONNECTIVITY_HARDENING_PLAN.md) §6(Layer 3) 의 **PoC 계획 상세**. [Layer 2 PoC](./4090_GITHUB_ACTIONS_TAILSCALE_POC.md) 와 배타가 아니라 **함께 쌓는** 계층이다.
> **이 문서는 계획만 담는다 — 코드/서버 구현은 하지 않는다.** 도구 표면·보안 원칙·transport 선택만 확정한다.

## 0. 목적

SSH 셸을 에이전트(Claude Code/Codex)가 직접 두드리는 대신, 4090 의 eval 운영을 **구조화된 MCP 도구**로 노출한다. 임의 셸 대신 **정해진 도구 + allowlist** 만 호출 가능하게 해, 에이전트가 안전하게 health/gpu/큐/로그/실행을 다루도록 한다.

- **L2(GitHub Actions)** 가 "세션 없이 도는 백업 실행 경로" 라면, **L3(MCP)** 는 "에이전트가 살아있을 때 쓰는 안전한 네이티브 인터페이스" 다.
- job 큐 **source-of-truth 는 여전히 CareerTunerAI `jobs/open`.** MCP 서버는 그 큐를 **읽고/돌리는** 얇은 도구 계층일 뿐, 큐의 진실을 새로 만들지 않는다.

## 1. 도구 표면 (tool surface) — 후보

| 도구 | 종류 | 동작 | 입력 제약 |
| --- | --- | --- | --- |
| `health_check()` | 읽기 | sshd/Tailscale/디스크/서비스 상태 요약 | 없음 |
| `gpu_status()` | 읽기 | GPU 사용률·VRAM·온도(`nvidia-smi` 요약) | 없음 |
| `ollama_status()` | 읽기 | Ollama 서비스·로드된 모델 목록 | 없음 |
| `list_jobs()` | 읽기 | CareerTunerAI `jobs/open` 큐 목록 | 없음 |
| `job_status(jobId)` | 읽기 | 해당 job 의 상태(대기/실행/완료/실패) | **jobId allowlist + 정규식** |
| `read_log(jobId, tailLines)` | 읽기 | 해당 job 로그의 **tail 만** | jobId allowlist · `tailLines` 상한(예 ≤ 500) |
| `run_job(jobId)` | 실행 | allowlist jobId 를 큐에서 실행 | **jobId allowlist + 정규식만** |
| `cancel_job(jobId)` | 실행 | 실행 중 job 취소 | **jobId allowlist + 정규식만** |

- 모든 jobId 는 L2 와 **동일한 정규식**(`^[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9-]+$`) + allowlist 를 통과해야 한다.
- **`run_arbitrary_shell` 류 도구는 두지 않는다.** (아래 §2)

## 2. 보안 원칙 (엄수)

1. **arbitrary shell 금지.** 임의 명령 실행 도구를 노출하지 않는다. 모든 동작은 위 고정 도구 집합으로만.
2. **jobId allowlist + 정규식.** 실행/취소/로그 도구는 허용된 jobId 형식·목록만 받는다. 경로/메타문자 주입 차단.
3. **repo/path allowlist.** git/파일 접근은 고정된 CareerTunerAI repo·경로로 제한. 임의 경로 읽기 금지.
4. **log tail 만.** `read_log` 는 tail N 줄만(상한 적용). 전체 로그·임의 파일 덤프 금지.
5. **secret 미출력.** 어떤 도구도 private key·token·authkey·secret 경로를 응답/로그에 내지 않는다.
6. **모든 tool call 감사 로그.** 누가/언제/어떤 도구·인자로 호출했는지 전부 audit 로그에 남긴다(인자에서 secret 제외).
7. **transport 보안.** §3 — 우선 tailnet 내부 HTTP, 공개 URL 없음. 이후 Secure MCP Tunnel(outbound-only).

## 3. Transport 선택

| 후보 | 인바운드 | 재부팅 생존성 | 호출자 | 평가 |
| --- | --- | --- | --- | --- |
| **HTTP over Tailscale** (우선) | tailnet 내부 포트만(공개 URL 없음) | **Windows 서비스화 필요** | 임의 MCP 클라(Claude Code/Codex) | **PoC 1순위** — tailnet 내부 한정, 공격면 작음 |
| **OpenAI Secure MCP Tunnel** (이후) | **인바운드 0(outbound-only)** | 서비스화 시 유리(ACL 무의존) | OpenAI 제품 한정 | tunnel 로 ACL 의존 제거. 호출자 좁음 → L2/L3 안정 후 |
| stdio (`codex mcp-server` 등) | 전송 별도 필요 | 자동부활 없음 | 로컬/ssh 경유 | 원격 트리거 부적합(참고용) |

- **PoC 권장 = HTTP over Tailscale.** 외부 공개 URL 없이 tailnet 내부에서만 접근. 4090 ACL 사건과 같은 노출을 만들지 않는다.
- **이후 = Secure MCP Tunnel(outbound-only).** OpenAI 제품에서 직접 호출이 필요해지면, 인바운드 0 으로 ACL 의존 자체를 제거. 단 호출자가 OpenAI 제품으로 좁아진다.
- 두 경우 모두 **MCP 서버를 Windows 서비스로 자동기동**해야 재부팅 생존(L2 방식 B 와 같은 원리).

## 4. L4 — Codex plugin / hooks / skills 의 위치 (연결성 무관 · UX 글루)

리서치 결론: plugin/hooks/skills 셋 다 **로컬 Codex 세션 lifecycle 자동화**이고 **외부/원격 이벤트 트리거가 없다** → **연결 안정성을 제공하지 못한다.** 따라서 L2(GitHub Actions)·L3(MCP) 가 자리잡은 **이후에**, 그 위를 호출하는 UX 계층으로만 둔다.

| 요소 | 역할(글루) | 비고 |
| --- | --- | --- |
| **plugin** | 4090 운영 명령/도구 패키징·배포(MCP·skills·hooks 묶음) | 연결성 자체는 못 줌 |
| **hooks** | 세션 시작 시 4090 `health_check`, PR 생성 후 job trigger 안내 등 로컬 글루 | 우리 job 은 자체 PS 러너로 돌아 hooks 걸릴 지점이 적음 |
| **skills** | job 작성/결과 해석/장애 분류 절차 표준화(SKILL.md) | 표준화 문서 계층 |

> 정직한 평가: 현 단계에선 **skip**. L2/L3 가 안정된 뒤, Codex 를 운영 인터페이스로 정식 채택할 때만 의미가 생긴다.

## 5. 자체 검증 체크리스트

- ✅ 이 문서는 **계획만** — 서버/도구 코드 없음.
- ✅ arbitrary shell 도구 없음. jobId allowlist+정규식, repo/path allowlist, log tail 만.
- ✅ secret 미출력 · 모든 tool call 감사.
- ✅ transport: HTTP over Tailscale 우선 → Secure MCP Tunnel(outbound-only) 이후.
- ✅ Codex plugin/hooks/skills 는 **L2/L3 이후 UX 글루**로만 정리(연결성 제공 아님).
- ✅ source-of-truth = CareerTunerAI `jobs/open`. backend/RAG/model 무관.

## 6. 다음 단계

1. (L2 PoC 검증 후) HTTP over Tailscale MCP 서버 **읽기 전용 도구**(`health_check`/`gpu_status`/`ollama_status`/`list_jobs`)부터 PoC 구현.
2. 읽기 도구 안정 후 `run_job`/`cancel_job` 추가(allowlist·감사 강화).
3. Windows 서비스 등록으로 재부팅 생존 확보.
4. 필요 시 Secure MCP Tunnel 검토 → 마지막에 L4(plugin/hooks/skills) 글루.
