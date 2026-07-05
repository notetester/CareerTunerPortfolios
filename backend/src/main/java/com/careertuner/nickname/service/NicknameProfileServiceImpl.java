package com.careertuner.nickname.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.nickname.domain.ConversationMemberProfile;
import com.careertuner.nickname.domain.NicknameProfile;
import com.careertuner.nickname.dto.ConversationProfileRequest;
import com.careertuner.nickname.dto.ConversationProfileResponse;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.dto.NicknameProfileRequest;
import com.careertuner.nickname.dto.NicknameProfileResponse;
import com.careertuner.nickname.mapper.NicknameProfileMapper;

import lombok.RequiredArgsConstructor;

/**
 * 복수 닉네임 프로필 서비스.
 *
 * <p>핵심 규칙:
 * <ul>
 *   <li>계정당 최소 1개의 기본 프로필 유지(첫 생성 시 자동 기본, 마지막 프로필 삭제 금지).</li>
 *   <li>닉네임은 전역 UNIQUE — 사전 검사 + UNIQUE 위반 catch 로 이중 방어.</li>
 *   <li>제재/신고/차단은 계정 단위이므로 표시명 해석은 항상 accountId 를 함께 반환한다.</li>
 * </ul></p>
 */
@Service
@RequiredArgsConstructor
public class NicknameProfileServiceImpl implements NicknameProfileService {

    private static final int MAX_PROFILES_PER_ACCOUNT = 10;
    private static final String ANONYMOUS_LABEL = "익명";

    private final NicknameProfileMapper mapper;

