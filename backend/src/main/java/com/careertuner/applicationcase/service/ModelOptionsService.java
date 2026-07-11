package com.careertuner.applicationcase.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.dto.ModelOptionsResponse;
import com.careertuner.applicationcase.dto.ModelOptionsResponse.ProviderOption;
import com.careertuner.applicationcase.dto.ModelOptionsResponse.StageOptions;
import com.careertuner.jobposting.service.JobPostingAiWorkerClient;
import com.careertuner.jobposting.service.JobPostingAiWorkerClient.WorkerCapabilities;
import com.careertuner.jobposting.service.JobPostingFallbackPolicy;

/**
 * 등록·재실행 화면의 단계별 모델 선택지를 실제 가용성 신호로 계산한다(Slice 1).
 *
 * <ul>
 * <li>OCR 은 sourceType 이 PDF/IMAGE 일 때만 채운다(텍스트·URL·수동 입력은 OCR 미적용 → null).</li>
 * <li>OpenAI OCR 의 {@code autoFallbackIncluded} 는 stage(PDF/IMAGE)별 {@code allowed(stage)} 로 계산한다.
 *     사용자가 직접 선택(selectable)하는 것과 자동 폴백 포함은 별개다(관리자 정책은 자동 폴백만 제어).</li>
 * <li>Self OCR 은 워커가 응답하고 준비된 엔진이 하나 이상일 때만 선택 가능하며, 실제 모델명은 준비된 엔진을 반영한다.</li>
 * <li>Local 은 enabled + Ollama 연결 + 설정 모델 존재까지 확인한다.</li>
 * </ul>
 */
@Service
public class ModelOptionsService {

    private static final String REASON_CLAUDE = "Claude 자격 증명 미설정";
    private static final String REASON_OPENAI = "OpenAI 자격 증명 미설정";

    private final BAnthropicClient anthropicClient;
    private final OpenAiProperties openAiProperties;
    private final BAnalysisProperties bAnalysisProperties;
    private final OllamaModelProbe ollamaModelProbe;
    private final JobPostingAiWorkerClient workerClient;
    private final JobPostingFallbackPolicy fallbackPolicy;

    public ModelOptionsService(BAnthropicClient anthropicClient,
                               OpenAiProperties openAiProperties,
                               BAnalysisProperties bAnalysisProperties,
                               OllamaModelProbe ollamaModelProbe,
                               JobPostingAiWorkerClient workerClient,
                               JobPostingFallbackPolicy fallbackPolicy) {
        this.anthropicClient = anthropicClient;
        this.openAiProperties = openAiProperties;
        this.bAnalysisProperties = bAnalysisProperties;
        this.ollamaModelProbe = ollamaModelProbe;
        this.workerClient = workerClient;
        this.fallbackPolicy = fallbackPolicy;
    }

    public ModelOptionsResponse modelOptions(String sourceType) {
        boolean claudeReady = anthropicClient.configured();
        boolean openAiReady = openAiProperties.configured();
        boolean localReady = bAnalysisProperties.getLocalLlm().isEnabled() && ollamaModelProbe.modelAvailable();

        return new ModelOptionsResponse(
                ocrOptions(sourceType, claudeReady, openAiReady),
                jobAnalysisOptions(localReady, claudeReady, openAiReady),
                companyAnalysisOptions(openAiReady, claudeReady, localReady));
    }

    private StageOptions ocrOptions(String sourceType, boolean claudeReady, boolean openAiReady) {
        String stage = ocrStage(sourceType);
        if (stage == null) {
            // 텍스트·URL·수동 입력은 OCR 미적용(텍스트 PDF 는 실행 시 PDFBox 직접 추출).
            return null;
        }
        WorkerCapabilities worker = workerClient.capabilities();
        boolean selfReady = worker.anyEngineReady();
        boolean openAiAutoFallback = fallbackPolicy.allowed(stage);

        ProviderOption claude = new ProviderOption("CLAUDE", "Claude", claudeReady,
                claudeReady ? null : REASON_CLAUDE, anthropicClient.model(), null);
        ProviderOption openAi = new ProviderOption("OPENAI", "OpenAI", openAiReady,
                openAiReady ? null : REASON_OPENAI, openAiProperties.getModel(), openAiAutoFallback);
        ProviderOption self = new ProviderOption("SELF_OCR", "자체 OCR 워커(PaddleOCR)", selfReady,
                selfReady ? null : selfOcrReason(worker), selfOcrModel(worker), null);
        // 우선순위 Claude → OpenAI → Self OCR 중 첫 selectable 을 기본값으로(모두 불가면 null).
        return new StageOptions(firstSelectable(List.of(claude, openAi, self)), List.of(claude, openAi, self));
    }

