package com.careertuner.reward.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.reward.domain.RewardRule;
import com.careertuner.reward.domain.UserLevelPolicy;
import com.careertuner.reward.domain.UserRewardAccount;
import com.careertuner.reward.domain.UserRewardHistory;

/** 리워드 적립/레벨 산정에 필요한 규칙·정책·이력·계정(users 리워드 컬럼) 접근. */
@Mapper
public interface RewardMapper {

    /** 사용 중(enabled=1)인 이벤트 규칙을 조회한다. 없으면 null(=미적립). */
    RewardRule findEnabledRuleByEvent(@Param("eventCode") String eventCode);

    /** 오늘(서버 날짜) 해당 이벤트로 적립된 횟수 — 일일 캡 판정용. */
    int countTodayGrantsByEvent(@Param("userId") Long userId, @Param("eventCode") String eventCode);

    /** 활성 레벨 정책을 min_point 오름차순으로 조회한다. */
    List<UserLevelPolicy> findActiveLevelsOrdered();

    /** users 리워드 컬럼 스냅샷(activity_point/user_level/credit). */
    UserRewardAccount findAccount(@Param("userId") Long userId);

    /** 누적 활동 포인트를 delta 만큼 가산한다. 성공 시 1. */
    int addActivityPoint(@Param("userId") Long userId, @Param("delta") int delta);

    /** 현재 레벨을 갱신한다. 성공 시 1. */
    int updateUserLevel(@Param("userId") Long userId, @Param("level") int level);

    /** 리워드 이력을 기록한다. */
    void insertHistory(UserRewardHistory history);

    /** 마이페이지용 최근 리워드 이력. */
    List<UserRewardHistory> findRecentHistoryByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
