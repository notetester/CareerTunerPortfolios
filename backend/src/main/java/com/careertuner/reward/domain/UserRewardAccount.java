package com.careertuner.reward.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** users 테이블의 리워드 관련 컬럼 스냅샷(activity_point/user_level/credit). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRewardAccount {
    private Long userId;
    private int activityPoint;
    private int userLevel;
    private int credit;
}
