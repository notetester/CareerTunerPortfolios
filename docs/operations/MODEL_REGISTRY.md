# 공용 Ollama 모델 레지스트리

> 공용 4090 Ollama에 올라간 파트별 모델의 **목록·메타데이터**를 관리한다.
> ⚠️ **모델 백업(실제 GGUF/LoRA/Modelfile 복사)은 이번 작업 범위가 아니다.** 지금은 추후 백업/재현을 추적할 수 있도록 **목록 관리 체계**만 잡는다.
> ⚠️ 담당자 **실명·연락처는 기재하지 않는다**(개인정보). 파트 코드로만 관리한다.

## 관리 항목
```text
파트 / 용도 / Ollama 모델명 / base model / 파일 구성 / 현재 위치 /
백엔드 환경변수명 / 검증 케이스 / 검증 결과 / 백업 여부 / 비고
```

## 등록 현황
| 파트 | Ollama 모델명 | base model | 용도 | 백엔드 env | 검증 | 백업 |
| --- | --- | --- | --- | --- | --- | --- |
| C | `careertuner-c-career-strategy-3b` | Qwen2.5-3B-Instruct (QLoRA) | 적합도 설명·전략/스킬 추천 보강(C_FIT_EXPLAIN) | `OSS_BASE_URL` + `CAREERTUNER_ANALYSIS_AI_OSS_MODEL` | case 2 원격 E2E 성공, fallback 아님 | 추후 진행 |
| D | `interview-3b` | (담당 파트 확인) | 면접 평가(D 담당) | (D 환경변수) | (D 확인) | 추후 진행 |

> 위치: 모든 항목 **팀 공용 4090 Ollama**. base/파일 구성/검증의 빈 칸은 해당 파트가 채운다.

## C 모델 상세 (예시 양식)
```text
파트:        C
Ollama 모델명: careertuner-c-career-strategy-3b
base model:  Qwen2.5-3B-Instruct (QLoRA 4bit, LoRA r16/α32)
용도:        커리어 전략 / 스킬 추천 / 직무 매칭 설명 보강 (C_FIT_EXPLAIN)
현재 위치:    팀 공용 4090 Ollama
백엔드 표준:  OSS_BASE_URL=http://localhost:11434/v1
검증:        case 2 원격 E2E 성공, ai_usage_log=자체모델(fallback 아님)
백업 여부:    추후 진행 (대상: Q4_K_M GGUF + Modelfile + LoRA adapter)
비고:        점수/판단은 서버 규칙엔진, 모델은 설명 텍스트만 생성(뉴로-심볼릭)
```

## 운영 원칙
- 각 파트는 자기 모델 행을 추가/갱신한다. **타 파트 모델 행을 임의로 바꾸지 않는다.**
- 대표 모델명을 변경하면 해당 파트가 백엔드 환경변수와 E2E 검증 결과를 함께 갱신한다([`4090_OLLAMA_TAILSCALE_POLICY.md`](4090_OLLAMA_TAILSCALE_POLICY.md) 4장).
