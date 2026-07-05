package com.careertuner.support.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.support.dto.GithubReadmeResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 가이드 포폴 스텝의 GitHub 링크 → README 원문 텍스트.
 * GET https://api.github.com/repos/{owner}/{repo}/readme (Accept: raw) — 공개 레포는 무인증,
 * IP당 시간 60회 제한(데모 규모엔 충분). README 없음 = 404(비공개 레포와 동일 응답이라 구분 안 함).
 */
@Slf4j
@Service
public class GithubReadmeService {

    private static final int MAX_TEXT_CHARS = 12000;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public GithubReadmeResponse fetchReadme(String repoUrl) {
        RepoRef ref = parse(repoUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://api.github.com/repos/" + ref.owner() + "/" + ref.repo() + "/readme"))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github.raw+json")
                    .header("User-Agent", "CareerTuner")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return GithubReadmeResponse.success(truncate(response.body()));
            }
            if (status == 404) {
                return GithubReadmeResponse.failure("NOT_FOUND");
            }
            // GitHub 은 1차 rate limit 도 403 으로 내려주는 경우가 있어 429 와 함께 같은 카피로 묶는다.
            if (status == 403 || status == 429) {
                return GithubReadmeResponse.failure("RATE_LIMITED");
            }
            log.warn("GitHub README 조회 실패 owner={} repo={} status={}", ref.owner(), ref.repo(), status);
            return GithubReadmeResponse.failure("FETCH_FAILED");
        } catch (IOException ex) {
            log.warn("GitHub README 조회 I/O 실패 owner={} repo={}: {}", ref.owner(), ref.repo(), ex.getMessage());
            return GithubReadmeResponse.failure("FETCH_FAILED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return GithubReadmeResponse.failure("FETCH_FAILED");
        }
    }

    private record RepoRef(String owner, String repo) {}

    /** github.com/{owner}/{repo} 변형(스킴 생략·트레일링 슬래시·.git·서브경로)을 owner/repo 로 정규화. */
    private RepoRef parse(String repoUrl) {
        String raw = repoUrl == null ? "" : repoUrl.trim();
        if (raw.isEmpty()) {
            throw invalid();
        }
        String withScheme = raw.matches("(?i)^https?://.*") ? raw : "https://" + raw;
        URI uri;
        try {
            uri = URI.create(withScheme);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        String host = uri.getHost();
        if (host == null) {
            throw invalid();
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        if (!host.equals("github.com")) {
            throw invalid();
        }
        String path = uri.getPath();
        if (path == null) {
            throw invalid();
        }
        String[] segments = Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
        if (segments.length < 2) {
            throw invalid();
        }
        String owner = segments[0];
        String repo = segments[1];
        if (repo.toLowerCase(Locale.ROOT).endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        if (!owner.matches("[A-Za-z0-9-]+") || !repo.matches("[A-Za-z0-9._-]+")) {
            throw invalid();
        }
        return new RepoRef(owner, repo);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "GitHub 저장소 주소를 입력해주세요");
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }
}
