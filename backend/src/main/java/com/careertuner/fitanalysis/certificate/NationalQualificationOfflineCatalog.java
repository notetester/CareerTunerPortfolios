package com.careertuner.fitanalysis.certificate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 국가자격 종목 목록 <b>오프라인 스냅샷</b> — 공공데이터포털 파일(한국산업인력공단_국가자격 종목 목록 정보,
 * CSV: 자격구분코드/자격구분명/계열명/종목명)을 classpath 리소스로 번들해 조회한다.
 *
 * <p>국가자격 목록은 연 단위 스냅샷 성격의 법정 목록이라(API 도 동일 시점 스냅샷을 반환) 네트워크 조회보다
 * 로컬 스냅샷이 낫다: Q-Net 게이트웨이 장애와 무관하게 <b>종류 판별(T/S)·라우팅이 항상 동작</b>하고,
 * 스냅샷은 완전한 목록이므로 무매칭을 NOT_FOUND 로 단정할 수 있다(잘림·게이트웨이 오판 없음).
 *
 * <p>한계(기존 API 경로와 동일): 한국산업인력공단 시행 종목만 담겨 있어 타 기관 시행 국가자격
 * (예: 대한상공회의소 컴퓨터활용능력)은 목록에 없다 → 민간 등록정보 경로로 넘어가 NOT_FOUND 가 될 수 있다.
 * 스냅샷 이후 신설된 국가자격도 마찬가지다(연 1회 수준 변동). 국가기술자격(T)의 jmCd 는 별도 매핑 리소스
 * (취득자 현황·공개문제 API 교차 + Q-Net 웹 검증 산출물)에서 채워지며, 매핑이 없는 종목은 jmCd=null 로 남아
 * 종목별 일정 조회만 레거시 경로로 degrade 한다(판별 기능 무영향).
 *
 * <p>리소스 로드 실패 시 {@link #available()} 이 false 가 되고 provider 가 기존 네트워크 경로로 동작한다
 * (스냅샷은 최적화이지 새로운 단일 장애점이 아니다).
 */
@Component
public class NationalQualificationOfflineCatalog {

    private static final Logger log = LoggerFactory.getLogger(NationalQualificationOfflineCatalog.class);

    private final String sourceName;
    private final String sourceUrl = "https://www.q-net.or.kr/";
    /** 정규화된 종목명 → entry. 종목명은 스냅샷 내 유일(2025-12-31 기준 613종 중복 0 검증). */
    private final Map<String, NationalQualificationCatalogEntry> byNormalizedName;

    @Autowired
    public NationalQualificationOfflineCatalog(
            @Value("${careertuner.certificate.catalog-snapshot.resource:cert/national-qualification-catalog-20251231.csv}")
            String resourcePath,
            @Value("${careertuner.certificate.catalog-snapshot.jmcd-resource:cert/national-tech-jmcd-20260711.csv}")
            String jmCdResourcePath,
            @Value("${careertuner.certificate.catalog-snapshot.label:20251231}") String snapshotLabel,
            @Value("${careertuner.certificate.catalog-snapshot.enabled:true}") boolean enabled) {
        this.sourceName = "한국산업인력공단 국가자격 종목 목록(오프라인 스냅샷 " + snapshotLabel + ")";
        this.byNormalizedName = enabled ? load(resourcePath, jmCdResourcePath) : Map.of();
        warnIfStale(snapshotLabel);
    }

    /** 스냅샷 라벨(yyyyMMdd)이 전년도 이하이면 경고 — 신설·개명 국가자격이 반영되지 않을 수 있다(연 1회 교체 권장). */
    private void warnIfStale(String snapshotLabel) {
        if (!available() || snapshotLabel == null || snapshotLabel.length() < 4) {
            return;
        }
        try {
            int labelYear = Integer.parseInt(snapshotLabel.substring(0, 4));
            int currentYear = java.time.Year.now().getValue();
            if (labelYear < currentYear) {
                log.warn("national catalog snapshot may be stale: label={} (현재 {}년) — 신설 국가자격 미반영 가능, "
                        + "공공데이터포털 신규 스냅샷으로 교체를 권장", snapshotLabel, currentYear);
            }
        } catch (NumberFormatException ignored) {
            // 라벨이 날짜 형식이 아니면 신선도 판단 불가 — 경고 생략(로드 자체는 유효).
        }
    }

    /** 스냅샷이 정상 로드돼 조회 가능한지. false 면 provider 가 네트워크 경로를 쓴다. */
    public boolean available() {
        return !byNormalizedName.isEmpty();
    }

    /**
     * 자격명으로 스냅샷을 조회한다. 스냅샷은 완전 목록이므로 무매칭=NOT_FOUND(스냅샷 시점 기준 부재).
     * {@link #available()} false 상태에서 호출되면 UPSTREAM_UNAVAILABLE(조회 불능 — 부재 단정 금지).
     */
    public NationalQualificationCatalogEvidence lookup(String certName) {
        if (certName == null || certName.isBlank() || !available()) {
            return new NationalQualificationCatalogEvidence(
                    NationalQualificationCatalogStatus.UPSTREAM_UNAVAILABLE, certName, null, sourceName, sourceUrl);
        }
        NationalQualificationCatalogEntry entry = byNormalizedName.get(QnetXmlSupport.norm(certName));
        if (entry == null) {
            return new NationalQualificationCatalogEvidence(
                    NationalQualificationCatalogStatus.NOT_FOUND, certName, null, sourceName, sourceUrl);
        }
        return new NationalQualificationCatalogEvidence(
                NationalQualificationCatalogStatus.FOUND, certName, entry, sourceName, sourceUrl);
    }

    /** 스냅샷 헤더 고정값 — 오인코딩(CP949 등)·다른 파일 오배치를 로드 시점에 즉시 걸러낸다. */
    static final String EXPECTED_HEADER = "자격구분코드,자격구분명,계열명,종목명";
    /**
     * 최소 엔트리 게이트 — 국가자격 목록은 수백 종 규모(2025-12-31 기준 613)라, 이보다 크게 적으면 잘림/파손으로
     * 보고 로드 실패 처리한다. lookup() 의 NOT_FOUND 단정 권한은 '완전한 스냅샷' 전제 위에서만 성립하기 때문
     * (파손 스냅샷의 무매칭을 부재로 단정하면 오류≠부재 위반).
     */
    static final int MIN_ENTRIES = 500;

    private static Map<String, NationalQualificationCatalogEntry> load(String resourcePath, String jmCdResourcePath) {
        // jmCd 매핑(종목명→코드, 국가기술자격 한정)은 선택 리소스 — 없거나 파손이어도 카탈로그 자체는 동작
        // (jmCd 는 통합 일정 API 종목별 조회에만 쓰이고, 없으면 해당 종목만 일정 조회가 레거시 경로로 degrade).
        Map<String, String> jmCdByName = loadJmCdMapping(jmCdResourcePath);
        Map<String, NationalQualificationCatalogEntry> map = new LinkedHashMap<>();
        int skipped = 0;
        int duplicated = 0;
        try (InputStream in = NationalQualificationOfflineCatalog.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.warn("national catalog snapshot resource missing: {} — 네트워크 경로로 동작", resourcePath);
                return Map.of();
            }
            // malformed 바이트를 U+FFFD 로 조용히 치환하지 않고 예외로 올린다(REPORT) — CP949 등 오인코딩 스냅샷이
            // mojibake 키로 '성공' 로드되어 모든 실제 국가자격을 NOT_FOUND 로 오판하는 경로를 차단.
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, decoder))) {
                String header = reader.readLine();
                if (header == null || !EXPECTED_HEADER.equals(header.replace("\uFEFF", "").trim())) {
                    log.warn("national catalog snapshot header mismatch: {} — 파손/오인코딩으로 보고 네트워크 경로로 동작",
                            resourcePath);
                    return Map.of();
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    // 스냅샷 CSV 는 4개 필드 고정·따옴표/내장콤마 없음(생성 시 검증). 방어적으로 4분할 초과분은 종목명에 합류.
                    String[] parts = line.split(",", 4);
                    if (parts.length < 4 || parts[3].isBlank()) {
                        skipped++;
                        continue;
                    }
                    String certName = parts[3].trim();
                    String jmCd = jmCdByName.get(QnetXmlSupport.norm(certName));
                    if (map.putIfAbsent(QnetXmlSupport.norm(certName), new NationalQualificationCatalogEntry(
                            jmCd, certName, parts[0].trim(), parts[1].trim(), parts[2].trim(), null, null)) != null) {
                        duplicated++; // 정규화 후 동일 종목명 — 뒤 행 폐기(현 스냅샷 중복 0 검증, 교체 시 감지용)
                    }
                }
            }
        } catch (Exception e) {
            log.warn("national catalog snapshot load failed: {} — 네트워크 경로로 동작", e.getClass().getSimpleName());
            return Map.of();
        }
        if (skipped > 0 || duplicated > 0) {
            log.warn("national catalog snapshot anomalies: skipped={} duplicated={} ({})",
                    skipped, duplicated, resourcePath);
        }
        if (map.size() < MIN_ENTRIES) {
            log.warn("national catalog snapshot too small: {} entries (<{}) — 잘림/파손으로 보고 네트워크 경로로 동작",
                    map.size(), MIN_ENTRIES);
            return Map.of();
        }
        long withJmCd = map.values().stream().filter(e -> e.jmCd() != null).count();
        log.info("national catalog snapshot loaded: {} entries ({} with jmCd) from {}",
                map.size(), withJmCd, resourcePath);
        return Map.copyOf(map);
    }

    /**
     * 국가기술자격 jmCd 매핑(CSV: 종목명,jmCd) 로드 — 취득자 현황/공개문제 API 교차 + Q-Net 웹 검증 산출물.
     * 선택 리소스: 실패해도 빈 맵(카탈로그 판별 기능 무영향). 컴퓨터시스템기사 등 canonical 미확정 종목은 미포함.
     */
    private static Map<String, String> loadJmCdMapping(String resourcePath) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = NationalQualificationOfflineCatalog.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.info("national tech jmCd mapping resource missing: {} — 종목별 일정 조회는 레거시 경로", resourcePath);
                return Map.of();
            }
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, decoder))) {
                String header = reader.readLine();
                if (header == null || !"종목명,jmCd".equals(header.replace("\uFEFF", "").trim())) {
                    log.warn("national tech jmCd mapping header mismatch: {} — 무시", resourcePath);
                    return Map.of();
                }
                String line;
                int skipped = 0;
                java.util.Set<String> conflicted = new java.util.HashSet<>();
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split(",", 2);
                    if (parts.length != 2 || parts[0].isBlank() || !parts[1].trim().matches("\\d{4}")) {
                        skipped++;
                        continue;
                    }
                    String key = QnetXmlSupport.norm(parts[0]);
                    String jmCd = parts[1].trim();
                    String prev = map.putIfAbsent(key, jmCd);
                    if (prev != null && !prev.equals(jmCd)) {
                        // 같은 종목명에 다른 코드 — 어느 쪽도 믿을 수 없다. 채택하면 다른 종목 일정 오귀속 위험이므로
                        // 해당 종목만 매핑에서 제외(레거시 경로로 degrade)하고 경고.
                        conflicted.add(key);
                    }
                }
                for (String key : conflicted) {
                    map.remove(key);
                }
                if (skipped > 0 || !conflicted.isEmpty()) {
                    log.warn("national tech jmCd mapping anomalies: skipped={} conflicted={} ({})",
                            skipped, conflicted.size(), resourcePath);
                }
            }
        } catch (Exception e) {
            log.warn("national tech jmCd mapping load failed: {} — 무시", e.getClass().getSimpleName());
            return Map.of();
        }
        return Map.copyOf(map);
    }
}
