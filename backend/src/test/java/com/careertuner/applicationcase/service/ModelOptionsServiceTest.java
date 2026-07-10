package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.dto.ModelOptionsResponse;
import com.careertuner.applicationcase.dto.ModelOptionsResponse.ProviderOption;
import com.careertuner.jobposting.service.JobPostingAiWorkerClient;
import com.careertuner.jobposting.service.JobPostingAiWorkerClient.WorkerCapabilities;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy;

class ModelOptionsServiceTest {

    private final BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
    private final OpenAiProperties openAiProperties = new OpenAiProperties();
    private final BAnalysisProperties bAnalysisProperties = new BAnalysisProperties();
    private final OllamaModelProbe ollamaModelProbe = mock(OllamaModelProbe.class);
    private final JobPostingAiWorkerClient workerClient = mock(JobPostingAiWorkerClient.class);
    private final JobPostingFallbackPolicy fallbackPolicy = mock(JobPostingFallbackPolicy.class);

    private final ModelOptionsService service = new ModelOptionsService(
            anthropicClient, openAiProperties, bAnalysisProperties, ollamaModelProbe, workerClient, fallbackPolicy);

    private void allReady() {
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku");
        openAiProperties.setApiKey("sk-test");
        openAiProperties.setModel("gpt-5");
        bAnalysisProperties.getLocalLlm().setEnabled(true);
        bAnalysisProperties.getLocalLlm().setModel("careertuner-b-jobposting-r1");
        // 운영 기본값과 동일하게 기업분석 provider=openai(그래야 company 기본값 OPENAI).
        bAnalysisProperties.getCompany().setProvider("openai");
        when(ollamaModelProbe.modelAvailable()).thenReturn(true);
        when(workerClient.capabilities())
                .thenReturn(new WorkerCapabilities(true, List.of("paddleocr", "ppstructure"), null));
    }

    private static ProviderOption option(List<ProviderOption> options, String provider) {
        return options.stream().filter(o -> o.provider().equals(provider)).findFirst().orElseThrow();
    }

    @Test
    void pdfSourceReturnsAllStagesSelectableWhenEverythingReady() {
        allReady();
        when(fallbackPolicy.allowed(JobPostingFallbackPolicy.STAGE_PDF_OCR)).thenReturn(true);

        ModelOptionsResponse response = service.modelOptions("PDF");

        assertThat(response.ocr()).isNotNull();
        assertThat(response.ocr().recommendedDefault()).isEqualTo("CLAUDE");
        ProviderOption self = option(response.ocr().options(), "SELF_OCR");
        assertThat(self.selectable()).isTrue();
        assertThat(self.actualModel()).isEqualTo("paddleocr, ppstructure");
        assertThat(option(response.ocr().options(), "OPENAI").autoFallbackIncluded()).isTrue();

        assertThat(response.jobAnalysis().recommendedDefault()).isEqualTo("LOCAL");
        assertThat(option(response.jobAnalysis().options(), "LOCAL").selectable()).isTrue();
        assertThat(response.companyAnalysis().recommendedDefault()).isEqualTo("OPENAI");
        assertThat(option(response.companyAnalysis().options(), "OPENAI").selectable()).isTrue();
    }

    @Test
    void openAiAutoFallbackReflectsStagePolicyPerSourceType() {
        allReady();
        when(fallbackPolicy.allowed(JobPostingFallbackPolicy.STAGE_PDF_OCR)).thenReturn(true);
        when(fallbackPolicy.allowed(JobPostingFallbackPolicy.STAGE_IMAGE_OCR)).thenReturn(false);

        ProviderOption pdfOpenAi = option(service.modelOptions("PDF").ocr().options(), "OPENAI");
        ProviderOption imageOpenAi = option(service.modelOptions("IMAGE").ocr().options(), "OPENAI");

        assertThat(pdfOpenAi.autoFallbackIncluded()).isTrue();
        assertThat(imageOpenAi.autoFallbackIncluded()).isFalse();
    }

