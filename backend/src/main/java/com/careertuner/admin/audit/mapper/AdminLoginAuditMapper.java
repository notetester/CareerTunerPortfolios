package com.careertuner.admin.audit.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.audit.dto.AdminLoginAuditRow;

/** 로그인 감사 목록 — 공통 그리드 계약 5종 세트 중 조회 전용 4종(bulk 없음). */
@Mapper
public interface AdminLoginAuditMapper {

    long countLogins(Map<String, Object> params);

    List<AdminLoginAuditRow> searchLogins(Map<String, Object> params);

    List<AdminLoginAuditRow> findLoginsForExport(Map<String, Object> params);

    List<AdminLoginAuditRow> findLoginsByIds(@Param("ids") List<Long> ids,
                                             @Param("sortBy") String sortBy,
                                             @Param("sortDir") String sortDir);
}
