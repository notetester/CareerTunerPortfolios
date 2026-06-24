package com.careertuner.community.search;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.support.chatbot.OllamaEmbeddingClient;

import tools.jackson.databind.ObjectMapper;

/**
 * 커뮤니티 글 임베딩 배치 적재. FAQ {@code ChatbotService.embedAll()} 패턴 복제.
 * 대상 텍스트는 title + "\n" + content. 결과는 community_post_embedding 별도 테이블에 upsert.
 */
@Service
public class CommunityEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CommunityEmbeddingService.class);

    private final OllamaEmbeddingClient embeddingClient;
    private final CommunityPostMapper postMapper;
    private final ObjectMapper objectMapper;

    public CommunityEmbeddingService(OllamaEmbeddingClient embeddingClient,
                                     CommunityPostMapper postMapper,
                                     ObjectMapper objectMapper) {
        this.embeddingClient = embeddingClient;
        this.postMapper = postMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 임베딩이 아직 없는 PUBLISHED 글을 일괄 임베딩.
     * @return 임베딩 완료 개수
     */
    public int embedAllPosts() {
        List<CommunityPost> targets = postMapper.findPostsWithoutEmbedding();
        log.info("커뮤니티 글 임베딩 시작: 대상 {}개", targets.size());

        int success = 0;
        for (CommunityPost post : targets) {
            try {
                String text = post.getTitle() + "\n" + post.getContent();
                double[] vector = embeddingClient.embed(text);
                String json = objectMapper.writeValueAsString(vector);
                postMapper.upsertEmbedding(post.getId(), json);
                success++;
                log.debug("커뮤니티 글 #{} 임베딩 완료", post.getId());
            } catch (Exception e) {
                log.error("커뮤니티 글 #{} 임베딩 실패: {}", post.getId(), e.getMessage());
            }
        }

        log.info("커뮤니티 글 임베딩 완료: {}/{}", success, targets.size());
        return success;
    }
}
