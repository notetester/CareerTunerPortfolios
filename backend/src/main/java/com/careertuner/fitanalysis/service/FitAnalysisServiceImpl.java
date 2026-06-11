package com.careertuner.fitanalysis.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.fitanalysis.ai.FitAnalysisAiCommand;
import com.careertuner.fitanalysis.ai.FitAnalysisAiResult;
import com.careertuner.fitanalysis.ai.FitAnalysisAiService;
import com.careertuner.fitanalysis.ai.FitAnalysisConfidence;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisHistoryEntryResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FitAnalysisServiceImpl implements FitAnalysisService {

    private static final String FEATURE_TYPE = "FIT_ANALYSIS";
    private static final int MOCK_CREDIT = 2;

    private final FitAnalysisMapper fitAnalysisMapper;
    private final FitAnalysisAiService fitAnalysisAiService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FitAnalysisDetailResponse> list(Long userId) {
        return fitAnalysisMapper.findLatestByUserId(userId).stream()
                .map(this::response)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FitAnalysisDetailResponse getByApplicationCase(Long userId, Long applicationCaseId) {
        FitAnalysisResult result = fitAnalysisMapper.findLatestByUserIdAndApplicationCaseId(userId, applicationCaseId);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "적합도 분석 결과를 찾을 수 없습니다.");
        }
        return response(result);
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
        // 신뢰도는 AI 판단이 아니라 입력 상태 기반의 결정적 계산이라 mock/실 AI 모두 동일하게 산정된다.
        FitAnalysisConfidence confidence = FitAnalysisConfidence.evaluate(command);

        FitAnalysisResult row = FitAnalysisResult.builder()
                .applicationCaseId(applicationCaseId)
                .fitScore(ai.fitScore())
                .matchedSkills(toJson(ai.matchedSkills()))
                .missingSkills(toJson(ai.missingSkills()))
                .recommendedStudy(toJson(ai.recommendedStudy()))
                .recommendedCertificates(toJson(ai.recommendedCertificates()))
                .strategy(ai.strategy())
                .sourceSnapshot(toJson(sourceSnapshot(source)))
                .scoreBasis(toJson(ai.scoreBasis()))
                .gapRecommendations(toJson(ai.gapRecommendations()))
                .certificateRecommendations(toJson(ai.certificateRecommendations()))
                .strategyActions(toJson(ai.strategyActions()))
                .conditionMatrix(toJson(ai.conditionMatrix()))
                .analysisConfidence(toJson(confidence))
                .applyDecision(toJson(ai.applyDecision()))
                .model(ai.usage().model())
                .status(ai.status())
                .errorMessage(ai.errorMessage())
                .build();
        fitAnalysisMapper.insertFitAnalysis(row);
        for (var item : ai.learningRoadmap()) {
            fitAnalysisMapper.insertLearningTask(FitAnalysisLearningTask.builder()
                    .fitAnalysisId(row.getId())
                    .skill(item.skill())
                    .title(item.title())
                    .practiceTask(item.practiceTask())
                    .expectedDuration(item.expectedDuration())
                    .priority(item.priority())
                    .sortOrder(item.sortOrder())
                    .build());
        }

        int tokenUsage = ai.usage().mock() && "SUCCESS".equals(ai.status()) ? estimateTokens(command) : ai.usage().totalTokens();
        int creditUsed = "SUCCESS".equals(ai.status()) ? MOCK_CREDIT : 0;
        fitAnalysisMapper.insertAiUsageLog(
                userId,
                applicationCaseId,
                FEATURE_TYPE,
                ai.status(),
                ai.usage().model(),
                ai.usage().inputTokens(),
                ai.usage().outputTokens(),
                tokenUsage,
                creditUsed,
                ai.errorMessage());

        return getByApplicationCase(userId, applicationCaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FitAnalysisHistoryEntryResponse> getHistory(Long userId, Long applicationCaseId) {
        List<FitAnalysisResult> rows = fitAnalysisMapper.findAllByUserIdAndApplicationCaseId(userId, applicationCaseId);
        List<FitAnalysisHistoryEntryResponse> entries = new java.util.ArrayList<>();
        FitAnalysisResult previous = null;
        for (FitAnalysisResult row : rows) {
            Integer previousScore = previous == null ? null : previous.getFitScore();
            Integer delta = previousScore == null || row.getFitScore() == null
                    ? null
                    : row.getFitScore() - previousScore;
            // 첫 분석은 비교 대상이 없으므로 역량 변화도 비워 둔다(전체 역량이 변화로 잡히는 노이즈 방지).
            List<String> gained = List.of();
            List<String> resolved = List.of();
            List<String> added = List.of();
            if (previous != null) {
                List<String> matched = parseList(row.getMatchedSkills());
                List<String> missing = parseList(row.getMissingSkills());
                List<String> previousMatched = parseList(previous.getMatchedSkills());
                List<String> previousMissing = parseList(previous.getMissingSkills());
                gained = difference(matched, previousMatched);
                resolved = difference(previousMissing, missing);
                added = difference(missing, previousMissing);
            }
            entries.add(new FitAnalysisHistoryEntryResponse(
                    row.getId(),
                    row.getFitScore(),
                    previousScore,
                    delta,
                    gained,
                    resolved,
                    added,
                    row.getModel(),
                    row.getStatus(),
                    row.getCreatedAt()));
            previous = row;
        }
        // 화면은 최신 분석부터 보여준다.
        java.util.Collections.reverse(entries);
        return entries;
    }

    /** base 에는 있고 other 에는 없는 항목(대소문자 무시). */
    private static List<String> difference(List<String> base, List<String> other) {
        var otherLower = other.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        return base.stream()
                .filter(value -> !otherLower.contains(value.toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    @Override
    @Transactional
    public FitAnalysisLearningTaskResponse updateLearningTask(Long userId,
                                                              Long fitAnalysisId,
                                                              Long taskId,
                                                              boolean completed) {
        int updated = fitAnalysisMapper.updateLearningTaskCompleted(userId, fitAnalysisId, taskId, completed);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "학습 과제를 찾을 수 없습니다.");
        }
        return FitAnalysisLearningTaskResponse.from(fitAnalysisMapper.findLearningTaskById(fitAnalysisId, taskId));
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values;
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    private String toJson(Object values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    private Map<String, Object> sourceSnapshot(FitAnalysisGenerationSource source) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("jobAnalysisId", source.getJobAnalysisId());
        snapshot.put("jobPostingId", source.getJobPostingId());
        snapshot.put("jobPostingRevision", source.getJobPostingRevision());
        snapshot.put("jobAnalysisCreatedAt", source.getJobAnalysisCreatedAt());
        snapshot.put("userProfileId", source.getUserProfileId());
        snapshot.put("profileUpdatedAt", source.getProfileUpdatedAt());
        snapshot.put("requiredSkills", parseList(source.getRequiredSkills()));
        snapshot.put("preferredSkills", parseList(source.getPreferredSkills()));
        snapshot.put("profileSkills", parseList(source.getProfileSkills()));
        snapshot.put("profileCertificates", parseList(source.getProfileCertificates()));
        return snapshot;
    }

    private FitAnalysisDetailResponse response(FitAnalysisResult result) {
        List<FitAnalysisLearningTaskResponse> tasks = fitAnalysisMapper.findLearningTasksByFitAnalysisId(result.getId())
                .stream()
                .map(FitAnalysisLearningTaskResponse::from)
                .toList();
        return FitAnalysisDetailResponse.of(result, tasks);
    }

    private int estimateTokens(FitAnalysisAiCommand command) {
        int skills = command.requiredSkills().size() + command.preferredSkills().size() + command.profileSkills().size();
        int duties = command.duties() == null ? 0 : command.duties().length();
        return Math.max(800, Math.min(4000, 600 + skills * 40 + duties));
    }
}
