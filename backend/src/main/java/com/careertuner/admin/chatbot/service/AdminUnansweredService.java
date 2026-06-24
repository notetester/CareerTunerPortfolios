package com.careertuner.admin.chatbot.service;

import java.util.List;

import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;
import com.careertuner.admin.chatbot.dto.FaqDraftResponse;
import com.careertuner.admin.faq.dto.AdminFaqRequest;
import com.careertuner.admin.faq.dto.AdminFaqResponse;
import com.careertuner.common.security.AuthUser;

/**
 * 답 못한 질문 운영 조회·상태변경·FAQ 전환(관리자). 운영 패널 1·2단계.
 */
public interface AdminUnansweredService {

    /** 상태별 그룹 집계 목록(빈도 desc, 최신 desc). */
    List<AdminUnansweredQuestionResponse> getUnanswered(AuthUser authUser, String status, int page, int size);

    /** 대표 id 가 속한 그룹의 상태를 REVIEWED/DISMISSED 로 일괄 변경. */
    void updateStatus(AuthUser authUser, Long id, String status);

    /** 대표 질문으로 FAQ 답변 초안 생성(저장 안 함, 반환만). 2단계. */
    FaqDraftResponse generateDraft(AuthUser authUser, Long id);

    /** 운영자가 다듬은 초안을 FAQ로 등록하고 원 질문 그룹을 CONVERTED 로 표시. 2단계. */
    AdminFaqResponse convert(AuthUser authUser, Long id, AdminFaqRequest request);
}
