package com.careertuner.ai.intake;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

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

    /**
     * 면접 모드 6종. {@code AutoPrepIntakeService.MODE_OPTIONS} 와 동일 계약이지만 그 상수는 D 의 private 이라
     * 직접 참조 대신 코드만 미러한다(검증·라벨용). 코드 집합 변경은 D 와 합의 대상.
     */
    private static final Map<String, String> MODE_LABELS = Map.of(
            "BASIC", "기본 면접",
            "JOB", "직무 면접",
            "PERSONALITY", "인성 면접",
            "PRESSURE", "압박 면접",
            "RESUME", "자소서 기반",
            "COMPANY", "기업 맞춤");

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

    @Tool("면접 모드를 확정한다. code 는 BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY 중 하나여야 한다.")
    public String chooseMode(@P("면접 모드 코드(BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY)") String code) {
        if (code == null || code.isBlank()) {
            return "면접 모드 코드가 필요해요.";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        String label = MODE_LABELS.get(normalized);
        if (label == null) {
            return "지원하지 않는 모드예요. BASIC/JOB/PERSONALITY/PRESSURE/RESUME/COMPANY 중에서 골라 주세요.";
        }
        trace.confirmMode(normalized);
        return "면접 모드를 \"" + label + "\" 로 정했어요.";
    }

    private static String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
