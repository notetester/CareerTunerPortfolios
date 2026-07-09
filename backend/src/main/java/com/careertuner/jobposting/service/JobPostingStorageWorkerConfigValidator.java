package com.careertuner.jobposting.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 공고 저장 provider ↔ OCR 워커 설정 정합성 검증(기동 시 fail-fast).
 *
 * <p>Cloudinary 처럼 로컬 경로가 없는(path=null) 원격 저장소를 store provider 로 쓰면서 OCR 워커를 켰다면,
 * 워커에 파일을 전달할 방법은 send-bytes(base64) 뿐이다. send-bytes 가 꺼져 있으면 워커는
 * {@code filePath=null}·{@code fileBase64} 없음 요청을 받아 빈 텍스트/{@code FAILED} 를 돌려주게 되고,
 * 사용자는 업로드 시점에야 실패를 겪는다({@link JobPostingAiWorkerClient#buildFileRequest}).
 *
 * <p>이 잘못된 조합은 런타임이 아니라 <b>기동 시점</b>에 막는다 — 잘못된 운영 설정이 배포되면 앱이 아예 뜨지 않고
 * 원인을 알려준다. 워커가 꺼져 있으면(로컬 PDFBox/Claude/OpenAI 로 추출) 무관하므로 세 조건이 모두 맞을 때만 발동한다.
 */
@Component
public class JobPostingStorageWorkerConfigValidator {

    private final JobPostingUploadProperties uploadProperties;
    private final JobPostingAiWorkerProperties aiWorkerProperties;

    public JobPostingStorageWorkerConfigValidator(JobPostingUploadProperties uploadProperties,
                                                  JobPostingAiWorkerProperties aiWorkerProperties) {
        this.uploadProperties = uploadProperties;
        this.aiWorkerProperties = aiWorkerProperties;
    }

    @PostConstruct
    void validate() {
        if (isRemoteStoreWithWorkerButNoBytes()) {
            throw new IllegalStateException(
                    "Invalid job posting extraction config: storage-provider='"
                    + normalizedStorageProvider() + "' has no local file path, so the OCR worker cannot access the "
                    + "uploaded file. Set careertuner.extraction.ai-worker.send-bytes=true "
                    + "(env JOB_POSTING_AI_WORKER_SEND_BYTES=true), or disable the worker "
                    + "(careertuner.extraction.ai-worker.enabled=false).");
        }
    }

    private boolean isRemoteStoreWithWorkerButNoBytes() {
        boolean remoteStore = CloudinaryJobPostingStorageProvider.SCHEME.equals(normalizedStorageProvider());
        return remoteStore && aiWorkerProperties.isEnabled() && !aiWorkerProperties.isSendBytes();
    }

    private String normalizedStorageProvider() {
        String provider = uploadProperties.getStorageProvider();
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
