package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.test.util.ReflectionTestUtils;

import com.careertuner.applicationcase.service.BAnthropicClient;
import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class JobPostingTextExtractorTest {

    @Test
    void imageExtractionDoesNotUseOpenAiFallbackByDefault() {
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        JobPostingTextExtractor extractor = new JobPostingTextExtractor(openAiClient);

        JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                "IMAGE",
                "local:application-postings/10/posting.png",
                "posting.png",
                "image/png",
                null,
                new byte[]{1, 2, 3}));

        assertThat(result.extractedText()).isEmpty();
        assertThat(result.usage()).isNull();
        verify(openAiClient, never()).extractImageText(any(), any());
    }

    @Test
    void fileExtractionUsesPythonWorkerWhenEnabledAndPreservesQualityMetadata() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/extract/job-posting", exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, """
                    {
                      "text": "Responsibilities: build APIs. Qualifications: Java and Spring.",
                      "meta": {
                        "strategy": "IMAGE_OCR",
                        "qualityScore": 55,
                        "qualityStatus": "REVIEW_REQUIRED",
                        "qualityReportJson": "{}",
                        "modelVersions": {"documentExtractionContract": "self_ai_v1"},
                        "fallbackEligible": true,
                        "fallbackReason": "explicit_low_confidence",
                        "warnings": ["ocr_low_confidence"]
                      }
                    }
                    """);
        });
        server.start();

        try {
            JobPostingAiWorkerProperties properties = new JobPostingAiWorkerProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            properties.setTimeout(Duration.ofSeconds(5));
            OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
            JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                    openAiClient,
                    new JobPostingAiWorkerClient(properties, new ObjectMapper()),
                    JobPostingFallbackPolicy.fromProperties(null),
                    InetAddress::getAllByName,
                    target -> {
                        throw new AssertionError("File extraction should not fetch URLs");
                    });

            JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                    "IMAGE",
                    "local:application-postings/10/posting.png",
                    "posting.png",
                    "image/png",
                    java.nio.file.Path.of("posting.png"),
                    new byte[]{1, 2, 3}));

            assertThat(requests).hasValue(1);
            assertThat(requestBody.get()).contains("\"sourceType\":\"IMAGE\"");
            assertThat(result.extractedText()).contains("Responsibilities");
            assertThat(result.extractionStrategy()).isEqualTo("IMAGE_OCR");
            assertThat(result.qualityScore()).isEqualTo(55);
            assertThat(result.qualityStatus()).isEqualTo("REVIEW_REQUIRED");
            assertThat(result.qualityReportJson()).isEqualTo("{}");
            assertThat(result.fallbackEligible()).isTrue();
            assertThat(result.fallbackReason()).isEqualTo("explicit_low_confidence");
            verify(openAiClient, never()).extractImageText(any(), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileExtractionPrefersOpenAiOverWorkerWhenFallbackAllowed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger workerRequests = new AtomicInteger();
        server.createContext("/extract/job-posting", exchange -> {
            workerRequests.incrementAndGet();
            sendJson(exchange, "{\"text\":\"worker should not be reached\"}");
        });
        server.start();

        try {
            OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
            when(openAiClient.extractImageText(any(), any()))
                    .thenReturn(new OpenAiResponsesClient.TextPayload("OpenAI extracted posting", null));

            OpenAiProperties openAiProperties = new OpenAiProperties();
            openAiProperties.setJobPostingFallbackEnabled(true);
            openAiProperties.setJobPostingFallbackAllowlist(Set.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR));

            JobPostingAiWorkerProperties workerProperties = new JobPostingAiWorkerProperties();
            workerProperties.setEnabled(true);
            workerProperties.setBaseUrl("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            workerProperties.setTimeout(Duration.ofSeconds(5));

            JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                    openAiClient,
                    new JobPostingAiWorkerClient(workerProperties, new ObjectMapper()),
                    JobPostingFallbackPolicy.fromProperties(openAiProperties),
                    InetAddress::getAllByName,
                    target -> {
                        throw new AssertionError("File extraction should not fetch URLs");
                    });

            JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                    "IMAGE",
                    "local:application-postings/10/posting.png",
                    "posting.png",
                    "image/png",
                    java.nio.file.Path.of("posting.png"),
                    new byte[]{1, 2, 3}));

            // Claude/OpenAI 1순위 — OpenAI 가 텍스트를 주면 워커는 호출되지 않는다.
            assertThat(result.extractedText()).isEqualTo("OpenAI extracted posting");
            assertThat(workerRequests).hasValue(0);
            verify(openAiClient).extractImageText(any(), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileExtractionFallsThroughToOpenAiWhenClaudeReturnsBlank() {
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.extractImageText(any(), any()))
                .thenReturn(new OpenAiResponsesClient.TextPayload("OpenAI recovered posting", null));

        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setJobPostingFallbackEnabled(true);
        openAiProperties.setJobPostingFallbackAllowlist(Set.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR));

        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                openAiClient,
                JobPostingAiWorkerClient.disabled(),
                JobPostingFallbackPolicy.fromProperties(openAiProperties),
                InetAddress::getAllByName,
                target -> {
                    throw new AssertionError("File extraction should not fetch URLs");
                });

        // Claude 가 빈 텍스트를 반환 — 성공으로 삼키지 말고 OpenAI 로 넘어가야 한다(IMAGE 경로).
        BAnthropicClient anthropic = mock(BAnthropicClient.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.extractImageText(any(), any())).thenReturn("   ");
        ReflectionTestUtils.setField(extractor, "anthropicClient", anthropic);

        JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                "IMAGE",
                "local:application-postings/10/posting.png",
                "posting.png",
                "image/png",
                java.nio.file.Path.of("posting.png"),
                new byte[]{1, 2, 3}));

        assertThat(result.extractedText()).isEqualTo("OpenAI recovered posting");
        verify(anthropic).extractImageText(any(), any());
        verify(openAiClient).extractImageText(any(), any());
    }

    @Test
    void pdfExtractionFallsThroughToOpenAiWhenPdfBoxAndClaudeReturnBlank() throws Exception {
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.extractPdfText(any(), any()))
                .thenReturn(new OpenAiResponsesClient.TextPayload("OpenAI recovered pdf posting", null));

        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setJobPostingFallbackEnabled(true);
        openAiProperties.setJobPostingFallbackAllowlist(Set.of(JobPostingFallbackPolicy.STAGE_PDF_OCR));

        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                openAiClient,
                JobPostingAiWorkerClient.disabled(),
                JobPostingFallbackPolicy.fromProperties(openAiProperties),
                InetAddress::getAllByName,
                target -> {
                    throw new AssertionError("File extraction should not fetch URLs");
                });

        // 발표 셋업 실측 재현: 텍스트 없는 PDF → PDFBox blank → Claude.extractPdfText blank → OpenAI 로 복구.
        BAnthropicClient anthropic = mock(BAnthropicClient.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.extractPdfText(any())).thenReturn("");
        ReflectionTestUtils.setField(extractor, "anthropicClient", anthropic);

        JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                "PDF",
                "local:application-postings/10/posting.pdf",
                "posting.pdf",
                "application/pdf",
                java.nio.file.Path.of("posting.pdf"),
                blankPdfBytes()));

        assertThat(result.extractedText()).isEqualTo("OpenAI recovered pdf posting");
        verify(anthropic).extractPdfText(any());
        verify(openAiClient).extractPdfText(any(), any());
    }

    @Test
    void fileExtractionFallsThroughToWorkerWhenOpenAiReturnsBlank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger workerRequests = new AtomicInteger();
        server.createContext("/extract/job-posting", exchange -> {
            workerRequests.incrementAndGet();
            sendJson(exchange, """
                    {
                      "text": "worker recovered posting text",
                      "meta": {"strategy": "IMAGE_OCR", "qualityStatus": "REVIEW_REQUIRED"}
                    }
                    """);
        });
        server.start();

        try {
            OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
            when(openAiClient.extractImageText(any(), any()))
                    .thenReturn(new OpenAiResponsesClient.TextPayload("   ", null));

            OpenAiProperties openAiProperties = new OpenAiProperties();
            openAiProperties.setJobPostingFallbackEnabled(true);
            openAiProperties.setJobPostingFallbackAllowlist(Set.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR));

            JobPostingAiWorkerProperties workerProperties = new JobPostingAiWorkerProperties();
            workerProperties.setEnabled(true);
            workerProperties.setBaseUrl("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            workerProperties.setTimeout(Duration.ofSeconds(5));

            JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                    openAiClient,
                    new JobPostingAiWorkerClient(workerProperties, new ObjectMapper()),
                    JobPostingFallbackPolicy.fromProperties(openAiProperties),
                    InetAddress::getAllByName,
                    target -> {
                        throw new AssertionError("File extraction should not fetch URLs");
                    });

            JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                    "IMAGE",
                    "local:application-postings/10/posting.png",
                    "posting.png",
                    "image/png",
                    java.nio.file.Path.of("posting.png"),
                    new byte[]{1, 2, 3}));

            // OpenAI 가 빈 텍스트를 주면 워커까지 폴백해 실제 텍스트를 복구한다.
            assertThat(result.extractedText()).isEqualTo("worker recovered posting text");
            assertThat(workerRequests).hasValue(1);
            verify(openAiClient).extractImageText(any(), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileExtractionPropagatesOpenAiFailureInsteadOfSavingEmptyResult() {
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.extractImageText(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI OCR down"));

        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setJobPostingFallbackEnabled(true);
        openAiProperties.setJobPostingFallbackAllowlist(Set.of(JobPostingFallbackPolicy.STAGE_IMAGE_OCR));

        // 워커 disabled — OpenAI 시스템 오류는 빈 결과로 삼키지 말고 상위로 전파해야 한다.
        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                openAiClient,
                JobPostingAiWorkerClient.disabled(),
                JobPostingFallbackPolicy.fromProperties(openAiProperties),
                InetAddress::getAllByName,
                target -> {
                    throw new AssertionError("File extraction should not fetch URLs");
                });

        Throwable thrown = catchThrowable(() -> extractor.extractFile(new StoredJobPostingFile(
                "IMAGE",
                "local:application-postings/10/posting.png",
                "posting.png",
                "image/png",
                java.nio.file.Path.of("posting.png"),
                new byte[]{1, 2, 3})));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(thrown.getMessage()).isEqualTo("OpenAI OCR down");
    }

    @Test
    void fileExtractionPreservesPythonWorkerFailureMetadataFromErrorResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/job-posting", exchange -> sendJson(exchange, """
                {
                  "text": "",
                  "meta": {
                    "strategy": "WORKER_ERROR",
                    "qualityScore": 0,
                    "qualityStatus": "FAILED",
                    "metrics": {"textLength": 0},
                    "warnings": ["worker_error:ValueError"],
                    "sectionHints": [],
                    "modelVersions": {"documentExtractionContract": "self_ai_v1"},
                    "fallbackEligible": false,
                    "fallbackReason": "worker_error"
                  }
                }
                """, 500));
        server.start();

        try {
            JobPostingAiWorkerProperties properties = new JobPostingAiWorkerProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            properties.setTimeout(Duration.ofSeconds(5));
            JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                    mock(OpenAiResponsesClient.class),
                    new JobPostingAiWorkerClient(properties, new ObjectMapper()),
                    JobPostingFallbackPolicy.fromProperties(null),
                    InetAddress::getAllByName,
                    target -> {
                        throw new AssertionError("File extraction should not fetch URLs");
                    });

            JobPostingTextExtractor.ExtractedPosting result = extractor.extractFile(new StoredJobPostingFile(
                    "IMAGE",
                    "local:application-postings/10/posting.png",
                    "posting.png",
                    "image/png",
                    java.nio.file.Path.of("posting.png"),
                    new byte[]{1, 2, 3}));

            assertThat(result.extractedText()).isEmpty();
            assertThat(result.extractionStrategy()).isEqualTo("WORKER_ERROR");
            assertThat(result.qualityScore()).isZero();
            assertThat(result.qualityStatus()).isEqualTo("FAILED");
            assertThat(result.fallbackEligible()).isFalse();
            assertThat(result.fallbackReason()).isEqualTo("worker_error");
            assertThat(result.qualityReportJson()).contains("worker_error:ValueError");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileExtractionFailsClosedWhenPythonWorkerReturnsMalformedJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/job-posting", exchange -> sendJson(exchange, "{broken-json"));
        server.start();

        try {
            JobPostingAiWorkerProperties properties = new JobPostingAiWorkerProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            properties.setTimeout(Duration.ofSeconds(5));
            OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
            JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                    openAiClient,
                    new JobPostingAiWorkerClient(properties, new ObjectMapper()),
                    JobPostingFallbackPolicy.fromProperties(null),
                    InetAddress::getAllByName,
                    target -> {
                        throw new AssertionError("File extraction should not fetch URLs");
                    });

            Throwable thrown = catchThrowable(() -> extractor.extractFile(new StoredJobPostingFile(
                    "IMAGE",
                    "local:application-postings/10/posting.png",
                    "posting.png",
                    "image/png",
                    java.nio.file.Path.of("posting.png"),
                    new byte[]{1, 2, 3})));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
            assertThat(thrown.getMessage()).isEqualTo("Python job posting worker response is invalid.");
            verify(openAiClient, never()).extractImageText(any(), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fileExtractionFailsClosedWhenPythonWorkerTimesOut() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/job-posting", exchange -> {
            try {
                Thread.sleep(1000L);
                sendJson(exchange, """
                        {
                          "text": "late response",
                          "meta": {
                            "strategy": "IMAGE_OCR",
                            "qualityScore": 10,
                            "qualityStatus": "FAILED"
                          }
                        }
                        """);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        try {
            JobPostingAiWorkerProperties properties = new JobPostingAiWorkerProperties();
            properties.setEnabled(true);
            properties.setBaseUrl("http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            properties.setTimeout(Duration.ofMillis(100));
            OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
            JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                    openAiClient,
                    new JobPostingAiWorkerClient(properties, new ObjectMapper()),
                    JobPostingFallbackPolicy.fromProperties(null),
                    InetAddress::getAllByName,
                    target -> {
                        throw new AssertionError("File extraction should not fetch URLs");
                    });

            Throwable thrown = catchThrowable(() -> extractor.extractFile(new StoredJobPostingFile(
                    "IMAGE",
                    "local:application-postings/10/posting.png",
                    "posting.png",
                    "image/png",
                    java.nio.file.Path.of("posting.png"),
                    new byte[]{1, 2, 3})));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
            assertThat(thrown.getMessage()).isEqualTo("Python job posting worker is unavailable.");
            verify(openAiClient, never()).extractImageText(any(), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blocksLocalhostBeforeNetworkFetch() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/posting", exchange -> {
            requests.incrementAndGet();
            sendHtml(exchange, "<html><title>Job</title><body>Backend developer</body></html>");
        });
        server.start();

        try {
            JobPostingTextExtractor extractor = extractor();

            assertBlocked(() -> extractor.extractUrl(
                    "http://localhost:%d/posting".formatted(server.getAddress().getPort())));

            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blocksLoopbackIpv4Address() {
        assertBlockedUrlValidation("http://127.0.0.1/admin");
    }

    @Test
    void blocksPrivateIpv4Address() {
        assertBlockedUrlValidation("http://192.168.0.10/posting");
    }

    @Test
    void blocksMetadataIpv4Address() {
        assertBlockedUrlValidation("http://169.254.169.254/latest/meta-data");
    }

    @Test
    void blocksCarrierGradeNatIpv4Address() {
        assertBlockedUrlValidation("http://100.64.0.1/posting");
    }

    @Test
    void blocksUnspecifiedIpv4Address() {
        assertBlockedUrlValidation("http://0.0.0.0/posting");
    }

    @Test
    void blocksWholeUnspecifiedIpv4Range() {
        assertBlockedUrlValidation("http://0.1.2.3/posting");
    }

    @Test
    void blocksBenchmarkIpv4Range() {
        assertBlockedUrlValidation("http://198.18.0.1/posting");
    }

    @Test
    void blocksDocumentationIpv4Range() {
        assertBlockedUrlValidation("http://198.51.100.1/posting");
    }

    @Test
    void blocksReservedIpv4Range() {
        assertBlockedUrlValidation("http://240.0.0.1/posting");
    }

    @Test
    void blocksLimitedBroadcastIpv4Address() {
        assertBlockedUrlValidation("http://255.255.255.255/posting");
    }

    @Test
    void blocksLinkLocalIpv4Address() {
        assertBlockedUrlValidation("http://169.254.1.10/posting");
    }

    @Test
    void blocksMulticastIpv4Address() {
        assertBlockedUrlValidation("http://224.0.0.1/posting");
    }

    @Test
    void blocksIpv6LoopbackAddress() {
        assertBlockedUrlValidation("http://[::1]/posting");
    }

    @Test
    void blocksIpv6UniqueLocalAddress() {
        assertBlockedUrlValidation("http://[fc00::1]/posting");
    }

    @Test
    void rejectsNonHttpSchemeWithHttpOnlyMessage() {
        Throwable thrown = catchThrowable(() -> JobPostingTextExtractor.validateSafeHttpUrl(
                "ftp://example.com/posting",
                host -> {
                    throw new AssertionError("Non-http schemes should be rejected before host resolution");
                }));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException exception = (BusinessException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(exception.getMessage()).isEqualTo("http 또는 https URL만 사용할 수 있습니다.");
    }

    @Test
    void acceptsSafePublicHostnameThroughValidationHelperWithoutNetworkFetch() throws Exception {
        String normalizedUrl = JobPostingTextExtractor.validateSafeHttpUrl(
                "https://jobs.example.com/postings/backend",
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});

        assertThat(normalizedUrl).isEqualTo("https://jobs.example.com/postings/backend");
    }

    @Test
    void fetchUsesAlreadyValidatedAddressWithoutResolvingHostAgain() throws Exception {
        InetAddress validatedAddress = InetAddress.getByName("93.184.216.34");
        AtomicInteger resolveCount = new AtomicInteger();
        AtomicReference<InetAddress> fetchAddress = new AtomicReference<>();

        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                mock(OpenAiResponsesClient.class),
                host -> {
                    int attempt = resolveCount.incrementAndGet();
                    if ("jobs.example.com".equals(host) && attempt == 1) {
                        return new InetAddress[]{validatedAddress};
                    }
                    return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
                },
                target -> {
                    fetchAddress.set(target.address());
                    return htmlResponse("<html><title>Job</title><body>Backend developer</body></html>");
                });

        JobPostingTextExtractor.ExtractedPosting result =
                extractor.extractUrl("http://jobs.example.com/posting");

        assertThat(result.extractedText()).contains("Job", "Backend developer");
        assertThat(fetchAddress).hasValue(validatedAddress);
        assertThat(resolveCount).hasValue(1);
    }

    @Test
    void rejectsRedirectTargetThatResolvesToUnsafeAddress() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                mock(OpenAiResponsesClient.class),
                host -> {
                    if ("jobs.example.com".equals(host)) {
                        return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
                    }
                    return new InetAddress[]{InetAddress.getByName(host)};
                },
                target -> {
                    requests.incrementAndGet();
                    return redirectResponse("http://127.0.0.1/internal");
                });

        assertBlocked(() -> extractor.extractUrl("http://jobs.example.com/posting"));

        assertThat(requests).hasValue(1);
    }

    @Test
    void rejectsMoreThanFiveRedirects() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                mock(OpenAiResponsesClient.class),
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                target -> {
                    int requestNumber = requests.incrementAndGet();
                    return redirectResponse("/posting-" + requestNumber);
                });

        Throwable thrown = catchThrowable(() -> extractor.extractUrl("http://jobs.example.com/posting"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(requests).hasValue(6);
    }

    @Test
    void encodesNonAsciiPathAndQueryBeforeSendingHttpRequest() throws Exception {
        AtomicReference<String> requestTarget = new AtomicReference<>();
        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                mock(OpenAiResponsesClient.class),
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                target -> {
                    requestTarget.set(target.encodedRequestTarget());
                    return htmlResponse("<html><title>Job</title><body>Backend developer</body></html>");
                });

        extractor.extractUrl("http://jobs.example.com/채용/백엔드?검색=개발자&level=중급");

        assertThat(requestTarget.get())
                .isEqualTo("/%EC%B1%84%EC%9A%A9/%EB%B0%B1%EC%97%94%EB%93%9C?%EA%B2%80%EC%83%89=%EA%B0%9C%EB%B0%9C%EC%9E%90&level=%EC%A4%91%EA%B8%89");
    }

    @Test
    void prefixesSlashWhenUrlHasQueryWithoutPath() throws Exception {
        AtomicReference<String> requestTarget = new AtomicReference<>();
        JobPostingTextExtractor extractor = new JobPostingTextExtractor(
                mock(OpenAiResponsesClient.class),
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                target -> {
                    requestTarget.set(target.encodedRequestTarget());
                    return htmlResponse("<html><title>Job</title><body>Backend developer</body></html>");
                });

        extractor.extractUrl("http://jobs.example.com?job=123");

        assertThat(requestTarget.get()).isEqualTo("/?job=123");
    }

    private static JobPostingTextExtractor extractor() {
        return new JobPostingTextExtractor(mock(OpenAiResponsesClient.class));
    }

    /** 텍스트가 없는 유효한 1페이지 PDF — PDFBox 추출이 빈 문자열이 되어 OCR 폴백 경로로 진입한다. */
    private static byte[] blankPdfBytes() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void assertBlockedUrlValidation(String url) {
        assertBlocked(() -> JobPostingTextExtractor.validateSafeHttpUrl(url, InetAddress::getAllByName));
    }

    private static void assertBlocked(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException exception = (BusinessException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(exception.getMessage()).isEqualTo("허용되지 않는 URL입니다.");
    }

    private static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(response);
        }
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        sendJson(exchange, json, 200);
    }

    private static void sendJson(HttpExchange exchange, String json, int statusCode) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(response);
        }
    }

    private static JobPostingTextExtractor.FetchedHttpResponse htmlResponse(String html) {
        return new JobPostingTextExtractor.FetchedHttpResponse(
                200,
                Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                html.getBytes(StandardCharsets.UTF_8));
    }

    private static JobPostingTextExtractor.FetchedHttpResponse redirectResponse(String location) {
        return new JobPostingTextExtractor.FetchedHttpResponse(
                302,
                Map.of("Location", List.of(location)),
                new byte[0]);
    }
}
