package com.careertuner.community.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.privacy.service.PrivacySurfaces;
import com.careertuner.support.chatbot.CosineSimilarity;
import com.careertuner.support.chatbot.OllamaEmbeddingClient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 2단계 커뮤니티 글 검색.
 * <p>1단계: SQL 로 후보를 느슨하게 좁히고(인덱스/LIKE), 2단계: 후보 안에서만 bge-m3 코사인.
 * 글 수가 늘어도 코사인 비용이 전체 글 수가 아니라 후보 수에 비례하게 만든다.
 * 임베딩 생성/코사인은 FAQ 챗봇 자산({@link OllamaEmbeddingClient}, {@link CosineSimilarity}) 재사용.
 */
@Service
public class CommunityPostSearchService {

    private static final Logger log = LoggerFactory.getLogger(CommunityPostSearchService.class);

    private final OllamaEmbeddingClient embeddingClient;
    private final CommunityPostMapper postMapper;
    private final CommunitySearchProperties props;
    private final ObjectMapper objectMapper;
    private final PrivacyPolicyService privacyPolicyService;

    public CommunityPostSearchService(OllamaEmbeddingClient embeddingClient,
                                      CommunityPostMapper postMapper,
                                      CommunitySearchProperties props,
                                      ObjectMapper objectMapper,
                                      PrivacyPolicyService privacyPolicyService) {
        this.embeddingClient = embeddingClient;
        this.postMapper = postMapper;
        this.props = props;
        this.objectMapper = objectMapper;
        this.privacyPolicyService = privacyPolicyService;
    }

    /**
     * @param query    검색 키워드/관심사 (자연어)
     * @param category PostCategory 이름(7종) 또는 null/빈문자열(필터 없음)
     * @param viewerId 뷰어(로그인 사용자 id). null(비로그인)이면 개인 차단 필터 없음
     * @return 유사도 상위 topK 글 (없으면 빈 리스트)
     */
    public List<PostHit> search(String query, String category, Long viewerId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        double[] queryVector = embeddingClient.embed(query);
        String normalizedCategory = normalizeCategory(category);
        List<String> keywords = extractKeywords(query);

        // 1단계 — SQL 후보 좁히기. 너무 적으면 단계적으로 완화(재현율 보호).
        List<PostCandidate> candidates = postMapper.findSearchCandidates(keywords, normalizedCategory, props.getCandidateLimit());
        if (candidates.size() < props.getMinCandidates() && normalizedCategory != null) {
            candidates = postMapper.findSearchCandidates(keywords, null, props.getCandidateLimit());
        }
        if (candidates.size() < props.getMinCandidates()) {
            candidates = postMapper.findSearchCandidates(null, null, props.getCandidateLimit());
        }
        // 뷰어가 차단한 작성자 후보 제거(P-02) — 코사인 전에 걸러 topK 를 허용 글로만 채운다.
        candidates = filterBlockedAuthors(candidates, viewerId);
        if (candidates.isEmpty()) {
            log.info("커뮤니티 검색 후보 없음: query='{}'", query);
            return List.of();
        }

        // 2단계 — 후보 안에서만 코사인.
        List<Scored> scored = new ArrayList<>();
        for (PostCandidate c : candidates) {
            double[] vec = parseEmbedding(c.getEmbedding());
            if (vec == null || vec.length != queryVector.length) {
                continue;
            }
            scored.add(new Scored(c, CosineSimilarity.compute(queryVector, vec)));
        }
        scored.sort(Comparator.comparingDouble(Scored::similarity).reversed());

        List<PostHit> hits = topHits(scored, props.getSimilarityThreshold());

        // 1회 재검색: 통과 결과가 0이면 완화 임계로 한 번만 재선별 (루프 아님).
        if (hits.isEmpty()) {
            hits = topHits(scored, props.getRetryThreshold());
            log.info("커뮤니티 검색 1회 재검색(완화 임계 {}): query='{}' → 결과 {}건",
                    props.getRetryThreshold(), query, hits.size());
        }

        log.info("커뮤니티 검색: query='{}', category={}, 후보 {}건 → 결과 {}건",
                query, normalizedCategory, candidates.size(), hits.size());
        return hits;
    }

    /**
     * 뷰어 기준 content.post(.anonymous) 차단 작성자의 후보를 제거한다(비로그인 뷰어는 필터 없음).
     * 익명 여부로 표면 키가 갈리므로 작성자를 두 그룹으로 나눠 blockedAuthorsAmong 벌크 판정(쿼리 수 고정).
     */
    private List<PostCandidate> filterBlockedAuthors(List<PostCandidate> candidates, Long viewerId) {
        if (viewerId == null || candidates.isEmpty()) {
            return candidates;
        }
        Set<Long> anonymousAuthors = new HashSet<>();
        Set<Long> namedAuthors = new HashSet<>();
        for (PostCandidate c : candidates) {
            (c.isAnonymous() ? anonymousAuthors : namedAuthors).add(c.getUserId());
        }
        Set<Long> blockedAnonymous = privacyPolicyService.blockedAuthorsAmong(
                viewerId, anonymousAuthors, PrivacySurfaces.CONTENT_POST + ".anonymous");
        Set<Long> blockedNamed = privacyPolicyService.blockedAuthorsAmong(
                viewerId, namedAuthors, PrivacySurfaces.CONTENT_POST);
        return candidates.stream()
                .filter(c -> !(c.isAnonymous() ? blockedAnonymous : blockedNamed).contains(c.getUserId()))
                .collect(Collectors.toList());
    }

    private List<PostHit> topHits(List<Scored> scored, double threshold) {
        return scored.stream()
                .filter(s -> s.similarity() >= threshold)
                .limit(props.getTopK())
                .map(s -> toHit(s.candidate()))
                .collect(Collectors.toList());
    }

    private PostHit toHit(PostCandidate c) {
        String content = c.getContent() == null ? "" : c.getContent();
        String snippet = content.length() > 120 ? content.substring(0, 120) + "…" : content;
        return new PostHit(c.getId(), c.getTitle(), "/community/posts/" + c.getId(), snippet);
    }

    /** 카테고리가 실제 PostCategory(7종)와 일치할 때만 필터로 사용, 아니면 무시. */
    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String upper = category.trim().toUpperCase();
        for (PostCategory pc : PostCategory.values()) {
            if (pc.name().equals(upper)) {
                return upper;
            }
        }
        return null;
    }

    /** 질의를 공백 단위 키워드로 분해(2글자 미만 토큰 제외). 1단계 LIKE OR 재료. */
    private List<String> extractKeywords(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .map(String::trim)
                .filter(t -> t.length() >= 2)
                .distinct()
                .limit(6)
                .collect(Collectors.toList());
    }

    private double[] parseEmbedding(String json) {
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            return arr;
        } catch (Exception e) {
            log.error("커뮤니티 임베딩 JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private record Scored(PostCandidate candidate, double similarity) {}
}
