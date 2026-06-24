package com.careertuner.support.chatbot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
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
        try {
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
            if (faqVector.length != queryVector.length) {
                log.warn("FAQ #{} 임베딩 차원 불일치: faq={}, query={} → 건너뜀",
                        faq.getId(), faqVector.length, queryVector.length);
                continue;
            }
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

        // LLM 응답이 비어 있으면 관련 없음 (NPE 가드)
        if (answer == null || answer.isBlank()) {
            log.warn("LLM 응답이 비어 있습니다 → 관련 FAQ 없음");
            return ChatbotAnswerDto.noMatch();
        }

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
        } catch (Exception e) {
            log.error("챗봇 응답 생성 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return ChatbotAnswerDto.noMatch();
        }
    }

    /**
     * 챗봇 에이전트의 searchFaq 툴용: 질문과 유사한 FAQ를 찾아 매칭 결과를 반환.
     * ask()와 달리 LLM 답변 생성 없이 매칭 FAQ만 돌려준다(에이전트가 요약/응답).
     * 각 매칭에 FAQ에 달린 link_url/link_label 을 포함한다(응답 링크 접지용).
     * @return 매칭 FAQ 목록, 없으면 빈 리스트
     */
    public List<FaqHit> searchFaqHits(String question) {
        try {
            double[] queryVector = embeddingClient.embed(question);
            List<Faq> faqs = faqMapper.findPublishedWithEmbedding();
            if (faqs.isEmpty()) {
                return List.of();
            }
            List<ScoredFaq> scored = new ArrayList<>();
            for (Faq faq : faqs) {
                double[] faqVector = parseEmbedding(faq.getEmbedding());
                if (faqVector == null || faqVector.length != queryVector.length) {
                    continue;
                }
                scored.add(new ScoredFaq(faq, CosineSimilarity.compute(queryVector, faqVector)));
            }
            scored.sort(Comparator.comparingDouble(ScoredFaq::similarity).reversed());

            return scored.stream()
                    .filter(s -> s.similarity() >= props.getSimilarityThreshold())
                    .limit(props.getTopK())
                    .map(s -> new FaqHit(s.faq().getQuestion(), s.faq().getAnswer(),
                            s.faq().getLinkUrl(), s.faq().getLinkLabel()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("FAQ 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 임계 필터 <b>전</b> FAQ 최고 유사도만 반환(운영 패널 1단계 — 미스 질문의 top_similarity 보강용).
     * searchFaqHits 와 임베딩·정렬 로직은 같되 임계 필터를 적용하지 않는다.
     * <p>임베딩 FAQ 가 0건이면 {@link OptionalDouble#empty()}(정상 미스 — 채울 유사도 없음).
     * <p><b>예외를 삼키지 않는다</b>: 임베딩/Ollama 장애 시 그대로 던져, 호출부가
     * "정상 미스(빈 결과)"와 "인프라 장애"를 구분해 장애를 미스로 오기록하지 않도록 한다.
     */
    public OptionalDouble topFaqSimilarity(String question) {
        double[] queryVector = embeddingClient.embed(question);
        List<Faq> faqs = faqMapper.findPublishedWithEmbedding();
        if (faqs.isEmpty()) {
            return OptionalDouble.empty();
        }
        double best = Double.NEGATIVE_INFINITY;
        for (Faq faq : faqs) {
            double[] faqVector = parseEmbedding(faq.getEmbedding());
            if (faqVector == null || faqVector.length != queryVector.length) {
                continue;
            }
            double similarity = CosineSimilarity.compute(queryVector, faqVector);
            if (similarity > best) {
                best = similarity;
            }
        }
        return best == Double.NEGATIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(best);
    }

    /** 매칭 FAQ를 "Q/A" 텍스트로 합쳐 반환(모델 컨텍스트용). 없으면 빈 문자열. */
    public String searchFaqContext(String question) {
        return searchFaqHits(question).stream()
                .map(h -> "Q: " + h.question() + "\nA: " + h.answer())
                .collect(Collectors.joining("\n\n"));
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
