package com.careertuner.applicationcase.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class BJobSentenceClassifier {

    public static final String SECTION_HEADER = "SECTION_HEADER";
    public static final String RESPONSIBILITY = "RESPONSIBILITY";
    public static final String REQUIRED = "REQUIRED";
    public static final String PREFERRED = "PREFERRED";
    public static final String QUALIFICATION = "QUALIFICATION";
    public static final String TECH_STACK = "TECH_STACK";
    public static final String EMPLOYMENT_CONDITION = "EMPLOYMENT_CONDITION";
    public static final String BENEFIT = "BENEFIT";
    public static final String APPLICATION_INFO = "APPLICATION_INFO";
    public static final String COMPANY_INFO = "COMPANY_INFO";
    public static final String OTHER = "OTHER";

    private static final Set<String> KNOWN_SKILLS = Set.of(
            "java", "spring", "spring boot", "mybatis", "jpa", "sql", "mysql", "postgresql",
            "redis", "kafka", "rabbitmq", "docker", "kubernetes", "aws", "azure", "gcp",
            "linux", "react", "typescript", "javascript", "vue", "node.js", "python",
            "django", "fastapi", "rest", "graphql", "git", "ci/cd", "jenkins", "github actions",
            "junit", "mockito", "testing", "monitoring", "prometheus", "grafana", "elasticsearch",
            "security", "oauth", "jwt", "agile", "scrum", "figma");

    public Classification classify(String text) {
        if (isBlank(text)) {
            return new Classification(List.of());
        }
        List<LabeledSentence> rows = new ArrayList<>();
        String currentSection = OTHER;
        int order = 1;
        for (String sentence : splitSentences(text)) {
            String label = label(sentence, currentSection);
            if (SECTION_HEADER.equals(label)) {
                currentSection = sectionLabel(sentence);
            }
            rows.add(new LabeledSentence(order++, sentence, label));
        }
        return new Classification(rows);
    }

    private List<String> splitSentences(String text) {
        List<String> rows = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (looksLikeHeader(line) || line.length() <= 90) {
                rows.add(line);
                continue;
            }
            for (String part : line.split("(?<=[.!?。])\\s+|[•ㆍ]\\s*")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    rows.add(trimmed);
                }
            }
        }
        return rows;
    }

    private String label(String sentence, String currentSection) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        if (looksLikeHeader(sentence)) {
            return SECTION_HEADER;
        }
        if (containsAny(lower, "우대", "preferred", "nice to have", "plus", "bonus")) {
            return PREFERRED;
        }
        if (PREFERRED.equals(currentSection)) {
            return PREFERRED;
        }
        if (containsAny(lower, "필수", "required", "must", "자격요건", "지원 자격", "requirements")) {
            return REQUIRED;
        }
        if (REQUIRED.equals(currentSection)) {
            return REQUIRED;
        }
        if (containsAny(lower, "자격", "qualification", "요건", "경험이 있으신", "경험을 보유")) {
            return QUALIFICATION;
        }
        if (QUALIFICATION.equals(currentSection)) {
            return QUALIFICATION;
        }
        if (COMPANY_INFO.equals(currentSection)) {
            return COMPANY_INFO;
        }
        if (containsAny(lower, "담당", "업무", "responsibilities", "duties", "what you will do", "build", "operate", "개발", "운영")) {
            return RESPONSIBILITY;
        }
        if (RESPONSIBILITY.equals(currentSection)) {
            return RESPONSIBILITY;
        }
        if (containsAny(lower, "기술", "스택", "tool", "stack") || KNOWN_SKILLS.stream().anyMatch(lower::contains)) {
            return TECH_STACK;
        }
        if (containsAny(lower, "정규직", "계약직", "인턴", "근무", "연봉", "remote", "재택", "full-time", "contract", "salary")) {
            return EMPLOYMENT_CONDITION;
        }
        if (containsAny(lower, "복지", "복리후생", "benefit", "휴가", "지원합니다")) {
            return BENEFIT;
        }
        if (containsAny(lower, "전형", "지원", "제출", "서류", "면접", "apply", "interview")) {
            return APPLICATION_INFO;
        }
        if (containsAny(lower, "회사", "기업", "서비스", "사업", "제품", "culture", "mission")) {
            return COMPANY_INFO;
        }
        return OTHER;
    }

    private String sectionLabel(String sentence) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "우대", "preferred")) {
            return PREFERRED;
        }
        if (containsAny(lower, "필수", "required")) {
            return REQUIRED;
        }
        if (containsAny(lower, "자격", "qualification", "요건")) {
            return QUALIFICATION;
        }
        if (containsAny(lower, "담당", "업무", "responsibilities", "duties", "직무")) {
            return RESPONSIBILITY;
        }
        if (containsAny(lower, "기술", "스택", "tool")) {
            return TECH_STACK;
        }
        if (containsAny(lower, "회사", "기업", "서비스", "사업")) {
            return COMPANY_INFO;
        }
        if (containsAny(lower, "복지", "복리후생")) {
            return BENEFIT;
        }
        if (containsAny(lower, "전형", "지원", "제출")) {
            return APPLICATION_INFO;
        }
        return OTHER;
    }

    private boolean looksLikeHeader(String value) {
        String trimmed = value.trim();
        if (trimmed.length() > 40 || trimmed.endsWith(".") || trimmed.endsWith("다")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT).replace(":", "");
        return containsAny(lower,
                "담당 업무", "주요 업무", "responsibilities", "duties", "직무 내용",
                "필수 조건", "자격 요건", "지원 자격", "requirements", "qualifications",
                "우대 조건", "preferred", "기술 스택", "복리후생", "전형 방법", "제출 서류",
                "회사 소개", "기업 소개");
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record LabeledSentence(int order, String text, String label) {
    }

    public record Classification(List<LabeledSentence> sentences) {

        public List<String> textsByLabel(String label) {
            return sentences.stream()
                    .filter(sentence -> label.equals(sentence.label()))
                    .map(LabeledSentence::text)
                    .toList();
        }

        public Map<String, List<String>> asMap() {
            return sentences.stream()
                    .collect(Collectors.groupingBy(
                            LabeledSentence::label,
                            Collectors.mapping(LabeledSentence::text, Collectors.toList())));
        }
    }
}
