# 원격 호출 셋업 — Tailscale (시연 장소 ≠ 4090)

> 시연은 4090과 다른 장소에서 한다. 시연 노트북의 백엔드가 **4090의 Ollama(자체모델)를 네트워크로** 호출해야 한다.
> → **Tailscale(메시 VPN)**: NAT/네트워크 무관, 사설망 한정(무인증 Ollama 보호), 포트포워딩 불필요.
> ★ **C 백엔드 코드 변경 없음** — `CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL` env 를 4090의 Tailscale IP 로만 바꾼다.

## 토폴로지
```
[시연 노트북]  Spring 백엔드 + 프런트(npm run dev)
     │  base-url = http://<4090-Tailscale-IP>:11434/v1   (Tailscale 사설망)
     ▼
[4090]  Ollama(careertuner-c-career-strategy-3b), OLLAMA_HOST=0.0.0.0:11434
```
둘 다 **같은 tailnet**(같은 Tailscale 계정/조직)에 들어가면 100.x IP 로 서로 직접 통신한다.

## 0. 사전 (팀 결정) — ★C 전용 새 tailnet
- `application.yaml` 의 `localhost`(Tailscale CGNAT 100.64.0.0/10)는 **F 쪽 tailnet** 이다. C 는 그걸 재사용하지 않고 **새 Tailscale 계정/tailnet 을 만든다.** (이번 작업 = 공유 4090 에 C 전용 새 셋업)
- 4090 은 **공유 PC** → Tailscale 설치/계정 전환은 팀·4090 관리자 합의 후. 6개 도메인이 공유 4090 을 원격 호출하는 공통 인프라이므로 팀 차원 결정 사항.
- ⚠️ **한 머신 = 한 tailnet 활성**: Windows Tailscale 은 여러 계정을 등록해도 동시에 활성은 하나뿐(`tailscale switch` 로 전환). 4090 이 F tailnet 에 이미 올라가 있으면, C 계정으로 전환하는 순간 F 연결이 끊긴다. 데모 시간대가 겹치면 **팀이 하나의 공용 tailnet 으로 합치는 게 정석**(권장). C 포트폴리오 데모만이라면 C 전용 tailnet 으로 독립 시연 가능.

## 1. 4090 설정 (1회)
1. Tailscale 설치: https://tailscale.com/download (Windows) → `tailscale up` → 브라우저 로그인 → **C 새 계정/tailnet** 으로 로그인(기존 F tailnet 재사용 안 함).
2. Ollama 가 Tailscale 인터페이스에서도 듣게: 시스템 환경변수
   ```
   OLLAMA_HOST = 0.0.0.0:11434
   ```
   설정 후 **Ollama 재시작**(트레이 종료 후 재실행, 또는 서비스 재시작). 기본값은 127.0.0.1 이라 그대로면 외부에서 못 닿는다.
   - 더 사설로: `OLLAMA_HOST=<4090-Tailscale-IP>:11434` 로 두면 Tailscale 인터페이스에만 바인딩(로컬 127.0.0.1 접근은 끊김).
3. 4090 Tailscale IP 확인: `tailscale ip -4` → `100.x.y.z`(기기별 고정).
4. 모델 확인: `ollama list` 에 `careertuner-c-career-strategy-3b` 있어야 함.

## 2. 시연 노트북 설정
1. Tailscale 설치 → `tailscale up` → 4090 과 **같은 C 계정/tailnet** 으로 로그인.
2. 연결 확인:
   ```powershell
   tailscale status                                   # 4090 기기가 보여야 함
   Invoke-RestMethod http://<4090-Tailscale-IP>:11434/v1/models   # careertuner-c-career-strategy-3b 보이면 성공
   ```
3. 백엔드 env (4090 IP 만 바뀜):
   ```powershell
   $env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://<4090-Tailscale-IP>:11434/v1"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
   cd backend; .\gradlew.bat bootRun
   ```
4. 프런트: `cd frontend; npm install; npm run dev`(★dev:mock 금지) → `http://localhost:5173` → 적합도 분석 → 자체모델 설명.

## 3. 보안
- **Ollama 는 무인증.** `0.0.0.0` 바인딩은 4090의 모든 인터페이스(LAN 포함)에 11434를 연다. **Tailscale ACL**(기본: 같은 tailnet 만 접근)이 접근 경계가 된다 — 공개 인터넷엔 안 열린다(포트포워딩 안 했으니).
- 더 좁히려면: ① `OLLAMA_HOST` 를 Tailscale IP 로 바인딩(LAN 차단) ② Tailscale ACL 로 시연 노트북만 11434 허용 ③ 4090 방화벽에서 11434 를 Tailscale 인터페이스로만 허용.
- 공유 4090엔 6명 모델이 함께라 노출 범위는 팀과 합의.

## 4. 지연/안정성
- Tailscale 은 가능하면 P2P 직접 연결(아니면 DERP 릴레이). 3B 생성(설명 ~500~900토큰)은 **모델 시간이 지배적**, Tailscale 왕복은 수십~수백 ms 추가 — 데모 체감 무리 없음.
- ★ **폴백 안전망**: 시연 중 Tailscale/Ollama 가 끊겨도 C 백엔드는 **OpenAI/Mock 으로 폴백**(이미 구현·검증). 앱은 안 죽고 mock 설명으로 degrade.
- ★ **백업 권장**: "자체모델이 화면에 뜬" **사전 캡처/녹화**(4090-local 또는 Tailscale 사전 리허설)를 준비. 라이브가 막혀도 발표 증거는 남는다.

## 5. 검증 체크리스트 (시연 전 리허설)
```text
□ 4090·노트북 둘 다 tailscale status 에 서로 보임
□ 노트북에서 http://<4090-ts-ip>:11434/v1/models 에 careertuner-c-career-strategy-3b 보임
□ 노트북 백엔드 bootRun 성공(공유 DB 패치 적용 상태 전제)
□ 적합도 분석 → model=careertuner-c-career-strategy-3b, strategy=자체모델 설명
□ Ollama 잠깐 끔 → mock 폴백, 화면 안 깨짐
□ 백업 캡처 확보
```

## 6. C 관점 요약
- 코드/스키마 변경 0. **env(base-url) 한 줄**이 localhost → Tailscale IP 로 바뀔 뿐.
- 같은 패턴으로 다른 도메인(A/B/D/E/F)도 자기 모델 base-url 만 4090 Tailscale IP 로 두면 원격 호출된다 — 팀 공통 인프라.
- 미확정: 공유 4090 Tailscale 설치/계정전환 승인, F tailnet 과의 동시 활성 충돌 조율, ACL 정책.
- 실행 순서·복붙용 명령어는 `reports/14_4090_commands_uie2e_and_remote.md`(4090 통합 명령 시트).
