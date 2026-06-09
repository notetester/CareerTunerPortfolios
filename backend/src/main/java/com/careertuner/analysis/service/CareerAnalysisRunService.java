package com.careertuner.analysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.domain.CareerAnalysisRun;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.mapper.CareerAnalysisRunMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CareerAnalysisRunService {

    private final CareerAnalysisRunMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CareerAnalysisRunResponse record(Long userId,
                                            String analysisType,
                                            Object input,
                                            Object result,
                                            CareerAnalysisAiUsage usage,
                                            String status,
                                            String errorMessage,
                                            boolean retryable) {
        CareerAnalysisRun run = CareerAnalysisRun.builder()
                .userId(userId)
                .analysisType(analysisType)
                .status(status)
                .inputSnapshot(json(input))
                .result(json(result))
                .model(usage.model())
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .tokenUsage(usage.totalTokens())
                .errorMessage(errorMessage)
                .retryable(retryable)
                .build();
        mapper.insert(run);
        mapper.insertAiUsageLog(
                userId,
                analysisType,
                status,
                usage.model(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                errorMessage);
        return CareerAnalysisRunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public List<CareerAnalysisRunResponse> listByUserId(Long userId) {
        return mapper.findByUserId(userId).stream().map(CareerAnalysisRunResponse::from).toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "{}";
        }
    }
}
