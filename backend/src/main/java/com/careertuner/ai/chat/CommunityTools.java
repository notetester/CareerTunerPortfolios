package com.careertuner.ai.chat;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.careertuner.ai.chat.ChatResponse.SiteLink;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.search.CommunityPostSearchService;
import com.careertuner.community.search.PostHit;
import com.careertuner.support.chatbot.ChatbotService;
import com.careertuner.support.chatbot.FaqHit;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 커뮤니티 챗봇 에이전트의 read-only 툴 모음.
 * <p>모델이 "언제 부를지" 스스로 판단한다. write/파괴 액션은 절대 노출하지 않는다.
 * LangChain4j 스타터가 이 @Tool 빈을 자동 와이어링한다.
 */
@Component
public class CommunityTools {

    private static final Logger log = LoggerFactory.getLogger(CommunityTools.class);
    private static final int SUMMARY_SOURCE_MAX = 2000;

    private final CommunityPostSearchService searchService;
    private final CommunityPostMapper postMapper;
    private final ChatbotService chatbotService;
    private final SearchTrace searchTrace;

    public CommunityTools(CommunityPostSearchService searchService,
                          CommunityPostMapper postMapper,
                          ChatbotService chatbotService,
                          SearchTrace searchTrace) {
        this.searchService = searchService;
        this.postMapper = postMapper;
        this.chatbotService = chatbotService;
        this.searchTrace = searchTrace;
    }

    @Tool("사용자가 커뮤니티의 면접 후기/자기소개서/취업 관련 글을 찾거나 추천받고 싶어할 때 호출한다. 관심사·직무·회사·키워드로 글을 검색한다.")
    public List<PostHit> searchCommunityPosts(
            @P("검색할 관심사/직무/회사/키워드") String query,
            @P("글 종류 필터. JOB_REVIEW/INTERVIEW_REVIEW/JOB_QUESTION/SUCCESS_STRATEGY/PORTFOLIO_FEEDBACK/CERTIFICATE_REVIEW/FREE 중 하나, 불명확하면 빈 문자열") String category) {
        log.info("TOOL searchCommunityPosts(query='{}', category='{}')", query, category);
        List<PostHit> hits = searchService.search(query, category);
        searchTrace.add(hits); // links 접지: 실제 툴이 돌려준 글만 기록
        return hits;
    }

    @Tool("커뮤니티 글 1개의 본문을 가져온다. 사용자가 특정 글을 가리키며 요약/내용을 물을 때 호출하고, 반환된 본문을 네가 직접 요약해 답한다.")
    public String getPostContent(@P("글 ID") Long postId) {
        log.info("TOOL getPostContent(postId={})", postId);
        CommunityPost post = postMapper.findById(postId);
        if (post == null || !"PUBLISHED".equals(post.getStatus())) {
            return "해당 글을 찾을 수 없습니다.";
        }
        String content = post.getContent() == null ? "" : post.getContent();
        if (content.length() > SUMMARY_SOURCE_MAX) {
            content = content.substring(0, SUMMARY_SOURCE_MAX) + "…";
        }
        return "제목: " + post.getTitle() + "\n본문:\n" + content;
    }

    @Tool("회원·계정·가입·로그인·탈퇴·결제·환불·포인트·신고 등 사이트 이용법·정책·절차에 관한 질문일 때 호출한다. 이런 질문은 직접 답하지 말고 반드시 이 툴로 실제 FAQ를 찾는다. 커뮤니티 글이 아니라 운영 FAQ를 묻는 경우다.")
    public String searchFaq(@P("FAQ 검색 키워드") String query) {
        log.info("TOOL searchFaq(query='{}')", query);
        List<FaqHit> hits = chatbotService.searchFaqHits(query);
        if (hits.isEmpty()) {
            return "관련 FAQ를 찾지 못했습니다.";
        }
        // 링크 접지: linkUrl 있는 FAQ만 SiteLink 로 기록(DB 출처라 신뢰).
        List<SiteLink> links = hits.stream()
                .filter(h -> h.linkUrl() != null && !h.linkUrl().isBlank())
                .map(h -> new SiteLink(
                        h.linkLabel() != null && !h.linkLabel().isBlank() ? h.linkLabel() : h.question(),
                        h.linkUrl()))
                .toList();
        searchTrace.addFaqLinks(links);
        // 모델에는 Q/A 텍스트만 (URL 은 넣지 않음 — 링크는 시스템이 붙임).
        return hits.stream()
                .map(h -> "Q: " + h.question() + "\nA: " + h.answer())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }
}
