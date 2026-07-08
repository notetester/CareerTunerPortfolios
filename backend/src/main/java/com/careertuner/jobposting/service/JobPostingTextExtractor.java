package com.careertuner.jobposting.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.service.BAnthropicClient;
import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;

@Service
public class JobPostingTextExtractor {

    private static final int MAX_EXTRACTED_TEXT_LENGTH = 120_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int URL_TIMEOUT_MILLIS = 5000;
    private static final int URL_MAX_BODY_SIZE = 1_000_000;
    private static final String USER_AGENT = "CareerTuner/1.0";
    private static final Logger log = LoggerFactory.getLogger(JobPostingTextExtractor.class);

    private final OpenAiResponsesClient openAiClient;
    // 공고 OCR 1차 폴백(Claude Vision). 생성자 오버로드(테스트 다수)를 건드리지 않으려 선택적 필드 주입.
    // 미주입(테스트)이면 null → 기존 OpenAI 경로만 사용한다.
    @Autowired(required = false)
    private BAnthropicClient anthropicClient;
    private final JobPostingAiWorkerClient aiWorkerClient;
    private final JobPostingFallbackPolicy fallbackPolicy;
    private final HostResolver hostResolver;
    private final HttpFetcher httpFetcher;

    @Autowired
    public JobPostingTextExtractor(OpenAiResponsesClient openAiClient,
                                   JobPostingAiWorkerClient aiWorkerClient,
                                   JobPostingFallbackPolicy fallbackPolicy) {
        this(openAiClient, aiWorkerClient, fallbackPolicy, InetAddress::getAllByName, new DirectSocketHttpFetcher());
    }

    JobPostingTextExtractor(OpenAiResponsesClient openAiClient) {
        this(openAiClient, JobPostingAiWorkerClient.disabled(), JobPostingFallbackPolicy.fromProperties(null), InetAddress::getAllByName, new DirectSocketHttpFetcher());
    }

    JobPostingTextExtractor(OpenAiResponsesClient openAiClient, HostResolver hostResolver, HttpFetcher httpFetcher) {
        this(openAiClient, JobPostingAiWorkerClient.disabled(), JobPostingFallbackPolicy.fromProperties(null), hostResolver, httpFetcher);
    }

    JobPostingTextExtractor(OpenAiResponsesClient openAiClient,
                            JobPostingAiWorkerClient aiWorkerClient,
                            JobPostingFallbackPolicy fallbackPolicy,
                            HostResolver hostResolver,
                            HttpFetcher httpFetcher) {
        this.openAiClient = openAiClient;
        this.aiWorkerClient = Objects.requireNonNull(aiWorkerClient, "aiWorkerClient");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
        this.httpFetcher = Objects.requireNonNull(httpFetcher, "httpFetcher");
    }

    public ExtractedPosting extractFile(StoredJobPostingFile file) {
        if ("PDF".equals(file.sourceType())) {
            String text = extractTextPdf(file);
            if (!text.isBlank()) {
                return new ExtractedPosting(file.sourceType(), file.fileReference(), null, limit(text), null);
            }
            // 텍스트가 없는 스캔/이미지 PDF → OCR 폴백.
            return ocrFallback(file, true);
        }
        return ocrFallback(file, false);
    }

