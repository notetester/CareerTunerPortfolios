package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;

import tools.jackson.databind.ObjectMapper;

/**
 * #3 워커 bytes 전송(sendBytes) 요청 바디 조립 검증.
 * co-location(파일경로 공유) 없이 OCR 하려고 파일 바이트를 base64 로 동봉하는 경로다.
 * HttpClient 없이 buildFileRequest 만 검증한다.
 */
class JobPostingAiWorkerClientBytesTest {

    private JobPostingAiWorkerClient client(boolean sendBytes) {
        JobPostingAiWorkerProperties props = new JobPostingAiWorkerProperties();
        props.setEnabled(true);
        props.setSendBytes(sendBytes);
        return new JobPostingAiWorkerClient(props, new ObjectMapper());
    }

    private StoredJobPostingFile file(int sizeBytes) {
        return new StoredJobPostingFile(
                "IMAGE",
                "local:application-postings/1/x.png",
                "x.png",
                "image/png",
                Path.of("C:/uploads/x.png"),
                new byte[sizeBytes]);
    }

    @Test
    void sendBytesOff_keepsFilePathOnly() {
        Map<String, Object> req = client(false).buildFileRequest(file(1024));
        assertThat(req).containsKey("filePath");
        assertThat(req).doesNotContainKey("fileBase64");
    }

    @Test
    void sendBytesOn_includesBase64AndKeepsFilePathFallback() {
        int raw = 10 * 1024 * 1024; // 10MB — base64 33% 증가·메모리 경로를 실제로 태운다.
        Map<String, Object> req = client(true).buildFileRequest(file(raw));
        assertThat(req).containsKey("filePath"); // filePath 는 fallback 으로 그대로 유지
        assertThat(req).containsKey("fileBase64");
        String b64 = (String) req.get("fileBase64");
        // base64 길이 ≈ raw * 4/3 (약 33% 증가) 이고 워커 상한(32MB) 안이어야 한다.
        assertThat(b64.length()).isBetween(raw, 32 * 1024 * 1024);
    }

    @Test
    void sendBytesOn_20mbBoundary_withinWorkerLimit() {
        int raw = 20 * 1024 * 1024; // 업로드 실효 상한(20MB) 경계 케이스
        Map<String, Object> req = client(true).buildFileRequest(file(raw));
        String b64 = (String) req.get("fileBase64");
        // 20MB → base64 약 26.7MB. 워커 요청 상한(32MB)으로 "수용 가능하게 설계"됨을 확인한다.
        assertThat(b64.length()).isGreaterThan(raw); // 약 33% 증가
        assertThat(b64.length()).isLessThanOrEqualTo(32 * 1024 * 1024);
    }

    @Test
    void sendBytesOn_nullBytes_noBase64() {
        StoredJobPostingFile f = new StoredJobPostingFile(
                "IMAGE", "ref", "x.png", "image/png", Path.of("C:/x.png"), null);
        Map<String, Object> req = client(true).buildFileRequest(f);
        assertThat(req).doesNotContainKey("fileBase64");
    }
}
