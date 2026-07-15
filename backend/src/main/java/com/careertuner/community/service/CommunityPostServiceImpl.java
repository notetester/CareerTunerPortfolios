package com.careertuner.community.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityAuthorVisibility;
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
import com.careertuner.community.domain.PostReaction;
import com.careertuner.community.domain.ReactionType;
import com.careertuner.community.event.PostEditedEvent;
import com.careertuner.community.event.PostPublishedEvent;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunitySubscriptionMapper;
import com.careertuner.community.mapper.CommunityTagMapper;
import com.careertuner.community.mapper.PostScrapMapper;
import com.careertuner.community.mapper.ReactionMapper;
import com.careertuner.community.moderation.event.InterviewExtractRequiredEvent;
import com.careertuner.community.moderation.domain.AiResultStatus;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.event.PostImageModerationRequiredEvent;
import com.careertuner.community.moderation.event.PostModerationRequiredEvent;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.moderation.event.PostTagRequiredEvent;
import com.careertuner.nickname.dto.DisplayNameQuery;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.service.NicknameProfileService;
import com.careertuner.privacy.domain.ContentAuthorRow;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.privacy.service.PrivacySurfaces;
import com.careertuner.reward.service.RewardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostServiceImpl implements CommunityPostService {

    private static final String DELETED_USER_STATUS = "DELETED";
    private static final String DELETED_USER_LABEL = "탈퇴한 사용자";

    /** 차단 작성자 게시글 톰스톤 문구 (docs/PERSONAL_BLOCK_POLICY.md §4 — silent deny). */
    private static final String BLOCKED_POST_TOMBSTONE = "차단한 사용자의 게시글입니다.";

    private final CommunityPostMapper postMapper;
    private final CommunityTagMapper tagMapper;
    private final ReactionMapper reactionMapper;
    private final PostScrapMapper scrapMapper;
    private final CommunitySubscriptionMapper subscriptionMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PostAiResultMapper aiResultMapper;
    private final PrivacyPolicyService privacyPolicyService;
    private final NicknameProfileService nicknameProfileService;
    private final PersonalizedFeedService personalizedFeedService;
    /**
     * 신고 누적 블러 임계·작성 rate-limit 값은 검열/중재 정책 콘솔에서 런타임 편집한다.
     * (이전 community.report.blur-threshold / community.post.rate-limit.* @Value 하드코딩을 대체)
     */
    private final com.careertuner.community.moderation.service.ModerationSettingService moderationSettingService;
    /** 활동 리워드 적립(글 작성 시 COMMUNITY_POST_CREATE). 규칙 off 면 미적립. */
    private final RewardService rewardService;

    /** 개인화 피드 정렬 키 — 이 값이면 PersonalizedFeedService(7:3 혼합)로 위임한다. */
    private static final String SORT_PERSONALIZED = "personalized";

    /** 리워드 적립은 본 작업 실패로 이어지지 않도록 예외를 흡수한다. */
    private void grantRewardSafely(Long userId, String eventCode, String refType, Long refId) {
        try {
            rewardService.grant(userId, eventCode, refType, refId);
        } catch (RuntimeException e) {
            log.warn("리워드 적립 실패 event={} userId={} : {}", eventCode, userId, e.getMessage());
        }
    }

    @Override
    public PostPageResponse getPosts(String category, String keyword, String sort, int page, int size, Long viewerId) {
        String status = PostStatus.PUBLISHED.name();
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        CommunityAuthorVisibility visibility = viewerId == null
                ? CommunityAuthorVisibility.visibleToAll()
                : resolveAuthorVisibility(viewerId, postMapper.findAuthorSurfaces(category, status, kw));

        // 개인화 정렬 — 검색어가 없을 때만 7:3 혼합 피드로 위임(키워드 검색은 기존 LIKE 경로 유지).
        if (SORT_PERSONALIZED.equals(sort) && (keyword == null || keyword.isBlank())) {
            return personalizedFeed(category, page, size, viewerId, visibility);
        }
        int offset = page * size;
        String blockedNamedAuthorIdsJson = visibility.blockedNamedAuthorIdsJson();
        String blockedAnonymousAuthorIdsJson = visibility.blockedAnonymousAuthorIdsJson();
        // 개인정보 정책 전체를 먼저 벌크 평가한 같은 차단 집합을 목록 LIMIT/OFFSET과 total에 함께 적용한다.
        List<CommunityPost> posts = postMapper.findAll(category, status, sort, kw, offset, size,
                blockedNamedAuthorIdsJson, blockedAnonymousAuthorIdsJson);
        int total = postMapper.countVisible(category, status, kw,
                blockedNamedAuthorIdsJson, blockedAnonymousAuthorIdsJson);
        // 비익명 작성자 표시명을 닉네임 프로필로 벌크 해석(N+1 방지). 익명 글은 해석 대상에서 제외.
        Map<DisplayNameQuery, DisplayNameResponse> resolved = resolveAuthorNames(posts);
        return new PostPageResponse(
                posts.stream().map(post -> toListResponse(post, resolved, viewerId)).toList(),
                total, page, size
        );
    }

    /**
     * id 목록 조회(챗봇 추천 모아보기). 입력 순서(FIELD)를 보존하고 최대 20건에 개인정보 정책을 벌크 적용한다.
     */
    @Override
    public PostPageResponse getPostsByIds(List<Long> ids, Long viewerId) {
        List<Long> distinct = ids == null ? List.of()
                : ids.stream().filter(java.util.Objects::nonNull).distinct().limit(20).toList();
        if (distinct.isEmpty()) {
            return new PostPageResponse(List.of(), 0, 0, 0);
        }
        List<CommunityPost> posts = filterBlockedAuthors(
                postMapper.findByIds(distinct, PostStatus.PUBLISHED.name()), viewerId);
        Map<DisplayNameQuery, DisplayNameResponse> resolved = resolveAuthorNames(posts);
        List<PostListResponse> items = posts.stream()
                .map(post -> toListResponse(post, resolved, viewerId)).toList();
        return new PostPageResponse(items, items.size(), 0, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getCategoryCounts(Long viewerId) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        CommunityAuthorVisibility visibility = viewerId == null
                ? CommunityAuthorVisibility.visibleToAll()
                : resolveAuthorVisibility(viewerId,
                        postMapper.findAuthorSurfaces(null, PostStatus.PUBLISHED.name(), null));
        for (Map<String, Object> row : postMapper.countPublishedByCategory(
                visibility.blockedNamedAuthorIdsJson(), visibility.blockedAnonymousAuthorIdsJson())) {
            String category = stringValue(row, "category");
            Long count = longValue(row, "cnt");
            if (category != null && count != null) {
                counts.put(category, count);
            }
        }
        return counts;
    }

    /** 작성자 표면 후보를 고정 크기로 나눠 개인정보 매퍼의 IN 절을 제한하면서 전체 정책 판정 결과를 만든다. */
    private CommunityAuthorVisibility resolveAuthorVisibility(Long viewerId, List<ContentAuthorRow> authorSurfaces) {
        if (viewerId == null || authorSurfaces == null || authorSurfaces.isEmpty()) {
            return CommunityAuthorVisibility.visibleToAll();
        }
        Set<Long> namedAuthors = new HashSet<>();
        Set<Long> anonymousAuthors = new HashSet<>();
        for (ContentAuthorRow row : authorSurfaces) {
            if (row == null || row.getUserId() == null) {
                continue;
            }
            (row.isAnonymous() ? anonymousAuthors : namedAuthors).add(row.getUserId());
        }
        Set<Long> blockedNamed = blockedAuthorsInBatches(
                viewerId, namedAuthors, PrivacySurfaces.CONTENT_POST);
        Set<Long> blockedAnonymous = blockedAuthorsInBatches(
                viewerId, anonymousAuthors, PrivacySurfaces.CONTENT_POST + ".anonymous");
        return new CommunityAuthorVisibility(blockedNamed, blockedAnonymous);
    }

    /** 임의 상한 없이 모든 후보를 500명 이하로 평가하고 차단 결과를 합친다. */
    private Set<Long> blockedAuthorsInBatches(Long viewerId, Set<Long> authorIds, String surface) {
        if (authorIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> blocked = new HashSet<>();
        for (Set<Long> batch : CommunityAuthorVisibility.partition(authorIds)) {
            Set<Long> batchResult = privacyPolicyService.blockedAuthorsAmong(viewerId, batch, surface);
            if (batchResult != null) {
                blocked.addAll(batchResult);
            }
        }
        return Set.copyOf(blocked);
    }

    /** MyBatis map 은 JDBC 드라이버에 따라 alias 대소문자가 달라질 수 있어 키를 대소문자 무시로 읽는다. */
    private static Object rowValue(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        return value == null ? null : value.toString();
    }

    private static Long longValue(Map<String, Object> row, String key) {
        Object value = rowValue(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 개인화 7:3 혼합 피드. PersonalizedFeedService 가 만든 블렌디드 순서를 그대로 유지한 채
     * (재정렬하지 않음) 표시명 벌크 해석만 얹어 응답한다. 차단은 후보 LIMIT 전에 이미 적용됐다.
     * 카테고리 필터는 서비스 계층 정규화(null/blank → null)를 따른다.
     */
    private PostPageResponse personalizedFeed(String category, int page, int size, Long viewerId,
                                              CommunityAuthorVisibility visibility) {
        String cat = (category == null || category.isBlank()) ? null : category;
        PersonalizedFeedService.FeedPage feed = personalizedFeedService.blendedFeed(
                viewerId, cat, page, size, visibility);
        List<CommunityPost> posts = feed.posts();
        Map<DisplayNameQuery, DisplayNameResponse> resolved = resolveAuthorNames(posts);
        return new PostPageResponse(
                posts.stream().map(post -> toListResponse(post, resolved, viewerId)).toList(),
                feed.total(), page, size
        );
    }

    /** 목록의 비익명 작성자 (userId, nicknameProfileId) 를 모아 표시명을 한 번에 해석한다. */
    private Map<DisplayNameQuery, DisplayNameResponse> resolveAuthorNames(List<CommunityPost> posts) {
        Set<DisplayNameQuery> queries = new HashSet<>();
        for (CommunityPost post : posts) {
            if (!post.isAnonymous()) {
                queries.add(new DisplayNameQuery(post.getUserId(), post.getNicknameProfileId()));
            }
        }
        return nicknameProfileService.bulkResolveDisplayNames(queries);
    }

    /** 비익명 작성자 표시명·프로필 id 를 담은 AuthorDto 생성. 익명은 마스킹 유지(닉네임 프로필로 덮지 않는다). */
    private PostListResponse.AuthorDto authorDto(CommunityPost post,
                                                 Map<DisplayNameQuery, DisplayNameResponse> resolved) {
        if (post.isAnonymous()) {
            return new PostListResponse.AuthorDto(null, "익명", null, true);
        }
        if (DELETED_USER_STATUS.equals(post.getUserStatus())) {
            return new PostListResponse.AuthorDto(null, DELETED_USER_LABEL, null, false);
        }
        DisplayNameResponse dn = resolved.get(new DisplayNameQuery(post.getUserId(), post.getNicknameProfileId()));
        String name = dn != null ? dn.displayName() : post.getUserName();
        Long profileId = dn != null ? dn.nicknameProfileId() : post.getNicknameProfileId();
        Long accountId = dn != null ? dn.accountId() : post.getUserId();
        return new PostListResponse.AuthorDto(accountId, name, profileId, false);
    }

    /**
     * 뷰어 기준 content.post(.anonymous) 차단 작성자 글을 목록에서 제거한다.
     * 익명 여부에 따라 표면 키가 갈리므로 작성자 id 를 두 그룹으로 나눠 벌크 판정(비로그인 뷰어는 필터 없음).
     */
    private List<CommunityPost> filterBlockedAuthors(List<CommunityPost> posts, Long viewerId) {
        if (viewerId == null || posts.isEmpty()) {
            return posts;
        }
        List<ContentAuthorRow> surfaces = posts.stream()
                .map(post -> new ContentAuthorRow(post.getUserId(), post.isAnonymous()))
                .toList();
        CommunityAuthorVisibility visibility = resolveAuthorVisibility(viewerId, surfaces);
        return posts.stream()
                .filter(post -> !(post.isAnonymous()
                        ? visibility.blockedAnonymousAuthorIds()
                        : visibility.blockedNamedAuthorIds()).contains(post.getUserId()))
                .toList();
    }

    @Override
    @Transactional
    public PostDetailResponse getPostDetail(Long postId, Long currentUserId) {
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        // 뷰어가 차단한 작성자의 글 — 본문 대신 톰스톤(blocked=true) 반환, 조회수도 올리지 않는다
        String surface = post.isAnonymous()
                ? PrivacySurfaces.CONTENT_POST + ".anonymous"
                : PrivacySurfaces.CONTENT_POST;
        if (currentUserId != null
                && !privacyPolicyService.allows(currentUserId, post.getUserId(), surface)) {
            return toBlockedDetailResponse(post);
        }

        // 본인 글이 아닐 때만 조회수 증가
        if (currentUserId == null || !currentUserId.equals(post.getUserId())) {
            incrementViewCount(postId);
        }

        CommunityInterviewReview review = null;
        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())) {
            review = postMapper.findInterviewReviewByPostId(postId);
        }

        ViewerState viewer = resolveViewerState(postId, currentUserId);
        boolean mine = currentUserId != null && currentUserId.equals(post.getUserId());
        return toDetailResponse(post, review, viewer, mine);
    }

    /** 뷰어의 리액션/스크랩/구독 상태 — 리액션은 벌크 1쿼리로 판정한다. */
    private ViewerState resolveViewerState(Long postId, Long currentUserId) {
        if (currentUserId == null) {
            return ViewerState.EMPTY;
        }
        boolean liked = false, disliked = false, recommended = false, disrecommended = false, bookmarked = false;
        for (PostReaction r : reactionMapper.findPostReactionsByUser(currentUserId, postId)) {
            ReactionType type = ReactionType.valueOf(r.getReactionType());
            switch (type) {
                case LIKE -> liked = true;
                case DISLIKE -> disliked = true;
                case RECOMMEND -> recommended = true;
                case DISRECOMMEND -> disrecommended = true;
                case BOOKMARK -> bookmarked = true;
            }
        }
        boolean scrapped = scrapMapper.findByUserAndPost(currentUserId, postId) != null;
        boolean subscribed = subscriptionMapper.existsPostSubscription(currentUserId, postId);
        return new ViewerState(liked, disliked, recommended, disrecommended, bookmarked, scrapped, subscribed);
    }

    /** 뷰어 상태 홀더 — 비로그인 뷰어는 전부 false. */
    private record ViewerState(boolean liked, boolean disliked, boolean recommended,
                               boolean disrecommended, boolean bookmarked, boolean scrapped, boolean subscribed) {
        static final ViewerState EMPTY = new ViewerState(false, false, false, false, false, false, false);
    }

    @Transactional
    public void incrementViewCount(Long postId) {
        postMapper.incrementViewCount(postId);
    }

    @Override
    @Transactional
    public Long createPost(CreatePostRequest request, Long userId) {
        if (PostCategory.RECOMMENDED_JOB == request.category()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "채용공고는 승인된 기업 공고 등록 화면에서만 작성할 수 있습니다.");
        }
        // 작성 rate-limit(도배 방지) — 최근 window 초 안에 max 건 이상이면 429. (콘솔 편집값)
        int postRateMax = moderationSettingService.getPostRateMax();
        if (postRateMax > 0) {
            int recent = postMapper.countRecentPostsByUser(userId,
                    LocalDateTime.now().minusSeconds(moderationSettingService.getPostRateWindowSeconds()));
            if (recent >= postRateMax) {
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "짧은 시간에 너무 많은 글을 작성했습니다. 잠시 후 다시 시도해 주세요.");
            }
        }
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
                .nicknameProfileId(normalizeProfileId(userId, request.anonymous(), request.nicknameProfileId()))
                .build();

        postMapper.insert(post);
        applyUserTags(post.getId(), request.tags());

        if (PostCategory.INTERVIEW_REVIEW == request.category()
                && request.interviewReview() != null) {
            upsertInterviewReview(post.getId(), request.interviewReview());
        }

        eventPublisher.publishEvent(new PostModerationRequiredEvent(post.getId()));
        eventPublisher.publishEvent(new PostImageModerationRequiredEvent(post.getId()));
        eventPublisher.publishEvent(new PostTagRequiredEvent(post.getId()));
        if (PostCategory.INTERVIEW_REVIEW == request.category()) {
            eventPublisher.publishEvent(new InterviewExtractRequiredEvent(post.getId()));
        }
        // 새 글 발행 → 커밋 후 관심 사용자 추천 알림(RECOMMENDED_POST). 수정 시에는 발행하지 않는다.
        eventPublisher.publishEvent(new PostPublishedEvent(post.getId()));
        // 활동 리워드 적립(규칙 on 일 때만). 실패해도 글 작성은 유지.
        grantRewardSafely(userId, "COMMUNITY_POST_CREATE", "POST", post.getId());
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
        post.setNicknameProfileId(normalizeProfileId(userId, request.anonymous(), request.nicknameProfileId()));
        postMapper.update(post);
        applyUserTags(postId, request.tags());

        // 면접후기: upsert 1쿼리로 insert or update 처리
        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())
                && request.interviewReview() != null) {
            upsertInterviewReview(postId, request.interviewReview());
        }

        eventPublisher.publishEvent(new PostModerationRequiredEvent(postId));
        eventPublisher.publishEvent(new PostImageModerationRequiredEvent(postId));
        eventPublisher.publishEvent(new PostTagRequiredEvent(postId));
        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())) {
            eventPublisher.publishEvent(new InterviewExtractRequiredEvent(postId));
        }
        // 수정 커밋 후 → reactionRetention=release 사용자의 이 글 리액션 해지(AFTER_COMMIT 리스너)
        eventPublisher.publishEvent(new PostEditedEvent(postId));
    }

    private static final int HOT_POST_DISPLAY_SIZE = 5;

    @Override
    public List<HotPostResponse> getHotPosts(Long viewerId) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        CommunityAuthorVisibility visibility = viewerId == null
                ? CommunityAuthorVisibility.visibleToAll()
                : resolveAuthorVisibility(
                        viewerId, postMapper.findHotAuthorSurfaces(PostStatus.PUBLISHED.name(), since));
        // 동일 차단 집합을 인기 점수 LIMIT 전에 적용하므로 허용 글이 20위 밖에 있어도 최대 5건을 채운다.
        List<CommunityPost> posts = postMapper.findHotPosts(
                PostStatus.PUBLISHED.name(), since,
                visibility.blockedNamedAuthorIdsJson(), visibility.blockedAnonymousAuthorIdsJson(),
                HOT_POST_DISPLAY_SIZE);
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

    private PostListResponse toListResponse(CommunityPost post,
                                            Map<DisplayNameQuery, DisplayNameResponse> resolved,
                                            Long viewerId) {
        PostCategory cat = PostCategory.valueOf(post.getCategory());
        int reportCount = post.getReportCount() == null ? 0 : post.getReportCount();
        // 신고 누적 자동 블러 — 임계 이상이면 비작성자에게 가린다(작성자·프론트 클릭 시 해제). (콘솔 편집값)
        boolean blurred = reportCount >= moderationSettingService.getReportBlurThreshold()
                && !post.getUserId().equals(viewerId);
        return new PostListResponse(
                post.getId(),
                post.getCategory(),
                cat.getLabel(),
                post.getTitle(),
                post.getContent(),
                parseJsonArray(post.getTagsJson()),
                authorDto(post, resolved),
                toStatsDto(post),
                post.getStatus(),
                post.getCreatedAt(),
                post.getCompanyName(),
                post.getJobTitle(),
                blurred,
                reportCount
        );
    }

    /** 단건(상세) 작성자 표시명 해석 — 목록의 authorDto 와 동일 규칙을 단일 키로 처리. */
    private PostListResponse.AuthorDto authorDto(CommunityPost post) {
        if (post.isAnonymous()) {
            return new PostListResponse.AuthorDto(null, "익명", null, true);
        }
        if (DELETED_USER_STATUS.equals(post.getUserStatus())) {
            return new PostListResponse.AuthorDto(null, DELETED_USER_LABEL, null, false);
        }
        DisplayNameResponse dn =
                nicknameProfileService.resolveDisplayName(post.getUserId(), post.getNicknameProfileId());
        String name = dn != null ? dn.displayName() : post.getUserName();
        Long profileId = dn != null ? dn.nicknameProfileId() : post.getNicknameProfileId();
        Long accountId = dn != null ? dn.accountId() : post.getUserId();
        return new PostListResponse.AuthorDto(accountId, name, profileId, false);
    }

    private static PostListResponse.StatsDto toStatsDto(CommunityPost post) {
        return new PostListResponse.StatsDto(
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getDislikeCount(),
                post.getRecommendCount(),
                post.getDisrecommendCount(),
                post.getBookmarkCount(),
                post.getScrapCount()
        );
    }

    private PostDetailResponse toDetailResponse(CommunityPost post, CommunityInterviewReview review,
                                                ViewerState viewer, boolean mine) {
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
                authorDto(post),
                toStatsDto(post),
                post.getStatus(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getCompanyName(),
                post.getJobTitle(),
                reviewDto,
                viewer.liked(),
                viewer.disliked(),
                viewer.recommended(),
                viewer.disrecommended(),
                viewer.bookmarked(),
                viewer.scrapped(),
                viewer.subscribed(),
                false,
                resolveBlurredImages(post.getId()),
                mine
        );
    }

    /**
     * AI 이미지 검열(IMAGE_MODERATION)에서 블러 대상으로 판정된 본문 이미지 + 사유(category).
     * 결과 없음/파싱 실패면 빈 목록 — 검열은 fail-open 이라 블러를 걸지 않는다.
     */
    private List<PostDetailResponse.BlurredImage> resolveBlurredImages(Long postId) {
        try {
            PostAiResult result = aiResultMapper.findByPostIdAndTaskType(postId, AiTaskType.IMAGE_MODERATION);
            if (result == null || result.getStatus() != AiResultStatus.COMPLETED || result.getResultJson() == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(result.getResultJson());
            List<PostDetailResponse.BlurredImage> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            // images[] 에서 action=blur 인 항목 + 사유(category)
            JsonNode images = root.path("images");
            if (images.isArray()) {
                for (JsonNode img : images) {
                    if (!"blur".equals(img.path("action").asText())) {
                        continue;
                    }
                    String url = img.path("url").asText();
                    if (url == null || url.isBlank() || !seen.add(url)) {
                        continue;
                    }
                    JsonNode cat = img.path("category");
                    list.add(new PostDetailResponse.BlurredImage(
                            url, cat.isMissingNode() || cat.isNull() ? null : cat.asText()));
                }
            }
            // 폴백: flagged[] 만 있는 경우(수동 flag 등) — 사유 없음
            JsonNode flagged = root.path("flagged");
            if (flagged.isArray()) {
                for (JsonNode node : flagged) {
                    String url = node.asText();
                    if (url != null && !url.isBlank() && seen.add(url)) {
                        list.add(new PostDetailResponse.BlurredImage(url, null));
                    }
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("이미지 검열 결과 조회 실패 postId={}: {}", postId, e.getMessage());
            return List.of();
        }
    }

    /** 차단 작성자 게시글 톰스톤 — 본문·태그·면접후기는 비우고 안내 문구만 내려간다. */
    private PostDetailResponse toBlockedDetailResponse(CommunityPost post) {
        PostCategory cat = PostCategory.valueOf(post.getCategory());
        return new PostDetailResponse(
                post.getId(),
                post.getCategory(),
                cat.getLabel(),
                post.getTitle(),
                BLOCKED_POST_TOMBSTONE,
                Collections.emptyList(),
                authorDto(post),
                toStatsDto(post),
                post.getStatus(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getCompanyName(),
                post.getJobTitle(),
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                Collections.emptyList(),
                false
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
        List<Long> oldUserTagIds = tagMapper.findUserTagIds(postId);
        Set<Long> affectedTagIds = new HashSet<>(oldUserTagIds);
        for (Long tagId : oldUserTagIds) {
            tagMapper.decrementUsageCount(tagId);
        }
        tagMapper.deleteUserPostTags(postId);

        if (tags == null) {
            affectedTagIds.forEach(tagMapper::reconcileUsageCount);
            return;
        }

        // 2. 새 사용자 태그 삽입 (마스터 INSERT IGNORE → post_tag is_ai=0). 신규 INSERT(affected==1)일 때만 usage 증가.
        for (String tagName : tags) {
            String trimmed = tagName == null ? "" : tagName.strip();
            if (trimmed.isEmpty()) continue;
            tagMapper.insertTag(trimmed);
            Long tagId = tagMapper.findIdByName(trimmed);
            if (tagId == null) continue;
            affectedTagIds.add(tagId);
            int affected = tagMapper.insertPostTag(postId, tagId, false);
            if (affected == 1) {
                tagMapper.incrementUsageCount(tagId);
            }
        }
        affectedTagIds.forEach(tagMapper::reconcileUsageCount);
    }

    /**
     * 저장할 닉네임 프로필 id 를 정규화한다.
     * 익명 작성이거나 profileId 가 없으면 null. 지정한 프로필이 작성자 소유 ACTIVE 가 아니면
     * (해석 결과가 다른 프로필/계정명으로 폴백) 저장하지 않고 null 로 둔다(위조 profileId 저장 방지).
     */
    private Long normalizeProfileId(Long userId, boolean anonymous, Long profileId) {
        if (anonymous || profileId == null) {
            return null;
        }
        DisplayNameResponse dn = nicknameProfileService.resolveDisplayName(userId, profileId);
        return dn != null && profileId.equals(dn.nicknameProfileId()) ? profileId : null;
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
