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

## 0. 사전 — ★4090 점검 결과(2026-06-21): Tailscale 미설치 → 신규 가입 필요
4090(hostname `chanssick`)을 직접 조회한 결과:
```text
- Tailscale 설치 여부: 미설치 (CLI/서비스 없음)
- localhost 가 4090 자신의 주소인지: 아님 (F가 쓰던 다른 장치의 Tailscale 주소)
- 4090 이 F tailnet 에 로그인된 상태인지: 아님
```
이 사실로 정리되는 것:
- `application.yaml:163` 의 `localhost`(model `gemma4`, `ai.chatbot`/`ai.tagging` 이 사용)는 **F의 다른 장치** 주소다. **4090 주소가 아니므로 C 자체모델 원격 호출에는 쓸 수 없다**(그 장치엔 `careertuner-c-career-strategy-3b` 가 없다). → "기존 IP 재사용" 경로는 성립하지 않는다.
- C 원격 호출을 하려면 **4090 을 C 또는 팀 tailnet 에 새로 가입시켜 새 `100.x` 주소를 발급**받아야 한다. Ollama endpoint = `http://<4090-new-tailscale-ip>:11434/v1`.
- 4090 은 현재 Tailscale 미설치라 **F tailnet→C tailnet 으로 switch 하는 상황이 아니다.** 따라서 F 기존 장치의 `localhost` 와 **직접 충돌하지 않는다.** (신규 설치이지 전환이 아님)
- ⚠️ 일반 원칙(유지): 향후 **동일한 4090 PC** 를 여러 tailnet 사이에서 전환하면 한 번에 **하나의 active tailnet 만** 쓸 수 있다(`tailscale switch`). 지금은 해당 없음.
- 4090 은 **공유 PC** → Tailscale 설치 자체는 팀·관리자 동의 후(공통 인프라 변경).

## 0-1. 권장 구조
```text
F 기존 장치:
- 기존 F tailnet 유지 · localhost 유지 · C 작업에서 사용하지 않음

4090 공유 PC:
- 현재 Tailscale 미설치
- C 또는 팀 tailnet 에 새로 가입 → 새 100.x.x.x 주소 발급
- Ollama endpoint: http://<4090-new-tailscale-ip>:11434/v1

노트북/백엔드 실행 PC:
- 4090 과 같은 C 또는 팀 tailnet 에 가입
- CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL=http://<4090-new-tailscale-ip>:11434/v1
```

## 1. 4090 설정 (1회) — 신규 설치
1. Tailscale 설치: https://tailscale.com/download (Windows) → `tailscale up` → 브라우저 로그인 → **C 또는 팀 tailnet** 에 새로 가입(4090 은 미설치 상태라 신규 등록, 새 100.x 주소 발급).
2. Ollama 가 Tailscale 인터페이스에서도 듣게: 시스템 환경변수
   ```
   OLLAMA_HOST = 0.0.0.0:11434
   ```
   설정 후 **Ollama 재시작**(트레이 종료 후 재실행, 또는 서비스 재시작). 기본값은 127.0.0.1 이라 그대로면 외부에서 못 닿는다.
   - 더 사설로: `OLLAMA_HOST=<4090-Tailscale-IP>:11434` 로 두면 Tailscale 인터페이스에만 바인딩(로컬 127.0.0.1 접근은 끊김).
3. 4090 Tailscale IP 확인: `tailscale ip -4` → `100.x.y.z`(기기별 고정).
4. 모델 확인: `ollama list` 에 `careertuner-c-career-strategy-3b` 있어야 함.

## 2. 시연 노트북 설정
1. Tailscale 설치 → `tailscale up` → 4090 과 **같은 C 또는 팀 tailnet** 에 가입.
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
- 미확정: 공유 4090 Tailscale 설치 승인, C 전용 vs 팀 공용 tailnet 선택, ACL 정책. (4090 미설치라 F 전환 충돌은 해당 없음)
- 실행 순서·복붙용 명령어는 `reports/14_4090_commands_uie2e_and_remote.md`(4090 통합 명령 시트).
