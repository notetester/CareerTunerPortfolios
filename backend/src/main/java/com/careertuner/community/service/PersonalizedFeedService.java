package com.careertuner.community.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.community.domain.CommunityAuthorVisibility;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.RecommendationCandidate;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.PersonalizedFeedMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 커뮤니티 개인화 피드(7:3 혼합) 생성 서비스.
 *
 * <p>구성:
 * <ol>
 *   <li><b>개인화 후보(기본 70%)</b> — 뷰어 프로필(희망 직무·스킬) 토큰과 최근 반응 카테고리에 매칭되는 글.</li>
 *   <li><b>신선·인기(기본 30%)</b> — 최신 + like_count 상위 글.</li>
 * </ol>
 * 두 풀을 각각 랭크하고, postId Set 으로 중복을 제거한 뒤 가중 라운드로빈으로 7:3 비율을 유지하며
 * 인터리브해 하나의 정렬된 목록을 만든다. 그 목록에서 요청한 페이지 구간만 잘라 반환한다(페이지 경계 넘어 페이징).
 *
 * <p>폴백: 비로그인·프로필 없음·신호 부족(desired_job/skills·최근 반응 전무) 사용자는
 * 개인화를 건너뛰고 신선·인기 목록만 반환한다. 별도 목 데이터 없이도 자연히 동작한다.
 *
 * <p>랭크/인터리브는 순수 Java 로 처리해(설명 가능·테스트 용이) SQL 은 후보를 넉넉히 뽑는 역할만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalizedFeedService {

    /** 최근 반응 카테고리 신호 상한 — 이 수만큼의 관심 카테고리를 개인화 후보 조건에 넣는다. */
    private static final int RECENT_CATEGORY_LIMIT = 5;

    private final CommunityPostMapper postMapper;
    private final PersonalizedFeedMapper feedMapper;
    private final PersonalizedFeedProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 블렌디드 개인화 피드 — 개인화 70% : 신선/인기 30% 로 인터리브한 뒤 요청 페이지를 잘라 반환한다.
     *
     * @param userId   뷰어 id (null 이면 폴백: 신선/인기)
     * @param category 카테고리 필터 (없으면 전체). 기존 정렬과 동일 의미.
     * @param page     0-base 페이지
     * @param size     페이지 크기
     * @param visibility 뷰어 개인정보 정책으로 숨길 익명·비익명 작성자 집합
     * @return 블렌디드 순서로 정렬된 이 페이지의 글 목록 (표시명 해석 전 원본 CommunityPost)
     */
    public FeedPage blendedFeed(Long userId, String category, int page, int size,
                                CommunityAuthorVisibility visibility) {
        int limit = Math.max(props.getCandidateLimit(), (page + 1) * size);
        String blockedNamedAuthorIdsJson = visibility.blockedNamedAuthorIdsJson();
        String blockedAnonymousAuthorIdsJson = visibility.blockedAnonymousAuthorIdsJson();

        // 1) 개인화 신호 수집 — 프로필 토큰 + 최근 반응 카테고리
        List<String> tokens = List.of();
        List<String> recentCategories = List.of();
        if (userId != null) {
            RecommendationCandidate profile = feedMapper.findViewerProfile(userId);
            if (profile != null) {
                tokens = RecommendationTokenMatcher.profileTokens(
                        profile.getDesiredJob(), parseJsonArray(profile.getSkillsJson()));
            }
            recentCategories = postMapper.findRecentReactedCategories(userId, RECENT_CATEGORY_LIMIT);
        }
        boolean hasSignal = !tokens.isEmpty() || !recentCategories.isEmpty();

        // 2) 신호 없으면(비로그인/신규/신호부족) 신선·인기 폴백 — 개인화 스킵
        if (!hasSignal) {
            List<CommunityPost> fresh = postMapper.findFreshPopular(
                    category, null,
                    blockedNamedAuthorIdsJson, blockedAnonymousAuthorIdsJson,
                    limit);
            return sliceFeed(fresh, page, size, false);
        }

        // 3) 두 풀 조회 후 랭크·중복 제거·인터리브
        List<CommunityPost> personalized =
                postMapper.findPersonalizedCandidates(
                        userId, category, tokens, recentCategories,
                        blockedNamedAuthorIdsJson, blockedAnonymousAuthorIdsJson,
                        limit);
        List<Long> excludeIds = personalized.stream().map(CommunityPost::getId).toList();
        List<CommunityPost> fresh = postMapper.findFreshPopular(
                category, excludeIds,
                blockedNamedAuthorIdsJson, blockedAnonymousAuthorIdsJson,
                limit);

        List<CommunityPost> blended = interleave(personalized, fresh, ratio());
        return sliceFeed(blended, page, size, true);
    }

    /**
     * 두 풀을 가중 라운드로빈으로 인터리브한다. postId Set 으로 중복을 제거하고,
     * 한 풀이 소진되면 남은 풀을 이어붙여 목록이 조기 종료되지 않게 한다.
     *
     * <p>비율 유지 방식: 소수 누산기(acc)에 매 스텝 ratio 를 더해, acc >= 1 이면 개인화 풀에서,
     * 아니면 신선 풀에서 뽑는다. ratio=0.7 이면 장기적으로 개인화:신선 = 7:3 이 유지된다.
     */
    static List<CommunityPost> interleave(List<CommunityPost> personalized,
                                          List<CommunityPost> fresh,
                                          double ratio) {
        Set<Long> seen = new LinkedHashSet<>();
        List<CommunityPost> result = new ArrayList<>(personalized.size() + fresh.size());
        int pi = 0, fi = 0;
        double acc = 0.0;
        while (pi < personalized.size() || fi < fresh.size()) {
            boolean takePersonalized;
            if (pi >= personalized.size()) {
                takePersonalized = false;            // 개인화 소진 → 신선 드레인
            } else if (fi >= fresh.size()) {
                takePersonalized = true;             // 신선 소진 → 개인화 드레인
            } else {
                acc += ratio;
                takePersonalized = acc >= 1.0;
                if (takePersonalized) {
                    acc -= 1.0;
                }
            }
            CommunityPost picked = takePersonalized ? personalized.get(pi++) : fresh.get(fi++);
            if (picked.getId() != null && seen.add(picked.getId())) {
                result.add(picked);
            }
        }
        return result;
    }

    /** 인터리브된 전체 목록에서 요청 페이지 구간만 자른다. total 은 블렌디드 목록 크기(후보 상한 내 근사치). */
    private FeedPage sliceFeed(List<CommunityPost> blended, int page, int size, boolean personalized) {
        int from = Math.min(page * size, blended.size());
        int to = Math.min(from + size, blended.size());
        return new FeedPage(new ArrayList<>(blended.subList(from, to)), blended.size(), personalized);
    }

    /** 설정 비율을 [0,1] 로 보정. 범위를 벗어나면 기본 0.7 로 되돌린다. */
    private double ratio() {
        double r = props.getPersonalizedRatio();
        return (r > 0.0 && r < 1.0) ? r : 0.7;
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

    /**
     * 블렌디드 피드 페이지 결과.
     *
     * @param posts        이 페이지의 글(블렌디드 순서)
     * @param total        블렌디드 목록 전체 크기(후보 상한 내). 서버 페이지네이션 total 로 사용.
     * @param personalized 개인화 신호로 실제 혼합됐는지(false 면 폴백). 필요 시 프론트 노출용.
     */
    public record FeedPage(List<CommunityPost> posts, int total, boolean personalized) {
    }
}
