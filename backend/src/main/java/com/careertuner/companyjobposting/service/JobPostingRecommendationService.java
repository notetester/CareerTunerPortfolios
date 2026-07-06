package com.careertuner.companyjobposting.service;

import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import com.careertuner.community.domain.RecommendationCandidate;
import com.careertuner.community.mapper.RecommendationCandidateMapper;
import com.careertuner.community.service.RecommendationTokenMatcher;
import com.careertuner.companyjobposting.domain.CompanyJobPosting;
import com.careertuner.companyjobposting.mapper.CompanyJobPostingMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * RECOMMENDED_JOB 자동 발행 엔진 — 공고가 PUBLISHED 될 때 관심 사용자에게 알림을 보낸다.
 *
 * <p>{@link com.careertuner.community.service.PostRecommendationService}(RECOMMENDED_POST) 패턴 복제:
 * <ul>
 *   <li>매칭: 공고 텍스트(제목+직무명+태그+회사명+근무지역) × 프로필(희망 직무+스킬),
 *       {@link RecommendationTokenMatcher} 부분일치(수정 금지 — import 재사용).</li>
 *   <li>후보: 기존 {@link RecommendationCandidateMapper} 재사용(ACTIVE + 희망 직무 입력 사용자).</li>
 *   <li>수신자 상한 {@value #MAX_RECIPIENTS}명, 공고 작성 기업 계정 제외, actorId 없음(시스템 발신).</li>
 *   <li>개인 차단·알림 설정 필터는 알림 코어(NotificationService)가 처리한다.</li>
 *   <li>클래스 트랜잭션 없음 — notify() 알림별 자체 트랜잭션. 실패는 로그만(best-effort).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingRecommendationService {

    /** 공고 1건당 추천 알림 수신자 상한. */
    private static final int MAX_RECIPIENTS = 100;

    /** 후보 조회 상한 — 전체 사용자 스캔 방지용 안전장치. */
    private static final int MAX_CANDIDATES = 2000;

    private final CompanyJobPostingMapper postingMapper;
    private final RecommendationCandidateMapper candidateMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /** AFTER_COMMIT 리스너에서 비동기 호출된다 — 어떤 예외도 밖으로 던지지 않는다. */
    public void recommendToMatchedUsers(Long postingId) {
        try {
            CompanyJobPosting posting = postingMapper.findById(postingId);
            if (posting == null || !"PUBLISHED".equals(posting.getStatus())) {
                return;
            }
            String postingText = buildPostingText(posting);
            if (postingText.isBlank()) {
                return;
            }

            List<RecommendationCandidate> candidates =
                    candidateMapper.findActiveCandidates(posting.getCompanyUserId(), MAX_CANDIDATES);
            int sent = 0;
            for (RecommendationCandidate candidate : candidates) {
                if (sent >= MAX_RECIPIENTS) {
                    break;
                }
                List<String> profileTokens = RecommendationTokenMatcher.profileTokens(
                        candidate.getDesiredJob(), parseJsonArray(candidate.getSkillsJson()));
                if (!RecommendationTokenMatcher.matchesAnyToken(postingText, profileTokens)) {
                    continue;
                }
                if (notifyQuietly(candidate.getUserId(), posting)) {
                    sent++;
                }
            }
            if (sent > 0) {
                log.info("추천 공고 알림 발행 완료: postingId={} 수신자 {}명 (후보 {}명)",
                        postingId, sent, candidates.size());
            }
        } catch (Exception ex) {
            // 추천은 부가 기능 — 공고 게시 흐름·다른 리스너에 영향을 주지 않는다.
            log.error("추천 공고 알림 매칭 실패 postingId={}", postingId, ex);
        }
    }

    private boolean notifyQuietly(Long userId, CompanyJobPosting posting) {
        try {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("RECOMMENDED_JOB")
                    .targetType("JOB_POSTING")
                    .targetId(posting.getId())
                    .title("회원님께 맞는 채용공고가 올라왔습니다")
                    .message(buildMessage(posting))
                    .link("/jobs/" + posting.getId())
                    .build());
            return true;
        } catch (Exception ex) {
            log.error("추천 공고 알림 발송 실패: postingId={} userId={}", posting.getId(), userId, ex);
            return false;
        }
    }

    private String buildMessage(CompanyJobPosting posting) {
        String company = posting.getCompanyName();
        return (company == null || company.isBlank() ? "" : "[" + company + "] ") + posting.getTitle();
    }

    /** 매칭 대상 텍스트 — 제목 + 직무명 + 태그 + 회사명 + 근무지역(본문 장문 필드는 우연 일치가 많아 제외). */
    private String buildPostingText(CompanyJobPosting posting) {
        StringJoiner joiner = new StringJoiner(" ");
        append(joiner, posting.getTitle());
        append(joiner, posting.getJobRole());
        for (String tag : parseJsonArray(posting.getTagsJson())) {
            append(joiner, tag);
        }
        append(joiner, posting.getCompanyName());
        append(joiner, posting.getWorkLocation());
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
