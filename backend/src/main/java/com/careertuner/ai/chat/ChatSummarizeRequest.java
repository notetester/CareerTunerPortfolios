package com.careertuner.ai.chat;

import java.util.List;

/**
 * 추천 후기 묶음 요약 요청. summaryChip 의 postIds 를 그대로 실어 보낸다.
 * conversationId 는 진단/연속성용으로만 받고 응답에 되돌려 준다(메모리 기록은 하지 않음).
 */
public record ChatSummarizeRequest(Long conversationId, List<Long> postIds) {}
