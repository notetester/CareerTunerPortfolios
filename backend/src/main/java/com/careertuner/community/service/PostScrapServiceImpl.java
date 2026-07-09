package com.careertuner.community.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostScrap;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.dto.ScrapResponse;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.PostScrapMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 스크랩 — 즐겨찾기(BOOKMARK, 링크형)와 분리된 스냅샷 보존형.
 * 스크랩 시점의 제목/본문/작성자 표시명/카테고리를 저장해 원본 수정·삭제와 무관하게 열람한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostScrapServiceImpl implements PostScrapService {

    private final PostScrapMapper scrapMapper;
    private final CommunityPostMapper postMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Map<String, Object> toggleScrap(Long postId, boolean anonymous, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        PostScrap existing = scrapMapper.findByUserAndPost(userId, postId);
        boolean active;
        if (existing != null) {
            scrapMapper.deleteById(existing.getId());
            scrapMapper.adjustScrapCount(postId, -1);
            active = false;
            log.info("스크랩 취소 postId={} userId={}", postId, userId);
        } else {
            try {
                scrapMapper.insert(PostScrap.builder()
                        .userId(userId)
                        .postId(postId)
                        .snapshotTitle(post.getTitle())
                        .snapshotContent(post.getContent())
                        .snapshotAuthorLabel(post.isAnonymous() ? "익명" : post.getUserName())
                        .snapshotCategory(post.getCategory())
                        .anonymous(anonymous)
                        .build());
                scrapMapper.adjustScrapCount(postId, 1);
                // 스크랩 알림 — 본인 글 스크랩은 스킵. 익명이면 _ANON 타입 + 이름 미포함.
                if (!userId.equals(post.getUserId())) {
                    notifyScrapped(post, anonymous, userId);
                }
            } catch (DuplicateKeyException e) {
                // 동시 토글 충돌 흡수(UNIQUE 없는 대신 위 존재 확인이 방어선 — 초근접 중복은 그대로 수용)
                log.info("스크랩 동시 등록 충돌 흡수 postId={} userId={}", postId, userId);
            }
            active = true;
            log.info("스크랩 등록 postId={} userId={} anonymous={}", postId, userId, anonymous);
        }
        CommunityPost fresh = postMapper.findById(postId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", active);
        result.put("scrapCount", fresh != null ? fresh.getScrapCount() : 0);
        return result;
    }

    private void notifyScrapped(CommunityPost post, boolean anonymous, Long actorId) {
        try {
            notificationService.notify(Notification.builder()
                    .userId(post.getUserId())
                    .actorId(actorId)
                    .type(anonymous ? "POST_SCRAP_ANON" : "POST_SCRAP")
                    .targetType("POST")
                    .targetId(post.getId())
                    .title(anonymous ? "누군가 게시글을 스크랩했습니다." : "게시글이 스크랩되었습니다.")
                    .message("'" + truncate(post.getTitle(), 30) + "' 게시글이 "
                            + (anonymous ? "익명으로 " : "") + "스크랩되었습니다.")
                    .link("/community/posts/" + post.getId())
                    .build());
        } catch (Exception e) {
            log.error("스크랩 알림 발행 실패: postId={} actorId={}", post.getId(), actorId, e);
        }
    }

    @Override
    public ScrapResponse.Page getMyScraps(Long userId, int page, int size) {
        int offset = page * size;
        List<ScrapResponse> items = scrapMapper.findByUser(userId, offset, size).stream()
                .map(ScrapResponse::from)
                .toList();
        return new ScrapResponse.Page(items, scrapMapper.countByUser(userId), page, size);
    }

    @Override
    public ScrapResponse getScrapDetail(Long scrapId, Long userId) {
        PostScrap scrap = scrapMapper.findById(scrapId);
        if (scrap == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "스크랩을 찾을 수 없습니다.");
        }
        if (!scrap.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 스크랩만 열람할 수 있습니다.");
        }
        return ScrapResponse.from(scrap);
    }

    @Override
    @Transactional
    public void deleteScrap(Long scrapId, Long userId) {
        PostScrap scrap = scrapMapper.findById(scrapId);
        if (scrap == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "스크랩을 찾을 수 없습니다.");
        }
        if (!scrap.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 스크랩만 삭제할 수 있습니다.");
        }
        scrapMapper.deleteById(scrapId);
        if (scrap.getPostId() != null) {
            scrapMapper.adjustScrapCount(scrap.getPostId(), -1);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
