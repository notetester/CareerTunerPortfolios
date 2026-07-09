package com.careertuner.profile.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 이력서 텍스트에서 KNOWN_SKILLS 부분일치 스캔 (LLM 아님).
 * B 의 extractRequiredSkills 알고리즘과 동일하되 sentinel("Job posting analysis") 은 복제하지 않는다.
 */
public final class ProfileKnownSkillsScanner {

    /**
     * BAnalysisGenerationService.KNOWN_SKILLS 와 동일 44개.
     * B 영역 코드를 건드리지 않기 위해 여기 복사본을 둔다.
     */
    public static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring", "Spring Boot", "MyBatis", "JPA", "SQL", "MySQL", "PostgreSQL",
            "Redis", "Kafka", "RabbitMQ", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
            "Linux", "React", "TypeScript", "JavaScript", "Vue", "Node.js", "Python",
            "Django", "FastAPI", "REST", "GraphQL", "Git", "CI/CD", "Jenkins", "GitHub Actions",
            "JUnit", "Mockito", "Testing", "Monitoring", "Prometheus", "Grafana", "Elasticsearch",
            "Security", "OAuth", "JWT", "Agile", "Scrum", "Figma");

    private ProfileKnownSkillsScanner() {
    }

    /** 원문 소문자 부분일치. 빈 결과여도 sentinel 을 넣지 않는다. */
    public static List<String> scan(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        return new ArrayList<>(found);
    }
}
