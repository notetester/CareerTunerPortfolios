package com.careertuner.fitanalysis.ai.prompt;

/**
 * 적합도 분석 계열 AI 프롬프트 모음 (C 담당, 기능별 프롬프트는 각 담당 하위 폴더에 둔다).
 *
 * <p>실제 LLM 연동 구현체가 사용할 시스템 프롬프트와 사용자 프롬프트 빌더를 보관한다.
 * 공통 AI 클라이언트/프롬프트 엔진(ai/common, ai/prompt)은 팀장 공통 영역이므로, 여기에는 C 도메인 프롬프트만 둔다.
 */
public final class FitAnalysisPromptCatalog {
    public static final String VERSION = "v0.2";

    private FitAnalysisPromptCatalog() {
    }

    /**
     * @deprecated <b>legacy 판단값 생성형 계약</b> — LLM 에게 fitScore/matched/missing/applyDecision 까지
     * 생성시키던 초기 프롬프트다. provider 판단값 소유 통일(뉴로-심볼릭 전면화) 이후 fitanalysis provider 는
     * {@link #FIT_EXPLAIN_SYSTEM_PROMPT}(설명 전용)만 사용한다. 새 코드에서 이 프롬프트로 판단값을
     * 생성시키지 말 것(점수/판단은 서버 규칙엔진 소유). 기록·비교 목적으로만 보존한다.
     */
    @Deprecated
    public static final String SYSTEM_PROMPT = """
            너는 채용 공고 요구 조건과 지원자 스펙을 비교하는 커리어 적합도 분석가다.
            반드시 한국어로, 주어진 JSON 스키마에 맞는 결과만 생성한다.
            규칙:
            - fitScore 는 0~100 정수이며 매칭/부족 근거와 일관되게 산정한다.
            - matchedSkills 는 공고 요구 역량 중 지원자가 보유한 항목, missingSkills 는 보완이 필요한 항목이다.
            - recommendedStudy 는 부족 역량을 메우는 학습 과제, recommendedCertificates 는 직무 실효성이 높은 자격증을 우선순위로 제안한다.
            - strategy 는 지원서/면접에서 강조할 점과 단기 보완 과제를 2~4문장으로 설명한다.
            - scoreBasis 는 점수 산정 근거를 설명하고, gapRecommendations 는 필수 미충족/우대 보완/장기 성장으로 구분한다.
            - learningRoadmap 은 최우선 부족 역량별로 핵심 개념 → 적용 실습 → 포트폴리오 근거화의 3단계를 만들고,
              각 단계의 학습 순서, 실습 과제, 예상 기간, 우선순위를 포함한다.
            - certificateRecommendations 는 자격증 우선순위와 추천 이유를 포함하고 과도한 자격증 준비는 낮은 우선순위로 안내한다.
            - strategyActions 는 지금 지원, 보완 후 재분석, 면접 준비 등 바로 실행할 다음 행동을 제안한다.
            - conditionMatrix 는 공고의 필수/우대 조건을 행 단위로 나열하고, 지원자 보유 여부를
              MET(충족)/PARTIAL(부분 충족)/UNMET(미충족)으로 판정하며 evidence 에 판정 근거를 적는다.
            - applyDecision 은 최종 지원 판단으로, APPLY(지원 가능)/COMPLEMENT(보완 후 지원)/HOLD(지원 보류) 중 하나를
              점수·필수 미충족 개수와 일관되게 고르고 reasons 와 지원 전 실행할 actions 를 제시한다.
            - 과장하지 말고, 근거가 약하면 보수적으로 평가한다.
            """;

