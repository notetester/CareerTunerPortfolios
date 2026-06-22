# grounding validator 라이브 회귀 결과 + 오탐 정정 (2026-06-22)

> 4090 라이브 회귀(reports/28) 실측. 결과: **guard 가 너무 공격적**(과도 폴백) — 자격증 오탐을 발견·수정. 원본 로그는 artifact repo(`CareerTunerAI`, 미커밋), 여기엔 요약.

## 1. 실측 (dev `8153acf`, provider=oss, grounding-retries=1)
| | 값 |
| --- | --- |
| API 성공 | **15/15**(폴백해도 화면 계약 안 깨짐 ✅) |
| 자체모델 응답 | 8/15 (53%) · **mock 폴백 7/15 (47%)** ← 과도 |
| case 2 | **0/5 자체모델**(5/5 grounding 폴백) ← 데모 케이스 |
| case 14 | 5/5 자체모델 |
| case 35 | 3/5 자체모델, 2/5 폴백 |
| 점수/판단 | case 2/14/35 = `10/HOLD`, 보충 case 1 = `63/COMPLEMENT` — **규칙엔진 값 유지 ✅** |
| false-positive(정상 응답 막힘) | COMPLEMENT case 1 → **3/3 자체모델 통과**(HOLD 톤 등 안 막힘) |

## 2. ★ 발견 — 자격증 오탐 (case 2 100% 폴백 원인)
grounding WARN(21건) 분포: `정보처리기사` **8**, `협업` 4, `Java` 2.
- **`정보처리기사`(8) = 오탐.** case 2 지원자는 **정보처리기사 자격증을 보유**(`profileCertificates`)하지만, 규칙엔진의 `missing` 은 cert 를 스킬로 안 쳐서 거기 남는다. 모델이 "정보처리기사 보유"(사실)를 말하면 guard 가 잘못 위반 처리 → 매 호출 폴백.
- **`협업`(4)·`Java`(2) = 진짜 catch.** 실제 missing(프로필에 없음)인데 모델이 보유라 주장 → guard 정상 작동(부족 역량을 보유로 서술 차단).

## 3. 수정 (이 PR)
**보유 자격증을 grounding-missing 집합에서 제외**한다(`OssFitAnalysisAiService`):
```text
missing(부족) 에서 profileCertificates 에 있는 항목 제거
→ 보유 cert(정보처리기사)는 더 이상 '보유 서술'로 오탐되지 않는다.
→ 보유하지 않은 cert 를 모델이 날조하면 여전히 잡힌다(missing 에 남으므로).
```
단위테스트 `heldCertificateNotFlaggedEvenIfRuleEngineMissing` 추가. 기대: case 2 폴백 대폭 감소(정보처리기사 오탐 제거).

## 4. 기타 회귀 판정
- ✅ 점수/판단 = 규칙엔진(자체모델이 안 만듦).
- ✅ API 15/15 성공 — 폴백해도 화면 계약 안 깨짐.
- ✅ false-positive(정상 HOLD/COMPLEMENT 차단) 없음(case 1 통과).
- ⚠️ `ollama stop` 은 다음 호출에 자동 재로드라 mock 폴백 미유발(서버 자체 중단 안 함). **grounding 소진 시 mock 폴백은 확인됨**(case 2). 즉 폴백 경로 자체는 정상.
- 잔여: `협업`/`Java` 류는 정상 catch(모델이 missing 을 보유 주장). 폴백이 안전망이라 화면은 안전. 폴백률을 더 낮추려면 후속으로 grounding-retries 상향 또는 soft-skill 정책 검토(이번 범위 밖).

## 5. 다음
- **재회귀 필요(4090):** cert fix 후 동일 절차로 자체모델 응답률/폴백률 재측정(특히 case 2). reports/28 절차 재사용.
- 재회귀가 양호하면 그때 **E2**(고유명사/제품명 날조 방지)로 진행.
