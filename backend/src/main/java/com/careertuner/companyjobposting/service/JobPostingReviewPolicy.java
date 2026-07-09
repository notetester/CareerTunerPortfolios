package com.careertuner.companyjobposting.service;

import org.springframework.stereotype.Component;

import com.careertuner.companyjobposting.mapper.CompanyJobPostingMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 신뢰등급별 공고 검토 정책 — admin_system_policy(JOB_POSTING_REVIEW_POLICY) config_json 을 읽는다.
 * 관리자 운영 정책 화면(AdminPolicyController)에서 수정 가능하며, 정책이 없거나 파싱 실패 시
 * 보수적 기본값(BASIC 둘 다 검토, VERIFIED 등록만 검토, PARTNER 검토 없음)으로 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingReviewPolicy {

    private final CompanyJobPostingMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * @param trustGrade 기업 신뢰등급(BASIC/VERIFIED/PARTNER)
     * @param update     true=게시 중 수정 검토 여부, false=신규 등록 검토 여부
     */
    public boolean requiresReview(String trustGrade, boolean update) {
        String grade = trustGrade == null ? "BASIC" : trustGrade.toUpperCase();
        String key = update ? "updateRequiresReview" : "createRequiresReview";
        try {
            String json = mapper.selectReviewPolicyJson();
            if (json != null && !json.isBlank()) {
                JsonNode gradeNode = objectMapper.readTree(json).path(grade).path(key);
                if (gradeNode.isBoolean()) {
                    return gradeNode.booleanValue();
                }
            }
        } catch (Exception ex) {
            log.warn("공고 검토 정책 파싱 실패 — 기본값 사용 grade={} update={}", grade, update, ex);
        }
        return defaultPolicy(grade, update);
    }

    /** 코드 기본값 — seed 와 동일: BASIC true/true, VERIFIED true/false, PARTNER false/false. */
    private static boolean defaultPolicy(String grade, boolean update) {
        return switch (grade) {
            case "PARTNER" -> false;
            case "VERIFIED" -> !update;
            default -> true;
        };
    }
}
