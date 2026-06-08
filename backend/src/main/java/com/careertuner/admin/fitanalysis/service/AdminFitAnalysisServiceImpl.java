package com.careertuner.admin.fitanalysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.mapper.AdminFitAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminFitAnalysisServiceImpl implements AdminFitAnalysisService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AdminFitAnalysisMapper adminFitAnalysisMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public List<AdminFitAnalysisListItemResponse> list() {
        return adminFitAnalysisMapper.findLatestAll().stream()
                .map(result -> AdminFitAnalysisListItemResponse.of(
                        result,
                        parseList(result.getMatchedSkills()),
                        parseList(result.getMissingSkills())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFitAnalysisDetailResponse get(Long id) {
        AdminFitAnalysisResult result = adminFitAnalysisMapper.findById(id);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
        return AdminFitAnalysisDetailResponse.of(
                result,
                parseList(result.getMatchedSkills()),
                parseList(result.getMissingSkills()),
                parseList(result.getRecommendedStudy()),
                parseList(result.getRecommendedCertificates()));
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST).stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return List.of(value.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
    }
}