    @Test
    void companyOpenAiModelPrefersOverride() {
        allReady();
        bAnalysisProperties.getCompany().setOpenAiModel("gpt-5.4-mini");

        ProviderOption openAi = option(service.modelOptions("PDF").companyAnalysis().options(), "OPENAI");
        assertThat(openAi.actualModel()).isEqualTo("gpt-5.4-mini");
    }

    @Test
    void unavailableProvidersAreNotSelectableAndCarryReasons() {
        // 기본값: anthropic 미설정, openAi apiKey 공백, local 비활성, 워커 오프라인.
        when(anthropicClient.configured()).thenReturn(false);
        when(ollamaModelProbe.modelAvailable()).thenReturn(false);
        when(workerClient.capabilities())
                .thenReturn(new WorkerCapabilities(false, List.of(), "ConnectException"));

        ModelOptionsResponse response = service.modelOptions("IMAGE");

        ProviderOption self = option(response.ocr().options(), "SELF_OCR");
        assertThat(self.selectable()).isFalse();
        assertThat(self.reason()).isEqualTo("OCR 워커 응답 없음");
        ProviderOption local = option(response.jobAnalysis().options(), "LOCAL");
        assertThat(local.selectable()).isFalse();
        assertThat(local.reason()).isEqualTo("로컬 모델 비활성화");
        assertThat(option(response.jobAnalysis().options(), "OPENAI").selectable()).isFalse();
        assertThat(option(response.companyAnalysis().options(), "CLAUDE").selectable()).isFalse();
    }

    @Test
    void selfOcrWorkerRespondsButNoEnginesReadyIsNotSelectable() {
        allReady();
        when(workerClient.capabilities()).thenReturn(new WorkerCapabilities(true, List.of(), null));

        ProviderOption self = option(service.modelOptions("PDF").ocr().options(), "SELF_OCR");
        assertThat(self.selectable()).isFalse();
        assertThat(self.reason()).isEqualTo("준비된 OCR 엔진 없음");
        assertThat(self.actualModel()).isNull();
    }

    @Test
    void textOrMissingSourceOmitsOcrStage() {
        allReady();
        assertThat(service.modelOptions("TEXT").ocr()).isNull();
        assertThat(service.modelOptions(null).ocr()).isNull();
    }

    @Test
    void recommendedDefaultSkipsUnavailableProviders() {
        allReady();
        when(anthropicClient.configured()).thenReturn(false); // Claude 불가

        ModelOptionsResponse response = service.modelOptions("PDF");

        // OCR 우선순위 Claude → OpenAI → Self: Claude 불가면 OpenAI 가 기본.
        assertThat(response.ocr().recommendedDefault()).isEqualTo("OPENAI");
        // Job 우선순위 Local → Claude → OpenAI: Local ready 유지 → LOCAL.
        assertThat(response.jobAnalysis().recommendedDefault()).isEqualTo("LOCAL");
    }

    @Test
    void companyRecommendedDefaultFollowsProviderConfig() {
        allReady();

        bAnalysisProperties.getCompany().setProvider("auto");
        assertThat(service.modelOptions("PDF").companyAnalysis().recommendedDefault()).isEqualTo("LOCAL");

        bAnalysisProperties.getCompany().setProvider("claude");
        assertThat(service.modelOptions("PDF").companyAnalysis().recommendedDefault()).isEqualTo("CLAUDE");

        bAnalysisProperties.getCompany().setProvider("openai");
        assertThat(service.modelOptions("PDF").companyAnalysis().recommendedDefault()).isEqualTo("OPENAI");
    }

    @Test
    void recommendedDefaultIsNullWhenNoProviderSelectable() {
        // 아무 provider 도 준비되지 않음(기본 미설정 + 워커 오프라인).
        when(anthropicClient.configured()).thenReturn(false);
        when(ollamaModelProbe.modelAvailable()).thenReturn(false);
        when(workerClient.capabilities()).thenReturn(new WorkerCapabilities(false, List.of(), "offline"));

        ModelOptionsResponse response = service.modelOptions("PDF");

        assertThat(response.ocr().recommendedDefault()).isNull();
        assertThat(response.jobAnalysis().recommendedDefault()).isNull();
        assertThat(response.companyAnalysis().recommendedDefault()).isNull();
    }
}
