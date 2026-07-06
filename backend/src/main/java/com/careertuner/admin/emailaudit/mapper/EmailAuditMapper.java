package com.careertuner.admin.emailaudit.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.emailaudit.dto.EmailAuditRow;

/** 이메일 인증/재설정 토큰 전역 감사 조회(email_verification 읽기 전용). */
@Mapper
public interface EmailAuditMapper {

    List<EmailAuditRow> findRecent(@Param("email") String email,
                                   @Param("purpose") String purpose,
                                   @Param("status") String status,
                                   @Param("limit") int limit);
}
