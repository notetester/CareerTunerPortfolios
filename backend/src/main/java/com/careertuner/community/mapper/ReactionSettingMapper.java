package com.careertuner.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 리액션 유지/해지 설정 저장소 — user_privacy_policy.policy_json 의
 * 별도 최상위 키 "reactionRetention" 만 읽고 쓴다(privacy 코어의 relations 키와 충돌 없음).
 */
@Mapper
public interface ReactionSettingMapper {

    /** reactionRetention JSON 객체 원문(없으면 null). */
    String findReactionRetentionJson(@Param("userId") Long userId);

    /** reactionRetention 키만 upsert — 다른 정책 키는 건드리지 않는다. */
    void upsertReactionRetentionJson(@Param("userId") Long userId, @Param("json") String json);
}
