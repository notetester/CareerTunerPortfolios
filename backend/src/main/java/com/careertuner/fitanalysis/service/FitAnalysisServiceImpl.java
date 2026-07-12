package com.careertuner.fitanalysis.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.fitanalysis.ai.FitAnalysisAiCommand;
import com.careertuner.fitanalysis.ai.FitAnalysisAiResult;
import com.careertuner.fitanalysis.ai.FitAnalysisAiService;
import com.careertuner.fitanalysis.ai.FitAnalysisConfidence;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;
import com.careertuner.fitanalysis.certificate.CertificateCareerCatalog;
import com.careertuner.fitanalysis.domain.CareerProfileSource;
import com.careertuner.fitanalysis.domain.FitAnalysisGateResult;
import com.careertuner.fitanalysis.domain.FitAnalysisGenerationSource;
import com.careertuner.fitanalysis.domain.FitAnalysisLearningTask;
import com.careertuner.fitanalysis.domain.FitAnalysisResult;
import com.careertuner.fitanalysis.certificate.CertificateEvidenceService;
import com.careertuner.fitanalysis.certificate.CertificateNeedGate;
import com.careertuner.fitanalysis.dto.CareerCertificateStrategyResponse;
import com.careertuner.fitanalysis.dto.CareerRoadmapResponse;
import com.careertuner.fitanalysis.dto.CertificateEvidenceResponse;
import com.careertuner.fitanalysis.dto.CertificateEvidenceSnapshot;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisHistoryEntryResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;
import com.careertuner.fitanalysis.dto.FitActionBoardResponse;
import com.careertuner.fitanalysis.dto.FitSafetyResponse;
import com.careertuner.fitanalysis.dto.FitScoreBreakdownResponse;
import com.careertuner.fitanalysis.dto.FitToneStrategyResponse;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FitAnalysisServiceImpl implements FitAnalysisService {

    private static final String FEATURE_TYPE = "FIT_ANALYSIS";
    private static final int MOCK_CREDIT = 2;

    private final FitAnalysisMapper fitAnalysisMapper;
    private final com.careertuner.profile.mapper.ProfileAiAnalysisMapper profileAiAnalysisMapper;
    private final FitAnalysisAiService fitAnalysisAiService;
    private final EvidenceGateService evidenceGateService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AiUsageLogService aiUsageLogService;
    private final CertificateEvidenceService certificateEvidenceService;
    private final TransactionTemplate transactionTemplate;

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
    public FitAnalysisDetailResponse generate(Long userId, Long applicationCaseId, boolean certificateStrategy,
                                              RequestedAiModel requestedModel) {
        // 외부 I/O(AI 호출·자격증 근거 조회)는 트랜잭션 밖에서 수행한다 — DB 커넥션을 물지 않아, Q-Net 등 외부 장애가
        // 커넥션 풀을 고갈시켜 무관한 조회까지 막는 일이 없다. DB 쓰기만 아래 transactionTemplate 로 짧게 감싼다.
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
                source.getDesiredJob(),
                composeCompanyContext(source),
                certificateStrategy,
                composeProfileInsight(userId, source.getProfileVersionId()));

        FitAnalysisResult previous = fitAnalysisMapper.findLatestByUserIdAndApplicationCaseId(userId, applicationCaseId);
        // 사용자가 고른 모델 tier 부터 시작하는 폴백(AUTO=현행). 판단값은 규칙엔진 소유라 모델 무관 불변, 설명만 가변.
        FitAnalysisAiResult ai = fitAnalysisAiService.generate(command, requestedModel);
        // 신뢰도는 AI 판단이 아니라 입력 상태 기반의 결정적 계산이라 mock/실 AI 모두 동일하게 산정된다.
        FitAnalysisConfidence confidence = FitAnalysisConfidence.evaluate(command);
        // review-first evidence gate: AI 호출 + 기존 E1 grounding guard '이후'의 결정론 후처리 안전층.
        // 점수/applyDecision/매칭/부족은 읽기만 하고 바꾸지 않는다(노출·검토 상태만 결정).
        EvidenceGateDecision gate = evidenceGateService.evaluate(command, ai);

        // 자격증 근거는 생성 시 1회만 수집·영속한다(읽기 경로에서는 provider 호출 금지 — Q-Net 장애가 조회 성능에 영향
        // 없게). best-effort: 수집이 실패해도 적합도 분석 생성을 중단하지 않고 판단값도 바꾸지 않는다.
        String certificateEvidence = collectCertificateEvidenceJson(command, ai);

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
                .certificateEvidence(certificateEvidence)
                .model(ai.usage().model())
                .promptVersion(FitAnalysisPromptCatalog.VERSION)
                .status(ai.status())
                .errorMessage(ai.errorMessage())
                .build();
        // DB 쓰기만 짧은 트랜잭션으로 원자 처리(외부 I/O 는 이미 위에서 끝났으므로 커넥션 점유 시간이 최소).
        transactionTemplate.executeWithoutResult(status -> {
            fitAnalysisMapper.insertFitAnalysis(row);
            fitAnalysisMapper.insertHistory(
                    row.getId(),
                    applicationCaseId,
                    previous == null ? null : previous.getFitScore(),
                    row.getFitScore(),
                    toJson(historyDiff(previous, row)));
            persistGate(row.getId(), gate);
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
            if ("SUCCESS".equals(ai.status())) {
                aiUsageLogService.recordSuccessValues(
                        userId,
                        applicationCaseId,
                        FEATURE_TYPE,
                        ai.usage().model(),
                        ai.usage().inputTokens(),
                        ai.usage().outputTokens(),
                        tokenUsage,
                        creditUsed);
            } else {
                fitAnalysisMapper.insertAiUsageLog(userId, applicationCaseId, FEATURE_TYPE, ai.status(),
                        ai.usage().model(), ai.usage().inputTokens(), ai.usage().outputTokens(),
                        tokenUsage, 0, ai.errorMessage());
            }

            // 적합도 분석이 성공하면 사용자에게 완료 알림을 남긴다.
            if ("SUCCESS".equals(ai.status())) {
                notificationService.notify(Notification.builder()
                        .userId(userId)
                        .type("FIT_ANALYSIS_COMPLETE")
                        .targetType("APPLICATION_CASE")
                        .targetId(applicationCaseId)
                        .title("적합도 분석이 완료되었습니다")
                        .message("%s · %s 적합도 %d점".formatted(
                                source.getCompanyName(), source.getJobTitle(), ai.fitScore()))
                        .link("/applications/" + applicationCaseId + "/fit")
                        .build());
            }
        });

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

    /**
     * B(company_analysis) 기업 맥락을 설명 생성 프롬프트용 텍스트로 조립한다. 세 항목이 모두 비면 {@code null}
     * (프롬프트에 기업 맥락 섹션이 붙지 않고 공고 기반으로 degrade). 판단값 계산엔 쓰이지 않는다(뉴로-심볼릭 불변식).
     */
    /**
     * A(profile_ai_analysis)의 프로필 요약 분석을 fit 설명용 참고 텍스트로 조립한다. 없으면 null(프로필 AI 분석
     * 미실행). 요약·강점·보완점만 담고, 판단값 계산엔 쓰지 않는다(companyContext 와 동일 격리). 실패는 null degrade.
     */
    private String composeProfileInsight(Long userId, Long profileVersionId) {
        try {
            com.careertuner.profile.domain.ProfileAiAnalysis row =
                    profileAiAnalysisMapper.findByUserIdAndFeature(userId, "PROFILE_SUMMARY");
            // 현재 비교 입력과 같은 불변 프로필 버전에서 생성한 요약만 사용한다.
            // 프로필 수정 뒤 재분석하지 않은 오래된 A 산출물이 최신 스펙 설명에 섞이는 것을 막는다.
            if (row == null || profileVersionId == null
                    || !profileVersionId.equals(row.getProfileVersionId())) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            if (row.getSummary() != null && !row.getSummary().isBlank()) {
                sb.append("- 요약: ").append(row.getSummary().trim()).append('\n');
            }
            appendJsonListLine(sb, "강점", row.getStrengths());
            appendJsonListLine(sb, "보완점", row.getGaps());
            return sb.isEmpty() ? null : sb.toString().trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void appendJsonListLine(StringBuilder sb, String label, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            List<String> items = objectMapper.readValue(json,
                    new tools.jackson.core.type.TypeReference<List<String>>() { });
            if (items != null && !items.isEmpty()) {
                sb.append("- ").append(label).append(": ").append(String.join(", ", items)).append('\n');
            }
        } catch (RuntimeException ignored) {
            // JSON 파싱 실패 시 해당 줄만 생략(전체 insight 는 계속).
        }
    }

    private static String composeCompanyContext(FitAnalysisGenerationSource source) {
        StringBuilder sb = new StringBuilder();
        appendContextLine(sb, "회사 요약", source.getCompanySummary());
        appendContextLine(sb, "최근 이슈", source.getRecentIssues());
        appendContextLine(sb, "면접 포인트", source.getInterviewPoints());
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    private static void appendContextLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CareerCertificateStrategyResponse careerCertificateStrategy(Long userId) {
        // 사용자 단위(desiredJob) 장기 전략 — 현재 지원 건 전략과 분리. 결정론 카탈로그만 사용(외부 API 미호출).
        CareerProfileSource profile = fitAnalysisMapper.findCareerProfile(userId);
        String desiredJob = profile == null ? null : profile.getDesiredJob();
        List<String> held = profile == null ? List.of() : parseList(profile.getProfileCertificates());
        if (desiredJob == null || desiredJob.isBlank()) {
            return new CareerCertificateStrategyResponse(null, List.of(), List.of(),
                    "프로필에 희망 직무를 등록하면 직군 기준 장기 자격증 전략을 제안합니다.");
        }

        Set<String> heldLower = new HashSet<>();
        for (String cert : held) {
            if (cert != null && !cert.isBlank()) {
                heldLower.add(cert.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> catalog = CertificateCareerCatalog.candidatesFor(desiredJob);
        // 보유분 중 직군 카탈로그와 겹치는 것만 '강점'으로(무관 자격증을 직군 강점으로 조작하지 않음).
        List<String> strengths = catalog.stream()
                .filter(name -> heldLower.contains(name.toLowerCase(Locale.ROOT)))
                .toList();
        List<CareerCertificateStrategyResponse.CareerCertificateCandidate> candidates = catalog.stream()
                .filter(name -> !heldLower.contains(name.toLowerCase(Locale.ROOT)))
                .map(name -> new CareerCertificateStrategyResponse.CareerCertificateCandidate(
                        name,
                        "%s 직군에서 장기적으로 취득 가치가 있는 후보입니다. 이번 지원 건과는 별개로, 학습 여유가 있을 때 준비하세요."
                                .formatted(desiredJob)))
                .toList();
        return new CareerCertificateStrategyResponse(desiredJob, strengths, candidates,
                "자격증은 보조 전략입니다. 실무 프로젝트·배포 경험 보완이 우선이며, 시험 일정은 공식 출처(Q-Net 등) 확인 후 계획하세요.");
    }


    /**
     * 장기 커리어 로드맵 — 결정론 생성(LLM 미관여). 트랜잭션 없음: 자격증 근거 수집이 외부 I/O 라
     * 커넥션 점유 금지 원칙(외부 I/O 는 트랜잭션 밖)을 따른다(DB 는 읽기 2회뿐).
     *
     * <p>날짜 소스는 둘뿐: ①확인된 실일정(자격증 회차 = 공식/사전공고, 지원건 마감 = 사용자 입력)
     * ②월 단위 학습 계획 블록(planningBlock=true 로 구분 — 확정 일정처럼 표시 금지). 반복 부족역량은
     * 사용자의 지원건별 최신 적합도 분석(missing_skills)을 빈도 집계한 것이다.
     */
    @Override
    public CareerRoadmapResponse careerRoadmap(Long userId, int months) {
        int horizon = Math.max(3, Math.min(24, months));
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        java.time.LocalDate horizonEnd = today.plusMonths(horizon);
        CareerProfileSource profile = fitAnalysisMapper.findCareerProfile(userId);
        String desiredJob = profile == null ? null : profile.getDesiredJob();
        if (desiredJob == null || desiredJob.isBlank()) {
            return new CareerRoadmapResponse(null, horizon, nowKstString(), List.of(),
                    List.of("프로필에 희망 직무를 등록하면 직군 기준 장기 로드맵을 생성합니다."));
        }

        List<CareerRoadmapResponse.RoadmapItem> items = new java.util.ArrayList<>();
        List<String> notes = new java.util.ArrayList<>();

        // ① 지원건 마감(사용자 실데이터) — 기간 내 미래 마감만.
        List<FitAnalysisResult> latest = fitAnalysisMapper.findLatestByUserId(userId);
        for (FitAnalysisResult fit : latest) {
            java.time.LocalDate deadline = fit.getDeadlineDate();
            if (deadline != null && !deadline.isBefore(today) && !deadline.isAfter(horizonEnd)) {
                items.add(new CareerRoadmapResponse.RoadmapItem("APPLICATION_DEADLINE",
                        (fit.getCompanyName() == null ? "지원 건" : fit.getCompanyName()) + " 서류 마감",
                        deadline.toString(), null, null,
                        fit.getJobTitle() == null ? null : fit.getJobTitle() + " · 적합도 "
                                + (fit.getFitScore() == null ? "-" : fit.getFitScore()) + "점",
                        "내 지원 건", false));
            }
        }

        // ② 자격증 후보(카탈로그 − 보유)의 확인된 회차 — 근거 조회는 CertificateEvidenceService 재사용.
        Set<String> heldLower = new HashSet<>();
        for (String cert : parseList(profile.getProfileCertificates())) {
            if (cert != null && !cert.isBlank()) {
                heldLower.add(cert.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> candidates = CertificateCareerCatalog.candidatesFor(desiredJob).stream()
                .filter(name -> !heldLower.contains(name.toLowerCase(Locale.ROOT)))
                .limit(3)
                .toList();
        List<CertificateEvidenceResponse> evidences = certificateEvidenceService.collect(candidates);
        boolean anyUnverified = false;
        boolean anyOfficialNoSchedule = false;
        for (CertificateEvidenceResponse evidence : evidences == null ? List.<CertificateEvidenceResponse>of() : evidences) {
            boolean preannounced = "PREANNOUNCED".equals(evidence.scheduleStatus());
            boolean verified = "VERIFIED_CURRENT".equals(evidence.scheduleStatus());
            if (!verified && !preannounced) {
                // 공식 확인된 미편성(OFFICIAL_NO_SCHEDULE)과 '확인 못함'을 구분해 노트를 정확히 낸다(확인된 사실 격하 금지).
                if ("OFFICIAL_NO_SCHEDULE".equals(evidence.scheduleStatus())) {
                    anyOfficialNoSchedule = true;
                } else {
                    anyUnverified = true; // 확인 안 된 자격증은 날짜 없이 노트로만(임의 일정 금지).
                }
                continue;
            }
            String caveat = preannounced ? " (사전공고 기준 - 최종 공고 확인 필요)" : "";
            int picked = 0;
            Set<java.time.LocalDate> seenExamDates = new HashSet<>(); // 같은 회차의 접수 변형(정기/빈자리) 행 중복 제거
            for (var round : evidence.scheduleRounds()) {
                java.time.LocalDate exam = parseYmd(round.docExam());
                if (exam == null || exam.isBefore(today) || exam.isAfter(horizonEnd)) {
                    continue;
                }
                if (!seenExamDates.add(exam)) {
                    continue; // 동일 시험일 회차는 이미 배치됨(접수 기간 변형만 다른 행).
                }
                if (picked >= 2) {
                    break; // 자격증당 다가오는 회차 최대 2개(로드맵 과밀 방지).
                }
                picked++;
                java.time.LocalDate regStart = parseYmd(round.docRegStart());
                java.time.LocalDate regEnd = parseYmd(round.docRegEnd());
                // 접수 시작이 지났어도 마감이 남았으면 '접수 진행 중'으로 표시(오늘 시작) — 진행 중인 접수를 통째로 누락하지 않는다.
                boolean regUpcoming = regStart != null && !regStart.isBefore(today);
                boolean regOngoing = regStart != null && regStart.isBefore(today)
                        && regEnd != null && !regEnd.isBefore(today);
                if (regUpcoming || regOngoing) {
                    java.time.LocalDate shownStart = regOngoing ? today : regStart;
                    items.add(new CareerRoadmapResponse.RoadmapItem("CERT_REGISTRATION",
                            evidence.certName() + " 원서접수" + (regOngoing ? "(진행 중)" : ""), shownStart.toString(),
                            regEnd == null ? null : regEnd.toString(), evidence.certName(),
                            (round.round() == null ? "" : round.round()) + caveat, evidence.sourceName(), false));
                }
                items.add(new CareerRoadmapResponse.RoadmapItem("CERT_EXAM",
                        evidence.certName() + " 필기시험", exam.toString(), null, evidence.certName(),
                        (round.round() == null ? "" : round.round()) + caveat, evidence.sourceName(), false));
                java.time.LocalDate prac = parseYmd(round.pracExamStart());
                if (prac != null && !prac.isBefore(today) && !prac.isAfter(horizonEnd)) {
                    items.add(new CareerRoadmapResponse.RoadmapItem("CERT_PRACTICAL",
                            evidence.certName() + " 실기시험", prac.toString(), null, evidence.certName(),
                            (round.round() == null ? "" : round.round()) + caveat, evidence.sourceName(), false));
                }
            }
        }
        if (anyUnverified) {
            notes.add("일부 후보 자격증은 공식 일정을 확인하지 못해 날짜 없이 제외했습니다 - 주관기관 공식 페이지를 확인하세요.");
        }
        if (anyOfficialNoSchedule) {
            notes.add("일부 후보 자격증은 공식 확인 결과 올해 시행일정이 편성되지 않았습니다(미편성 확인).");
        }

        // ③ 반복 부족역량 상위 3 → 월 단위 학습 블록(계획 제안). 다음 달부터 순차 2개월씩.
        Map<String, Integer> gapCount = new LinkedHashMap<>();
        for (FitAnalysisResult fit : latest) {
            for (String skill : parseList(fit.getMissingSkills())) {
                if (skill != null && !skill.isBlank()) {
                    gapCount.merge(skill.trim(), 1, Integer::sum);
                }
            }
        }
        List<String> topGaps = gapCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        java.time.LocalDate blockStart = today.plusMonths(1).withDayOfMonth(1);
        for (String skill : topGaps) {
            if (blockStart.isAfter(horizonEnd)) {
                break;
            }
            // 로드맵 기간을 넘지 않게 종료일 clamp(기간 밖으로 삐져나가는 계획 블록 방지).
            java.time.LocalDate blockEnd = blockStart.plusMonths(2).minusDays(1);
            if (blockEnd.isAfter(horizonEnd)) {
                blockEnd = horizonEnd;
            }
            items.add(new CareerRoadmapResponse.RoadmapItem("SKILL_LEARNING",
                    skill + " 집중 학습", blockStart.toString(), blockEnd.toString(), null,
                    "최근 적합도 분석에서 " + gapCount.get(skill) + "회 부족 역량으로 나온 스킬 - 월 단위 학습 계획 제안",
                    null, true));
            blockStart = blockStart.plusMonths(2);
        }
        if (!topGaps.isEmpty()) {
            notes.add("학습 블록은 월 단위 '계획 제안'입니다(확정 일정 아님) - 플래너에 반영 후 자유롭게 조정하세요.");
        }
        if (latest.isEmpty()) {
            notes.add("적합도 분석 이력이 없어 반복 부족역량 기반 학습 블록은 생성하지 못했습니다 - 지원 건을 분석하면 로드맵이 풍부해집니다.");
        }
        notes.add("자격증 일정은 공식 확인(공공데이터 통합 조회)·공단 사전공고에 근거한 실일정만 포함합니다.");

        items.sort(java.util.Comparator.comparing(CareerRoadmapResponse.RoadmapItem::startDate));
        return new CareerRoadmapResponse(desiredJob, horizon, nowKstString(),
                List.copyOf(items), List.copyOf(notes));
    }

    private static String nowKstString() {
        return java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).toString();
    }

    private static java.time.LocalDate parseYmd(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) {
            return null;
        }
        try {
            return java.time.LocalDate.of(Integer.parseInt(yyyymmdd.substring(0, 4)),
                    Integer.parseInt(yyyymmdd.substring(4, 6)), Integer.parseInt(yyyymmdd.substring(6, 8)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 추천 자격증 근거를 생성 시 1회 수집해 snapshot JSON 으로 만든다. 근거가 없으면(게이트 OFF/provider 미활성)
     * null 을 반환해 컬럼을 비운다(기존 응답과 동일 degrade). 수집·직렬화 실패는 분석 전체를 막지 않는다(null 반환).
     */
    private String collectCertificateEvidenceJson(FitAnalysisAiCommand command, FitAnalysisAiResult ai) {
        try {
            // 게이트 판정(상태·신호)을 함께 저장 — 탭 요청이어도 무조건 추천이 아니라 평가이므로 NOT_NEEDED/OPTIONAL 도
            // 정상. 규칙엔진(mock)과 동일 입력·동일 순수함수라 판정이 일치한다(판단값은 바꾸지 않음).
            CertificateNeedGate.Decision decision = CertificateNeedGate.evaluate(
                    command.requiredSkills(), command.preferredSkills(), command.duties(), command.jobTitle(),
                    command.profileCertificates(), ai.missingSkills(), command.userRequested());
            List<CertificateEvidenceResponse> items = certificateEvidenceService.collect(ai.recommendedCertificates());
            if (!decision.active() && (items == null || items.isEmpty())) {
                return null; // 게이트 OFF + 근거 없음 → 자격증 섹션 숨김(기존 동작).
            }
            return objectMapper.writeValueAsString(new CertificateEvidenceSnapshot(
                    java.time.LocalDateTime.now().toString(),
                    decision.status().name(),
                    decision.triggeredSignals(),
                    items == null ? List.of() : items));
        } catch (RuntimeException e) {
            return null;
        }
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
        snapshot.put("profileVersionId", source.getProfileVersionId());
        snapshot.put("profileVersionNo", source.getProfileVersionNo());
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
        // 읽기 경로: 저장된 snapshot 만 역직렬화(외부 API 호출 없음). null 이면 기존과 동일하게 근거 섹션 없음.
        CertificateEvidenceSnapshot certSnapshot = parseValue(result.getCertificateEvidence(),
                new TypeReference<CertificateEvidenceSnapshot>() {}, null);
        return FitAnalysisDetailResponse.of(
                result,
                tasks,
                scoreBreakdown(result.getFitScore(), conditions),
                actionBoard(actions, tasks),
                adverseStrategies(gaps),
                next24HourActions(actions, gaps),
                toneStrategies(result.getFitScore(), gaps),
                safety(result.getId()),
                certSnapshot);
    }

    /** evidence gate 결정과 evidence 버킷 스냅샷을 C-only 테이블에 저장한다(원본·점수 미변경). */
    private void persistGate(Long fitAnalysisId, EvidenceGateDecision gate) {
        fitAnalysisMapper.insertGateResult(FitAnalysisGateResult.builder()
                .fitAnalysisId(fitAnalysisId)
                .gateStatus(gate.gateStatus())
                .needsHumanReview(gate.needsHumanReview())
                .reasonCount(gate.reasons().size())
                .maxSeverity(gate.maxSeverity())
                .gateReasonsJson(toJson(gate.reasons()))
                .evidenceGateVersion(EvidenceGateDecision.VERSION)
                .ragRuntimeEnabled(EvidenceGateDecision.RAG_RUNTIME_ENABLED)
                .rewriteApplied(EvidenceGateDecision.REWRITE_APPLIED)
                .build());
        for (EvidenceGateDecision.EvidenceSource src : gate.evidenceSources()) {
            fitAnalysisMapper.insertEvidenceSource(
                    fitAnalysisId, src.sourceType(), src.userOwned(), src.items().size(), toJson(src.items()));
        }
    }

    /** 저장된 gate 결정을 응답 safety 블록으로 변환한다. R3 이전 분석은 gate 가 없어 null(하위호환). */
    private FitSafetyResponse safety(Long fitAnalysisId) {
        FitAnalysisGateResult gate = fitAnalysisMapper.findGateResultByFitAnalysisId(fitAnalysisId);
        if (gate == null) {
            return null;
        }
        List<FitSafetyResponse.Reason> reasons = parseValue(
                gate.getGateReasonsJson(),
                new TypeReference<List<FitSafetyResponse.Reason>>() {},
                List.of());
        return new FitSafetyResponse(
                gate.getGateStatus(),
                gate.isNeedsHumanReview(),
                gate.getMaxSeverity(),
                reasons,
                gate.getEvidenceGateVersion(),
                gate.isRagRuntimeEnabled(),
                gate.isRewriteApplied());
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
