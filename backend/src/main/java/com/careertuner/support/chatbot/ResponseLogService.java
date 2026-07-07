package com.careertuner.support.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 챗봇이 응답할 때마다 한 턴(답함/못함·유사도·인용 FAQ·전환)을 chatbot_response_log 에 적재한다
 * (운영 패널 3단계-2 적재 훅).
 * <p><b>부수효과 전용·best-effort</b>: 내부 예외를 전부 삼켜 챗봇 응답 경로를 절대 깨뜨리지 않는다
 * ({@link UnansweredQuestionService} 와 동일한 graceful degradation 원칙). 어느 응답 경로
 * (NAV_FAST/FAQ_FAST/AGENT)에서 왔는지·자동 해결 여부는 호출부가 결정해 넘긴다.
 * <p>질문 컬럼은 VARCHAR(1000) 이라 초과분은 절단해 적재 실패(길이 초과)를 막는다.
 */
@Service
public class ResponseLogService {

    private static final Logger log = LoggerFactory.getLogger(ResponseLogService.class);

    /** question 컬럼 최대 길이(VARCHAR(1000) 와 일치). 초과분은 절단. */
    private static final int QUESTION_MAX_LEN = 1000;

    private final ResponseLogMapper mapper;

    public ResponseLogService(ResponseLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 응답 1턴 기록.
     * @param conversationId 발생 대화 id(있으면)
     * @param userId         로그인 유저 id(비로그인 null)
     * @param question       사용자 원문 질문(verbatim; 1000자 초과 절단)
     * @param responsePath   응답 경로: NAV_FAST / FAQ_FAST / AGENT
     * @param faqReferenced  FAQ 근거로 답함(=자동 해결로 카운트)
     * @param topSimilarity  FAQ 최고 유사도(계산 불가/해당 없음이면 null)
     * @param matchedFaqId   인용한 최상위 FAQ id(없으면 null)
     * @param handoff        상담사/문의 전환 여부
     */
    public void record(Long conversationId, Long userId, String question, String responsePath,
                       boolean faqReferenced, Double topSimilarity, Long matchedFaqId, boolean handoff) {
        try {
            if (question == null || question.isBlank()) {
                return;
            }
            mapper.insert(conversationId, userId, truncate(question), responsePath,
                    faqReferenced, topSimilarity, matchedFaqId, handoff);
        } catch (Exception e) {
            // 적재 실패는 챗봇 응답과 무관 → 로그만 남기고 흘려보낸다.
            log.warn("응답 로그 기록 실패(무시): {}", e.getMessage());
        }
    }

    private String truncate(String question) {
        return question.length() > QUESTION_MAX_LEN ? question.substring(0, QUESTION_MAX_LEN) : question;
    }

    /** 오늘(로컬 자정 이후) 해당 사용자가 보낸 질문 수 — 챗봇 일일 쿼터 산정용. */
    public int countToday(Long userId) {
        return mapper.countByUserSince(userId, java.time.LocalDate.now().atStartOfDay());
    }
}