    /**
     * 이미지/스캔 공고문 OCR: Claude(Haiku) Vision(1순위) → OpenAI Vision(정책 허용 시) → OCR 워커(OpenAI 미허용 시 폴백) → 추출 실패 안내.
     * Claude 를 1순위로, OpenAI 는 fallback policy 가 허용할 때 워커보다 우선한다. 원격 OCR 워커는 최후 폴백으로만 태운다(워커 강화는 후속).
     * 최종 안내 단계는 목업이 아니다 — 가짜 공고문은 잘못된 분석으로 이어지므로, 실패 시 사용자가 텍스트로 직접 입력하도록 안내한다.
     */
    private ExtractedPosting ocrFallback(StoredJobPostingFile file, boolean pdf) {
        // 1) Claude(Haiku) Vision — 1순위. 미설정/예외/빈 결과면 다음 단계로.
        //    빈 텍스트를 성공으로 반환하면 뒤의 좋은 폴백(OpenAI/워커)에 도달하지 못하므로, blank 는 "추출 실패"로 본다.
        if (anthropicClient != null && anthropicClient.configured()) {
            try {
                String text = pdf
                        ? anthropicClient.extractPdfText(file.bytes())
                        : anthropicClient.extractImageText(file.contentType(), file.bytes());
                if (text != null && !text.isBlank()) {
                    return new ExtractedPosting(file.sourceType(), file.fileReference(), null, limit(text), null);
                }
                log.warn("공고 OCR: Claude 빈 결과 → 다음 폴백 ({})", pdf ? "PDF" : "IMAGE");
            } catch (RuntimeException ex) {
                log.warn("공고 OCR: Claude 실패 → 다음 폴백 ({}): {}", pdf ? "PDF" : "IMAGE", ex.getMessage());
            }
        }
        // 2) OpenAI Vision — 폴백 정책이 허용할 때만. 예외는 상위로 전파하고(시스템 오류를 빈 결과로 삼키지 않음),
        //    성공했지만 빈 텍스트면 워커로 넘긴다.
        String stage = pdf ? JobPostingFallbackPolicy.STAGE_PDF_OCR : JobPostingFallbackPolicy.STAGE_IMAGE_OCR;
        if (fallbackPolicy.allowed(stage)) {
            OpenAiResponsesClient.TextPayload payload = pdf
                    ? openAiClient.extractPdfText(file.originalFilename(), file.bytes())
                    : openAiClient.extractImageText(file.contentType(), file.bytes());
            String text = payload.text();
            if (text != null && !text.isBlank()) {
                return new ExtractedPosting(file.sourceType(), file.fileReference(), null, limit(text), payload.usage());
            }
            log.warn("공고 OCR: OpenAI 빈 결과 → 워커 폴백 ({})", pdf ? "PDF" : "IMAGE");
        }
        // 3) OCR 워커 — Claude/OpenAI 가 텍스트를 못 준 경우의 폴백(설정 시). 미설정이면 Optional.empty → 실패 안내로.
        //    워커의 응답 오류/타임아웃/malformed 는 기존대로 fail-closed 로 상위 전파된다.
        Optional<ExtractedPosting> worker = aiWorkerClient.extractFile(file);
        if (worker.isPresent()) {
            return worker.get();
        }
        // 4) 최종: 추출 실패 안내(목업 아님 — 가짜 공고문은 잘못된 분석을 부른다).
        return new ExtractedPosting(file.sourceType(), file.fileReference(), null, "", null,
                pdf ? "IMAGE_PDF_OCR" : "IMAGE_OCR",
                0,
                "FAILED",
                null,
                null,
                false,
                "OCR providers unavailable (Claude/OpenAI/worker). 공고문을 텍스트로 직접 입력해 주세요.");
    }

