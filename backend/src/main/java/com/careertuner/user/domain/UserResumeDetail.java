package com.careertuner.user.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이력서 상세 스펙 VO (user_resume_detail).
 *
 * <p>사람인/잡코리아식 상세 스펙(수상/대외활동/희망 근무조건 등)을 JSON 문자열로 보관한다.
 * user_profile 과 충돌 없는 확장 테이블이며, 추천 매칭이 참조할 수 있게 필드만 제공한다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResumeDetail {

    private Long userId;
    private String educationJson;
    private String careerJson;
    private String certificateJson;
    private String languageJson;
    private String awardJson;
    private String activityJson;
    private String skillJson;
    private String portfolioJson;
    private String desiredConditionJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
