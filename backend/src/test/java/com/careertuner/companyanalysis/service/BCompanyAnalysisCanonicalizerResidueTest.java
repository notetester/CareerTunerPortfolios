package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateOutcome;

import tools.jackson.databind.ObjectMapper;

/**
 * D-6 이슈B/A 후속 nit(폴리시·품질): 라벨 제거·문장 제거가 남기는 잔재 정리 검증.
 *
 * <p>두 잔재 클래스(실측 D-6 10건에서 확인)를 대상으로 한다.
 * <ul>
 *   <li><b>label-strip orphan 조사(case09)</b>: {@code [웹 검색 근거]의 스니펫} 처럼 라벨에 붙어 있던
 *       조사({@code 의})가 라벨 제거 후 홀로 남는 것을 정리한다.</li>
 *   <li><b>gate-remove dangling 접속부사(case08)</b>: 문장이 {@code guardFreeText} 로 제거되면서
 *       뒤 문장의 접속부사({@code 그러나})가 선행 문장을 잃고 매달리는 것을 정리한다.</li>
 * </ul>
 *
 * <p>안전 계약: 내용어(예: {@code 의무})와 공백으로 분리된 지시어({@code 이 회사}), 그리고
 * 제거가 일어나지 않은 정상 텍스트의 접속부사는 절대 건드리지 않는다.
 */
class BCompanyAnalysisCanonicalizerResidueTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BCompanyAnalysisCanonicalizer canonicalizer = new BCompanyAnalysisCanonicalizer(mapper);

    private static final String LABEL = "[웹 검색 근거]";
    // 테스트 수치(15억)를 포함하지 않는 중립 공고 — gate-remove 재현이 수치 미접지로 발동하도록.
    private static final String POSTING = """
            딥그로브 AI 리서치 인턴 채용
            담당업무: AI 엔터테인먼트 제품 리서치 및 데이터 구조화 지원
            자격요건: 자기 주도적 리서치 경험, 결론 도출 경험
            """;

    // ── label-strip orphan 조사(case09) ──

    @Test
    void orphanParticleGluedToStrippedLabelIsRemoved() {
        CompanyAnalysisPayload payload = payload(recentIssues(
                "공고문에서 확인할 수 있는 최근 이슈는 없으며, " + LABEL
                        + "의 스니펫으로 확인한 텀블벅 굿즈 펀딩 성과를 기록한다."));

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().recentIssues())
                .doesNotContain("근거]")
                .doesNotContain(", 의 ")
                .doesNotContain(" 의 스니펫")
                .contains("없으며, 스니펫으로 확인한");
    }

    @Test
    void multiCharOrphanParticleGluedToLabelIsRemoved() {
        CompanyAnalysisPayload payload = payload(companySummary(
                "딥그로브는 " + LABEL + "에서는 시드 투자를 유치한 스타트업이다."));

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .doesNotContain("근거]")
                .isEqualTo("딥그로브는 시드 투자를 유치한 스타트업이다.");
    }

    // ── ★ 네거티브: 내용어·공백 분리 지시어 보존 ──

    @Test
    void contentWordGluedToLabelIsPreserved() {
        // "]의무를" — '의'는 조사가 아니라 '의무'라는 내용어의 첫 글자다. 경계 lookahead 로 보존.
        CompanyAnalysisPayload payload = payload(companySummary(
                "회사는 " + LABEL + "의무를 성실히 이행한다."));

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .doesNotContain("근거]")
                .contains("의무를 성실히 이행한다");
    }

    @Test
    void spaceSeparatedDemonstrativeAfterLabelIsPreserved() {
        // "] 이 회사" — 공백으로 분리된 '이'는 지시어이므로 절대 제거하지 않는다(글루 아님).
        CompanyAnalysisPayload payload = payload(companySummary(
                "요약: " + LABEL + " 이 회사는 빠르게 성장한다."));

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .doesNotContain("근거]")
                .contains("이 회사는 빠르게 성장한다");
    }

    // ── gate-remove dangling 접속부사(case08) ──

    @Test
    void danglingConjunctionAfterSentenceRemovalIsStripped() {
        CompanyAnalysisPayload payload = payload(recentIssues(
                "공고문에서 확인할 수 있는 최근 이슈는 없으며, " + LABEL
                        + "에서는 딥그로브가 15억원 규모의 시드 투자를 유치했다는 내용이 포함되어 있다."
                        + " 그러나 공고문 자체에는 이러한 정보가 명시되지 않았다."));

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().recentIssues())
                .doesNotContain("15억")
                .doesNotStartWith("그러나")
                .doesNotContain("그러나 공고문")
                .isEqualTo("공고문 자체에는 이러한 정보가 명시되지 않았다.");
        // 앞 문장은 수치 미접지로 제거됐어야 한다.
        assertThat(result.gateActions())
                .anyMatch(action -> "recentIssues".equals(action.field())
                        && action.action() == GateOutcome.REMOVED
                        && action.detail().contains("15억"));
    }

    // ── ★ 네거티브: 제거가 없으면 접속부사 보존 ──

    @Test
    void conjunctionInIntactTextIsPreserved() {
        CompanyAnalysisPayload payload = payload(recentIssues(
                "회사는 꾸준히 성장한다. 그러나 경쟁이 치열하다."));

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().recentIssues())
                .contains("그러나 경쟁이 치열하다");
    }

    // ── 헬퍼 ──

    private CanonicalCompanyAnalysis canonicalize(CompanyAnalysisPayload payload) {
        return canonicalizer.canonicalizeForStorage(payload, 123L, 2, POSTING, "딥그로브", "AI 리서치 인턴");
    }

    private static CompanyAnalysisPayload payload(CompanyAnalysisPayload seed) {
        return seed;
    }

    private static CompanyAnalysisPayload companySummary(String value) {
        return new CompanyAnalysisPayload(value, "확인된 최근 이슈입니다.", "IT",
                "[]", "면접 포인트입니다.", "[]", "[]", "[]", "[]",
                new Usage("test-model", 10, 10, 20));
    }

    private static CompanyAnalysisPayload recentIssues(String value) {
        return new CompanyAnalysisPayload("딥그로브 기업 요약입니다.", value, "IT",
                "[]", "면접 포인트입니다.", "[]", "[]", "[]", "[]",
                new Usage("test-model", 10, 10, 20));
    }
}
