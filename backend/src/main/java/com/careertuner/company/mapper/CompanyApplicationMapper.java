package com.careertuner.company.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.company.domain.CompanyApplication;

@Mapper
public interface CompanyApplicationMapper {

    /** id 는 useGeneratedKeys 로 채워진다. */
    void insert(CompanyApplication application);

    CompanyApplication findById(Long id);

    /** 승인 트랜잭션용 비관적 잠금 조회(SELECT ... FOR UPDATE). */
    CompanyApplication findByIdForUpdate(Long id);

    /** 내 최신 신청 1건(상태 카드용). */
    CompanyApplication findLatestByUserId(Long userId);

    int countPendingByUserId(Long userId);

    /** 관리자 목록. status 가 null/빈값이면 전체. 최신순 상한 {@code limit}. */
    List<CompanyApplication> findAll(@Param("status") String status, @Param("limit") int limit);

    /** 승인/반려 확정. PENDING 상태에서만 갱신되도록 status 조건을 건다. */
    int review(@Param("id") Long id,
               @Param("status") String status,
               @Param("rejectReason") String rejectReason,
               @Param("reviewedBy") Long reviewedBy);

    // ── users / user_role_change_history — 기업 승인 트랜잭션 전용 SQL (소유 경계 때문에 이 매퍼에 둔다) ──

    /** 현재 role 조회(비관적 잠금) — 승인 트랜잭션에서 role 이중 변경을 막는다. */
    String selectUserRoleForUpdate(Long userId);

    /** 현재 role 조회(잠금 없음) — 신청 자격 검사용. */
    String selectUserRole(Long userId);

    int updateUserRole(@Param("userId") Long userId, @Param("role") String role);

    void insertRoleChangeHistory(@Param("userId") Long userId,
                                 @Param("previousRole") String previousRole,
                                 @Param("newRole") String newRole,
                                 @Param("reason") String reason,
                                 @Param("changedBy") Long changedBy);
}
