package com.careertuner.admin.chatbot.service;

import com.careertuner.admin.chatbot.dto.ThresholdPreviewResponse;
import com.careertuner.common.security.AuthUser;

/**
 * 유사도 임계값 슬라이더 미리보기(F2, §1-Q3). <b>읽기 전용 — 실 챗봇 컷오프(0.5 고정)를 바꾸지 않는다.</b>
 * 저장/적용 동작은 의도적으로 제공하지 않는다(슬라이더 실적용은 사람 승인·별도 게이트).
 * /api/admin/chatbot/threshold/preview.
 */
public interface AdminChatbotThresholdService {

    /**
     * 후보 임계값 미리보기: chatbot_response_log.top_similarity 분포에서 "threshold 미만 = 공백 N" 을
     * 읽기 전용으로 계산하고, 슬라이더 위치별 변화를 그릴 0.05 폭 히스토그램을 함께 준다.
     *
     * @param threshold 후보 임계값. null 이면 INVALID_INPUT, 범위 밖이면 [0.0,1.0] 으로 보정.
     */
    ThresholdPreviewResponse preview(AuthUser authUser, Double threshold);
}
