package com.careertuner.applicationcase.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApplicationCaseExtractionQualityGate {

    public static final String QUALITY_PASS = "PASS";
    public static final String QUALITY_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    public static final String QUALITY_FAILED = "FAILED";

    private static final int PASS_SCORE = 70;
    private static final int REVIEW_SCORE = 40;
    private static final int MIN_PASS_TEXT_LENGTH = 500;
    private static final int MIN_USABLE_TEXT_LENGTH = 200;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LINE_WHITESPACE = Pattern.compile("[ \\t]+");
    private static final Pattern ISOLATED_JAMO = Pattern.compile("[㄰-㆏]");
    private static final String HEADER_TRIM_CHARS = " \t·•◦▪‣▶▷●○■□*-–—:：.|";
    private static final List<String> INLINE_HEADER_SEPARATORS = List.of(":", "：", "-", "–", "—");
    private static final String ALLOWED_SUSPECT_SYMBOLS = "·/,.()[]{}%~:;-+–—'\"!?&•…";
    private static final List<SectionKeywordGroup> SECTION_KEYWORDS = List.of(
            new SectionKeywordGroup("company", List.of("회사", "기업", "company", "organization")),
            new SectionKeywordGroup("role", List.of("직무", "포지션", "채용", "job", "role", "position", "hiring")),
            new SectionKeywordGroup("duties", List.of("업무", "담당", "responsibilities", "duties", "what you will do")),
            new SectionKeywordGroup("qualifications", List.of("자격", "요건", "requirements", "qualifications", "minimum")),
            new SectionKeywordGroup("skills", List.of("기술", "스킬", "skills", "stack", "java", "spring", "react", "python")),
            new SectionKeywordGroup("employment", List.of("근무", "고용", "employment", "location", "benefits", "salary")),
            new SectionKeywordGroup("deadline", List.of("마감", "접수", "deadline", "apply", "application"))
    );
    private static final List<String> SECTION_HEADERS = List.of(
            "Company",
            "Role",
            "Position",
            "Responsibilities",
            "Duties",
            "What you will do",
            "Qualifications",
            "Requirements",
            "Skills",
            "Employment",
            "Benefits",
            "Apply",
            "Deadline",
            "모집부문",
            "담당업무",
            "주요업무",
            "업무내용",
            "자격요건",
            "지원자격",
            "우대사항",
            "기술스택",
            "근무조건",
            "복리후생",
            "전형절차",
            "접수방법",
            "회사소개",
            "지원방법",
            "홈페이지 지원",
            "근무지역",
            "근무형태",
            "공개채용",
            "경력채용",
            "함께할업무",
            "함께할 업무",
            "업무예요",
            "입사지원",
            "이력서",
            "포지션"
    );
    private static final List<String> CRITICAL_SECTION_HEADERS = List.of(
            "담당업무",
            "주요업무",
            "업무내용",
            "함께할업무",
            "함께할 업무",
            "responsibilities",
            "duties",
            "what you will do"
    );
    private static final List<String> USEFUL_WORK_TOKENS = List.of(
            "개발",
            "운영",
            "설계",
            "구축",
            "개선",
            "관리",
            "분석",
            "협업",
            "구현",
            "담당",
            "수행",
            "기획",
            "build",
            "operate",
            "develop",
            "design",
            "manage",
            "implement",
            "maintain",
            "api",
            "react",
            "vue",
            "typescript",
            "javascript",
            "spring",
            "node",
            "python",
            "java",
            "kotlin",
            "aws",
            "sql",
            "kubernetes",
            "docker",
            "mysql"
    );
    private static final List<String> SECTION_HEADER_NORMS =
            SECTION_HEADERS.stream().map(ApplicationCaseExtractionQualityGate::normalizeHeaderToken).toList();
    private static final List<String> CRITICAL_SECTION_HEADER_NORMS =
            CRITICAL_SECTION_HEADERS.stream().map(ApplicationCaseExtractionQualityGate::normalizeHeaderToken).toList();

    private final ObjectMapper objectMapper;

    public ApplicationCaseExtractionQualityGate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public QualityGateResult evaluate(String sourceType, ExtractedPosting extractedPosting, String postingText) {
        String normalizedText = normalize(postingText);
        int textLength = normalizedText.length();
        List<String> sectionHints = sectionHints(normalizedText);
        CriticalSectionMetrics criticalSection = criticalSectionMetrics(postingText);
        List<String> warnings = warnings(normalizedText, textLength, sectionHints.size());
        int score = score(normalizedText, textLength, sectionHints.size(), warnings.size());
        String qualityStatus = qualityStatus(score, textLength, sectionHints.size());
        if (criticalSection.contentInsufficient()) {
            warnings.add("critical_section_content_insufficient");
            if (QUALITY_PASS.equals(qualityStatus)) {
                qualityStatus = QUALITY_REVIEW_REQUIRED;
            }
        }
        String strategy = strategy(sourceType, extractedPosting);
        Map<String, Object> metrics = metrics(normalizedText, textLength, sectionHints.size(), criticalSection);
        String reportJson = toJson(report(strategy, score, qualityStatus, metrics, warnings, sectionHints));
        String modelVersionsJson = toJson(Map.of(
                "qualityGate", "rules-v1",
                "fallbackPolicy", "openai-disabled-by-default"));

        return new QualityGateResult(
                strategy,
                score,
                qualityStatus,
                reportJson,
                modelVersionsJson,
                false,
                "OpenAI fallback is disabled by default.");
    }

    private static int score(String text, int textLength, int sectionCount, int warningCount) {
        if (textLength == 0) {
            return 0;
        }
        int score = 20;
        if (textLength >= MIN_PASS_TEXT_LENGTH) {
            score += 35;
        } else if (textLength >= MIN_USABLE_TEXT_LENGTH) {
            score += 25;
        } else if (textLength >= 100) {
            score += 10;
        }
        score += Math.min(sectionCount * 12, 35);
        if (hasJobSignal(text)) {
            score += 10;
        }
        score -= Math.min(warningCount * 8, 24);
        return Math.max(0, Math.min(100, score));
    }

    private static String qualityStatus(int score, int textLength, int sectionCount) {
        if (textLength < MIN_USABLE_TEXT_LENGTH || score < REVIEW_SCORE) {
            return QUALITY_FAILED;
        }
        if (score >= PASS_SCORE && textLength >= MIN_PASS_TEXT_LENGTH && sectionCount >= 2) {
            return QUALITY_PASS;
        }
        return QUALITY_REVIEW_REQUIRED;
    }

    private static List<String> sectionHints(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        for (SectionKeywordGroup group : SECTION_KEYWORDS) {
            if (group.keywords().stream().anyMatch(lower::contains)) {
                hints.add(group.name());
            }
        }
        return hints;
    }

    private static List<String> warnings(String text, int textLength, int sectionCount) {
        List<String> warnings = new ArrayList<>();
        if (textLength == 0) {
            warnings.add("empty_text");
            return warnings;
        }
        if (textLength < MIN_USABLE_TEXT_LENGTH) {
            warnings.add("text_too_short");
        }
        if (textLength < MIN_PASS_TEXT_LENGTH) {
            warnings.add("text_short_for_auto_analysis");
        }
        if (sectionCount < 2) {
            warnings.add("section_keywords_insufficient");
        }
        if (text.indexOf('\uFFFD') >= 0) {
            warnings.add("replacement_character_detected");
        }
        if (symbolRatio(text) > 0.35) {
            warnings.add("high_symbol_noise");
        }
        return warnings;
    }

    private static Map<String, Object> metrics(String text,
                                               int textLength,
                                               int sectionCount,
                                               CriticalSectionMetrics criticalSection) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("textLength", textLength);
        metrics.put("wordCount", wordCount(text));
        metrics.put("sectionKeywordCount", sectionCount);
        metrics.put("criticalSectionExists", criticalSection.exists());
        metrics.put("criticalSectionUsefulLineCount", criticalSection.usefulLineCount());
        metrics.put("criticalSectionMeaningfulCharCount", criticalSection.meaningfulCharCount());
        metrics.put("criticalSectionSuspectLineRatio", criticalSection.suspectLineRatio());
        metrics.put("symbolRatio", symbolRatio(text));
        return metrics;
    }

    private static Map<String, Object> report(String strategy,
                                              int score,
                                              String status,
                                              Map<String, Object> metrics,
                                              List<String> warnings,
                                              List<String> sectionHints) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("strategy", strategy);
        report.put("qualityScore", score);
        report.put("qualityStatus", status);
        report.put("metrics", metrics);
        report.put("warnings", warnings);
        report.put("sectionHints", sectionHints);
        report.put("fallbackEligible", false);
        return report;
    }

    private static String strategy(String sourceType, ExtractedPosting extractedPosting) {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PDF" -> "PDF_TEXT";
            case "IMAGE" -> "IMAGE_OCR";
            case "URL" -> "HTML_TEXT";
            case "TEXT", "MANUAL" -> "TEXT_DIRECT";
            default -> extractedPosting == null ? "TEXT_DIRECT" : extractedPosting.sourceType();
        };
    }

    private static boolean hasJobSignal(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("채용")
                || lower.contains("직무")
                || lower.contains("지원")
                || lower.contains("hiring")
                || lower.contains("apply")
                || lower.contains("responsibilities");
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return WHITESPACE.split(text.trim()).length;
    }

    private static double symbolRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        long symbols = text.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
                .count();
        return Math.round((symbols / (double) text.length()) * 1000.0) / 1000.0;
    }

    private static CriticalSectionMetrics criticalSectionMetrics(String text) {
        List<String> lines = logicalSectionLines(text);
        List<String> body = null;
        for (int index = 0; index < lines.size(); index++) {
            String norm = normalizeHeaderToken(lines.get(index));
            if (isCriticalSectionHeader(norm)) {
                body = new ArrayList<>();
                for (int next = index + 1; next < lines.size(); next++) {
                    String following = lines.get(next);
                    if (isHeaderLikeLine(following)) {
                        break;
                    }
                    body.add(following);
                }
                break;
            }
        }
        if (body == null) {
            return CriticalSectionMetrics.empty();
        }
        int useful = 0;
        int suspect = 0;
        int meaningful = 0;
        for (String line : body) {
            if (isUsefulWorkLine(line)) {
                useful++;
            }
            if (isSuspectLine(line)) {
                suspect++;
            }
            meaningful += meaningfulCharCount(line);
        }
        double suspectRatio = body.isEmpty()
                ? 0.0
                : Math.round((suspect / (double) body.size()) * 10000.0) / 10000.0;
        return new CriticalSectionMetrics(true, useful, meaningful, suspectRatio);
    }

    private static List<String> logicalSectionLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.replace('\r', '\n').replace('\u00A0', ' ').split("\n")) {
            String line = LINE_WHITESPACE.matcher(rawLine).replaceAll(" ").trim();
            if (line.isBlank()) {
                continue;
            }
            lines.addAll(splitInlineSectionHeader(line));
        }
        return lines;
    }

    private static List<String> splitInlineSectionHeader(String line) {
        String stripped = line.trim();
        if (stripped.isEmpty()) {
            return List.of();
        }
        for (String separator : INLINE_HEADER_SEPARATORS) {
            int separatorIndex = stripped.indexOf(separator);
            if (separatorIndex <= 0) {
                continue;
            }
            String prefix = stripped.substring(0, separatorIndex);
            String prefixNorm = normalizeHeaderToken(prefix);
            if (!isSectionHeader(prefixNorm)) {
                continue;
            }
            String body = stripped.substring(separatorIndex + separator.length()).trim();
            if (body.isEmpty()) {
                return List.of(prefix.trim());
            }
            return List.of(prefix.trim(), body);
        }
        return List.of(stripped);
    }

    private static boolean isHeaderLikeLine(String line) {
        return isSectionHeader(normalizeHeaderToken(line));
    }

    private static boolean isSectionHeader(String norm) {
        return norm != null
                && !norm.isBlank()
                && norm.length() <= 16
                && SECTION_HEADER_NORMS.contains(norm);
    }

    private static boolean isCriticalSectionHeader(String norm) {
        return norm != null
                && !norm.isBlank()
                && norm.length() <= 16
                && CRITICAL_SECTION_HEADER_NORMS.contains(norm);
    }

    private static String normalizeHeaderToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && HEADER_TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && HEADER_TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return WHITESPACE.matcher(value.substring(start, end)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean isUsefulWorkLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return USEFUL_WORK_TOKENS.stream().anyMatch(lower::contains);
    }

    private static boolean isSuspectLine(String line) {
        String stripped = line == null ? "" : line.trim();
        if (stripped.isEmpty()) {
            return false;
        }
        if (ISOLATED_JAMO.matcher(stripped).find()) {
            return true;
        }
        for (int offset = 0; offset < stripped.length();) {
            int codePoint = stripped.codePointAt(offset);
            if (Character.isWhitespace(codePoint)
                    || Character.isLetterOrDigit(codePoint)
                    || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            if (ALLOWED_SUSPECT_SYMBOLS.indexOf(codePoint) >= 0) {
                offset += Character.charCount(codePoint);
                continue;
            }
            return true;
        }
        String[] tokens = WHITESPACE.split(stripped);
        if (tokens.length >= 3) {
            long shortTokens = List.of(tokens).stream().filter(token -> token.length() <= 2).count();
            if (shortTokens / (double) tokens.length > 0.7) {
                return true;
            }
        }
        return meaningfulCharCount(stripped) <= 2;
    }

    private static int meaningfulCharCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) text.codePoints()
                .filter(ch -> Character.isLetterOrDigit(ch) || (ch >= 0xAC00 && ch <= 0xD7A3))
                .count();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize extraction quality report.", ex);
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    public record QualityGateResult(
            String extractionStrategy,
            int qualityScore,
            String qualityStatus,
            String qualityReportJson,
            String modelVersionsJson,
            boolean fallbackEligible,
            String fallbackReason
    ) {
        public boolean pass() {
            return QUALITY_PASS.equals(qualityStatus);
        }

        public boolean reviewRequired() {
            return QUALITY_REVIEW_REQUIRED.equals(qualityStatus);
        }

        public boolean failed() {
            return QUALITY_FAILED.equals(qualityStatus);
        }
    }

    private record SectionKeywordGroup(String name, List<String> keywords) {
    }

    private record CriticalSectionMetrics(
            boolean exists,
            int usefulLineCount,
            int meaningfulCharCount,
            double suspectLineRatio
    ) {
        private static CriticalSectionMetrics empty() {
            return new CriticalSectionMetrics(false, 0, 0, 0.0);
        }

        private boolean contentInsufficient() {
            return exists
                    && usefulLineCount == 0
                    && (meaningfulCharCount < 30 || suspectLineRatio >= 0.5);
        }
    }
}