    public ExtractedPosting extractUrl(String url) {
        ValidatedHttpUrl validatedUrl = validateSafeHttpUrlForFetch(url, hostResolver);
        try {
            Document document = fetchDocument(validatedUrl);
            document.select("script, style, noscript, svg").remove();
            String title = document.title() == null ? "" : document.title().trim();
            String body = document.body() == null ? "" : document.body().text().trim();
            String text = (title.isBlank() ? body : title + "\n\n" + body).trim();
            if (text.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "URL에서 공고문 텍스트를 추출하지 못했습니다.");
            }
            String limitedText = limit(text);
            return aiWorkerClient.extractText("URL", validatedUrl.normalizedUrl(), validatedUrl.normalizedUrl(), limitedText)
                    .orElseGet(() -> new ExtractedPosting("URL", validatedUrl.normalizedUrl(), validatedUrl.normalizedUrl(), limitedText, null));
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "URL을 불러오지 못했습니다.");
        }
    }

    private Document fetchDocument(ValidatedHttpUrl initialUrl) throws IOException {
        ValidatedHttpUrl currentUrl = initialUrl;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            FetchedHttpResponse response = fetch(currentUrl);
            int statusCode = response.statusCode();
            if (isRedirect(statusCode)) {
                if (redirectCount == MAX_REDIRECTS) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "URL 리다이렉트가 너무 많습니다.");
                }
                currentUrl = validateRedirectUrl(currentUrl, response.firstHeader("Location"));
                continue;
            }
            if (statusCode < 200 || statusCode >= 400) {
                throw new IOException("Unexpected HTTP status: " + statusCode);
            }
            return Jsoup.parse(
                    new ByteArrayInputStream(response.body()),
                    charsetFromContentType(response.firstHeader("Content-Type")),
                    currentUrl.normalizedUrl());
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "URL 리다이렉트가 너무 많습니다.");
    }

    private FetchedHttpResponse fetch(ValidatedHttpUrl url) throws IOException {
        IOException lastException = null;
        for (InetAddress address : url.addresses()) {
            try {
                return httpFetcher.fetch(new HttpRequestTarget(url.uri(), url.normalizedUrl(), address));
            } catch (IOException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("No validated addresses available");
    }

    private String extractTextPdf(StoredJobPostingFile file) {
        try (PDDocument document = Loader.loadPDF(file.bytes())) {
            return new PDFTextStripper().getText(document).trim();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PDF 텍스트를 추출하지 못했습니다.");
        }
    }

    static String validateSafeHttpUrl(String url) {
        return validateSafeHttpUrl(url, InetAddress::getAllByName);
    }

    static String validateSafeHttpUrl(String url, HostResolver resolver) {
        return validateSafeHttpUrlForFetch(url, resolver).normalizedUrl();
    }

    private static ValidatedHttpUrl validateSafeHttpUrlForFetch(String url, HostResolver resolver) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL을 입력해 주세요.");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "http 또는 https URL만 사용할 수 있습니다.");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL 형식이 올바르지 않습니다.");
            }
            return new ValidatedHttpUrl(uri, uri.toString(), validateSafeHost(host, resolver));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL 형식이 올바르지 않습니다.");
        }
    }

    private static List<InetAddress> validateSafeHost(String host, HostResolver resolver) {
        if (isLocalhostName(host)) {
            throw blockedUrlException();
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL의 호스트를 확인할 수 없습니다.");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL의 호스트를 확인할 수 없습니다.");
        }
        for (InetAddress address : addresses) {
            if (isUnsafeAddress(address)) {
                throw blockedUrlException();
            }
        }
        return List.of(addresses);
    }

    private ValidatedHttpUrl validateRedirectUrl(ValidatedHttpUrl currentUrl, String location) {
        if (location == null || location.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "URL 리다이렉트 주소가 올바르지 않습니다.");
        }
        try {
            URI resolvedUri = currentUrl.uri().resolve(location.trim());
            return validateSafeHttpUrlForFetch(resolvedUri.toString(), hostResolver);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "URL 리다이렉트 주소가 올바르지 않습니다.");
        }
    }

    private static String charsetFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String[] parameters = contentType.split(";");
        for (int i = 1; i < parameters.length; i++) {
            String parameter = parameters[i].trim();
            int equalsIndex = parameter.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String name = parameter.substring(0, equalsIndex).trim();
            if (!"charset".equalsIgnoreCase(name)) {
                continue;
            }
            String value = parameter.substring(equalsIndex + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean isLocalhostName(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        return "localhost".equals(normalizedHost)
                || normalizedHost.endsWith(".localhost")
                || "localhost.localdomain".equals(normalizedHost);
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || isSpecialIpv4Address(address)
                || isMetadataAddress(address)
                || isCarrierGradeNatAddress(address)
                || isIpv6UniqueLocalAddress(address);
    }

    private static boolean isSpecialIpv4Address(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        return isIpv4InRange(address, 0x00000000L, 8)
                || isIpv4InRange(address, 0x7f000000L, 8)
                || isIpv4InRange(address, 0xc0000000L, 24)
                || isIpv4InRange(address, 0xc0000200L, 24)
                || isIpv4InRange(address, 0xc0586300L, 24)
                || isIpv4InRange(address, 0xc6120000L, 15)
                || isIpv4InRange(address, 0xc6336400L, 24)
                || isIpv4InRange(address, 0xcb007100L, 24)
                || isIpv4InRange(address, 0xe0000000L, 4)
                || isIpv4InRange(address, 0xf0000000L, 4)
                || isIpv4InRange(address, 0xffffffffL, 32);
    }

    private static boolean isMetadataAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        return unsigned(bytes[0]) == 169
                && unsigned(bytes[1]) == 254
                && unsigned(bytes[2]) == 169
                && unsigned(bytes[3]) == 254;
    }

    private static boolean isCarrierGradeNatAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        return unsigned(bytes[0]) == 100
                && unsigned(bytes[1]) >= 64
                && unsigned(bytes[1]) <= 127;
    }

    private static boolean isIpv6UniqueLocalAddress(InetAddress address) {
        return address instanceof Inet6Address
                && (unsigned(address.getAddress()[0]) & 0xfe) == 0xfc;
    }

    private static boolean isIpv4InRange(InetAddress address, long network, int prefixLength) {
        long value = ipv4ToLong(address);
        long mask = prefixLength == 0 ? 0L : (0xffffffffL << (32 - prefixLength)) & 0xffffffffL;
        return (value & mask) == (network & mask);
    }

    private static long ipv4ToLong(InetAddress address) {
        byte[] bytes = address.getAddress();
        return ((long) unsigned(bytes[0]) << 24)
                | ((long) unsigned(bytes[1]) << 16)
                | ((long) unsigned(bytes[2]) << 8)
                | unsigned(bytes[3]);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static BusinessException blockedUrlException() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않는 URL입니다.");
    }

    private String limit(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_EXTRACTED_TEXT_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_EXTRACTED_TEXT_LENGTH);
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(name)) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }

    private record ValidatedHttpUrl(URI uri, String normalizedUrl, List<InetAddress> addresses) {
        private ValidatedHttpUrl {
            addresses = List.copyOf(addresses);
        }
    }

    record HttpRequestTarget(URI uri, String normalizedUrl, InetAddress address) {
        String encodedRequestTarget() {
            return DirectSocketHttpFetcher.requestTarget(uri);
        }
    }

    record FetchedHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        FetchedHttpResponse {
            headers = headers == null ? Map.of() : headers;
            body = body == null ? new byte[0] : body;
        }

        String firstHeader(String name) {
            return JobPostingTextExtractor.firstHeader(headers, name);
        }
    }

    private static final class DirectSocketHttpFetcher implements HttpFetcher {

        @Override
        public FetchedHttpResponse fetch(HttpRequestTarget target) throws IOException {
            try (Socket socket = openSocket(target)) {
                OutputStream output = socket.getOutputStream();
                output.write(buildRequest(target.uri()).getBytes(StandardCharsets.US_ASCII));
                output.flush();

                InputStream input = socket.getInputStream();
                String statusLine = readHeaderLine(input);
                int statusCode = parseStatusCode(statusLine);
                Map<String, List<String>> headers = readHeaders(input);
                byte[] body = shouldReadBody(statusCode) ? readBody(input, headers) : new byte[0];
                return new FetchedHttpResponse(statusCode, headers, body);
            }
        }

        private static Socket openSocket(HttpRequestTarget target) throws IOException {
            URI uri = target.uri();
            int port = effectivePort(uri);
            Socket rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(target.address(), port), URL_TIMEOUT_MILLIS);
            rawSocket.setSoTimeout(URL_TIMEOUT_MILLIS);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return rawSocket;
            }

            try {
                String host = asciiHost(uri.getHost());
                SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(rawSocket, host, port, true);
                SSLParameters parameters = sslSocket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                if (!isIpLiteral(host)) {
                    parameters.setServerNames(List.of(new SNIHostName(host)));
                }
                sslSocket.setSSLParameters(parameters);
                sslSocket.startHandshake();
                return sslSocket;
            } catch (IOException ex) {
                rawSocket.close();
                throw ex;
            } catch (RuntimeException ex) {
                rawSocket.close();
                throw ex;
            }
        }

        private static String buildRequest(URI uri) {
            return "GET " + requestTarget(uri) + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader(uri) + "\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n"
                    + "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
        }

        private static String requestTarget(URI uri) {
            String asciiUrl = uri.toASCIIString();
            String authority = uri.getRawAuthority();
            if (authority != null) {
                String authorityNeedle = "//" + authority;
                int authorityStart = asciiUrl.indexOf(authorityNeedle);
                if (authorityStart >= 0) {
                    int targetStart = authorityStart + authorityNeedle.length();
                    String target = asciiUrl.substring(targetStart);
                    int fragmentIndex = target.indexOf('#');
                    if (fragmentIndex >= 0) {
                        target = target.substring(0, fragmentIndex);
                    }
                    if (target.startsWith("?")) {
                        target = "/" + target;
                    }
                    return target.isBlank() ? "/" : target;
                }
            }
            String rawPath = uri.getRawPath();
            return rawPath == null || rawPath.isBlank() ? "/" : rawPath;
        }

        private static String hostHeader(URI uri) {
            String host = asciiHost(uri.getHost());
            if (host.contains(":") && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            int port = uri.getPort();
            if (port != -1 && port != defaultPort(uri.getScheme())) {
                return host + ":" + port;
            }
            return host;
        }

        private static int effectivePort(URI uri) {
            int port = uri.getPort();
            return port == -1 ? defaultPort(uri.getScheme()) : port;
        }

        private static int defaultPort(String scheme) {
            return "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }

        private static String asciiHost(String host) {
            if (host.contains(":")) {
                return host;
            }
            return IDN.toASCII(host);
        }

        private static boolean isIpLiteral(String host) {
            if (host.contains(":")) {
                return true;
            }
            for (int i = 0; i < host.length(); i++) {
                char c = host.charAt(i);
                if (!Character.isDigit(c) && c != '.') {
                    return false;
                }
            }
            return true;
        }

        private static int parseStatusCode(String statusLine) throws IOException {
            String[] parts = statusLine.split(" ", 3);
            if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
                throw new IOException("Invalid HTTP status line: " + statusLine);
            }
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid HTTP status code: " + statusLine, ex);
            }
        }

        private static Map<String, List<String>> readHeaders(InputStream input) throws IOException {
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            int totalHeaderCharacters = 0;
            while (true) {
                String line = readHeaderLine(input);
                if (line.isEmpty()) {
                    return headers;
                }
                totalHeaderCharacters += line.length();
                if (totalHeaderCharacters > 64_000) {
                    throw new IOException("HTTP response headers too large");
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex <= 0) {
                    continue;
                }
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            }
        }

        private static String readHeaderLine(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            boolean sawLineBreak = false;
            int next;
            while ((next = input.read()) != -1) {
                if (next == '\n') {
                    sawLineBreak = true;
                    break;
                }
                if (next != '\r') {
                    line.write(next);
                }
                if (line.size() > 8_000) {
                    throw new IOException("HTTP response header line too large");
                }
            }
            if (!sawLineBreak && line.size() == 0) {
                throw new EOFException("Unexpected end of HTTP response");
            }
            return line.toString(StandardCharsets.ISO_8859_1);
        }

        private static boolean shouldReadBody(int statusCode) {
            return (statusCode < 100 || statusCode >= 200)
                    && statusCode != 204
                    && statusCode != 304;
        }

        private static byte[] readBody(InputStream input, Map<String, List<String>> headers) throws IOException {
            String transferEncoding = firstHeader(headers, "Transfer-Encoding");
            if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
                return readChunkedBody(input);
            }

            String contentLength = firstHeader(headers, "Content-Length");
            if (contentLength != null) {
                try {
                    long length = Long.parseLong(contentLength.trim());
                    if (length >= 0) {
                        return readFixedLength(input, length);
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to close-delimited body handling.
                }
            }
            return readUntilEof(input);
        }

        private static byte[] readFixedLength(InputStream input, long length) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream((int) Math.min(length, URL_MAX_BODY_SIZE));
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0 && body.size() < URL_MAX_BODY_SIZE) {
                int bytesToRead = (int) Math.min(Math.min(buffer.length, remaining), URL_MAX_BODY_SIZE - body.size());
                int read = input.read(buffer, 0, bytesToRead);
                if (read == -1) {
                    throw new EOFException("Unexpected end of HTTP response body");
                }
                body.write(buffer, 0, read);
                remaining -= read;
            }
            return body.toByteArray();
        }

        private static byte[] readUntilEof(InputStream input) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (body.size() < URL_MAX_BODY_SIZE) {
                int bytesToRead = Math.min(buffer.length, URL_MAX_BODY_SIZE - body.size());
                int read = input.read(buffer, 0, bytesToRead);
                if (read == -1) {
                    break;
                }
                body.write(buffer, 0, read);
            }
            return body.toByteArray();
        }

        private static byte[] readChunkedBody(InputStream input) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (true) {
                String sizeLine = readHeaderLine(input);
                int extensionIndex = sizeLine.indexOf(';');
                String sizeText = extensionIndex == -1 ? sizeLine : sizeLine.substring(0, extensionIndex);
                long chunkSize;
                try {
                    chunkSize = Long.parseLong(sizeText.trim(), 16);
                } catch (NumberFormatException ex) {
                    throw new IOException("Invalid HTTP chunk size: " + sizeLine, ex);
                }
                if (chunkSize == 0) {
                    while (!readHeaderLine(input).isEmpty()) {
                        // Discard trailers.
                    }
                    return body.toByteArray();
                }

                long remaining = chunkSize;
                while (remaining > 0 && body.size() < URL_MAX_BODY_SIZE) {
                    int bytesToRead = (int) Math.min(Math.min(buffer.length, remaining), URL_MAX_BODY_SIZE - body.size());
                    int read = input.read(buffer, 0, bytesToRead);
                    if (read == -1) {
                        throw new EOFException("Unexpected end of HTTP chunk");
                    }
                    body.write(buffer, 0, read);
                    remaining -= read;
                }
                if (remaining > 0) {
                    return body.toByteArray();
                }
                readChunkTerminator(input);
            }
        }

        private static void readChunkTerminator(InputStream input) throws IOException {
            int first = input.read();
            int second = input.read();
            if (first == '\r' && second == '\n') {
                return;
            }
            if (first == '\n') {
                return;
            }
            throw new IOException("Invalid HTTP chunk terminator");
        }
    }

    public record ExtractedPosting(
            String sourceType,
            String uploadedFileUrl,
            String originalText,
            String extractedText,
            OpenAiResponsesClient.Usage usage,
            String extractionStrategy,
            Integer qualityScore,
            String qualityStatus,
            String qualityReportJson,
            String modelVersionsJson,
            boolean fallbackEligible,
            String fallbackReason
    ) {
        public ExtractedPosting(String sourceType,
                                String uploadedFileUrl,
                                String originalText,
                                String extractedText,
                                OpenAiResponsesClient.Usage usage) {
            this(sourceType, uploadedFileUrl, originalText, extractedText, usage,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null);
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    @FunctionalInterface
    interface HttpFetcher {
        FetchedHttpResponse fetch(HttpRequestTarget target) throws IOException;
    }
}
