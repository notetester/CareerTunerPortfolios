package com.careertuner.ai.autoprep;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인테이크 챗봇 백엔드. 한 줄 요청을 두뇌로 해석해 슬롯을 채우고, 부족하면 무엇을 더 받아야 하는지 안내한다.
 * 실제 6파트 실행은 하지 않는 미리보기다 — ready=true 면 같은 요청으로 /run 을 호출하면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepIntakeService {

    private final AutoPrepPlanner planner;
    private final ApplicationCaseService applicationCaseService;

    public AutoPrepIntakeResponse intake(Long userId, AutoPrepRequest request) {
        PrepPlan plan = planner.plan(userId, request);
        PrepSlots slots = plan.slots();
        boolean ready = slots.applicationCaseId() != null;

        if (ready) {
            return new AutoPrepIntakeResponse(plan, true,
                    "준비 완료 — " + describe(slots) + " 기준으로 전체 준비를 시작할 수 있어요.",
                    List.of());
        }

        List<ApplicationCaseResponse> candidates = safeList(userId);
        String message = candidates.isEmpty()
                ? "아직 등록된 지원 건이 없어요. 채용공고로 지원 건을 먼저 만들어 주세요. (프로필·커뮤니티는 지원 건 없이도 진행됩니다.)"
                : "어느 지원 건으로 준비할까요? 아래에서 골라 주세요.";
        return new AutoPrepIntakeResponse(plan, false, message, candidates);
    }

    private String describe(PrepSlots slots) {
        StringBuilder sb = new StringBuilder();
        if (slots.company() != null && !slots.company().isBlank()) {
            sb.append(slots.company().trim());
        }
        if (slots.jobTitle() != null && !slots.jobTitle().isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(slots.jobTitle().trim());
        }
        if (sb.length() == 0) {
            sb.append("선택한 지원 건");
        }
        sb.append(" / ").append(modeLabel(slots.mode()));
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
}
