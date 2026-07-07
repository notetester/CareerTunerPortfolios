package com.careertuner.sms;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SmsOtpMapper {

    /** id 는 useGeneratedKeys 로 채워진다. */
    void insert(SmsOtpCode code);

    /** 해당 (user, phone) 의 미검증·미만료 최신 코드 1건. */
    SmsOtpCode findLatestActive(@Param("userId") Long userId, @Param("phone") String phone);

    /** 쿨다운 판정용 — 해당 (user, phone) 의 가장 최근 발송 시각. */
    LocalDateTime findLastIssuedAt(@Param("userId") Long userId, @Param("phone") String phone);

    /** 검증 시도 횟수 1 증가. */
    void increaseAttempt(@Param("id") Long id);

    /** 검증 성공 처리(verified_at 기록). */
    void markVerified(@Param("id") Long id);
}