    private StageOptions jobAnalysisOptions(boolean localReady, boolean claudeReady, boolean openAiReady) {
        ProviderOption local = new ProviderOption("LOCAL", "자체 모델(R1)", localReady,
                localReady ? null : localReason(), bAnalysisProperties.getLocalLlm().getModel(), null);
        ProviderOption claude = new ProviderOption("CLAUDE", "Claude", claudeReady,
                claudeReady ? null : REASON_CLAUDE, anthropicClient.model(), null);
        ProviderOption openAi = new ProviderOption("OPENAI", "OpenAI", openAiReady,
                openAiReady ? null : REASON_OPENAI, openAiProperties.getModel(), null);
        // 우선순위 Local → Claude → OpenAI 중 첫 selectable(모두 불가면 null).
        return new StageOptions(firstSelectable(List.of(local, claude, openAi)), List.of(local, claude, openAi));
    }

    private StageOptions companyAnalysisOptions(boolean openAiReady, boolean claudeReady, boolean localReady) {
        ProviderOption openAi = new ProviderOption("OPENAI", "OpenAI", openAiReady,
                openAiReady ? null : REASON_OPENAI, companyOpenAiModel(), null);
        ProviderOption claude = new ProviderOption("CLAUDE", "Claude", claudeReady,
                claudeReady ? null : REASON_CLAUDE, anthropicClient.model(), null);
        ProviderOption local = new ProviderOption("LOCAL", "자체 모델(R1)", localReady,
                localReady ? null : localReason(), bAnalysisProperties.getLocalLlm().getModel(), null);
        // 기업분석 우선순위는 설정(provider=openai|claude|auto)을 따른다(BAnalysisGenerationService 와 동일).
        return new StageOptions(
                firstSelectable(companyPriorityOrder(openAi, claude, local)),
                List.of(openAi, claude, local));
    }

    /** 기업분석 provider 우선순위 — 설정값에 따라 정렬(companyProviderOrder 와 동일 규칙). */
    private List<ProviderOption> companyPriorityOrder(ProviderOption openAi, ProviderOption claude, ProviderOption local) {
        String provider = bAnalysisProperties.getCompany().getProvider();
        String normalized = provider == null ? "auto" : provider.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "openai" -> List.of(openAi, claude, local);
            case "claude", "anthropic" -> List.of(claude, openAi, local);
            default -> List.of(local, claude, openAi);
        };
    }

    /** 우선순위 순서에서 첫 selectable provider 를 기본값으로 반환한다(없으면 null → UI 기본 미선택). */
    private static String firstSelectable(List<ProviderOption> orderedByPriority) {
        return orderedByPriority.stream()
                .filter(ProviderOption::selectable)
                .map(ProviderOption::provider)
                .findFirst()
                .orElse(null);
    }

    /** 기업분석 OpenAI 는 company override(있으면) 를 우선한다(공용 모델과 스코프 격리). */
    private String companyOpenAiModel() {
        String override = bAnalysisProperties.getCompany().getOpenAiModel();
        return override != null && !override.isBlank() ? override : openAiProperties.getModel();
    }

    private String localReason() {
        return bAnalysisProperties.getLocalLlm().isEnabled()
                ? "Ollama 연결 불가 또는 모델 미탑재"
                : "로컬 모델 비활성화";
    }

    private String selfOcrReason(WorkerCapabilities worker) {
        return worker.available() ? "준비된 OCR 엔진 없음" : "OCR 워커 응답 없음";
    }

    private String selfOcrModel(WorkerCapabilities worker) {
        return worker.readyEngines().isEmpty() ? null : String.join(", ", worker.readyEngines());
    }

    private static String ocrStage(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        return switch (sourceType.trim().toUpperCase(Locale.ROOT)) {
            case "PDF" -> JobPostingFallbackPolicy.STAGE_PDF_OCR;
            case "IMAGE" -> JobPostingFallbackPolicy.STAGE_IMAGE_OCR;
            default -> null;
        };
    }
}
