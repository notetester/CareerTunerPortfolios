package com.careertuner.company.service;

import java.util.List;

import com.careertuner.company.dto.CompanyApplicationRequest;
import com.careertuner.company.dto.CompanyApplicationResponse;
import com.careertuner.company.dto.CompanyProfileResponse;

public interface CompanyApplicationService {

    /** 기업 계정 전환 신청. 현재 role=USER 만, PENDING 1건 제한. */
    CompanyApplicationResponse apply(Long userId, CompanyApplicationRequest request);

    /** 내 최신 신청 1건. 없으면 null(프런트가 신청 폼을 보여준다). */
    CompanyApplicationResponse myLatestApplication(Long userId);

    /** 내 기업 프로필. 승인된 COMPANY 계정이 아니면 null. */
    CompanyProfileResponse myProfile(Long userId);

    // ── 관리자 ──

    List<CompanyApplicationResponse> adminList(String status);

    /**
     * 승인 — 단일 트랜잭션: 신청 FOR UPDATE → users.role='COMPANY' →
     * user_role_change_history INSERT → company_profile 생성 → APPROVED 확정.
     */
    CompanyApplicationResponse approve(Long adminId, Long applicationId);

    /** 반려 — 사유 필수. */
    CompanyApplicationResponse reject(Long adminId, Long applicationId, String reason);
}
