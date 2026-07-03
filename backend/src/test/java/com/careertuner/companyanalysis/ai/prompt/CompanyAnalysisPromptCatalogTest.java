package com.careertuner.companyanalysis.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.prompt.dto.AdminPromptView;

/**
 * b-v4 프롬프트 계약 구조 테스트(235 §10 D-3 · B-1/B-2 흡수) — 비R1.
 * WEB 근거 입력·sourceKind=WEB/URL 지시·WEB 미입력 가드·unknowns 유도 few-shot·
 * evidence 원문 인용 GOOD/BAD few-shot·버전 bump·레거시 보존을 고정한다.
 * 실효(unknowns 실생성·SUPPORTED 강등 감소)는 D-6 실R1 재검증에서 확인한다.
 */
class CompanyAnalysisPromptCatalogTest {

    // ── D-3 코어: 공고+웹 근거 동시 입력 ──

    @Test
    void systemPromptDeclaresWebEvidenceInputBlock() {
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("[웹 검색 근거]")
                .contains("스니펫+URL");
    }

    @Test
    void systemPromptInstructsWebSourceKindWithUrlSourceRef() {
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("sourceKind=\"WEB\"")
                .contains("sourceRef=그 스니펫의 URL")
                .contains("URL이 없는 웹 근거는 verifiedFacts에 쓰지 않는다");
        // D-2 canonicalizer 계약과 일치하는 완성형 예시(WEB fact 형태)가 존재한다.
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("\"sourceKind\": \"WEB\", \"sourceRef\": \"https://news.example.com/1\"");
    }

    /** D-4 배선 전 WEB 허위 생성 방지 — 근거 블록이 없으면 WEB 출처 금지. */
    @Test
    void systemPromptForbidsWebSourceWithoutWebEvidenceBlock() {
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("[웹 검색 근거] 블록이 입력에 없으면")
                .contains("절대 만들지 않는다")
                .contains("제공된 URL이 있을 때만");
    }

    /** D-2 gate 가 자유서술을 공고 corpus 기준으로만 대조하므로 WEB-only 단정을 막는다. */
    @Test
    void systemPromptRestrictsWebEvidenceToVerifiedFacts() {
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("자유서술에는 웹 근거만으로 확인한 내용을 단정하지 않는다");
    }

    // ── B-1 흡수: unknowns 유도 + few-shot ──

    @Test
    void systemPromptStrengthensUnknownsGuidanceWithFewShot() {
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("반드시 unknowns에 topic/reason/neededSource로 남긴다")
                .contains("unknowns 예시");
        // few-shot 이 실제로 채워진 unknowns 항목을 보여준다.
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("{\"topic\": \"사원수와 조직 규모\"")
                .contains("\"neededSource\": \"공시자료, 뉴스 기사\"");
    }

    // ── B-2 흡수: evidence 원문 인용 GOOD/BAD few-shot ──

    @Test
    void systemPromptFixesEvidenceQuotingWithGoodBadFewShot() {
        assertThat(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT)
                .contains("자기 말로 바꿔 쓰지 않는다")
                .contains("GOOD:")
                .contains("BAD:")
                .contains("evidence 재서술 — 금지");
    }

    // ── 버전·뷰·레거시 ──

    @Test
    void versionBumpedForContractChange() {
        assertThat(CompanyAnalysisPromptCatalog.VERSION).isEqualTo("b-v4");
    }

    @Test
    void adminPromptViewRendersActivePrompt() {
        AdminPromptView view = CompanyAnalysisPromptCatalog.view();

        assertThat(view.feature()).isEqualTo("company-analysis");
        assertThat(view.version()).isEqualTo("b-v4");
        assertThat(view.systemPrompt()).isEqualTo(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT);
        assertThat(view.schemaSummary()).isEqualTo(CompanyAnalysisPromptCatalog.SCHEMA_SUMMARY);
    }

    /** 회귀 비교 기준인 b-v2 레거시 프롬프트는 그대로다(웹 근거 문구 미포함 + 핵심 문장 보존). */
    @Test
    void legacyBv2PromptIsPreserved() {
        assertThat(CompanyAnalysisPromptCatalog.LEGACY_B_V2_SYSTEM_PROMPT)
                .contains("외부 웹 검색을 하지 않는다.")
                .contains("sourceKind에는 JOB_POSTING만 사용한다.")
                .doesNotContain("[웹 검색 근거]");
    }

    /** SCHEMA_SUMMARY 는 실제 schema/parser 필드만 나열한다(보류된 unknowns 전용 컬럼 등 암시 금지). */
    @Test
    void schemaSummaryStaysAlignedWithActualContract() {
        assertThat(CompanyAnalysisPromptCatalog.SCHEMA_SUMMARY)
                .contains("verifiedFacts<=8[]{fact,source,evidence,factId,sourceKind,sourceRef}")
                .contains("unknowns<=5[]{topic,reason,neededSource}");
    }
}
