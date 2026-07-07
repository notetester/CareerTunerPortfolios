package com.careertuner.correction.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.billing.dto.AiChargeCommand;
import com.careertuner.billing.dto.AiChargeResult;
import com.careertuner.billing.service.AiChargeService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.CorrectionAiClient;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.domain.CorrectionRequest;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.dto.CorrectionResultPayload;
import com.careertuner.correction.mapper.CorrectionMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int ORIGINAL_TEXT_MAX_LENGTH = 12000;
    private static final String CHARGE_REF_TYPE = "CORRECTION";

    private static final String TYPE_SELF_INTRO = "SELF_INTRO";
    private static final String TYPE_INTERVIEW_ANSWER = "INTERVIEW_ANSWER";
    private static final String TYPE_RESUME = "RESUME";
    private static final String TYPE_PORTFOLIO = "PORTFOLIO";

    /** 첨삭 유형별 알림 문구 라벨. */
    private static final Map<String, String> CORRECTION_TYPE_LABELS = Map.of(
            TYPE_SELF_INTRO, "자기소개서",
            TYPE_INTERVIEW_ANSWER, "면접 답변",
            TYPE_RESUME, "이력서",
            TYPE_PORTFOLIO, "포트폴리오");

    private final CorrectionMapper correctionMapper;
    private final CorrectionAiClient aiClient;
    private final CorrectionAiUsageLogService usageLogService;
    private final ApplicationCaseAccessService applicationCaseAccessService;
    private final CorrectionContextService contextService;
    private final TransactionTemplate transactionTemplate;
    private final AiChargeService aiChargeService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public CorrectionResponse create(Long userId, CorrectionCreateRequest request) {
        return create(userId, request, true);
    }

    /** AutoPrep은 단계별 결제 고지 계약이 없어 기존 WRITE 동작을 비과금으로 유지한다. */
    public CorrectionResponse createUnchargedForAutoPrep(Long userId, CorrectionCreateRequest request) {
        return create(userId, request, false);
    }

    private CorrectionResponse create(Long userId, CorrectionCreateRequest request, boolean chargeRequired) {
        String correctionType = normalizeCorrectionType(request == null ? null : request.correctionType());
        String originalText = normalizeOriginalText(request == null ? null : request.originalText());
        String sourceType = normalizeSourceType(request == null ? null : request.sourceType());
        String policyAcknowledgementKey = chargeRequired
                ? normalizePolicyAcknowledgementKey(request == null ? null : request.policyAcknowledgementKey())
                : null;
        Long applicationCaseId = request == null ? null : request.applicationCaseId();
        ApplicationCase applicationCase = applicationCaseId == null
                ? null
                : applicationCaseAccessService.requireOwned(userId, applicationCaseId);
        String featureType = featureType(correctionType);
        var selfInput = contextService.build(
                userId,
                correctionType,
                applicationCase,
                originalText,
                request == null ? null : request.questionText());

        CorrectionPayload payload;
        try {
            payload = aiClient.correct(new CorrectionCommand(
                    correctionType,
                    sourceType,
                    request == null ? null : request.sourceRefId(),
                    applicationCaseId,
                    applicationCase,
                    originalText,
                    request == null ? null : request.questionText(),
                    selfInput));
            validateChargeablePayload(payload);
        } catch (RuntimeException ex) {
            recordFailureBestEffort(userId, applicationCaseId, featureType, ex);
            throw ex;
        }

        CorrectionResponse response = transactionTemplate.execute(status -> {
            Long aiUsageLogId = usageLogService.recordSuccess(
                    userId, applicationCaseId, featureType, payload.usage());
            CorrectionRequest correction = CorrectionRequest.builder()
                    .userId(userId)
                    .applicationCaseId(applicationCaseId)
                    .correctionType(correctionType)
                    .sourceType(sourceType)
                    .sourceRefId(request == null ? null : request.sourceRefId())
                    .originalText(originalText)
                    .improvedText(payload.improvedText())
                    .resultJson(resultJson(payload))
                    .status("SUCCESS")
                    .aiUsageLogId(aiUsageLogId)
                    .build();
            correctionMapper.insert(correction);
            AiChargeResult chargeResult = null;
            if (chargeRequired) {
                chargeResult = aiChargeService.charge(new AiChargeCommand(
                        userId,
                        featureType,
                        CHARGE_REF_TYPE,
                        correction.getId(),
                        aiUsageLogId,
                        null,
                        payload.usage().totalTokens(),
                        "AI 첨삭 사용",
                        policyAcknowledgementKey));
                requireCompletedCharge(chargeResult);
            }
            return CorrectionResponse.from(
                    correction,
                    resultPayload(payload),
                    chargeResult,
                    payload.usage().totalTokens());
        });
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction transaction did not complete.");
        }
        notifyCompletion(userId, correctionType, response.id());
        return response;
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> list(Long userId, Long applicationCaseId, String correctionType, Integer limit) {
        if (applicationCaseId != null) {
            applicationCaseAccessService.requireOwned(userId, applicationCaseId);
        }
        String normalizedType = isBlank(correctionType) ? null : normalizeCorrectionType(correctionType);
        return correctionMapper.findByUserId(userId, applicationCaseId, normalizedType, normalizeLimit(limit)).stream()
                .map(correction -> CorrectionResponse.from(correction, readResultPayload(correction.getResultJson())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CorrectionResponse get(Long userId, Long id) {
        CorrectionRequest correction = correctionMapper.findByIdAndUserId(id, userId);
        if (correction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Correction request was not found.");
        }
        return CorrectionResponse.from(correction, readResultPayload(correction.getResultJson()));
    }

    private String normalizeCorrectionType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        return switch (type) {
            case TYPE_SELF_INTRO, TYPE_INTERVIEW_ANSWER, TYPE_RESUME, TYPE_PORTFOLIO -> type;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "Unsupported correction type.");
        };
    }

    private String normalizeSourceType(String value) {
        String sourceType = value == null || value.trim().isEmpty() ? "DIRECT_INPUT" : value.trim().toUpperCase();
        if (sourceType.length() > 40) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType is too long.");
        }
        return sourceType;
    }

    private String normalizeOriginalText(String value) {
        if (isBlank(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "originalText is required.");
        }
        String text = value.trim();
        if (text.length() > ORIGINAL_TEXT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "originalText is too long.");
        }
        return text;
    }

    private String normalizePolicyAcknowledgementKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isBlank() || key.length() > 120 || !key.matches("[A-Za-z0-9:_-]+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차감 정책 확인키가 올바르지 않습니다.");
        }
        return key;
    }

    private void validateChargeablePayload(CorrectionPayload payload) {
        if (payload == null || isBlank(payload.improvedText()) || payload.usage() == null) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "첨삭 결과 또는 AI 사용량 정보를 확인할 수 없습니다.");
        }
    }

    private void requireCompletedCharge(AiChargeResult result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "첨삭 사용 요금을 처리하지 못했습니다.");
        }
        if (result.chargeType() == AiChargeResult.ChargeType.TICKET
                || result.chargeType() == AiChargeResult.ChargeType.CREDIT) {
            return;
        }
        if ("ALREADY_CHARGED".equals(result.reason()) || "ALREADY_DEDUCTED".equals(result.reason())) {
            return;
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "첨삭 사용 요금이 확정되지 않았습니다.");
    }

    private void notifyCompletion(Long userId, String correctionType, Long correctionId) {
        try {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("CORRECTION_COMPLETE")
                    .targetType(CHARGE_REF_TYPE)
                    .targetId(correctionId)
                    .title("첨삭이 완료되었습니다")
                    .message("%s 첨삭 결과가 준비되었습니다.".formatted(
                            CORRECTION_TYPE_LABELS.getOrDefault(correctionType, "첨삭")))
                    .link("/correction")
                    .build());
        } catch (RuntimeException ex) {
            log.error("첨삭 완료 알림 발행 실패: userId={}, correctionId={}", userId, correctionId, ex);
        }
    }

    private void recordFailureBestEffort(
            Long userId, Long applicationCaseId, String featureType, RuntimeException failure) {
        try {
            usageLogService.recordFailure(
                    userId, applicationCaseId, featureType, userFacingFailureMessage(failure));
        } catch (RuntimeException logFailure) {
            log.error("첨삭 실패 사용량 로그 저장 실패: userId={}, featureType={}", userId, featureType, logFailure);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String featureType(String correctionType) {
        return "CORRECTION_" + correctionType;
    }

    private CorrectionResultPayload resultPayload(CorrectionPayload payload) {
        return new CorrectionResultPayload(
                payload.summary(),
                payload.issues(),
                payload.changeReasons(),
                payload.suggestions());
    }

    private String resultJson(CorrectionPayload payload) {
        try {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("summary", payload.summary());
            result.put("issues", payload.issues());
            result.put("changeReasons", payload.changeReasons());
            result.put("suggestions", payload.suggestions());
            if (payload.modelResult() != null && !payload.modelResult().isEmpty()) {
                result.put("modelResult", payload.modelResult());
            }
            return objectMapper.writeValueAsString(result);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Correction result could not be serialized.");
        }
    }

    private CorrectionResultPayload readResultPayload(String resultJson) {
        if (isBlank(resultJson)) {
            return CorrectionResultPayload.empty();
        }
        try {
            var root = objectMapper.readTree(resultJson);
            return new CorrectionResultPayload(
                    root.path("summary").asText(""),
                    stringList(root.path("issues")),
                    stringList(root.path("changeReasons")),
                    stringList(root.path("suggestions")));
        } catch (JacksonException ex) {
            return CorrectionResultPayload.empty();
        }
    }

    private List<String> stringList(tools.jackson.databind.JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new java.util.ArrayList<>();
        for (var item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String userFacingFailureMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (isBlank(message)) {
            return "Correction generation failed.";
        }
        return message.length() > 300 ? "Correction generation failed." : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
