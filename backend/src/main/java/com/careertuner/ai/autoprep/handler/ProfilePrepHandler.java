package com.careertuner.ai.autoprep.handler;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.mapper.ProfileMapper;

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

    @Override
    public String key() {
        return "PROFILE";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context) {
        long start = System.nanoTime();
        UserProfile profile = profileMapper.findByUserId(context.userId());
        if (profile == null) {
            return PrepStepResult.skipped("PROFILE", "프로필이 없어 건너뜀 — 프로필을 먼저 입력하세요.");
        }
        ProfileAiResult result = profileAiService.evaluate(profile, "PROFILE_SUMMARY");
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("PROFILE", "프로필 요약·역량 정리 완료", result, ms);
    }
}
