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
    private static final List<SectionKeywordGroup> SECTION_KEYWORDS = List.of(
            new SectionKeywordGroup("company", List.of("회사", "기업", "company", "organization")),
            new SectionKeywordGroup("role", List.of("직무", "포지션", "채용", "job", "role", "position", "hiring")),
            new SectionKeywordGroup("duties", List.of("업무", "담당", "responsibilities", "duties", "what you will do")),
            new SectionKeywordGroup("qualifications", List.of("자격", "요건", "requirements", "qualifications", "minimum")),
            new SectionKeywordGroup("skills", List.of("기술", "스킬", "skills", "stack", "java", "spring", "react", "python")),
            new SectionKeywordGroup("employment", List.of("근무", "고용", "employment", "location", "benefits", "salary")),
            new SectionKeywordGroup("deadline", List.of("마감", "접수", "deadline", "apply", "application"))
    );

    private final ObjectMapper objectMapper;

    public ApplicationCaseExtractionQualityGate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public QualityGateResult evaluate(String sourceType, ExtractedPosting extractedPosting, String postingText) {
        String normalizedText = normalize(postingText);
        int textLength = normalizedText.length();
        List<String> sectionHints = sectionHints(normalizedText);
        List<String> warnings = warnings(normalizedText, textLength, sectionHints.size());
        int score = score(normalizedText, textLength, sectionHints.size(), warnings.size());
        String qualityStatus = qualityStatus(score, textLength, sectionHints.size());
        String strategy = strategy(sourceType, extractedPosting);
        Map<String, Object> metrics = metrics(normalizedText, textLength, sectionHints.size());
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

    private static Map<String, Object> metrics(String text, int textLength, int sectionCount) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("textLength", textLength);
        metrics.put("wordCount", wordCount(text));
        metrics.put("sectionKeywordCount", sectionCount);
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
}
