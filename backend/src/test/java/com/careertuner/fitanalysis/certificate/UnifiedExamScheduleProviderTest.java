package com.careertuner.fitanalysis.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class UnifiedExamScheduleProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UnifiedExamScheduleProvider provider = new UnifiedExamScheduleProvider(
            "key", "https://unused.invalid", Duration.ofSeconds(1), objectMapper, HttpClient.newHttpClient());

    private static String ok(int totalCount, String itemsJson) {
        return "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE\"},"
                + "\"body\":{\"items\":" + itemsJson + ",\"numOfRows\":50,\"pageNo\":1,\"totalCount\":" + totalCount + "}}";
    }

    private static final String ROUND = "{\"implYy\":\"2026\",\"implSeq\":3,\"qualgbCd\":\"T\","
            + "\"description\":\"국가기술자격 기사 (2026년도 제3회)\",\"docRegStartDt\":\"20260720\","
            + "\"docRegEndDt\":\"20260723\",\"docExamStartDt\":\"20260807\",\"docExamEndDt\":\"20260901\","
            + "\"docPassDt\":\"20260909\",\"pracRegStartDt\":\"20260921\",\"pracRegEndDt\":\"20260928\","
            + "\"pracExamStartDt\":\"20261024\",\"pracExamEndDt\":\"20261107\",\"pracPassDt\":\"20261120\"}";

    @Test
    void normalRoundsParseIntoScheduleRounds() {
        UnifiedExamScheduleProvider.PageResult r = provider.parsePage(ok(1, "[" + ROUND + "]"));

        assertThat(r).isNotNull();
        assertThat(r.totalCount()).isEqualTo(1);
        assertThat(r.rounds()).hasSize(1);
        var round = r.rounds().get(0);
        assertThat(round.round()).contains("제3회");
        assertThat(round.docRegStart()).isEqualTo("20260720");
        assertThat(round.docExam()).isEqualTo("20260807");
        assertThat(round.docPass()).isEqualTo("20260909");
        assertThat(round.pracExamStart()).isEqualTo("20261024");
        assertThat(round.pracPass()).isEqualTo("20261120");
    }

    @Test
    void resultCodeNot00IsParseFailureNotEmptySchedule() {
        // 실측: numOfRows>50 은 resultCode 930 — 오류를 '미편성'으로 오분류하면 안 된다(오류≠부재).
        String err = "{\"header\":{\"resultCode\":\"930\",\"resultMsg\":\"한 페이지당 조회 가능한 최대 목록 수는 50개를 넘을 수 없습니다.\"}}";
        assertThat(provider.parsePage(err)).isNull();
    }

    @Test
    void malformedBodyIsParseFailure() {
        assertThat(provider.parsePage("not-json")).isNull();
        assertThat(provider.parsePage("")).isNull();
        assertThat(provider.parsePage(null)).isNull();
        // 00 이어도 items 배열이 없으면 미확증 — 부재 단정 금지.
        assertThat(provider.parsePage("{\"header\":{\"resultCode\":\"00\"},\"body\":{}}")).isNull();
    }

    @Test
    void datelessItemsAreDropped() {
        String dateless = "{\"description\":\"국가기술자격 기사 (2026년도 제0회)\"}";
        UnifiedExamScheduleProvider.PageResult r = provider.parsePage(ok(1, "[" + dateless + "]"));

        assertThat(r).isNotNull();
        assertThat(r.itemCount()).isEqualTo(1);
        assertThat(r.rounds()).isEmpty(); // 날짜 없는 회차는 근거로 쓰지 않음
    }

    @Test
    void missingJmCdDoesNotCallNetworkAndDegrades() {
        // 응답에 종목명이 없는 API 라 jmCd 없는 조회는 금지(오귀속 방지) — 네트워크 없이 즉시 degrade.
        assertThat(provider.lookup(null, "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
        assertThat(provider.lookup(" ", "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void blankKeyDegradesWithoutNetwork() {
        UnifiedExamScheduleProvider noKey = new UnifiedExamScheduleProvider(
                "", "https://unused.invalid", Duration.ofSeconds(1), objectMapper, HttpClient.newHttpClient());

        assertThat(noKey.enabled()).isFalse();
        assertThat(noKey.lookup("1320", "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void unreachableHostDegradesInsteadOfThrowing() {
        assertThat(provider.lookup("1320", "정보처리기사").status())
                .isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void missingTotalCountIsParseFailureNotCompleteReception() {
        // totalCount 없이는 완전 수신을 판정할 수 없다 — 잘림 가드가 항등식이 되는 것을 차단(형식 미확증=degrade).
        String noTotal = "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":[" + ROUND + "]}}";
        assertThat(provider.parsePage(noTotal)).isNull();
    }

    // ── lookup() 상태 기계(페이지네이션·무날짜·미편성) — HttpClient 스텁으로 검증 ──

    private static UnifiedExamScheduleProvider stubbed(java.util.List<String> bodies) {
        java.util.Iterator<String> it = bodies.iterator();
        java.net.http.HttpClient client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        try {
            org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        java.net.http.HttpResponse<String> resp = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
                        org.mockito.Mockito.when(resp.statusCode()).thenReturn(200);
                        org.mockito.Mockito.when(resp.body()).thenReturn(it.next());
                        return resp;
                    });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new UnifiedExamScheduleProvider("key", "https://stub.invalid", Duration.ofSeconds(1),
                new ObjectMapper(), client);
    }

    @Test
    void confirmedZeroItemsIsOfficialNoSchedule() {
        // 실측: 미편성 종목은 00 + items=[] + totalCount=0 — 확인된 부재만 OFFICIAL_NO_SCHEDULE.
        var e = stubbed(java.util.List.of(ok(0, "[]"))).lookup("7872", "모터그레이더운전기능사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.OFFICIAL_NO_SCHEDULE);
    }

    @Test
    void datelessItemsDegradeInsteadOfClaimingNoSchedule() {
        // item 은 있는데 날짜 필드가 전부 없음(상류 필드명 변경 등) — '미편성 확증' 금지, degrade(→레거시 폴백 가능).
        String dateless = "{\"description\":\"국가기술자격 기사 (2026년도 제3회)\"}";
        var e = stubbed(java.util.List.of(ok(1, "[" + dateless + "]"))).lookup("1320", "정보처리기사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void multiPageRoundsAccumulateAcrossPages() {
        // 상설검정(회차 50+) 시나리오: 51회차 = 50 + 1, 두 페이지 누적 후 VERIFIED_CURRENT.
        String fifty = "[" + String.join(",", java.util.Collections.nCopies(50, ROUND)) + "]";
        var e = stubbed(java.util.List.of(ok(51, fifty), ok(51, "[" + ROUND + "]"))).lookup("7910", "한식조리기능사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.VERIFIED_CURRENT);
        assertThat(e.rounds()).hasSize(51);
    }

    @Test
    void oversizedTotalBeyondMaxPagesDegradesNotTruncatedVerified() {
        // MAX_PAGES(3)로도 못 다 받는 규모 — 잘린 목록으로 일정을 말하지 않는다.
        String fifty = "[" + String.join(",", java.util.Collections.nCopies(50, ROUND)) + "]";
        var e = stubbed(java.util.List.of(ok(999, fifty), ok(999, fifty), ok(999, fifty))).lookup("0000", "가상종목");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void secondPageFailureDiscardsPartialRounds() {
        // 2페이지째 오류(930 등) — 부분 수집분으로 VERIFIED 를 말하지 않고 전체 degrade.
        String fifty = "[" + String.join(",", java.util.Collections.nCopies(50, ROUND)) + "]";
        String err = "{\"header\":{\"resultCode\":\"930\",\"resultMsg\":\"limit\"}}";
        var e = stubbed(java.util.List.of(ok(51, fifty), err)).lookup("7910", "한식조리기능사");

        assertThat(e.status()).isEqualTo(ScheduleEvidenceStatus.UPSTREAM_UNAVAILABLE);
        assertThat(e.rounds()).isEmpty();
    }
}
