# 시연 PC 빠른 세팅 · 점검 절차

> 시연 장소·시연 PC는 **아직 미정**이다. 특정 머신을 고정하지 않고, **어떤 PC에서든** 빠르게 공용 4090 Ollama에 붙어 검증할 수 있도록 절차와 점검 스크립트를 둔다.

## 1. 전제
- 발표장 PC / 개인 노트북(백업·후보 시연 머신) / 예비 PC **모두 가능성을 열어둔다.**
- VPN(메시 VPN) 클라이언트 **설치·로그인·접근 권한 수락은 사람이 직접** 한다(보안상 자동화하지 않는다).
- 접근 권한을 받은 뒤(권한 부여 방식은 저장소 외부 운영에서 관리) 아래 점검을 수행한다.

## 2. 점검 순서
```text
1. VPN 클라이언트 설치/연결 상태 확인
2. 공용 서버 도달: http://localhost:11434/v1/models 응답
3. 백엔드 표준 환경변수: OSS_BASE_URL=http://localhost:11434/v1
4. C 모델 careertuner-c-career-strategy-3b 호출 가능 확인
5. case 2 기준 E2E 성공 확인
6. ai_usage_log에서 자체모델 사용 / fallback 아님 확인
```
- 점검 스크립트(읽기 전용): [`scripts/check_demo_client.ps1`](scripts/check_demo_client.ps1)
- 이 스크립트는 **VPN 로그인 자체를 대신하지 않는다.** 설치·연결·API 도달 상태를 빠르게 점검하고, 실패 시 원인 체크리스트를 출력한다.

## 3. 검증 기준 (어떤 PC든 공통)
```text
- VPN으로 공용 서버(localhost) 접근 가능
- http://localhost:11434/v1/models 응답 성공
- OSS_BASE_URL=http://localhost:11434/v1 사용
- C 모델 careertuner-c-career-strategy-3b 호출 성공
- case 2 기준 E2E 성공
- ai_usage_log에서 자체모델 사용 / fallback 아님 확인
```
- `case 2`는 현재 성공 이력이 있는 **기본 검증 케이스**로 둔다. **시연 머신 자체는 고정하지 않는다.**

## 4. 시연 운영 우선순위
```text
1순위: 실제 발표 환경에서 사용 가능한 PC
2순위: 개인 노트북(백업/후보 시연 머신) — HDMI / 화면 공유
3순위: 사전 녹화 영상 또는 성공 캡처
```
- 발표장 PC에 VPN 설치·로그인이 막히면 **개인 노트북으로 전환**할 수 있어야 한다.

## 5. 백엔드 환경변수 (참고)
```text
시연 PC / 팀원 PC:   OSS_BASE_URL=http://localhost:11434/v1
4090 내부 로컬 디버깅: OSS_BASE_URL=http://localhost:11434/v1
공통:                CAREERTUNER_ANALYSIS_AI_PROVIDER=oss
                     CAREERTUNER_ANALYSIS_AI_OSS_MODEL=careertuner-c-career-strategy-3b
                     CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS=1280
```
> 표준 주소·모델명·API 경로 규약은 [`4090_OLLAMA_TAILSCALE_POLICY.md`](4090_OLLAMA_TAILSCALE_POLICY.md) 참고.
