package com.careertuner.fitanalysis.ai;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 스킬 <b>동의어</b> 정규화(같은 스킬의 다른 표기 — JS↔JavaScript, K8s↔Kubernetes 등)를 위한 큐레이션 카탈로그.
 *
 * <p>규칙엔진의 스킬 매칭이 substring 기반이라 역방향 약어 케이스(보유 "JS" vs 요구 "JavaScript" →
 * {@code "js".contains("javascript")}=false)에서 false-negative 가 났다. 이 카탈로그는 두 이름을 같은
 * <b>정규형</b>으로 매핑해 그 매칭을 살린다.
 *
 * <p><b>가산적으로만 쓴다</b>(기존 substring 매칭에 OR 로 얹음) — 매칭을 늘리기만 하고 줄이지 않는다.
 * 상하위 관계(예: MSSQL vs SQL — 제품 vs 언어)는 담지 않는다(다른 스킬을 같다고 판정하는 false-positive 방지).
 * 항목 추가 시 '정말 같은 스킬의 다른 표기인지'가 채택 기준이다.
 */
@Component
public class SkillAliasCatalog {

    private static final Logger log = LoggerFactory.getLogger(SkillAliasCatalog.class);

    /** 정규화된 별칭 → 정규형(canonical). */
    private final Map<String, String> aliasToCanonical;

    public SkillAliasCatalog(
            @Value("${careertuner.analysis.skill-alias-resource:cert/skill-aliases.csv}") String resourcePath) {
        this.aliasToCanonical = load(resourcePath);
    }

    /** 스킬의 정규형 — 별칭이면 매핑값, 아니면 정규화한 자기 자신. 비교의 단위. */
    public String canonical(String skill) {
        String normalized = normalize(skill);
        if (normalized.isEmpty()) {
            return normalized;
        }
        return aliasToCanonical.getOrDefault(normalized, normalized);
    }

    /** 소문자 + 공백·점·하이픈 제거(node.js / Node JS / node-js → nodejs). */
    static String normalize(String skill) {
        if (skill == null) {
            return "";
        }
        return skill.toLowerCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
    }

    private static Map<String, String> load(String resourcePath) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = SkillAliasCatalog.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.info("skill alias resource missing: {} — 동의어 정규화 없이 substring 매칭만", resourcePath);
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.readLine(); // 헤더
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 2);
                    if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                        map.put(normalize(parts[0]), normalize(parts[1]));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("skill alias load failed: {} — 동의어 정규화 비활성", e.getClass().getSimpleName());
            return Map.of();
        }
        return Map.copyOf(map);
    }
}
