package com.careertuner.support.chatbot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.support.domain.Faq;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);
    private static final String NO_ANSWER = "해당 내용은 확인이 어렵습니다. 고객센터로 문의해 주시면 도와드리겠습니다.";

    private final OllamaEmbeddingClient embeddingClient;
    private final OllamaChatClient chatClient;
    private final ChatbotFaqMapper faqMapper;
    private final ChatbotProperties props;
    private final ObjectMapper objectMapper;

    public ChatbotService(OllamaEmbeddingClient embeddingClient,
                          OllamaChatClient chatClient,
                          ChatbotFaqMapper faqMapper,
                          ChatbotProperties props,
                          ObjectMapper objectMapper) {
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
        this.faqMapper = faqMapper;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    // ── 임베딩 적재 ──────────────────────────────────────────────────

    /**
     * 임베딩이 없는 발행 FAQ를 일괄 임베딩.
     * @param forceAll true면 전체 재임베딩, false면 미임베딩만
     * @return 임베딩 완료 개수
     */
    public int embedAll(boolean forceAll) {
        List<Faq> targets = forceAll
                ? faqMapper.findPublishedAll()
                : faqMapper.findPublishedWithoutEmbedding();

        log.info("FAQ 임베딩 시작: 대상 {}개 (forceAll={})", targets.size(), forceAll);

        int success = 0;
        for (Faq faq : targets) {
            try {
                String text = faq.getQuestion() + " " + faq.getAnswer();
                double[] vector = embeddingClient.embed(text);
                String json = objectMapper.writeValueAsString(vector);
                faqMapper.updateEmbedding(faq.getId(), json);
                success++;
                log.debug("FAQ #{} 임베딩 완료", faq.getId());
            } catch (Exception e) {
                log.error("FAQ #{} 임베딩 실패: {}", faq.getId(), e.getMessage());
            }
        }

        log.info("FAQ 임베딩 완료: {}/{}", success, targets.size());
        return success;
    }

    // ── 질문 → 검색 → 답변 ──────────────────────────────────────────

    public ChatbotAnswerDto ask(String question) {
        // 1. 사용자 질문 임베딩
        double[] queryVector = embeddingClient.embed(question);

        // 2. 임베딩이 있는 FAQ 전체 로드
        List<Faq> faqs = faqMapper.findPublishedWithEmbedding();
        if (faqs.isEmpty()) {
            log.warn("임베딩된 FAQ가 없습니다");
            return ChatbotAnswerDto.noMatch();
        }

        // 3. 코사인 유사도 계산 + 정렬
        List<ScoredFaq> scored = new ArrayList<>();
        for (Faq faq : faqs) {
            double[] faqVector = parseEmbedding(faq.getEmbedding());
            if (faqVector == null) continue;
            double similarity = CosineSimilarity.compute(queryVector, faqVector);
            scored.add(new ScoredFaq(faq, similarity));
        }
        scored.sort(Comparator.comparingDouble(ScoredFaq::similarity).reversed());

        // 4. 임계값 이상인 것만 필터 → 상위 K개
        List<ScoredFaq> topK = scored.stream()
                .filter(s -> s.similarity() >= props.getSimilarityThreshold())
                .limit(props.getTopK())
                .collect(Collectors.toList());

        // 5. 임계값을 넘는 FAQ가 없으면 관련 없음
        if (topK.isEmpty()) {
            double topSimilarity = scored.isEmpty() ? 0.0 : scored.get(0).similarity();
            log.info("유사도 최고값 {} < 임계값 {} → 관련 FAQ 없음",
                    topSimilarity, props.getSimilarityThreshold());
            return ChatbotAnswerDto.noMatch();
        }

        // 6. gemma4로 답변 생성
        List<Long> matchedIds = topK.stream().map(s -> s.faq().getId()).collect(Collectors.toList());
        String faqContext = topK.stream()
                .map(s -> "Q: " + s.faq().getQuestion() + "\nA: " + s.faq().getAnswer())
                .collect(Collectors.joining("\n\n"));

        String answer = chatClient.generateAnswer(faqContext, question);

        // LLM이 FAQ로 답변 불가 판단 시 참고 문서 제거
        if (answer.contains("확인이 어렵습니다") || answer.contains("고객센터로 문의")) {
            return ChatbotAnswerDto.noMatch();
        }

        // 매칭 FAQ 중 link_url이 있는 것만 SiteLink로 변환 (최대 2개)
        List<SiteLink> links = topK.stream()
                .filter(s -> s.faq().getLinkUrl() != null && !s.faq().getLinkUrl().isBlank())
                .limit(2)
                .map(s -> new SiteLink(s.faq().getLinkUrl(), s.faq().getLinkLabel()))
                .collect(Collectors.toList());

        double bestSimilarity = topK.get(0).similarity();
        return new ChatbotAnswerDto(answer, links, matchedIds, bestSimilarity);
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
            log.error("임베딩 JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    record ScoredFaq(Faq faq, double similarity) {}
}
