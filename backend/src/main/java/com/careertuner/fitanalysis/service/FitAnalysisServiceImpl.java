package com.careertuner.fitanalysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.fitanalysis.ai.FitAnalysisAiCommand;
import com.careertuner.fitanalysis.ai.FitAnalysisAiResult;
import com.careertuner.fitanalysis.ai.FitAnalysisAiService;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FitAnalysisServiceImpl implements FitAnalysisService {

    private static final String FEATURE_TYPE = "FIT_ANALYSIS";
    private static final int MOCK_CREDIT = 2;

    private final FitAnalysisMapper fitAnalysisMapper;
    private final FitAnalysisAiService fitAnalysisAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public List<FitAnalysisDetailResponse> list(Long userId) {
        return fitAnalysisMapper.findLatestByUserId(userId).stream()
                .map(FitAnalysisDetailResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FitAnalysisDetailResponse getByApplicationCase(Long userId, Long applicationCaseId) {
        FitAnalysisResult result = fitAnalysisMapper.findLatestByUserIdAndApplicationCaseId(userId, applicationCaseId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
        return FitAnalysisDetailResponse.from(result);
    }

    @Override
    @Transactional
    public FitAnalysisDetailResponse generate(Long userId, Long applicationCaseId) {
        FitAnalysisGenerationSource source = fitAnalysisMapper.findGenerationSource(userId, applicationCaseId);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원 건을 찾을 수 없습니다.");
        }

        FitAnalysisAiCommand command = new FitAnalysisAiCommand(
                source.getCompanyName(),
                source.getJobTitle(),
                parseList(source.getRequiredSkills()),
                parseList(source.getPreferredSkills()),
                source.getDuties(),
                parseList(source.getProfileSkills()),
                parseList(source.getProfileCertificates()),
                source.getDesiredJob());

        FitAnalysisAiResult ai = fitAnalysisAiService.generate(command);

        FitAnalysisResult row = FitAnalysisResult.builder()
                .applicationCaseId(applicationCaseId)
                .fitScore(ai.fitScore())
                .matchedSkills(toJson(ai.matchedSkills()))
                .missingSkills(toJson(ai.missingSkills()))
                .recommendedStudy(toJson(ai.recommendedStudy()))
                .recommendedCertificates(toJson(ai.recommendedCertificates()))
                .strategy(ai.strategy())
                .build();
        fitAnalysisMapper.insertFitAnalysis(row);

        // 공통 사용량 로깅(공통 규약). mock 단계에서는 model="mock"으로 기록한다.
        fitAnalysisMapper.insertAiUsageLog(userId, applicationCaseId, FEATURE_TYPE, "SUCCESS", "mock",
                estimateTokens(command), MOCK_CREDIT);

        return getByApplicationCase(userId, applicationCaseId);
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private int estimateTokens(FitAnalysisAiCommand command) {
        int skills = command.requiredSkills().size() + command.preferredSkills().size() + command.profileSkills().size();
        int duties = command.duties() == null ? 0 : command.duties().length();
        return Math.max(800, Math.min(4000, 600 + skills * 40 + duties));
    }
}