    @Override
    @Transactional
    public List<NicknameProfileResponse> myProfiles(Long userId) {
        ensureDefaultProfile(userId);
        return mapper.findByUserId(userId).stream()
                .map(NicknameProfileResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public NicknameProfileResponse create(Long userId, NicknameProfileRequest request) {
        String nickname = requireNickname(request.nickname());
        int existing = mapper.countByUserId(userId);
        if (existing >= MAX_PROFILES_PER_ACCOUNT) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "닉네임 프로필은 최대 %d개까지 만들 수 있습니다.".formatted(MAX_PROFILES_PER_ACCOUNT));
        }
        if (mapper.countByNicknameExcluding(nickname, null) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }

        NicknameProfile profile = NicknameProfile.builder()
                .userId(userId)
                .nickname(nickname)
                .avatarFileId(request.avatarFileId())
                .bio(blankToNull(request.bio()))
                .isDefault(existing == 0) // 첫 프로필은 자동 기본
                .status("ACTIVE")
                .build();
        try {
            mapper.insert(profile);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
        return NicknameProfileResponse.from(mapper.findById(profile.getId()));
    }

    @Override
    @Transactional
    public NicknameProfileResponse update(Long userId, Long profileId, NicknameProfileRequest request) {
        NicknameProfile existing = requireOwned(userId, profileId);
        String nickname = requireNickname(request.nickname());
        if (mapper.countByNicknameExcluding(nickname, profileId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
        existing.setNickname(nickname);
        existing.setAvatarFileId(request.avatarFileId());
        existing.setBio(blankToNull(request.bio()));
        try {
            mapper.update(existing);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }
        return NicknameProfileResponse.from(mapper.findById(profileId));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long profileId) {
        NicknameProfile existing = requireOwned(userId, profileId);
        if (mapper.countByUserId(userId) <= 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "기본 프로필은 최소 1개가 필요합니다. 새 프로필을 만든 뒤 삭제해 주세요.");
        }
        mapper.hide(profileId, userId);
        // 기본 프로필을 삭제했다면 남은 것 중 하나를 기본으로 승격
        if (existing.isDefault()) {
            NicknameProfile next = mapper.findDefaultByUserId(userId);
            if (next != null) {
                mapper.clearDefault(userId);
                mapper.markDefault(next.getId(), userId);
            }
        }
    }

    @Override
    @Transactional
    public NicknameProfileResponse setDefault(Long userId, Long profileId) {
        requireOwned(userId, profileId);
        mapper.clearDefault(userId);
        mapper.markDefault(profileId, userId);
        return NicknameProfileResponse.from(mapper.findById(profileId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname, Long excludeProfileId) {
        String normalized = requireNickname(nickname);
        return mapper.countByNicknameExcluding(normalized, excludeProfileId) == 0;
    }

    // ── 채팅방 전용 프로필 ──

    @Override
    @Transactional(readOnly = true)
    public ConversationProfileResponse getConversationProfile(Long userId, Long conversationId) {
        requireMember(conversationId, userId);
        ConversationMemberProfile mapping = mapper.findConversationProfile(conversationId, userId);
        if (mapping == null) {
            return new ConversationProfileResponse(conversationId, userId, null, null, false, false);
        }
        String nickname = resolveNickname(mapping.getNicknameProfileId(), mapping.isAnonymous());
        return new ConversationProfileResponse(conversationId, userId,
                mapping.getNicknameProfileId(), nickname, mapping.isAnonymous(), true);
    }

    @Override
    @Transactional
    public ConversationProfileResponse setConversationProfile(Long userId, Long conversationId,
                                                              ConversationProfileRequest request) {
        requireMember(conversationId, userId);

        Long profileId = request.nicknameProfileId();
        boolean anonymous;
        if (profileId != null) {
            // 지정 프로필은 반드시 본인 소유여야 하고, 익명과 상호배타
            requireOwned(userId, profileId);
            anonymous = false;
        } else {
            // 프로필 미지정: 익명 참가로 처리
            anonymous = true;
        }

        mapper.upsertConversationProfile(ConversationMemberProfile.builder()
                .conversationId(conversationId)
                .userId(userId)
                .nicknameProfileId(profileId)
                .anonymous(anonymous)
                .build());

        String nickname = resolveNickname(profileId, anonymous);
        return new ConversationProfileResponse(conversationId, userId, profileId, nickname, anonymous, true);
    }

    // ── 표시명 해석 ──

    @Override
    @Transactional(readOnly = true)
    public DisplayNameResponse resolveDisplayName(Long accountId, Long profileId) {
        if (profileId != null) {
            NicknameProfile profile = mapper.findById(profileId);
            // 프로필이 실제로 그 계정 소유일 때만 표시 계층을 적용(귀속은 계정 단위)
            if (profile != null && profile.getUserId().equals(accountId) && "ACTIVE".equals(profile.getStatus())) {
                return new DisplayNameResponse(accountId, profile.getId(), profile.getNickname(),
                        profile.getAvatarFileId(), false);
            }
        }
        // 폴백: 계정 기본 프로필 → 없으면 계정명
        NicknameProfile defaultProfile = mapper.findDefaultByUserId(accountId);
        if (defaultProfile != null) {
            return new DisplayNameResponse(accountId, defaultProfile.getId(), defaultProfile.getNickname(),
                    defaultProfile.getAvatarFileId(), false);
        }
        String accountName = mapper.findAccountName(accountId);
        return new DisplayNameResponse(accountId, null,
                accountName != null ? accountName : "회원", null, false);
    }

    // ── 내부 헬퍼 ──

    /** 계정에 프로필이 하나도 없으면 계정명 기반 기본 프로필을 1개 자동 생성한다. */
    private void ensureDefaultProfile(Long userId) {
        if (mapper.countByUserId(userId) > 0) {
            return;
        }
        String base = mapper.findAccountName(userId);
        String candidate = buildUniqueNickname(base != null && !base.isBlank() ? base : "회원");
        try {
            mapper.insert(NicknameProfile.builder()
                    .userId(userId)
                    .nickname(candidate)
                    .isDefault(true)
                    .status("ACTIVE")
                    .build());
        } catch (DuplicateKeyException e) {
            // 극히 드문 경합 — 재시도 없이 무시(다음 조회 시 존재)
        }
    }

    /** base 를 기반으로 전역 UNIQUE 를 만족하는 닉네임 후보를 만든다(최대 30자). */
    private String buildUniqueNickname(String base) {
        String trimmed = base.length() > 24 ? base.substring(0, 24) : base;
        if (mapper.countByNicknameExcluding(trimmed, null) == 0) {
            return trimmed;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = trimmed + i;
            if (candidate.length() <= 30 && mapper.countByNicknameExcluding(candidate, null) == 0) {
                return candidate;
            }
        }
        return trimmed + System.currentTimeMillis() % 100000;
    }

    private String resolveNickname(Long profileId, boolean anonymous) {
        if (anonymous || profileId == null) {
            return ANONYMOUS_LABEL;
        }
        NicknameProfile profile = mapper.findById(profileId);
        return profile != null ? profile.getNickname() : ANONYMOUS_LABEL;
    }

    private NicknameProfile requireOwned(Long userId, Long profileId) {
        NicknameProfile profile = mapper.findById(profileId);
        if (profile == null || !profile.getUserId().equals(userId) || !"ACTIVE".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "닉네임 프로필을 찾을 수 없습니다.");
        }
        return profile;
    }

    private void requireMember(Long conversationId, Long userId) {
        if (mapper.isActiveMember(conversationId, userId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 대화방의 참여자가 아닙니다.");
        }
    }

    private String requireNickname(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임을 입력해 주세요.");
        }
        if (trimmed.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 30자 이하여야 합니다.");
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
