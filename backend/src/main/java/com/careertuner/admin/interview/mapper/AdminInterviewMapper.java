package com.careertuner.admin.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.interview.dto.AdminInterviewAiFailureRow;
import com.careertuner.admin.interview.dto.AdminInterviewSessionRow;
import com.careertuner.admin.interview.dto.AdminInterviewSummary;

@Mapper
public interface AdminInterviewMapper {

    List<AdminInterviewSessionRow> findSessions(
            @Param("keyword") String keyword,
            @Param("mode") String mode,
            @Param("hasReport") Boolean hasReport,
            @Param("offset") int offset,
            @Param("size") int size);

    long countSessions(
            @Param("keyword") String keyword,
            @Param("mode") String mode,
            @Param("hasReport") Boolean hasReport);

    AdminInterviewSummary findSummary();

    AdminInterviewSessionRow findSession(@Param("id") Long id);

    String findReport(@Param("id") Long id);

    /** 면접 AI 기능 실패 이력(INTERVIEW_* feature_type, status=FAILED). */
    List<AdminInterviewAiFailureRow> findAiFailures(@Param("limit") int limit);

    /** 관리자 운영 메모 조회 (상세 전용 — 목록 SessionSelect 엔 넣지 않아 마이그레이션 전에도 목록은 동작). */
    String findAdminMemo(@Param("id") Long id);

    /** 메모 수정 트랜잭션에서 최신 변경 전 값을 읽고 행을 잠근다. */
    String findAdminMemoForUpdate(@Param("id") Long id);

    /** 관리자 운영 메모 갱신. */
    int updateAdminMemo(@Param("id") Long id, @Param("adminMemo") String adminMemo);
}
