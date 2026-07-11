package com.careertuner.fitanalysis.certificate;

import java.io.InputStream;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.careertuner.fitanalysis.certificate.CertificateScheduleEvidence.ScheduleRound;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 국가전문자격 시험일정 <b>사전공고 번들</b> — 공단 연간 사전공고(안) PDF 와 통합 일정 API(B490007) 응답을
 * 날짜 조인한 산출물(EXACT 53/54 스테이지)을 classpath JSON 으로 번들해 조회한다.
 *
 * <p>국가전문자격은 통합 API 응답에 자격명이 없어(회차 description 뿐) 이름 기반 자동 조회가 불가능하고,
 * 자격별 조회 키(계열코드 매핑)도 미공개다 — 그래서 오프라인 조인 산출물이 유일한 자격명 귀속 일정 소스다.
 * 사전공고는 (안)이므로 신뢰층은 {@link ScheduleEvidenceStatus#PREANNOUNCED} — 자격별 최종 시행계획 공고로
 * 확정된다는 안내를 반드시 동반한다(확정 일정으로 단정 금지).
 *
 * <p><b>연도 가드</b>: 번들의 대상 연도(KST)가 지나면 자동으로 비활성 — 작년 일정을 올해 것처럼 내는 것을
 * 차단한다(오래된 일정 단정 금지). 새해 사전공고가 나오면 번들 파일과 연도만 교체한다.
 */
@Component
public class NationalProfExamScheduleBundle {

    private static final Logger log = LoggerFactory.getLogger(NationalProfExamScheduleBundle.class);
    private static final String SOURCE_NAME = "한국산업인력공단 국가전문자격 시행일정 사전공고(연간, 안)";
    private static final String SOURCE_URL = "https://www.q-net.or.kr/";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final int bundleYear;
    private final Map<String, List<ScheduleRound>> byNormalizedName;

    @Autowired
    public NationalProfExamScheduleBundle(
            @Value("${careertuner.certificate.prof-schedule-bundle.resource:cert/national-prof-schedule-2026-preannounced.json}")
            String resourcePath,
            @Value("${careertuner.certificate.prof-schedule-bundle.enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        Loaded loaded = enabled ? load(resourcePath, objectMapper) : new Loaded(0, Map.of());
        this.bundleYear = loaded.year;
        this.byNormalizedName = loaded.byName;
    }

    /** 번들이 로드됐고 대상 연도가 현재(KST)와 일치하는지 — 아니면 사전공고 일정을 내지 않는다. */
    public boolean available() {
        return !byNormalizedName.isEmpty() && bundleYear == Year.now(KST).getValue();
    }

    /**
     * 자격명으로 사전공고 일정을 조회한다. 매칭되면 PREANNOUNCED(+회차), 아니면 null 을 반환해
     * 호출부가 기존 경로(시행기관 확인 안내)로 처리하게 한다 — 번들 무매칭은 '일정 없음' 단정이 아니다
     * (사전공고 37종 밖 자격·연도 불일치·로드 실패 모두 동일).
     */
    public CertificateScheduleEvidence lookup(String certName) {
        if (certName == null || certName.isBlank() || !available()) {
            return null;
        }
        List<ScheduleRound> rounds = byNormalizedName.get(QnetXmlSupport.norm(certName));
        if (rounds == null || rounds.isEmpty()) {
            return null;
        }
        return new CertificateScheduleEvidence(ScheduleEvidenceStatus.PREANNOUNCED,
                null, certName, SOURCE_NAME, SOURCE_URL, rounds);
    }

    private record Loaded(int year, Map<String, List<ScheduleRound>> byName) {
    }

    private static Loaded load(String resourcePath, ObjectMapper objectMapper) {
        try (InputStream in = NationalProfExamScheduleBundle.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.warn("prof schedule bundle resource missing: {} — 전문자격 일정 안내는 시행기관 확인으로 동작", resourcePath);
                return new Loaded(0, Map.of());
            }
            JsonNode root = objectMapper.readTree(in);
            int year = root.path("year").asInt(0);
            JsonNode byNameNode = root.path("byName");
            if (year <= 0 || !byNameNode.isObject()) {
                log.warn("prof schedule bundle malformed: {} — 무시", resourcePath);
                return new Loaded(0, Map.of());
            }
            Map<String, List<ScheduleRound>> map = new LinkedHashMap<>();
            byNameNode.properties().forEach(entry -> {
                List<ScheduleRound> rounds = new ArrayList<>();
                for (JsonNode r : entry.getValue()) {
                    ScheduleRound round = new ScheduleRound(
                            text(r, "round"), text(r, "docRegStart"), text(r, "docRegEnd"),
                            text(r, "docExam"), text(r, "docPass"),
                            text(r, "pracExamStart"), text(r, "pracExamEnd"), text(r, "pracPass"));
                    if (round.docExam() != null || round.docRegStart() != null) {
                        rounds.add(round);
                    }
                }
                if (!rounds.isEmpty()) {
                    map.put(QnetXmlSupport.norm(entry.getKey()), List.copyOf(rounds));
                }
            });
            if (map.size() < 20) {
                // 사전공고는 37종(그룹 해체 시 36+ 이름) — 크게 적으면 파손으로 보고 미사용(부분 번들로 단정 금지).
                log.warn("prof schedule bundle too small: {} entries — 무시", map.size());
                return new Loaded(0, Map.of());
            }
            log.info("prof schedule bundle loaded: {} certs (year {})", map.size(), year);
            return new Loaded(year, Map.copyOf(map));
        } catch (Exception e) {
            log.warn("prof schedule bundle load failed: {} — 무시", e.getClass().getSimpleName());
            return new Loaded(0, Map.of());
        }
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText("");
        return v.isBlank() ? null : v.trim();
    }
}
