package com.careertuner.correction.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CorrectionService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int ORIGINAL_TEXT_MAX_LENGTH = 12000;

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
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public CorrectionResponse create(Long userId, CorrectionCreateRequest request) {
        String correctionType = normalizeCorrectionType(request == null ? null : request.correctionType());
        String originalText = normalizeOriginalText(request == null ? null : request.originalText());
        String sourceType = normalizeSourceType(request == null ? null : request.sourceType());
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
        } catch (RuntimeException ex) {
            usageLogService.recordFailure(userId, applicationCaseId, featureType, userFacingFailureMessage(ex));
            throw ex;
        }

        return transactionTemplate.execute(status -> {
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
            // 첨삭이 성공하면 사용자에게 완료 알림을 남긴다.
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("CORRECTION_COMPLETE")
                    .targetType("CORRECTION")
                    .targetId(correction.getId())
                    .title("첨삭이 완료되었습니다")
                    .message("%s 첨삭 결과가 준비되었습니다.".formatted(
                            CORRECTION_TYPE_LABELS.getOrDefault(correctionType, "첨삭")))
                    .link("/correction")
                    .build());
            return CorrectionResponse.from(correction, resultPayload(payload));
        });
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
