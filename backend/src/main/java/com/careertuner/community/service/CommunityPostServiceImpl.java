package com.careertuner.community.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityInterviewReview;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.dto.CreatePostRequest;
import com.careertuner.community.dto.HotPostResponse;
import com.careertuner.community.dto.PostDetailResponse;
import com.careertuner.community.dto.PostListResponse;
import com.careertuner.community.dto.PostPageResponse;
import com.careertuner.community.dto.UpdatePostRequest;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunityTagMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.community.moderation.event.InterviewExtractRequiredEvent;
import com.careertuner.community.moderation.event.PostModerationRequiredEvent;
import com.careertuner.community.moderation.event.PostTagRequiredEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostServiceImpl implements CommunityPostService {

    private final CommunityPostMapper postMapper;
    private final CommunityTagMapper tagMapper;
    private final ReactionMapper reactionMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PostPageResponse getPosts(String category, String keyword, String sort, int page, int size) {
        int offset = page * size;
        String status = PostStatus.PUBLISHED.name();
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<CommunityPost> posts = postMapper.findAll(category, status, sort, kw, offset, size);
        int total = postMapper.countAll(category, status, kw);
        return new PostPageResponse(
                posts.stream().map(this::toListResponse).toList(),
                total, page, size
        );
    }

    @Override
    @Transactional
    public PostDetailResponse getPostDetail(Long postId, Long currentUserId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        // 본인 글이 아닐 때만 조회수 증가
        if (currentUserId == null || !currentUserId.equals(post.getUserId())) {
            incrementViewCount(postId);
        }

        CommunityInterviewReview review = null;
        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())) {
            review = postMapper.findInterviewReviewByPostId(postId);
        }

        boolean liked = false;
        boolean bookmarked = false;
        if (currentUserId != null) {
            liked = reactionMapper.findPostReaction(currentUserId, postId, "LIKE") != null;
            bookmarked = reactionMapper.findPostReaction(currentUserId, postId, "BOOKMARK") != null;
        }

        return toDetailResponse(post, review, liked, bookmarked);
    }

    @Transactional
    public void incrementViewCount(Long postId) {
        postMapper.incrementViewCount(postId);
    }

    @Override
    @Transactional
    public Long createPost(CreatePostRequest request, Long userId) {
        CommunityPost post = CommunityPost.builder()
                .userId(userId)
                .category(request.category().name())
                .title(request.title())
                .content(request.content())
                .companyName(request.companyName())
                .jobTitle(request.jobTitle())
                .interviewType(request.interviewType())
                .difficulty(request.difficulty())
                .status(PostStatus.PUBLISHED.name())
                .tagsJson(toJson(request.tags()))
                .anonymous(request.anonymous())
                .build();

        postMapper.insert(post);
        applyUserTags(post.getId(), request.tags());

        if (PostCategory.INTERVIEW_REVIEW == request.category()
                && request.interviewReview() != null) {
            upsertInterviewReview(post.getId(), request.interviewReview());
        }

        eventPublisher.publishEvent(new PostModerationRequiredEvent(post.getId()));
        eventPublisher.publishEvent(new PostTagRequiredEvent(post.getId()));
        if (PostCategory.INTERVIEW_REVIEW == request.category()) {
            eventPublisher.publishEvent(new InterviewExtractRequiredEvent(post.getId()));
        }
        return post.getId();
    }

    @Override
    @Transactional
    public void updatePost(Long postId, UpdatePostRequest request, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 게시글만 수정할 수 있습니다.");
        }

        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCompanyName(request.companyName());
        post.setJobTitle(request.jobTitle());
        post.setInterviewType(request.interviewType());
        post.setDifficulty(request.difficulty());
        post.setTagsJson(toJson(request.tags()));
        post.setAnonymous(request.anonymous());
        postMapper.update(post);
        applyUserTags(postId, request.tags());

        // 면접후기: upsert 1쿼리로 insert or update 처리
        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())
                && request.interviewReview() != null) {
            upsertInterviewReview(postId, request.interviewReview());
        }

        eventPublisher.publishEvent(new PostModerationRequiredEvent(postId));
        eventPublisher.publishEvent(new PostTagRequiredEvent(postId));
        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())) {
            eventPublisher.publishEvent(new InterviewExtractRequiredEvent(postId));
        }
    }

    @Override
    public List<HotPostResponse> getHotPosts() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<CommunityPost> posts = postMapper.findHotPosts(PostStatus.PUBLISHED.name(), since, 5);
        return posts.stream()
                .map(p -> new HotPostResponse(p.getId(), p.getTitle(), p.getCommentCount(), p.getViewCount()))
                .toList();
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 게시글만 삭제할 수 있습니다.");
        }
        postMapper.updateStatus(postId, PostStatus.DELETED.name());
    }

    // ── private helpers ──

    private void upsertInterviewReview(Long postId, CreatePostRequest.InterviewReviewRequest req) {
        CommunityInterviewReview review = CommunityInterviewReview.builder()
                .postId(postId)
                .companyName(req.companyName())
                .jobRole(req.jobRole())
                .interviewType(req.interviewType())
                .difficulty(req.difficulty())
                .interviewDate(req.interviewDate() != null ? LocalDate.parse(req.interviewDate()) : null)
                .resultStatus(req.resultStatus())
                .questionsJson(toJson(req.questions()))
                .build();
        postMapper.upsertInterviewReview(review);
    }

    private PostListResponse toListResponse(CommunityPost post) {
        PostCategory cat = PostCategory.valueOf(post.getCategory());
        return new PostListResponse(
                post.getId(),
                post.getCategory(),
                cat.getLabel(),
                post.getTitle(),
                post.getContent(),
                parseJsonArray(post.getTagsJson()),
                new PostListResponse.AuthorDto(
                        post.isAnonymous() ? null : post.getUserId(),
                        post.isAnonymous() ? "익명" : post.getUserName(),
                        post.isAnonymous()
                ),
                new PostListResponse.StatsDto(
                        post.getViewCount(),
                        post.getCommentCount(),
                        post.getLikeCount(),
                        post.getBookmarkCount()
                ),
                post.getStatus(),
                post.getCreatedAt(),
                post.getCompanyName(),
                post.getJobTitle()
        );
    }

    private PostDetailResponse toDetailResponse(CommunityPost post, CommunityInterviewReview review,
                                                boolean liked, boolean bookmarked) {
        PostCategory cat = PostCategory.valueOf(post.getCategory());
        PostDetailResponse.InterviewReviewDto reviewDto = null;

        if (review != null) {
            reviewDto = new PostDetailResponse.InterviewReviewDto(
                    review.getCompanyName(),
                    review.getJobRole(),
                    review.getInterviewType(),
                    review.getDifficulty(),
                    review.getInterviewDate() != null ? review.getInterviewDate().toString() : null,
                    review.getResultStatus(),
                    parseJsonArray(review.getQuestionsJson())
            );
        }

        return new PostDetailResponse(
                post.getId(),
                post.getCategory(),
                cat.getLabel(),
                post.getTitle(),
                post.getContent(),
                parseJsonArray(post.getTagsJson()),
                new PostListResponse.AuthorDto(
                        post.isAnonymous() ? null : post.getUserId(),
                        post.isAnonymous() ? "익명" : post.getUserName(),
                        post.isAnonymous()
                ),
                new PostListResponse.StatsDto(
                        post.getViewCount(),
                        post.getCommentCount(),
                        post.getLikeCount(),
                        post.getBookmarkCount()
                ),
                post.getStatus(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getCompanyName(),
                post.getJobTitle(),
                reviewDto,
                liked,
                bookmarked
        );
    }

    /**
     * 사용자 직접 입력 태그를 정규화 테이블(community_post_tag, is_ai=0)에 반영한다.
     * 설계규칙(f_schema.sql:216-219): post_tag 가 원본, tags_json 은 표시 캐시.
     * 사용자 태그를 여기 넣어야 이후 AI 태깅의 tags_json 재구성(findTagNamesByPostId)이 사용자 태그를 보존한다.
     * AI 태그 처리(PostModerationService.applyAiTags)와 동형이며 대상만 is_ai=0. 수정 시 기존 사용자 태그를 갈아끼운다.
     */
    private void applyUserTags(Long postId, List<String> tags) {
        // 1. 기존 사용자 태그 usage_count 감소 후 삭제 (수정 시 재반영). AI 태그(is_ai=1)는 건드리지 않는다.
        for (Long tagId : tagMapper.findUserTagIds(postId)) {
            tagMapper.decrementUsageCount(tagId);
        }
        tagMapper.deleteUserPostTags(postId);

        if (tags == null) return;

        // 2. 새 사용자 태그 삽입 (마스터 INSERT IGNORE → post_tag is_ai=0). 신규 INSERT(affected==1)일 때만 usage 증가.
        for (String tagName : tags) {
            String trimmed = tagName == null ? "" : tagName.strip();
            if (trimmed.isEmpty()) continue;
            tagMapper.insertTag(trimmed);
            Long tagId = tagMapper.findIdByName(trimmed);
            if (tagId == null) continue;
            int affected = tagMapper.insertPostTag(postId, tagId, false);
            if (affected == 1) {
                tagMapper.incrementUsageCount(tagId);
            }
        }
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("JSON 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
