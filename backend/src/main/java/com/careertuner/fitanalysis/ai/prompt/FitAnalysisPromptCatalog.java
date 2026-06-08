package com.careertuner.fitanalysis.ai.prompt;

/**
 * 적합도 분석 계열 AI 프롬프트 모음 (C 담당, 기능별 프롬프트는 각 담당 하위 폴더에 둔다).
 *
 * <p>실제 LLM 연동 구현체가 사용할 시스템 프롬프트와 사용자 프롬프트 빌더를 보관한다.
 * 공통 AI 클라이언트/프롬프트 엔진(ai/common, ai/prompt)은 팀장 공통 영역이므로, 여기에는 C 도메인 프롬프트만 둔다.
 */
public final class FitAnalysisPromptCatalog {

    private FitAnalysisPromptCatalog() {
    }

    public static final String SYSTEM_PROMPT = """
            너는 채용 공고 요구 조건과 지원자 스펙을 비교하는 커리어 적합도 분석가다.
            반드시 한국어로, 주어진 JSON 스키마에 맞는 결과만 생성한다.
            규칙:
            - fitScore 는 0~100 정수이며 매칭/부족 근거와 일관되게 산정한다.
            - matchedSkills 는 공고 요구 역량 중 지원자가 보유한 항목, missingSkills 는 보완이 필요한 항목이다.
            - recommendedStudy 는 부족 역량을 메우는 학습 과제, recommendedCertificates 는 직무 실효성이 높은 자격증을 우선순위로 제안한다.
            - strategy 는 지원서/면접에서 강조할 점과 단기 보완 과제를 2~4문장으로 설명한다.
            - 과장하지 말고, 근거가 약하면 보수적으로 평가한다.
            """;

    /**
     * 사용자 프롬프트 본문 생성. 실 LLM 구현체에서 공고/프로필 입력을 채워 호출한다.
     */
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

    private static String safe(String value) {
        return value == null || value.isBlank() ? "(정보 없음)" : value.trim();
    }
}
