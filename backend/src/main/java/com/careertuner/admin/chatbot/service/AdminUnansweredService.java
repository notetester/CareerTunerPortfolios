package com.careertuner.admin.chatbot.service;

import java.util.List;

import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;
import com.careertuner.common.security.AuthUser;

/**
 * 답 못한 질문 운영 조회·상태변경(관리자). 운영 패널 1단계.
 */
public interface AdminUnansweredService {

    /** 상태별 그룹 집계 목록(빈도 desc, 최신 desc). */
    List<AdminUnansweredQuestionResponse> getUnanswered(AuthUser authUser, String status, int page, int size);

    /** 대표 id 가 속한 그룹의 상태를 REVIEWED/DISMISSED 로 일괄 변경. */
    void updateStatus(AuthUser authUser, Long id, String status);
}
