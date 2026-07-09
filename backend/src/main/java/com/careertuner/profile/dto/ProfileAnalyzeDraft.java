package com.careertuner.profile.dto;

import java.util.List;

/**
 * 이력서 구조화 초안. Profile.tsx 폼 키와 일치.
 * preferences / desiredJob / certificates / languages 는 뽑지 않는다.
 */
public record ProfileAnalyzeDraft(
        Object education,
        Object career,
        Object projects,
        List<String> skills,
        List<String> portfolioLinks
) {
}
