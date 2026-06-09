package com.careertuner.admin.fitanalysis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListItemResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoRequest;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisMemoResponse;
import com.careertuner.admin.fitanalysis.mapper.AdminFitAnalysisMapper;
import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisMemo;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminFitAnalysisServiceImpl implements AdminFitAnalysisService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AdminFitAnalysisMapper adminFitAnalysisMapper;
    private final ObjectMapper objectMapper;

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
                parseList(result.getRecommendedCertificates()),
                listMemos(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminFitAnalysisMemoResponse> listMemos(Long fitAnalysisId) {
        ensureFitAnalysisExists(fitAnalysisId);
        return adminFitAnalysisMapper.findMemosByFitAnalysisId(fitAnalysisId).stream()
                .map(AdminFitAnalysisMemoResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminFitAnalysisMemoResponse createMemo(Long fitAnalysisId, Long adminUserId, AdminFitAnalysisMemoRequest request) {
        ensureFitAnalysisExists(fitAnalysisId);
        AdminFitAnalysisMemo memo = AdminFitAnalysisMemo.builder()
                .fitAnalysisId(fitAnalysisId)
                .adminUserId(adminUserId)
                .memoType(normalizeMemoType(request.memoType()))
                .content(request.content().trim())
                .build();
        adminFitAnalysisMapper.insertMemo(memo);
        return AdminFitAnalysisMemoResponse.from(adminFitAnalysisMapper.findMemoByIdAndFitAnalysisId(memo.getId(), fitAnalysisId));
    }

    @Override
    @Transactional
    public AdminFitAnalysisMemoResponse updateMemo(Long fitAnalysisId, Long memoId, AdminFitAnalysisMemoRequest request) {
        AdminFitAnalysisMemo memo = adminFitAnalysisMapper.findMemoByIdAndFitAnalysisId(memoId, fitAnalysisId);
        if (memo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
        memo.setMemoType(normalizeMemoType(request.memoType()));
        memo.setContent(request.content().trim());
        int updated = adminFitAnalysisMapper.updateMemo(memo);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
        return AdminFitAnalysisMemoResponse.from(adminFitAnalysisMapper.findMemoByIdAndFitAnalysisId(memoId, fitAnalysisId));
    }

    @Override
    @Transactional
    public void deleteMemo(Long fitAnalysisId, Long memoId) {
        int deleted = adminFitAnalysisMapper.deleteMemo(memoId, fitAnalysisId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "운영 메모를 찾을 수 없습니다.");
        }
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

    private void ensureFitAnalysisExists(Long fitAnalysisId) {
        if (adminFitAnalysisMapper.findById(fitAnalysisId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
    }

    private static String normalizeMemoType(String value) {
        return value == null || value.isBlank() ? "GENERAL" : value.trim().toUpperCase();
    }
}
