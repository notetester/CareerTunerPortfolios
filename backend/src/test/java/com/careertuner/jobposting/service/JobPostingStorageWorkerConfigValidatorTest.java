package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

/**
 * 저장 provider ↔ OCR 워커 설정 정합성 기동 가드 검증.
 * Cloudinary(path=null) + 워커 ON + send-bytes OFF 만 fail-fast, 나머지 조합은 통과.
 */
class JobPostingStorageWorkerConfigValidatorTest {

    private JobPostingStorageWorkerConfigValidator validator(String storageProvider,
                                                             boolean workerEnabled,
                                                             boolean sendBytes) {
        JobPostingUploadProperties upload = new JobPostingUploadProperties();
        upload.setStorageProvider(storageProvider);
        JobPostingAiWorkerProperties worker = new JobPostingAiWorkerProperties();
        worker.setEnabled(workerEnabled);
        worker.setSendBytes(sendBytes);
        return new JobPostingStorageWorkerConfigValidator(upload, worker);
    }

    @Test
    void failsFastWhenCloudinaryWorkerEnabledButSendBytesOff() {
        Throwable thrown = catchThrowable(() -> validator("cloudinary", true, false).validate());

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage()).contains("send-bytes");
    }

    @Test
    void failsFastIsCaseInsensitiveOnStorageProvider() {
        Throwable thrown = catchThrowable(() -> validator("  Cloudinary ", true, false).validate());

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsCloudinaryWorkerEnabledWhenSendBytesOn() {
        assertThatCode(() -> validator("cloudinary", true, true).validate()).doesNotThrowAnyException();
    }

    @Test
    void allowsCloudinaryWhenWorkerDisabled() {
        assertThatCode(() -> validator("cloudinary", false, false).validate()).doesNotThrowAnyException();
    }

    @Test
    void allowsLocalStoreRegardlessOfWorkerAndBytes() {
        assertThatCode(() -> validator("local", true, false).validate()).doesNotThrowAnyException();
        assertThatCode(() -> validator(null, true, false).validate()).doesNotThrowAnyException();
    }
}
