# needs_policy 라벨 정책 결정 브리프 — golden60 HALLUCINATED_SKILL (2026-06-26)

> reports/41 다모델 합의의 **잔여 결정 1건**을 사람/팀이 한 번에 비준하도록 정리.
> 13후보 중 **동률(2:2)로 갈린 4건**만이 실제 판단 대상이고, **valid_error(진짜 날조)는 4건 모두 0**.
> 즉 결정은 "오류냐"가 아니라 **`acceptable_gray` vs `harness_false_positive` 라벨 컨벤션** 하나다.
>
> **[정정 2026-06-26 — reports/43]** 외부 6평가를 더한 다모델 합의(10판정)에서 동률 4건이 전부 해소됐고,
> 아래 §3 항목 **B(현장 인력 배치…)의 `harness_false_positive` 권고는 외부6 6:0·전체10 8:2 로
> `acceptable_gray` 로 번복**됐다. §2 규칙은 reports/43 §6 으로 정정한다(다개념 결합/추상접미 구는 매핑돼도 gray).
> valid_error=0 결론은 불변.

## 1. 결정 대상: 동률 4건 (나머지 9건은 이미 합의)
| # | flaggedText | 직무 | 투표(gray:fp) | normalizer |
| --- | --- | --- | --- | --- |
| A | 물류 관리 및 KPI 분석 | 물류(WMS) | 2:2 | unresolved |
| B | 현장 인력 배치와 작업 동선 조정, 협력사 커뮤니케이션 능력 | 물류(WMS) | 2:2 | unresolved |
| C | 사내 안전관리 시스템 운영 | 안전(EHS) | 2:2 | unresolved |
| D | 수요예측 기반 발주 최적화 | 물류(SCM) | 2:2 | soft_match |

(나머지 9건: agreement ≥3/4 — 추가 판정 불필요. *이 표의 라벨/카운트는 내부 4판정 스냅샷이며,
이후 외부6 통합 전체10 합의에서 9건은 harness_fp 6 · acceptable_gray 3 으로 갱신됨 — 최종 분포는 [reports/43](43_external_6judge_integration_and_audit.md) §2·§3 단일출처.*)

## 2. 제안 규칙 (한 줄로 비준 가능)
> **harness_false_positive** ⟺ 미매칭 토큰그룹이 **전부 discrete allowedSkill에 매핑**된다
> (접미어·괄호부연·연결어 변형). → 정규화가 풀어야 할 **매처 실패**.
> **acceptable_gray** ⟺ 토큰그룹 중 **하나라도 allowedSkill에 없고 duties 서술/도구명/우산개념에만 근거**한다.
> → 매처가 매칭할 대상이 애초에 없음(정상 미매칭) + 표현만 느슨함.
> **둘 다 valid_error 아님** (13/13·2모델 0 확정).

핵심 분별점: *"매처가 매칭했어야 하는데 형식 때문에 놓쳤나(=fp)" vs "매칭할 allowedSkill 자체가 없나(=gray)".*

## 3. 규칙 적용 결과 (권고 라벨)
| # | 미매칭 부분 | allowedSkill 매핑 여부 | 권고 |
| --- | --- | --- | --- |
| A | `물류 관리`(우산개념·skill 없음) + `KPI 분석`(=`물류 KPI 분석`) | **일부만** 매핑 | **acceptable_gray** |
| B | `현장 인력 배치와 작업 동선 조정`(=`현장 인력 운영`, duties 동일 문장) | **전부** 매핑 | ~~harness_false_positive~~ → **acceptable_gray** (reports/43 번복) |
| C | `사내 안전관리 시스템 운영`(duties 도구명 verbatim, **allowedSkill 없음**) | 미매핑 | **acceptable_gray** |
| D | `수요예측`(=allowed, soft) + `발주 최적화`(duties 서술·skill 없음) | **일부만** 매핑 | **acceptable_gray** |

→ 동률 4건은 **전부 acceptable_gray (gray 4 · fp 0)** 로 확정(위 §3 표 — B 가 reports/43 에서 fp→gray 로 번복됨).
**최종 13건 분포(전체10 합의 · [reports/43](43_external_6judge_integration_and_audit.md) §2·§3 단일출처): harness_fp 6 · acceptable_gray 7 · valid_error 0 · needs_policy 0.**
(동률 4건 gray 4 + 나머지 9건 fp 6·gray 3 = fp 6·gray 7. 이전 'gray 3·fp 1'·'9건 fp 5·gray 4' 는 내부4 스냅샷이라 정정.)

## 4. 하니스 함의 (별도 PR로만, 이번엔 결정만)
- **harness_fp 클래스(6건)** 는 `skill_normalizer` 의 `SPLIT_RE`/`SUFFIX_NOUNS` 확장으로 결정론적 해소 가능
  (연결어 `및`/콤마 분해 + `배치/조정/운영/활용/사용법` 등 접미어 strip 후 allowedSkill 재매칭).
- **acceptable_gray 클래스(7건)** 는 매칭 대상 allowedSkill이 없으므로 **자동 해소하지 않는다** — 별도 gray 버킷으로
  병렬 집계(날조로 카운트하지 않음). raw HALLUC 지표는 그대로 유지(병렬 원칙, reports/39).
- 이 정밀화는 **측정 하니스 한정** 변경이며 점수·판단 로직·E2 validator·D/F 모델은 불변. 적용 여부는 규칙 비준 후 결정.

## 5. 사람/팀에 묻는 것 (단 하나)
**§2 규칙을 기본값으로 채택하는가?** (채택 시 §3 라벨이 자동 확정, §4 정규화 확장이 후속 PR 후보.)
- 외부 교차판정: golden-set-002 `judge_chatgpt_packet.md`(13후보 전체) 를 다른 AI/사람에게 돌려 verdict(JSONL)를
  받으면 `judge_consensus.py` 에 파일만 추가해 5·6모델 합의로 재집계 가능(reports/41 §다음).

## 산출물
- 이 문서(메인 repo 요약/결정).
- 근거 원자료: CareerTunerAI `results/2026-06-23-golden-set-002-review/`(`consensus.jsonl`·`judge_packet.jsonl`).
