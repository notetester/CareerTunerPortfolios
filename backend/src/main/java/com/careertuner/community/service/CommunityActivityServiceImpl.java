package com.careertuner.community.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.ActivityItem;
import com.careertuner.community.dto.ActivityPageResponse;
import com.careertuner.community.mapper.CommunityActivityMapper;
import com.careertuner.privacy.service.PrivacyPolicyService;

import lombok.RequiredArgsConstructor;

/**
 * 커뮤니티 활동 목록 — 내 것(전체)과 타인 프로필(공개범위·익명 제외 필터)을 한 서비스로 처리.
 * 공개범위 표면 키는 privacy 코어의 activity.* 6종(PrivacySurfaces)과 1:1 대응한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityActivityServiceImpl implements CommunityActivityService {

    /** 탭 → privacy 표면 키(activity.{tab}). 순서는 UI 탭 순서와 동일. */
    private static final List<String> TABS =
            List.of("posts", "comments", "replies", "likes", "bookmarks", "scraps");

    private final CommunityActivityMapper activityMapper;
    private final PrivacyPolicyService privacyPolicyService;

    @Override
    public ActivityPageResponse getMyActivity(Long userId, String tab, int page, int size) {
        String normalized = normalizeTab(tab);
        return fetch(userId, normalized, true, page, size);
    }

    @Override
    public ActivityPageResponse getUserActivity(Long profileUserId, Long viewerId, String tab, int page, int size) {
        String normalized = normalizeTab(tab);
        requireUser(profileUserId);
        if (viewerId != null && viewerId.equals(profileUserId)) {
            // 자기 프로필을 자기 경로로 열람 — 내 활동과 동일 취급
            return fetch(profileUserId, normalized, true, page, size);
        }
        // allows(대상 = 정책 소유자, 뷰어 = 행위자) — 차단 관계·관계별 공개범위 자동 평가
        boolean allowed = privacyPolicyService.allows(profileUserId, viewerId, "activity." + normalized);
        if (!allowed) {
            return new ActivityPageResponse(normalized, false, List.of(), 0, page, size);
        }
        return fetch(profileUserId, normalized, false, page, size);
    }

    @Override
    public ActivityPageResponse.TabsDto getUserActivityTabs(Long profileUserId, Long viewerId) {
        String name = requireUser(profileUserId);
        boolean self = viewerId != null && viewerId.equals(profileUserId);
        Map<String, Boolean> tabs = new LinkedHashMap<>();
        for (String tab : TABS) {
            tabs.put(tab, self || privacyPolicyService.allows(profileUserId, viewerId, "activity." + tab));
        }
        return new ActivityPageResponse.TabsDto(profileUserId, name, tabs);
    }

    /* ── 내부 ── */

    private ActivityPageResponse fetch(Long ownerId, String tab, boolean includeAnonymous, int page, int size) {
        int offset = page * size;
        List<ActivityItem> items;
        int total;
        switch (tab) {
            case "posts" -> {
                items = activityMapper.findPosts(ownerId, includeAnonymous, offset, size);
                total = activityMapper.countPosts(ownerId, includeAnonymous);
            }
            case "comments" -> {
                items = activityMapper.findComments(ownerId, includeAnonymous, false, offset, size);
                total = activityMapper.countComments(ownerId, includeAnonymous, false);
            }
            case "replies" -> {
                items = activityMapper.findComments(ownerId, includeAnonymous, true, offset, size);
                total = activityMapper.countComments(ownerId, includeAnonymous, true);
            }
            case "likes" -> {
                items = activityMapper.findLikedItems(ownerId, includeAnonymous, offset, size);
                total = activityMapper.countLikedItems(ownerId, includeAnonymous);
            }
            case "bookmarks" -> {
                items = activityMapper.findBookmarkedPosts(ownerId, includeAnonymous, offset, size);
                total = activityMapper.countBookmarkedPosts(ownerId, includeAnonymous);
            }
            case "scraps" -> {
                items = activityMapper.findScraps(ownerId, includeAnonymous, offset, size);
                total = activityMapper.countScraps(ownerId, includeAnonymous);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 활동 탭입니다: " + tab);
        }
        return new ActivityPageResponse(tab, true,
                items.stream().map(ActivityPageResponse.ItemDto::from).toList(),
                total, page, size);
    }

    private String normalizeTab(String tab) {
        String normalized = tab == null ? "posts" : tab.trim().toLowerCase();
        if (!TABS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 활동 탭입니다: " + tab);
        }
        return normalized;
    }

    private String requireUser(Long userId) {
        String name = activityMapper.findUserName(userId);
        if (name == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return name;
    }
}
