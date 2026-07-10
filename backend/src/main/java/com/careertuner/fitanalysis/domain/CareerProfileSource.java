package com.careertuner.fitanalysis.domain;

import lombok.Data;

/**
 * 장기 커리어 전략용 사용자 프로필 원천(읽기 전용). user_profile(A)만 조회하며 C 는 수정하지 않는다.
 * 특정 지원 건과 무관한 사용자 단위 데이터라 fit_analysis(공고 단위)와 분리한다.
 */
@Data
public class CareerProfileSource {

    private String desiredJob;            // user_profile.desired_job
    private String profileSkills;         // user_profile.skills (JSON)
    private String profileCertificates;   // user_profile.certificates (JSON)
}
