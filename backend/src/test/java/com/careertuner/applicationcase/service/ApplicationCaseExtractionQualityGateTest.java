package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.careertuner.jobposting.service.JobPostingTextExtractor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ApplicationCaseExtractionQualityGateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationCaseExtractionQualityGate qualityGate =
            new ApplicationCaseExtractionQualityGate(objectMapper);

    @Test
    void sharedFixtureStatusesMatchPythonWorkerContract() throws Exception {
        JsonNode cases = objectMapper.readTree(Files.readString(Path.of(
                "../ml/job-posting-worker/tests/fixtures/quality_gate_cases.json")));

        for (JsonNode testCase : cases) {
            String text = testCase.path("text").asText();
            String expectedStatus = testCase.path("expectedStatus").asText();

            ApplicationCaseExtractionQualityGate.QualityGateResult result =
                    qualityGate.evaluate("TEXT", null, text);

            assertThat(result.qualityStatus())
                    .as(testCase.path("name").asText())
                    .isEqualTo(expectedStatus);
        }
    }

    @Test
    void garbledCriticalSectionDemotesPassToReview() throws Exception {
        String text = String.join("\n",
                "회사소개",
                "넥스트클라우드는 핀테크 결제 플랫폼을 운영하는 기업입니다. 대용량 트래픽을 안정적으로 처리합니다.",
                "주요업무",
                "티",
                "공 Y (을 ) AY",
                "우표눔를용라어를ㅋ크이극아",
                "자격요건",
                "Java와 Spring Boot 개발 경력 5년 이상. SQL 활용 능력과 Git 협업 경험이 있으신 분.",
                "우대사항",
                "Kubernetes, Docker 운영 경험과 AWS 클라우드 인프라 경험을 우대합니다.",
                "근무조건",
                "정규직이며 서울 강남구에서 근무합니다. 연봉은 협의합니다.")
                + "\n"
                + "핀테크 결제 서비스를 안정적으로 제공하기 위해 노력합니다. ".repeat(12);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(result.qualityStatus()).isEqualTo(ApplicationCaseExtractionQualityGate.QUALITY_REVIEW_REQUIRED);
        assertThat(report.path("warnings").toString()).contains("critical_section_content_insufficient");
        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isTrue();
        assertThat(report.path("metrics").path("criticalSectionUsefulLineCount").asInt()).isZero();
    }

    @Test
    void inlineGarbledCriticalSectionDemotesPassToReview() throws Exception {
        String text = String.join("\n",
                "Company: Acme Payments",
                "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                "Responsibilities: 티",
                "공 Y (을 ) AY",
                "우표눔를용라어를ㅋ크이극아",
                "Qualifications: Java Spring Boot SQL",
                "Benefits: remote option and flexible work")
                + "\n"
                + "Fintech payment services require stable operations and reliable customer-facing platforms. ".repeat(12);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(result.qualityStatus()).isEqualTo(ApplicationCaseExtractionQualityGate.QUALITY_REVIEW_REQUIRED);
        assertThat(report.path("warnings").toString()).contains("critical_section_content_insufficient");
        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isTrue();
        assertThat(report.path("metrics").path("criticalSectionUsefulLineCount").asInt()).isZero();
    }

    @Test
    void inlineWhatYouWillDoGarbledCriticalSectionDemotesPassToReview() throws Exception {
        String text = String.join("\n",
                "Company: Acme Payments",
                "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                "What you will do: 티",
                "공 Y (을 ) AY",
                "우표눔를용라어를ㅋ크이극아",
                "Qualifications: Java Spring Boot SQL",
                "Benefits: remote option and flexible work")
                + "\n"
                + "Fintech payment services require stable operations and reliable customer-facing platforms. ".repeat(12);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(result.qualityStatus()).isEqualTo(ApplicationCaseExtractionQualityGate.QUALITY_REVIEW_REQUIRED);
        assertThat(report.path("warnings").toString()).contains("critical_section_content_insufficient");
        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isTrue();
        assertThat(report.path("metrics").path("criticalSectionUsefulLineCount").asInt()).isZero();
    }

    @Test
    void inlineValidCriticalSectionIsNotDemoted() throws Exception {
        String text = String.join("\n",
                "Company: Acme Payments",
                "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                "Responsibilities: build APIs and operate services",
                "Qualifications: Java Spring Boot SQL",
                "Benefits: remote option and flexible work")
                + "\n"
                + "Fintech payment services require stable operations and reliable customer-facing platforms. ".repeat(12);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(result.qualityStatus()).isEqualTo(ApplicationCaseExtractionQualityGate.QUALITY_PASS);
        assertThat(report.path("warnings").toString()).doesNotContain("critical_section_content_insufficient");
        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isTrue();
        assertThat(report.path("metrics").path("criticalSectionUsefulLineCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void inlineWhatYouWillDoValidCriticalSectionIsNotDemoted() throws Exception {
        String text = String.join("\n",
                "Company: Acme Payments",
                "Acme Payments operates reliable fintech payment platforms for high-volume merchants.",
                "What you will do: build APIs and operate services",
                "Qualifications: Java Spring Boot SQL",
                "Benefits: remote option and flexible work")
                + "\n"
                + "Fintech payment services require stable operations and reliable customer-facing platforms. ".repeat(12);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(result.qualityStatus()).isEqualTo(ApplicationCaseExtractionQualityGate.QUALITY_PASS);
        assertThat(report.path("warnings").toString()).doesNotContain("critical_section_content_insufficient");
        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isTrue();
        assertThat(report.path("metrics").path("criticalSectionUsefulLineCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void realWorldDutiesHeaderJikmuNaeyongIsRecognized() throws Exception {
        // 두레팜(고용24) 실측 헤더 "직무내용" 을 업무 섹션으로 인식해야 한다.
        String text = String.join("\n",
                "회사소개",
                "핀테크 결제 플랫폼을 운영하는 기업입니다.",
                "직무내용",
                "데이터 수집 및 분석, AI 모델 개발과 운영을 담당합니다.",
                "이런동료를기다립니다",
                "Java와 Spring Boot 개발 경험이 있으신 분.")
                + "\n"
                + "안정적인 서비스를 제공하기 위해 노력합니다. ".repeat(12);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isTrue();
        assertThat(report.path("metrics").path("criticalSectionUsefulLineCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(report.path("warnings").toString()).doesNotContain("critical_section_content_insufficient");
    }

    @Test
    void positionNameHeaderIsNotTreatedAsCriticalDutiesSection() throws Exception {
        // "모집직무" 는 직무명 헤더라 critical duties 로 잡으면 안 된다(업무 본문 없이 직무명만으로 PASS 방지).
        String text = String.join("\n",
                "회사소개",
                "핀테크 결제 플랫폼을 운영하는 기업입니다.",
                "모집직무",
                "AI 개발자, 데이터 분석가",
                "자격요건",
                "Python 경험이 있으신 분.")
                + "\n"
                + "안정적인 서비스를 제공합니다. ".repeat(40);

        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, text);
        JsonNode report = objectMapper.readTree(result.qualityReportJson());

        assertThat(report.path("metrics").path("criticalSectionExists").asBoolean()).isFalse();
    }

    @Test
    void modelVersionsJsonMergesOcrProviderWithoutOverwritingQualityGateInfo() throws Exception {
        // Claude/OpenAI OCR 로 만든 ExtractedPosting(ocrProvider/model 귀속 포함)
        JobPostingTextExtractor.ExtractedPosting extracted = new JobPostingTextExtractor.ExtractedPosting(
                "IMAGE", "cloudinary:image/authenticated/pdf/application-postings/10/x", null,
                "채용정보", null, "openai", "gpt-4o");

        ApplicationCaseExtractionQualityGate.QualityGateResult result = qualityGate.evaluate(
                "IMAGE", extracted,
                "회사명: Acme 직무: 백엔드 개발자 자격요건: Java, Spring 우대사항: AWS 담당업무: API 개발 접수기간: 2026-07-31");
        JsonNode modelVersions = objectMapper.readTree(result.modelVersionsJson());

        // quality gate 정보는 보존하고 ocr 귀속을 merge(덮어쓰지 않음)
        assertThat(modelVersions.path("qualityGate").asText()).isEqualTo("rules-v1");
        assertThat(modelVersions.path("fallbackPolicy").isMissingNode()).isFalse();
        assertThat(modelVersions.path("ocr").path("provider").asText()).isEqualTo("openai");
        assertThat(modelVersions.path("ocr").path("model").asText()).isEqualTo("gpt-4o");
    }

    @Test
    void modelVersionsJsonHasNoOcrKeyWhenProviderAbsent() throws Exception {
        ApplicationCaseExtractionQualityGate.QualityGateResult result =
                qualityGate.evaluate("TEXT", null, "회사명: Acme 자격요건: Java 담당업무: 개발");
        JsonNode modelVersions = objectMapper.readTree(result.modelVersionsJson());

        assertThat(modelVersions.path("qualityGate").asText()).isEqualTo("rules-v1");
        assertThat(modelVersions.path("ocr").isMissingNode()).isTrue();
    }
}
