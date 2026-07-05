package com.careertuner.nickname.service;

import java.util.List;

import com.careertuner.nickname.dto.ConversationProfileRequest;
import com.careertuner.nickname.dto.ConversationProfileResponse;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.dto.NicknameProfileRequest;
import com.careertuner.nickname.dto.NicknameProfileResponse;

/** 복수 닉네임 프로필 + 채팅방 전용 프로필 + 표시명 해석. */
public interface NicknameProfileService {

    // ── 내 닉네임 프로필 관리 ──

    List<NicknameProfileResponse> myProfiles(Long userId);

    NicknameProfileResponse create(Long userId, NicknameProfileRequest request);

    NicknameProfileResponse update(Long userId, Long profileId, NicknameProfileRequest request);

    void delete(Long userId, Long profileId);

    NicknameProfileResponse setDefault(Long userId, Long profileId);

    /** 닉네임 전역 사용 가능 여부(중복 검사). */
    boolean isNicknameAvailable(String nickname, Long excludeProfileId);

    // ── 채팅방 전용 프로필 ──

    ConversationProfileResponse getConversationProfile(Long userId, Long conversationId);

    ConversationProfileResponse setConversationProfile(Long userId, Long conversationId, ConversationProfileRequest request);

    // ── 표시명 해석(다른 도메인 통합 지점) ──

    /**
     * 특정 닉네임 프로필의 표시명을 해석한다.
     * profileId 가 null 이면 계정 기본 프로필/계정명으로 폴백한다.
     */
    DisplayNameResponse resolveDisplayName(Long accountId, Long profileId);
}
