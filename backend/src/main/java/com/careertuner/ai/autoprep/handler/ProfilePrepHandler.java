package com.careertuner.ai.autoprep.handler;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.profile.service.ProfilePortfolioService;

import lombok.RequiredArgsConstructor;

/**
 * ① 프로필·스펙 (A). 사용자 프로필을 AI로 요약·역량 정리해 이후 단계(적합도 등)의 토대로 삼는다.
 * 프로필이 없으면 건너뛴다. AI는 ProfileAiService(자체→규칙 폴백) 가 담당.
 */
@Component
@RequiredArgsConstructor
public class ProfilePrepHandler implements PrepStepHandler {

    private final ProfileMapper profileMapper;
    private final ProfileAiService profileAiService;
    private final ProfilePortfolioService profilePortfolioService;

    @Override
    public String key() {
        return "PROFILE";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context, PrepProgress progress) {
        long start = System.nanoTime();
        progress.substep("프로필 로드", "보유 스펙·경력 불러오기");
        UserProfile profile = profileMapper.findByUserId(context.userId());
        if (profile == null) {
            return PrepStepResult.skipped("PROFILE", "프로필이 없어 건너뜀 — 프로필을 먼저 입력하세요.");
        }
        String portfolioEvidence = profilePortfolioService.evidenceText(context.userId());
        if (portfolioEvidence != null && !portfolioEvidence.isBlank()) {
            // resumeText/selfIntro 와 섞지 않고 명시적인 PORTFOLIO 컨텍스트로 직렬화해 오분류를 막는다.
            profile.setPortfolioEvidence(portfolioEvidence);
            progress.substep("포트폴리오 연결", "프로필 포트폴리오 파일을 별도 근거로 불러오기");
        }
        progress.substep("역량 정리", "AI 요약·역량 추출");
        ProfileAiResult result = profileAiService.evaluate(profile, "PROFILE_SUMMARY");
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("PROFILE", "프로필 요약·역량 정리 완료", result, ms);
    }
}
