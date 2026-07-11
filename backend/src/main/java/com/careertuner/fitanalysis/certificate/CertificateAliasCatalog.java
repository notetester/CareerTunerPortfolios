package com.careertuner.fitanalysis.certificate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 자격증 별칭(통용 약어 → 공식 등록/종목 명칭) 카탈로그 — <b>실측 검증된 항목만</b> 담는다.
 * 예: 'SQLD' 는 민간자격 등록명이 아니라 한국데이터산업진흥원 등록자격 'SQL' 의 등급명(2026-07-11 odcloud 실측).
 * 미검증 별칭을 추가하면 다른 자격으로 오귀속될 수 있으므로, 항목 추가 시 등록정보/종목목록 대조가 선행 조건이다.
 */
@Component
public class CertificateAliasCatalog {

    private static final Logger log = LoggerFactory.getLogger(CertificateAliasCatalog.class);

    private final Map<String, String> byNormalizedAlias;

    @Autowired
    public CertificateAliasCatalog(
            @Value("${careertuner.certificate.alias-resource:cert/cert-aliases.csv}") String resourcePath) {
        this.byNormalizedAlias = load(resourcePath);
    }

    /** 별칭이면 공식 명칭을, 아니면 빈 Optional 을 반환한다(원명 자체가 공식명일 수 있으므로 치환 강제 금지). */
    public Optional<String> officialNameFor(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNormalizedAlias.get(QnetXmlSupport.norm(name)));
    }

    private static Map<String, String> load(String resourcePath) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = CertificateAliasCatalog.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine(); // 헤더
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 2);
                    if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                        map.put(QnetXmlSupport.norm(parts[0]), parts[1].trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("cert alias load failed: {} — 별칭 없이 동작", e.getClass().getSimpleName());
            return Map.of();
        }
        return Map.copyOf(map);
    }
}
