package com.careertuner.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class JobPostingTextExtractorTest {

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
