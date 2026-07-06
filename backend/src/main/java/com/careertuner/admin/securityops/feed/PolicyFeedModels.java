package com.careertuner.admin.securityops.feed;

import java.util.List;

/** 정책기관 피드 import 관련 요청·결과·파싱 모델 묶음. */
public final class PolicyFeedModels {

    private PolicyFeedModels() {
    }

    /** JSON API 업로드 요청. rows 를 직접 주거나, rawText(CSV/JSON 문자열)를 주면 서버가 파싱한다. */
    public record PolicyFeedImportRequest(
            String sourceName,
            String action,          // 기본 BLOCK
            String category,        // 기본 SECURITY
            String rawText,         // CSV 또는 JSON 원문(선택)
            List<PolicyFeedRow> rows) {
    }

    /** 피드 한 행. matchType 이 비면 value 로 자동 추론한다. */
    public record PolicyFeedRow(
            String value,
            String matchType,
            String action,
            String reason) {
    }

    /** 파싱된(정규화·검증 완료) 규칙 후보. */
    public record ParsedFeedRule(
            String value,
            String matchType,
            String action,
            String reason,
            boolean valid,
            String error) {

        public static ParsedFeedRule invalid(String value, String error) {
            return new ParsedFeedRule(value, null, null, null, false, error);
        }
    }

    /** import 결과 리포트. */
    public record PolicyFeedImportResult(
            Long batchId,
            String batchCode,
            int total,
            int created,
            int skipped,
            int failed,
            List<String> messages) {
    }
}
