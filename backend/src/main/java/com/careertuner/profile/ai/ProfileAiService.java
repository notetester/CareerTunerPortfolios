package com.careertuner.profile.ai;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.profile.domain.UserProfile;

public interface ProfileAiService {

    ProfileAiResult evaluate(UserProfile profile, String featureType);

    /**
     * 사용자가 모델을 <b>명시 선택</b>하는 경로(기본 AUTO=현행 폴백). 기본 구현은 모델을 무시하고
     * {@link #evaluate(UserProfile, String)} 로 위임한다 — leaf provider 는 선택을 몰라도 되고, 진입/폴백
     * 디스패처만 이 오버라이드로 tier 를 고른다. 모델 선택은 어느 provider 가 요약·강점·보완점 텍스트를 쓰는가만
     * 바꾸고, 점수 계산(JobFamilyWeightPolicy·ProfileScoreCalculator·검증)은 provider 공통으로 유지한다.
     */
    default ProfileAiResult evaluate(UserProfile profile, String featureType, RequestedAiModel requestedModel) {
        return evaluate(profile, featureType);
    }
}
