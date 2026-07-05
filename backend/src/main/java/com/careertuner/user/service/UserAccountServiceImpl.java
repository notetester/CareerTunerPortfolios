package com.careertuner.user.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.user.domain.User;
import com.careertuner.user.domain.UserResumeDetail;
import com.careertuner.user.dto.AccountInfoResponse;
import com.careertuner.user.dto.UserResumeDetailRequest;
import com.careertuner.user.dto.UserResumeDetailResponse;
import com.careertuner.user.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 계정 확충 + 이력서 상세 스펙 서비스.
 *
 * <p>로그인 아이디는 최초 1회만 설정(변경 불가), 전화번호/아이디는 전역 UNIQUE.
 * 이력서 상세는 user_profile 저장 패턴과 동일하게 JSON 직렬화 후 upsert 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AccountInfoResponse accountInfo(Long userId) {
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional
    public AccountInfoResponse setLoginId(Long userId, String loginId) {
        User user = requireUser(userId);
        if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "로그인 아이디는 이미 설정되어 변경할 수 없습니다.");
        }
        String normalized = loginId == null ? "" : loginId.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디를 입력해 주세요.");
        }
        if (mapper.countByLoginId(normalized) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        try {
            // login_id IS NULL 인 경우에만 반영 — 경합 시 0행이면 이미 설정됨
            if (mapper.setLoginIdIfAbsent(userId, normalized) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "로그인 아이디는 이미 설정되어 변경할 수 없습니다.");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional
    public AccountInfoResponse setPhone(Long userId, String phone) {
        requireUser(userId);
        String normalized = normalizePhone(phone);
        if (mapper.countByPhone(normalized, userId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 전화번호입니다.");
        }
        try {
            mapper.updatePhone(userId, normalized);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 등록된 전화번호입니다.");
        }
        return toAccountInfo(requireUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResumeDetailResponse getResumeDetail(Long userId) {
        UserResumeDetail detail = mapper.findResumeDetail(userId);
        return toResumeResponse(userId, detail);
    }

    @Override
    @Transactional
    public UserResumeDetailResponse saveResumeDetail(Long userId, UserResumeDetailRequest request) {
        UserResumeDetail detail = UserResumeDetail.builder()
                .userId(userId)
                .educationJson(json(request.education()))
                .careerJson(json(request.career()))
                .certificateJson(json(request.certificates()))
                .languageJson(json(request.languages()))
                .awardJson(json(request.awards()))
                .activityJson(json(request.activities()))
                .skillJson(json(request.skills()))
                .portfolioJson(json(request.portfolios()))
                .desiredConditionJson(json(request.desiredCondition()))
                .build();
        mapper.upsertResumeDetail(detail);
        return toResumeResponse(userId, mapper.findResumeDetail(userId));
    }

    // ── 매핑 ──

    private AccountInfoResponse toAccountInfo(User user) {
        return new AccountInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getLoginId(),
                user.getLoginId() != null && !user.getLoginId().isBlank(),
                user.getPhone(),
                user.isPhoneVerified(),
                user.isPasswordEnabled(),
                mapper.findLinkedProviders(user.getId()));
    }

    private UserResumeDetailResponse toResumeResponse(Long userId, UserResumeDetail detail) {
        if (detail == null) {
            return new UserResumeDetailResponse(userId, null, null, null, null, null, null, null, null, null, null);
        }
        return new UserResumeDetailResponse(
                userId,
                object(detail.getEducationJson()),
                object(detail.getCareerJson()),
                object(detail.getCertificateJson()),
                object(detail.getLanguageJson()),
                object(detail.getAwardJson()),
                object(detail.getActivityJson()),
                object(detail.getSkillJson()),
                object(detail.getPortfolioJson()),
                object(detail.getDesiredConditionJson()),
                detail.getUpdatedAt());
    }

    private User requireUser(Long userId) {
        User user = mapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        return user;
    }

    private String normalizePhone(String phone) {
        String trimmed = phone == null ? "" : phone.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전화번호를 입력해 주세요.");
        }
        // 하이픈 제거 후 재포맷(01012345678 → 010-1234-5678)로 통일 저장
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return "%s-%s-%s".formatted(digits.substring(0, 3), digits.substring(3, 7), digits.substring(7));
        }
        if (digits.length() == 10) {
            return "%s-%s-%s".formatted(digits.substring(0, 3), digits.substring(3, 6), digits.substring(6));
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "휴대폰 번호 형식이 올바르지 않습니다.");
    }

    private Object object(String jsonValue) {
        if (jsonValue == null || jsonValue.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonValue, Object.class);
        } catch (Exception e) {
            return jsonValue;
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이력서 상세 JSON 형식이 올바르지 않습니다.");
        }
    }
}
