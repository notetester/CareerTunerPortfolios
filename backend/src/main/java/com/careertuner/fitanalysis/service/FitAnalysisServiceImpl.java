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
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisHistoryEntryResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;
import com.careertuner.fitanalysis.dto.FitActionBoardResponse;
import com.careertuner.fitanalysis.dto.FitScoreBreakdownResponse;
import com.careertuner.fitanalysis.dto.FitToneStrategyResponse;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.NotificationMapper;
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
    private final NotificationMapper notificationMapper;
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

        FitAnalysisResult previous = fitAnalysisMapper.findLatestByUserIdAndApplicationCaseId(userId, applicationCaseId);
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
                .promptVersion(FitAnalysisPromptCatalog.VERSION)
                .status(ai.status())
                .errorMessage(ai.errorMessage())
                .build();
        fitAnalysisMapper.insertFitAnalysis(row);
        fitAnalysisMapper.insertHistory(
                row.getId(),
                applicationCaseId,
                previous == null ? null : previous.getFitScore(),
                row.getFitScore(),
                toJson(historyDiff(previous, row)));
        int conditionOrder = 1;
        for (var condition : ai.conditionMatrix()) {
            String severity = "REQUIRED".equals(condition.conditionType()) && "UNMET".equals(condition.matchStatus())
                    ? "HIGH"
                    : "UNMET".equals(condition.matchStatus()) ? "MEDIUM" : "LOW";
            fitAnalysisMapper.insertConditionMatch(row.getId(), condition, severity, conditionOrder++);
        }
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

        // 적합도 분석이 성공하면 사용자에게 완료 알림을 남긴다.
        if ("SUCCESS".equals(ai.status())) {
            notificationMapper.insert(Notification.builder()
                    .userId(userId)
                    .type("FIT_ANALYSIS_COMPLETED")
                    .targetType("APPLICATION_CASE")
                    .targetId(applicationCaseId)
                    .title("적합도 분석이 완료되었습니다")
                    .message("%s · %s 적합도 %d점".formatted(
                            source.getCompanyName(), source.getJobTitle(), ai.fitScore()))
                    .link("/applications/" + applicationCaseId + "/fit")
                    .build());
        }

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
        List<com.careertuner.fitanalysis.ai.FitConditionMatch> conditions = parseValue(
                result.getConditionMatrix(), new TypeReference<List<com.careertuner.fitanalysis.ai.FitConditionMatch>>() {}, List.of());
        List<com.careertuner.fitanalysis.ai.FitGapRecommendation> gaps = parseValue(
                result.getGapRecommendations(), new TypeReference<List<com.careertuner.fitanalysis.ai.FitGapRecommendation>>() {}, List.of());
        List<String> actions = parseList(result.getStrategyActions());
        return FitAnalysisDetailResponse.of(
                result,
                tasks,
                scoreBreakdown(result.getFitScore(), conditions),
                actionBoard(actions, tasks),
                adverseStrategies(gaps),
                next24HourActions(actions, gaps),
                toneStrategies(result.getFitScore(), gaps));
    }

    private Map<String, Object> historyDiff(FitAnalysisResult previous, FitAnalysisResult current) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (previous == null) {
            diff.put("scoreDelta", null);
            diff.put("gainedSkills", List.of());
            diff.put("resolvedGaps", List.of());
            diff.put("newGaps", List.of());
            return diff;
        }
        diff.put("scoreDelta", current.getFitScore() == null || previous.getFitScore() == null
                ? null : current.getFitScore() - previous.getFitScore());
        diff.put("gainedSkills", difference(parseList(current.getMatchedSkills()), parseList(previous.getMatchedSkills())));
        diff.put("resolvedGaps", difference(parseList(previous.getMissingSkills()), parseList(current.getMissingSkills())));
        diff.put("newGaps", difference(parseList(current.getMissingSkills()), parseList(previous.getMissingSkills())));
        return diff;
    }

    private <T> T parseValue(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            T value = objectMapper.readValue(json, type);
            return value == null ? fallback : value;
        } catch (JacksonException ex) {
            return fallback;
        }
    }

    private static List<FitScoreBreakdownResponse> scoreBreakdown(
            Integer score,
            List<com.careertuner.fitanalysis.ai.FitConditionMatch> rows) {
        int target = score == null ? 0 : score;
        int required = weightedConditionScore(rows, "REQUIRED", 45);
        int preferred = weightedConditionScore(rows, "PREFERRED", 25);
        int remainder = Math.max(0, target - required - preferred);
        int project = Math.min(15, remainder);
        int experience = Math.min(10, Math.max(0, remainder - project));
        int profile = Math.min(5, Math.max(0, remainder - project - experience));
        int assigned = required + preferred + project + experience + profile;
        int unassigned = Math.max(0, target - assigned);
        int requiredExtra = Math.min(45 - required, unassigned);
        required += requiredExtra;
        unassigned -= requiredExtra;
        int preferredExtra = Math.min(25 - preferred, unassigned);
        preferred += preferredExtra;
        unassigned -= preferredExtra;
        int projectExtra = Math.min(15 - project, unassigned);
        project += projectExtra;
        return List.of(
                new FitScoreBreakdownResponse("REQUIRED", "필수 조건 충족도", required, 45, "필수 요구조건의 충족·부분 충족 비율"),
                new FitScoreBreakdownResponse("PREFERRED", "우대 조건 충족도", preferred, 25, "우대 요구조건의 충족·부분 충족 비율"),
                new FitScoreBreakdownResponse("PROJECT", "프로젝트 연관성", project, 15, "매칭 역량과 프로젝트 적용 가능성"),
                new FitScoreBreakdownResponse("EXPERIENCE", "경력·경험 신뢰도", experience, 10, "등록된 경험 근거의 활용 가능성"),
                new FitScoreBreakdownResponse("PROFILE", "프로필 완성도 보정", profile, 5, "분석 입력의 완성도와 신뢰도"));
    }

    private static int weightedConditionScore(
            List<com.careertuner.fitanalysis.ai.FitConditionMatch> rows,
            String type,
            int maximum) {
        List<com.careertuner.fitanalysis.ai.FitConditionMatch> typed = rows.stream()
                .filter(row -> type.equals(row.conditionType()))
                .toList();
        if (typed.isEmpty()) {
            return 0;
        }
        double matched = typed.stream().mapToDouble(row ->
                "MET".equals(row.matchStatus()) ? 1.0 : "PARTIAL".equals(row.matchStatus()) ? 0.5 : 0.0).sum();
        return (int) Math.round(maximum * matched / typed.size());
    }

    private static FitActionBoardResponse actionBoard(List<String> actions, List<FitAnalysisLearningTaskResponse> tasks) {
        List<String> done = tasks.stream().filter(FitAnalysisLearningTaskResponse::completed)
                .map(FitAnalysisLearningTaskResponse::title).limit(4).toList();
        List<String> inProgress = tasks.stream().filter(task -> !task.completed())
                .map(FitAnalysisLearningTaskResponse::title).limit(2).toList();
        List<String> todo = actions.stream().filter(action -> !inProgress.contains(action) && !done.contains(action)).limit(4).toList();
        return new FitActionBoardResponse(todo, inProgress, done);
    }

    private static List<String> adverseStrategies(List<com.careertuner.fitanalysis.ai.FitGapRecommendation> gaps) {
        return gaps.stream().limit(4).map(gap ->
                "%s 부족은 숨기지 말고, 현재 진행 중인 학습·실습 결과와 완료 시점을 함께 설명하세요."
                        .formatted(gap.skill())).toList();
    }

    private static List<String> next24HourActions(
            List<String> actions,
            List<com.careertuner.fitanalysis.ai.FitGapRecommendation> gaps) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        actions.stream().limit(2).forEach(result::add);
        gaps.stream().filter(gap -> "HIGH".equals(gap.priority())).limit(2)
                .map(gap -> "%s 보완을 위한 60분 실습을 시작하고 결과를 기록합니다.".formatted(gap.skill()))
                .forEach(result::add);
        result.add("지원 마감일과 제출에 필요한 자료를 확인합니다.");
        return result.stream().limit(3).toList();
    }

    private static List<FitToneStrategyResponse> toneStrategies(
            Integer score,
            List<com.careertuner.fitanalysis.ai.FitGapRecommendation> gaps) {
        int value = score == null ? 0 : score;
        long high = gaps.stream().filter(gap -> "HIGH".equals(gap.priority())).count();
        return List.of(
                new FitToneStrategyResponse("DIRECT", "냉정한 평가",
                        "현재 적합도는 %d점이며 우선 해결할 고위험 부족 역량은 %d개입니다. 근거 없는 낙관보다 보완 완료 여부로 지원 시점을 결정하세요."
                                .formatted(value, high)),
                new FitToneStrategyResponse("ENCOURAGING", "격려형 평가",
                        "현재 강점을 유지하면서 우선 부족 역량부터 하나씩 해결하면 충분히 경쟁력을 높일 수 있습니다."),
                new FitToneStrategyResponse("ACTION", "실행 중심 평가",
                        "오늘 24시간 액션을 완료하고, 학습 결과를 프로필에 반영한 뒤 재분석으로 변화를 확인하세요."));
    }

    private int estimateTokens(FitAnalysisAiCommand command) {
        int skills = command.requiredSkills().size() + command.preferredSkills().size() + command.profileSkills().size();
        int duties = command.duties() == null ? 0 : command.duties().length();
        return Math.max(800, Math.min(4000, 600 + skills * 40 + duties));
    }
}
