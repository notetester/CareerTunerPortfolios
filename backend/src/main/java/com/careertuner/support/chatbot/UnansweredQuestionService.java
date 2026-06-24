package com.careertuner.support.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 챗봇이 FAQ로 답 못한 질문을 적재한다(운영 패널 1단계 수집 훅).
 * <p><b>부수효과 전용·best-effort</b>: 내부 예외를 전부 삼켜 챗봇 응답 경로를 절대 깨뜨리지 않는다
 * (quickReplies 와 동일한 graceful degradation 원칙). 인프라 장애 미스 여부 판정은 호출부 책임이고,
 * 여기서는 "정상 미스로 판정된 질문" 만 들어온다.
 */
@Service
public class UnansweredQuestionService {

    private static final Logger log = LoggerFactory.getLogger(UnansweredQuestionService.class);

    /** 정규화 키 최대 길이(컬럼 VARCHAR(255) 와 일치). 초과분은 절단. */
    private static final int NORM_MAX_LEN = 255;

    private final UnansweredQuestionMapper mapper;

    public UnansweredQuestionService(UnansweredQuestionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 답 못한 질문 1건 기록.
     * @param question       사용자 원문 질문(verbatim)
     * @param topSimilarity  FAQ 최고 유사도(임계 미달; 계산 불가/임베딩 FAQ 0건이면 null)
     * @param embedding      질문 임베딩 JSON(군집화용; 직렬화 실패 시 null)
     * @param bestFaqId      가장 가까웠던 FAQ id(없으면 null)
     * @param userId         로그인 유저 id(비로그인 null)
     * @param conversationId 발생 대화 id(있으면)
     */
    public void record(String question, Double topSimilarity, String embedding, Long bestFaqId,
                       Long userId, Long conversationId) {
        try {
            if (question == null || question.isBlank()) {
                return;
            }
            mapper.insert(question, normalize(question), topSimilarity, embedding, bestFaqId, userId, conversationId);
        } catch (Exception e) {
            // 적재 실패는 챗봇 응답과 무관 → 로그만 남기고 흘려보낸다.
            log.warn("답 못한 질문 기록 실패(무시): {}", e.getMessage());
        }
    }

    /**
     * 빈도 집계 키. FastPathService.isFaqIntent 와 <b>동일 규칙</b>(trim+소문자+공백제거)으로 일관성 유지.
     * 컬럼 길이(255) 초과 시 절단.
     */
    private String normalize(String question) {
        String norm = question.trim().toLowerCase().replace(" ", "");
        return norm.length() > NORM_MAX_LEN ? norm.substring(0, NORM_MAX_LEN) : norm;
    }
}
