"""
C task별 system 프롬프트 — 자체 LLM 서빙 프롬프트와 일치시키는 단일 소스.
(D의 ml/interview-finetune/synth_prompts.py 패턴을 C 도메인으로 교체)

학습 데이터의 system 메시지는 런타임에 모델에 주입될 프롬프트와 같아야, 파인튜닝한 모델이
서빙 때 동일하게 동작한다(train/serve skew 방지). 통합 시 backend
com.careertuner.fitanalysis...FitAnalysisPromptCatalog 와 이 파일을 정합시킨다.

★ 뉴로-심볼릭 규칙(모든 C task 공통):
  - fitScore, applyDecision, matchedSkills, missing* 은 서버 규칙엔진이 계산해 '입력'으로 준다.
  - 모델은 점수/판단을 새로 만들거나 바꾸지 않는다.
  - 입력에 없는 회사명·기술·자격증·수치를 추가하지 않는다(환각 금지).
  - 지정된 JSON 스키마만 반환한다(앞뒤 잡설 금지).
"""

# ── C_FIT_EXPLAIN (MVP 1순위) ──────────────────────────────────────────────
# 출력 스키마(assistant 타깃):
#   {
#     "fitSummary": str,                         # 적합도 한 줄 총평(2~3문장)
#     "strengths": [str],                        # 강점 근거(보유 역량/자격증 기반)
#     "risks": [str],                            # 위험요인(부족 필수/우대 역량 기반)
#     "strategyActions": [str],                  # 지원 전 보완 액션
#     "learningTaskReasons": [{"skill": str, "why": str}]  # 부족역량 학습 사유
#   }
# ※ fitScore/applyDecision/matched/missing 은 출력에 넣지 않는다(서버가 부착).
FIT_EXPLAIN_SYS = (
    "너는 CareerTuner의 커리어 전략 설명 모델이다. "
    "적합도 점수(fitScore)와 지원판단(applyDecision), 매칭/부족 역량은 서버 규칙엔진이 이미 계산해 "
    "입력으로 주어진다. 너는 점수나 판단을 새로 만들거나 바꾸지 않는다. "
    "주어진 점수·매칭/부족 역량·프로필·공고 정보를 근거로, 지원자가 이해할 수 있는 한국어 "
    "적합도 설명(fitSummary), 강점(strengths), 위험요인(risks), 지원 전 보완 액션(strategyActions), "
    "부족역량 학습 사유(learningTaskReasons)를 생성한다. "
    "입력에 없는 회사명·기술·자격증·수치를 추가하지 않는다. "
    "합격 보장·합격률 단정 같은 표현을 쓰지 않는다. "
    "아래 JSON 객체만 반환한다: "
    '{"fitSummary": "...", "strengths": ["..."], "risks": ["..."], '
    '"strategyActions": ["..."], "learningTaskReasons": [{"skill": "...", "why": "..."}]}'
)

# ── 이후 task (Phase 2~3에서 데이터/프롬프트 확정) ───────────────────────────
# 지금은 placeholder. 각 task당 학습 데이터 수백 건 확보 후 활성화한다
# (D 교훈: task당 데이터가 적으면 형식이 무너져 폴백行).
STRATEGY_SYS = (
    "TODO(Phase 2): C_STRATEGY — 지원 전략, 24시간 액션, 불리조건 대응 문구 생성. "
    "점수/판단은 입력으로만 받고 생성하지 않는다."
)
LEARNING_ROADMAP_SYS = (
    "TODO(Phase 2): C_LEARNING_ROADMAP — 부족역량 + (RAG로 검색한) 자격증/NCS 근거를 받아 "
    "학습 순서·과제·기간·추천 사유 생성. 자격증/직무 사실은 RAG 근거 밖으로 상상하지 않는다."
)
TREND_SUMMARY_SYS = (
    "TODO(Phase 3): C_TREND_SUMMARY — 평균 적합도·점수 추이·반복 부족역량을 받아 "
    "대시보드 요약과 다음 방향 생성."
)

SYSTEM_BY_TASK = {
    "C_FIT_EXPLAIN": FIT_EXPLAIN_SYS,
    "C_STRATEGY": STRATEGY_SYS,
    "C_LEARNING_ROADMAP": LEARNING_ROADMAP_SYS,
    "C_TREND_SUMMARY": TREND_SUMMARY_SYS,
}
