package com.careertuner.ai.autoprep;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse.ModeOption;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.correction.ai.CorrectionModelWarmupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인테이크 챗봇 백엔드(멀티턴). 한 줄 요청을 두뇌로 해석하고, 부족한 슬롯을 한 번에 하나씩 되묻는다.
 *
 * <p>되묻기 순서: ① 지원 건이 필요한 파트(JOB·FIT·INTERVIEW)가 있는데 caseId 없음 → CASE
 * ② 면접(INTERVIEW)이 있는데 모드 미지정 → MODE ③ 다 차면 ready. 상태는 클라이언트가 슬롯을
 * 누적해 매 턴 보내는 stateless 방식(applicationCaseId·mode 를 다음 요청에 실어 보냄).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepIntakeService {

    private static final Set<String> CASE_REQUIRED = Set.of("JOB", "FIT", "INTERVIEW");

    private static final List<ModeOption> MODE_OPTIONS = List.of(
            new ModeOption("BASIC", "기본 면접"),
            new ModeOption("JOB", "직무 면접"),
            new ModeOption("PERSONALITY", "인성 면접"),
            new ModeOption("PRESSURE", "압박 면접"),
            new ModeOption("RESUME", "자소서 기반"),
            new ModeOption("PORTFOLIO", "포트폴리오 기반"),
            new ModeOption("REAL", "실전 종합"),
            new ModeOption("COMPANY", "기업 맞춤"));

    private final AutoPrepPlanner planner;
    private final ApplicationCaseService applicationCaseService;
    private final CorrectionModelWarmupService correctionModelWarmupService;

    public AutoPrepIntakeResponse intake(Long userId, AutoPrepRequest request) {
        PrepPlan plan = planner.plan(userId, request);
        warmCorrectionModelWhenLikely(plan, request);

        // ① 지원 건 필요한데 없음
        boolean needsCase = plan.steps().stream().anyMatch(CASE_REQUIRED::contains);
        if (needsCase && plan.slots().applicationCaseId() == null) {
            List<ApplicationCaseResponse> candidates = safeList(userId);
            String parsedCompany = CaseSlotValidator.resolvedOrNull(plan.slots().company());
            String message = candidates.isEmpty()
                    ? "이 준비에는 지원 건이 필요해요. 채용공고로 지원 건을 먼저 만들어 주세요."
                    : (parsedCompany != null
                        ? "\"" + parsedCompany + "\" 지원 건을 못 찾았어요. 어느 지원 건으로 준비할까요?"
                        : "어느 지원 건으로 준비할까요?");
            return new AutoPrepIntakeResponse(plan, false, message, "CASE", candidates, List.of());
        }

        // ①′ 지원 건은 있는데 회사명/직무명이 미확인(placeholder·blank) — ④ 온보딩 게이트와 같은
        //    검증기(CaseSlotValidator)로 판정한다. ③에는 지원 건 필드를 고칠 경로가 없으므로 코드 고정
        //    문구로 차단·안내한다(LLM 미경유). 확정은 지원 건 상세(수정) 또는 ④ 보정 흐름에서.
        if (needsCase && plan.slots().applicationCaseId() != null) {
            boolean companyUnresolved = CaseSlotValidator.isUnresolved(plan.slots().company());
            boolean jobTitleUnresolved = CaseSlotValidator.isUnresolved(plan.slots().jobTitle());
            if (companyUnresolved || jobTitleUnresolved) {
                String missing = companyUnresolved && jobTitleUnresolved ? "회사명·직무명"
                        : companyUnresolved ? "회사명" : "직무명";
                return new AutoPrepIntakeResponse(plan, false,
                        "이 지원 건은 " + missing + "이 아직 확정되지 않았어요. 지원 건 상세에서 "
                                + missing + "을 확인·수정한 뒤 다시 요청해 주세요.",
                        null, List.of(), List.of());
            }
        }

        // ② 면접인데 모드 미지정
        boolean needsMode = plan.steps().contains("INTERVIEW");
        boolean hasMode = notBlank(request.mode());
        if (needsMode && !hasMode) {
            return new AutoPrepIntakeResponse(plan, false, "면접 모드는 어떤 걸로 할까요?", "MODE", List.of(), MODE_OPTIONS);
        }

        // ③ 준비 완료
        return new AutoPrepIntakeResponse(plan, true,
                "좋아요 — " + describe(plan) + " 기준으로 지금 바로 준비를 시작할게요.", null, List.of(), List.of());
    }

    private String describe(PrepPlan plan) {
        PrepSlots slots = plan.slots();
        StringBuilder sb = new StringBuilder();
        // 보간 방어: 미확인 슬롯 값(placeholder·blank)은 사용자 문장에 절대 삽입하지 않는다(버그1).
        String company = CaseSlotValidator.resolvedOrNull(slots.company());
        String jobTitle = CaseSlotValidator.resolvedOrNull(slots.jobTitle());
        if (company != null) {
            sb.append(company);
        }
        if (jobTitle != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(jobTitle);
        }
        if (sb.length() == 0) {
            sb.append("선택한 내용");
        }
        if (plan.steps().contains("INTERVIEW")) {
            sb.append(" / ").append(modeLabel(slots.mode()));
        } else {
            sb.append(" / ").append(plan.steps().size()).append("개 단계");
        }
        return sb.toString();
    }

    private String modeLabel(String mode) {
        if (mode == null) {
            return "기본 면접";
        }
        return switch (mode) {
            case "JOB" -> "직무 면접";
            case "PERSONALITY" -> "인성 면접";
            case "PRESSURE" -> "압박 면접";
            case "RESUME" -> "자소서 기반 면접";
            case "PORTFOLIO" -> "포트폴리오 기반 면접";
            case "REAL" -> "실전 종합 면접";
            case "COMPANY" -> "기업 맞춤 면접";
            default -> "기본 면접";
        };
    }

    private List<ApplicationCaseResponse> safeList(Long userId) {
        try {
            List<ApplicationCaseResponse> cases = applicationCaseService.list(userId, null, false);
            if (cases == null) {
                return List.of();
            }
            // (C3) 회사·직무 둘 다 미확인 placeholder 건은 후보에서 제외 — ③ 진입 후보 칩 노이즈 차단.
            return cases.stream()
                    .filter(c -> !(CaseSlotValidator.isUnresolved(c.companyName())
                            && CaseSlotValidator.isUnresolved(c.jobTitle())))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("AutoPrep 인테이크 지원 건 목록 조회 실패: {}", ex.getMessage());
            return List.of();
        }
    }

    private void warmCorrectionModelWhenLikely(PrepPlan plan, AutoPrepRequest request) {
        if (plan == null || !plan.steps().contains("WRITE")) {
            return;
        }
        boolean hasSource = request != null
                && (notBlank(request.coverLetterText())
                    || (request.attachmentFileIds() != null && !request.attachmentFileIds().isEmpty()));
        boolean explicitWriteIntent = "CUSTOM_PREP".equals(plan.intent());
        if (hasSource || explicitWriteIntent) {
            correctionModelWarmupService.warmAsync("AUTO_PREP_WRITE");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
