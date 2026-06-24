# grounding validator — 부족 역량을 '보유'로 서술하는 환각 차단 (E1)

> reports/21·26 평가에서 드러난 가장 위험한 오류: **부족 역량(missing)을 보유 역량처럼 서술하는 grounding failure.**
> 점수/판단보다 위험하다 — 사용자가 자기 부족 역량을 '있다'고 오해할 수 있다. 재학습 없이 **제품(백엔드) 레벨**에서 막는다.

## 1. 왜 필요한가 — 측정된 사례
- **base**: clear-gap(HOLD)에서 "Go·gRPC·Kubernetes·분산시스템 **필수 스킬을 보유**"라 서술 — 4개 모두 부족. 미달자에게 적격감을 줌.
- **LoRA**: data-hold 에서 `strengths`="Spark·TensorFlow를 **갖추고 있어**" ↔ `risks`="Spark·TensorFlow도 **부재**" 자기모순(둘 다 실제 부족).
- 두 경우 다 **계약 지표(JSON 파싱·키·CJK)는 통과** — 사실 충실도는 별도 guard가 필요(계약 ≠ grounding).

## 2. 뉴로-심볼릭 장치
규칙엔진(`MockFitAnalysisAiService`)이 계산한 **matched/missing** 사실을, LLM 산문이 **뒤집지 못하게** 서버에서 검증한다. 점수/판단은 그대로 규칙엔진 소유 — **validator 는 `fitScore`/`applyDecision` 을 만들거나 바꾸지 않는다.**

## 3. 보수적 검사 (false-positive 회피)
- **검사 필드**: `fitSummary`, `strengths` 만. (`risks`·`strategyActions`·`learningTaskReasons`·`gapRecommendations` 는 부족 역량이 나오는 게 정상이라 검사 안 함.)
- **위반 판정**: 한 문장에 **'보유' 류 표현**("보유/갖추고/강점/경험 있/활용 가능/숙련/기반이 있" 등)이 있고, **'결핍·부정' 표현**("부족/없/않/미보유/부재/보유하지" 등)이 **없을 때만** + 그 문장에 missing 스킬이 등장 → 위반.
- 정상으로 두는 예(위반 아님):
  ```text
  "Kubernetes 경험이 부족하므로 학습이 필요합니다."   (결핍 문맥)
  "Spring을 보유하지 않았습니다."                    (부정)
  "즉시 지원하기보다는 역량을 보완하세요."             (스킬 보유 서술 아님)
  "Java를 보유하고 있습니다."                        (Java는 matched, missing 아님)
  ```

## 4. 동작 (retry → fallback)
```text
1. 자체모델 응답 생성(CareerAnalysisOssClient, 자체 transient 재시도 포함)
2. JSON 파싱
3. 금지키(fitScore/score/applyDecision/decision)는 읽지 않음(기존 화이트리스트)
4. grounding validator: missing 을 fitSummary/strengths 에서 '보유'로 서술하면 위반
5. 위반 → 재호출(oss.grounding-retries, 기본 1회)
6. 재시도 소진 → BusinessException → 기존 FallbackFitAnalysisAiService(OpenAI→Mock) 폴백
```
설정: `careertuner.analysis.ai.oss.grounding-retries`(기본 1). 점수/판단은 어느 경로든 규칙엔진 값.

## 5. 로그 (개인정보·전체 출력 미기록)
위반 시 WARN: `field`(fitSummary|strengths) · `missingSkill` · 매칭된 보유 표현(`phrase`) · `model` · `attempt`. **prompt/raw output 전문·개인정보는 남기지 않는다.**

## 6. 한계
- 완전한 자연어 의미 검사가 아니다(휴리스틱: 보유표현 + 결핍부정 가드 + missing 스킬 동시 등장). false-negative(놓치는 위반) 가능 — 보수적 설계상 false-positive 보다 누락을 택했다.
- 향후 **골든셋 확대 + 사람 검토**로 정밀화. 의미 단위 검증(NLI 등)은 후속.
- 검사 대상은 fitSummary(=화면 strategy)와 strengths. strengths 는 현재 결과 병합에 직접 쓰이진 않지만, 위반 시 응답 품질 신호로 보고 재호출한다.
