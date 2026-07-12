package com.careertuner.ai.intake;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.CaseSlotValidator;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 인테이크 에이전트가 호출하는 read-only 툴 3종. 전부 외부 부수효과(DB write·실행)가 없고,
 * 검증을 통과한 결과만 {@link IntakeSlotTrace}(요청 범위)에 기록한다 — "write/실행 액션 미노출" 경계 유지.
 * 실제 실행은 컨트롤러/클라이언트가 D 의 {@code /run/stream} 으로 한다.
 *
 * <p>userId 는 전역 정적이 아니라 요청 범위 {@link IntakeSlotTrace} 에서 읽는다(동시성 안전).</p>
 */
@Component
public class IntakeTools {

    private final ApplicationCaseService applicationCaseService;
    private final IntakeSlotTrace trace;

    public IntakeTools(ApplicationCaseService applicationCaseService, IntakeSlotTrace trace) {
        this.applicationCaseService = applicationCaseService;
        this.trace = trace;
    }

    @Tool("사용자의 지원 건(회사·직무) 목록을 가져온다. 어떤 지원 건을 준비할지 모호할 때 호출해 후보를 보여주고 고르게 한다.")
    public String listCases() {
        Long userId = trace.userId();
        if (userId == null) {
            return "로그인이 필요해요. 먼저 로그인해 주세요.";
        }
        List<ApplicationCaseResponse> cases = applicationCaseService.list(userId, null, false);
        if (cases != null) {
            // (C3) 회사·직무 둘 다 미확인 placeholder 건은 LLM 노출·화이트리스트에서 제외 —
            // 선택 불가 잔재를 목록에 보여주면 qwen3 혼란과 오선택(T005류)만 유발한다.
            cases = cases.stream()
                    .filter(c -> !(CaseSlotValidator.isUnresolved(c.companyName())
                            && CaseSlotValidator.isUnresolved(c.jobTitle())))
                    .toList();
        }
        trace.recordFetchedCases(cases);
        if (cases == null || cases.isEmpty()) {
            return "등록된 지원 건이 없습니다. 먼저 지원 건을 만들어 주세요.";
        }
        StringBuilder sb = new StringBuilder();
        for (ApplicationCaseResponse c : cases) {
            sb.append("id=").append(c.id())
              .append(" · ").append(nonBlank(c.companyName(), "(회사 미정)"))
              .append(" · ").append(nonBlank(c.jobTitle(), "(직무 미정)"))
              .append('\n');
        }
        return sb.toString().trim();
    }

    @Tool("사용자가 고른 지원 건을 확정한다. caseId 는 반드시 listCases 가 보여준 목록 안의 값이어야 한다.")
    public String chooseCase(@P("확정할 지원 건 id") Long caseId) {
        if (caseId == null) {
            return "지원 건 id가 필요해요. 먼저 목록을 확인해 주세요.";
        }
        List<ApplicationCaseResponse> known = trace.fetchedCases();
        if (known.isEmpty()) {
            // 이전 턴 후보 캐시가 비어 있으면(프로세스 재시작 등) 소유 목록을 다시 가져와 검증.
            Long userId = trace.userId();
            if (userId != null) {
                known = applicationCaseService.list(userId, null, false);
                trace.recordFetchedCases(known);
            }
        }
        ApplicationCaseResponse match = known.stream()
                .filter(c -> caseId.equals(c.id()))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return "그 지원 건을 찾을 수 없어요. 먼저 목록에서 골라 주세요.";
        }
        trace.confirmCase(caseId);
        return "지원 건을 \"" + nonBlank(match.companyName(), "(회사 미정)")
                + " · " + nonBlank(match.jobTitle(), "(직무 미정)") + "\" 로 정했어요.";
    }

    @Tool("면접 모드(BASIC/JOB/PERSONALITY/PRESSURE/RESUME/PORTFOLIO/REAL/COMPANY)는 사용자가 화면의 모드 칩에서 직접 고른다. "
            + "이 도구는 모드를 확정하지 않으며, 모드가 필요하면 호출해 사용자에게 칩으로 고르도록 안내만 한다.")
    public String chooseMode(@P("무시됨 — 모드는 칩(selectedModeCode)으로만 확정한다") String code) {
        // 발견①(mode 결정성): mode 는 6개 고정 enum 의 닫힌 선택지라 자유텍스트 이해가 불필요하다. 모델(qwen3)이
        // 텍스트로 mode 를 결정하는 경로를 원천 제거한다 — 이 도구는 더는 trace 에 mode 를 쓰지 않는다.
        // mode 확정은 오직 칩(selectedModeCode → applyExplicitSelections 의 trace.confirmMode)으로만 한다.
        // (case→mode 순서 가드는 칩 경로 쪽이 독립 보유하므로 여기선 불필요.)
        return "면접 유형은 제가 임의로 정하지 않아요. 화면의 면접 유형 칩에서 직접 골라 주세요.";
    }

    private static String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
