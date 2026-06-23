package com.careertuner.ai.autoprep;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인테이크 챗봇 백엔드. 한 줄 요청을 두뇌로 해석해 슬롯을 채우고, 부족하면 무엇을 더 받아야 하는지 안내한다.
 *
 * <p>동적 plan 과 정합: 지원 건이 필요한 파트(JOB·FIT·INTERVIEW)가 plan 에 있을 때만 caseId 를 요구한다.
 * "자소서만/프로필만/커뮤니티만" 같은 요청은 지원 건 없이도 바로 ready 다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepIntakeService {

    // 지원 건(공고)이 있어야 의미 있는 파트. 이게 plan 에 없으면 caseId 없이도 바로 시작 가능.
    private static final Set<String> CASE_REQUIRED = Set.of("JOB", "FIT", "INTERVIEW");

    private final AutoPrepPlanner planner;
    private final ApplicationCaseService applicationCaseService;

    public AutoPrepIntakeResponse intake(Long userId, AutoPrepRequest request) {
        PrepPlan plan = planner.plan(userId, request);
        boolean needsCase = plan.steps().stream().anyMatch(CASE_REQUIRED::contains);
        boolean hasCase = plan.slots().applicationCaseId() != null;
        boolean ready = !needsCase || hasCase;

        if (ready) {
            return new AutoPrepIntakeResponse(plan, true,
                    "준비 완료 — " + describe(plan) + " 기준으로 시작할 수 있어요.", List.of());
        }

        // 지원 건이 필요한데 못 찾은 경우만 되묻는다.
        List<ApplicationCaseResponse> candidates = safeList(userId);
        String message = candidates.isEmpty()
                ? "이 준비에는 지원 건이 필요해요. 채용공고로 지원 건을 먼저 만들어 주세요."
                : "어느 지원 건으로 준비할까요? 아래에서 골라 주세요.";
        return new AutoPrepIntakeResponse(plan, false, message, candidates);
    }

    private String describe(PrepPlan plan) {
        PrepSlots slots = plan.slots();
        StringBuilder sb = new StringBuilder();
        if (notBlank(slots.company())) {
            sb.append(slots.company().trim());
        }
        if (notBlank(slots.jobTitle())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(slots.jobTitle().trim());
        }
        if (sb.length() == 0) {
            sb.append("선택한 내용");
        }
        // 면접 파트가 있으면 모드를, 없으면 단계 수를 덧붙인다.
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
            case "COMPANY" -> "기업 맞춤 면접";
            default -> "기본 면접";
        };
    }

    private List<ApplicationCaseResponse> safeList(Long userId) {
        try {
            List<ApplicationCaseResponse> cases = applicationCaseService.list(userId, null, false);
            return cases == null ? List.of() : cases;
        } catch (RuntimeException ex) {
            log.warn("AutoPrep 인테이크 지원 건 목록 조회 실패: {}", ex.getMessage());
            return List.of();
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
