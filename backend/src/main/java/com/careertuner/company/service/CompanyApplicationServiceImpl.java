package com.careertuner.company.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.company.domain.CompanyApplication;
import com.careertuner.company.domain.CompanyProfile;
import com.careertuner.company.dto.CompanyApplicationRequest;
import com.careertuner.company.dto.CompanyApplicationResponse;
import com.careertuner.company.dto.CompanyProfileResponse;
import com.careertuner.company.event.CompanyApplicationReviewedEvent;
import com.careertuner.company.event.CompanyApplicationSubmittedEvent;
import com.careertuner.company.mapper.CompanyApplicationMapper;
import com.careertuner.company.mapper.CompanyProfileMapper;

import lombok.RequiredArgsConstructor;

/**
 * 기업 계정 전환 파이프라인.
 *
 * <p>TT(BUSINESS_ACCOUNT_APPLICATION) 규칙 3개를 유지한다:
 * (a) 현재 role=USER 만 신청, (b) PENDING 1건 제한,
 * (c) 승인은 단일 @Transactional 에서 SELECT FOR UPDATE → role UPDATE → role 이력 → APPROVED.
 * 알림은 전부 AFTER_COMMIT 리스너(CompanyApplicationNotifyListener)에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class CompanyApplicationServiceImpl implements CompanyApplicationService {

    private static final int ADMIN_LIST_LIMIT = 200;

    private final CompanyApplicationMapper applicationMapper;
    private final CompanyProfileMapper profileMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CompanyApplicationResponse apply(Long userId, CompanyApplicationRequest request) {
        String role = applicationMapper.selectUserRole(userId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        if (!"USER".equals(role)) {
            throw new BusinessException(ErrorCode.CONFLICT, "일반 회원만 기업 계정을 신청할 수 있습니다.");
        }
        if (applicationMapper.countPendingByUserId(userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "검토 중인 기업 신청이 이미 있습니다.");
        }

        CompanyApplication application = CompanyApplication.builder()
                .userId(userId)
                .companyName(truncate(request.companyName().strip(), 100))
                .businessNumber(truncate(strip(request.businessNumber()), 50))
                .contact(truncate(request.contact().strip(), 100))
                .description(truncate(strip(request.description()), 1000))
                .status("PENDING")
                .build();
        applicationMapper.insert(application);

        eventPublisher.publishEvent(new CompanyApplicationSubmittedEvent(
                application.getId(), userId, application.getCompanyName()));
        return CompanyApplicationResponse.from(applicationMapper.findById(application.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyApplicationResponse myLatestApplication(Long userId) {
        CompanyApplication latest = applicationMapper.findLatestByUserId(userId);
        return latest == null ? null : CompanyApplicationResponse.from(latest);
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyProfileResponse myProfile(Long userId) {
        CompanyProfile profile = profileMapper.findByUserId(userId);
        return profile == null ? null : CompanyProfileResponse.from(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyApplicationResponse> adminList(String status) {
        String normalized = status == null || status.isBlank() ? null : status.strip().toUpperCase();
        return applicationMapper.findAll(normalized, ADMIN_LIST_LIMIT).stream()
                .map(CompanyApplicationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CompanyApplicationResponse approve(Long adminId, Long applicationId) {
        CompanyApplication application = requirePendingForUpdate(applicationId);

        String previousRole = applicationMapper.selectUserRoleForUpdate(application.getUserId());
        if (!"USER".equals(previousRole)) {
            throw new BusinessException(ErrorCode.CONFLICT, "신청자의 현재 권한이 USER 가 아니라 승인할 수 없습니다.");
        }
        applicationMapper.updateUserRole(application.getUserId(), "COMPANY");
        applicationMapper.insertRoleChangeHistory(
                application.getUserId(), previousRole, "COMPANY",
                "기업 회원 신청 승인: " + application.getCompanyName(), adminId);

        // 기업 프로필 생성 — 기본 신뢰등급 BASIC(재승인 케이스면 기존 프로필 유지)
        if (profileMapper.findByUserId(application.getUserId()) == null) {
            profileMapper.insert(CompanyProfile.builder()
                    .userId(application.getUserId())
                    .companyName(application.getCompanyName())
                    .businessNumber(application.getBusinessNumber())
                    .trustGrade("BASIC")
                    .build());
        }

        applicationMapper.review(applicationId, "APPROVED", null, adminId);
        eventPublisher.publishEvent(new CompanyApplicationReviewedEvent(
                applicationId, application.getUserId(), application.getCompanyName(), true, null));
        return CompanyApplicationResponse.from(applicationMapper.findById(applicationId));
    }

    @Override
    @Transactional
    public CompanyApplicationResponse reject(Long adminId, Long applicationId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유를 입력해 주세요.");
        }
        CompanyApplication application = requirePendingForUpdate(applicationId);
        applicationMapper.review(applicationId, "REJECTED", truncate(reason.strip(), 500), adminId);
        eventPublisher.publishEvent(new CompanyApplicationReviewedEvent(
                applicationId, application.getUserId(), application.getCompanyName(), false, reason.strip()));
        return CompanyApplicationResponse.from(applicationMapper.findById(applicationId));
    }

    private CompanyApplication requirePendingForUpdate(Long applicationId) {
        CompanyApplication application = applicationMapper.findByIdForUpdate(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기업 신청을 찾을 수 없습니다.");
        }
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 신청입니다.");
        }
        return application;
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
