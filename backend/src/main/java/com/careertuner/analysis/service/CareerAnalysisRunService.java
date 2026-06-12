package com.careertuner.analysis.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.domain.CareerAnalysisRun;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.mapper.CareerAnalysisRunMapper;
import com.careertuner.analysis.ai.prompt.CareerTrendPromptCatalog;
import com.careertuner.dashboard.ai.prompt.DashboardInsightPromptCatalog;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 장기 경향/대시보드 요약 AI 실행 이력 저장소 + read-through 캐시 코디네이터(C 담당).
 *
 * <p>요약 AI는 비용(토큰)이 드는 자원이므로 매 조회마다 재실행하지 않는다.
 * {@link #findFreshRun}로 같은 입력(fingerprint)의 최신 성공 실행이 있으면 그대로 재사용하고,
 * 입력이 바뀌었거나(데이터 변경) 사용자가 명시적으로 재생성을 요청할 때만 새로 기록한다.
 * 사용량 로그(ai_usage_log)와 크레딧 차감은 실제 AI 실행이 일어난 {@link #record} 시점에만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class CareerAnalysisRunService {

    private final CareerAnalysisRunMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 같은 입력 지문(fingerprint)의 최신 실행을 재사용 후보로 반환한다.
     * 실패(FAILED)는 재사용하지 않는다(성공/Fallback 결과만 캐시 대상).
     */
    @Transactional(readOnly = true)
    public Optional<CareerAnalysisRun> findFreshRun(Long userId, String analysisType, String fingerprint) {
        if (fingerprint == null) {
            return Optional.empty();
        }
        CareerAnalysisRun latest = mapper.findLatest(userId, analysisType);
        if (latest == null
                || "FAILED".equals(latest.getStatus())
                || !fingerprint.equals(latest.getInputFingerprint())) {
            return Optional.empty();
        }
        return Optional.of(latest);
    }

    /**
     * AI를 실제로 실행한 결과를 기록한다(career_analysis_run + ai_usage_log).
     * creditUsed > 0 이면 사용자가 명시적으로 지시한 재생성으로 보고 사용량 로그에 차감을 남긴다.
     */
    @Transactional
    public CareerAnalysisRunResponse record(Long userId,
                                            String analysisType,
                                            String fingerprint,
                                            Object input,
                                            Object result,
                                            CareerAnalysisAiUsage usage,
                                            String status,
                                            String errorMessage,
                                            boolean retryable,
                                            int creditUsed) {
        CareerAnalysisRun run = CareerAnalysisRun.builder()
                .userId(userId)
                .analysisType(analysisType)
                .status(status)
                .inputSnapshot(json(input))
                .inputFingerprint(fingerprint)
                .result(json(result))
                .model(usage.model())
                .promptVersion(promptVersion(analysisType))
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .tokenUsage(usage.totalTokens())
                .errorMessage(errorMessage)
                .retryable(retryable)
                .createdAt(LocalDateTime.now())
                .build();
        mapper.insert(run);
        if ("DASHBOARD_SUMMARY".equals(analysisType)) {
            mapper.insertDashboardInsight(
                    userId,
                    run.getId(),
                    summaryText(result),
                    status,
                    usage.model(),
                    usage.totalTokens());
        }
        mapper.insertAiUsageLog(
                userId,
                analysisType,
                status,
                usage.model(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                creditUsed,
                errorMessage);
        return CareerAnalysisRunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public List<CareerAnalysisRunResponse> listByUserId(Long userId) {
        return mapper.findByUserId(userId).stream().map(CareerAnalysisRunResponse::from).toList();
    }

    /** 입력 핵심 필드 문자열로부터 안정적인 캐시 키(SHA-256 hex)를 만든다. */
    public static String fingerprint(String canonical) {
        if (canonical == null) {
            canonical = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256은 표준 JDK에 항상 존재하므로 사실상 도달하지 않는다.
            return Integer.toHexString(canonical.hashCode());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "{}";
        }
    }

    private String summaryText(Object result) {
        try {
            return objectMapper.valueToTree(result).path("summary").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String promptVersion(String analysisType) {
        return "DASHBOARD_SUMMARY".equals(analysisType)
                ? DashboardInsightPromptCatalog.VERSION
                : CareerTrendPromptCatalog.VERSION;
    }
}