    /**
     * 자체 파인튜닝 모델(C_FIT_EXPLAIN)용 시스템 프롬프트. <b>학습 데이터의 system 메시지와 동일</b>해야
     * train/serve skew 가 없다(ml/career-strategy-llm/scripts/synth_prompts.py 의 FIT_EXPLAIN_SYS 와 일치).
     * 점수/판단은 서버 규칙엔진이 계산해 입력으로 주고, 모델은 <b>설명만</b> 생성한다(뉴로-심볼릭).
     */
    public static final String FIT_EXPLAIN_SYSTEM_PROMPT =
            "너는 CareerTuner의 커리어 전략 설명 모델이다. "
            + "적합도 점수(fitScore)와 지원판단(applyDecision), 매칭/부족 역량은 서버 규칙엔진이 이미 계산해 "
            + "입력으로 주어진다. 너는 점수나 판단을 새로 만들거나 바꾸지 않는다. "
            + "주어진 점수·매칭/부족 역량·프로필·공고 정보를 근거로, 지원자가 이해할 수 있는 한국어 "
            + "적합도 설명(fitSummary), 강점(strengths), 위험요인(risks), 지원 전 보완 액션(strategyActions), "
            + "부족역량 학습 사유(learningTaskReasons)를 생성한다. "
            + "입력에 없는 회사명·기술·자격증·수치를 추가하지 않는다. "
            + "'기업 맥락'이 주어지면 지원 전략(strategyActions)과 설명에서 '이 회사는 ~하니 ~하게 접근하라'처럼 "
            + "지원 방향으로만 활용하고, 기업 정보를 지원자의 보유 역량으로 서술하지 않는다. "
            + "기업 맥락이 없으면 공고 기반으로만 설명한다. "
            + "합격 보장·합격률 단정 같은 표현을 쓰지 않는다. "
            + "아래 JSON 객체만 반환한다: "
            + "{\"fitSummary\": \"...\", \"strengths\": [\"...\"], \"risks\": [\"...\"], "
            + "\"strategyActions\": [\"...\"], \"learningTaskReasons\": [{\"skill\": \"...\", \"why\": \"...\"}]}";

    /**
     * @deprecated legacy 판단값 생성형 계약의 사용자 프롬프트({@link #SYSTEM_PROMPT} 짝). 뉴로-심볼릭 전면화
     * 이후 fitanalysis provider 는 {@link #fitExplainUserPrompt} 만 사용한다. 기록·비교 목적으로만 보존한다.
     */
    @Deprecated
    public static String userPrompt(String companyName,
                                    String jobTitle,
                                    String requiredSkills,
                                    String preferredSkills,
                                    String duties,
                                    String profileSkills,
                                    String profileCertificates,
                                    String desiredJob) {
        return """
                [공고]
                회사: %s
                직무: %s
                필수 역량: %s
                우대 역량: %s
                담당 업무: %s

                [지원자 프로필]
                희망 직무: %s
                보유 기술: %s
                보유 자격증: %s

                위 정보를 비교해 적합도 분석 결과를 생성하라.
                """.formatted(
                safe(companyName), safe(jobTitle), safe(requiredSkills), safe(preferredSkills),
                safe(duties), safe(desiredJob), safe(profileSkills), safe(profileCertificates));
    }

    /**
     * 자체모델(C_FIT_EXPLAIN) 입력 본문. 규칙엔진 사전계산값(점수/판단/매칭/부족)을 입력으로 함께 넣는다.
     * ml/career-strategy-llm/scripts/assemble_dataset.py 의 build_fit_user 와 같은 구조여야 한다(train/serve 정합).
     */
    public static String fitExplainUserPrompt(String companyName, String jobTitle, String desiredJob,
                                              String requiredSkills, String preferredSkills, String duties,
                                              String profileSkills, String profileCertificates,
                                              int fitScore, String applyDecision,
                                              String matchedSkills, String missingRequiredSkills,
                                              String missingPreferredSkills, String companyContext) {
        // 핵심 본문은 학습 데이터(assemble_dataset.py build_fit_user)와 동일 구조를 유지한다(train/serve 정합).
        String base = """
                # 적합도 분석 입력
                회사명: %s
                직무명: %s
                희망 직무: %s

                ## 공고 요구
                - 필수 스킬: %s
                - 우대 스킬: %s
                - 주요 업무: %s

                ## 지원자 프로필
                - 보유 스킬: %s
                - 보유 자격증: %s

                ## 규칙엔진 사전계산 (서버 확정값 — 변경 금지)
                - 적합도 점수(fitScore): %d
                - 지원판단(applyDecision): %s
                - 매칭 역량: %s
                - 부족 필수역량: %s
                - 부족 우대역량: %s
                """.formatted(
                safe(companyName), safe(jobTitle), safe(desiredJob),
                safe(requiredSkills), safe(preferredSkills), safe(duties),
                safe(profileSkills), safe(profileCertificates),
                fitScore, safe(applyDecision), safe(matchedSkills),
                safe(missingRequiredSkills), safe(missingPreferredSkills));
        // 기업 맥락은 있을 때만 뒤에 덧붙인다(없으면 본문이 학습 프롬프트와 byte 동일 → skew 없음).
        // 지원 회사 정보이지 지원자 보유역량이 아니다 — strategyActions/설명에서 접근 전략으로만 활용.
        if (companyContext != null && !companyContext.isBlank()) {
            return base + """

                    ## 기업 맥락 (지원 회사 정보 — 지원자 보유역량과 혼동 금지, 지원 전략 참고용)
                    %s
                    """.formatted(companyContext.trim());
        }
        return base;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "(정보 없음)" : value.trim();
    }
}
