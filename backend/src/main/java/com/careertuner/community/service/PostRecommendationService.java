package com.careertuner.community.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityInterviewReview;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.domain.RecommendationCandidate;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.RecommendationCandidateMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 추천 공고/후기 자동 발행 엔진 (RECOMMENDED_POST).
 *
 * <p>후기 성격 카테고리(취업후기·면접후기) 글이 PUBLISHED 로 생성되면,
 * 글 텍스트(제목+태그+회사명+직무명)와 사용자 프로필(희망 직무+스킬)을
 * {@link RecommendationTokenMatcher} 로 부분일치 매칭해 관심 사용자에게 알림을 발행한다.
 *
 * <p>운영 원칙:
 * <ul>
 *   <li>수신자 상한 {@value #MAX_RECIPIENTS}명, 글 작성자 제외.</li>
 *   <li>actorId = 글 작성자 — 차단 억제 판정용으로만 넣는다(P-03≡N-13). 발신 주체는 여전히 시스템이며,
 *       응답에는 노출되지 않는다(NotificationServiceImpl 이 RECOMMENDED_* actor 를 마스킹 — 익명 글 신원 보호).</li>
 *   <li>개인 차단·알림 설정 필터는 알림 코어(NotificationService/PushDispatcher)가 처리한다.</li>
 *   <li>클래스 트랜잭션을 걸지 않는다 — notify()가 알림별 자체 트랜잭션으로 처리해
 *       대량 팬아웃 시 커넥션 장기 점유·전체 롤백을 피한다(AdminCampaignServiceImpl 패턴).</li>
 *   <li>실패는 조용히(로그만) — 글 작성 흐름을 깨지 않는다.</li>
 * </ul>
 *
 * <p>참고: 현재 커뮤니티에는 채용공고 성격 카테고리가 없어(PostCategory 7종 전부 후기/질문/자유)
 * 커뮤니티 글 기반 RECOMMENDED_JOB 발행은 하지 않는다. RECOMMENDED_JOB 은
 * 관리자 캠페인(AdminCampaignServiceImpl) 경로로만 발송된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRecommendationService {

    /** 후기 성격 카테고리 — 이 카테고리 글만 추천 대상으로 삼는다. */
    private static final Set<String> REVIEW_CATEGORIES =
            Set.of(PostCategory.JOB_REVIEW.name(), PostCategory.INTERVIEW_REVIEW.name());

    /** 글 1건당 추천 알림 수신자 상한. */
    private static final int MAX_RECIPIENTS = 100;

    /** 후보 조회 상한 — 전체 사용자 스캔 방지용 안전장치. */
    private static final int MAX_CANDIDATES = 2000;

    private final CommunityPostMapper postMapper;
    private final RecommendationCandidateMapper candidateMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 새로 발행된 글에 대해 관심 사용자를 매칭해 RECOMMENDED_POST 알림을 발행한다.
     * AFTER_COMMIT 리스너에서 비동기로 호출된다 — 어떤 예외도 밖으로 던지지 않는다.
     */
    public void recommendToMatchedUsers(Long postId) {
        try {
            CommunityPost post = postMapper.findById(postId);
            if (post == null || !PostStatus.PUBLISHED.name().equals(post.getStatus())) {
                return;
            }
            if (!REVIEW_CATEGORIES.contains(post.getCategory())) {
                return;
            }

            String postText = buildPostText(post);
            if (postText.isBlank()) {
                return;
            }

            List<RecommendationCandidate> candidates =
                    candidateMapper.findActiveCandidates(post.getUserId(), MAX_CANDIDATES);
            int sent = 0;
            for (RecommendationCandidate candidate : candidates) {
                if (sent >= MAX_RECIPIENTS) {
                    break;
                }
                List<String> profileTokens = RecommendationTokenMatcher.profileTokens(
                        candidate.getDesiredJob(), parseJsonArray(candidate.getSkillsJson()));
                if (!RecommendationTokenMatcher.matchesAnyToken(postText, profileTokens)) {
                    continue;
                }
                if (notifyQuietly(candidate.getUserId(), post)) {
                    sent++;
                }
            }
            if (sent > 0) {
                log.info("추천 알림 발행 완료: postId={} category={} 수신자 {}명 (후보 {}명)",
                        postId, post.getCategory(), sent, candidates.size());
            }
        } catch (Exception ex) {
            // 추천은 부가 기능 — 어떤 실패도 글 작성 흐름·다른 리스너에 영향을 주지 않는다.
            log.error("추천 알림 매칭 실패 postId={}", postId, ex);
        }
    }

    /** 개별 수신자 발송 — 실패해도 다음 수신자 발송을 계속한다(best-effort). */
    private boolean notifyQuietly(Long userId, CommunityPost post) {
        try {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    // 수신자가 작성자를 차단했으면 notify() 의 isBlockedSender 가 발행 자체를 억제한다.
                    .actorId(post.getUserId())
                    .type("RECOMMENDED_POST")
                    .targetType("POST")
                    .targetId(post.getId())
                    .title("회원님 관심 분야의 후기가 올라왔습니다")
                    .message(post.getTitle())
                    .link("/community/posts/" + post.getId())
                    .build());
            return true;
        } catch (Exception ex) {
            log.error("추천 알림 발송 실패: postId={} userId={}", post.getId(), userId, ex);
            return false;
        }
    }

    /**
     * 매칭 대상 글 텍스트 — 제목 + 태그 + 회사명 + 직무명.
     * 면접후기는 확장 테이블(community_interview_review)의 회사명/직무도 합친다.
     * 본문(content)은 제외한다 — 우연 일치가 많아 추천 정밀도를 떨어뜨린다.
     */
    private String buildPostText(CommunityPost post) {
        StringJoiner joiner = new StringJoiner(" ");
        append(joiner, post.getTitle());
        for (String tag : parseJsonArray(post.getTagsJson())) {
            append(joiner, tag);
        }
        append(joiner, post.getCompanyName());
        append(joiner, post.getJobTitle());

        if (PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())) {
            CommunityInterviewReview review = postMapper.findInterviewReviewByPostId(post.getId());
            if (review != null) {
                append(joiner, review.getCompanyName());
                append(joiner, review.getJobRole());
            }
        }
        return joiner.toString();
    }

    private static void append(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
