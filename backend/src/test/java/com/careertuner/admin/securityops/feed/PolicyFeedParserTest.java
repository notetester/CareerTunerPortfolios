package com.careertuner.admin.securityops.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.securityops.feed.PolicyFeedModels.ParsedFeedRule;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정책 피드 파서 회귀 테스트 — CSV/JSON 파싱, 헤더 자동감지, 매치타입 자동추론, 검증을 증명한다.
 */
class PolicyFeedParserTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final PolicyFeedParser parser = new PolicyFeedParser(objectMapper);

    @Test
    void inferMatchType_byValuePattern() {
        assertThat(parser.inferMatchType("192.168.0.0/16")).isEqualTo("CIDR");
        assertThat(parser.inferMatchType("10.0.0.1~10.0.0.9")).isEqualTo("IP_RANGE");
        assertThat(parser.inferMatchType("10.0.0.1-10.0.0.9")).isEqualTo("IP_RANGE");
        assertThat(parser.inferMatchType("KR")).isEqualTo("COUNTRY");
        assertThat(parser.inferMatchType("AS12345")).isEqualTo("ASN"); // ASN 은 AS 접두 또는 명시 타입으로 준다
        assertThat(parser.inferMatchType("203.0.113.7")).isEqualTo("IP");
        assertThat(parser.inferMatchType("not-an-ip-value")).isNull();
    }

    @Test
    void csv_withHeader_mapsColumns() {
        String csv = """
                value,matchType,action,reason
                203.0.113.0/24,CIDR,BLOCK,악성 대역
                RU,COUNTRY,BLOCK,국가 차단
                """;
        List<ParsedFeedRule> rules = parser.parse(csv, "BLOCK");
        assertThat(rules).hasSize(2);
        assertThat(rules).allMatch(ParsedFeedRule::valid);
        assertThat(rules.get(0).matchType()).isEqualTo("CIDR");
        assertThat(rules.get(0).value()).isEqualTo("203.0.113.0/24");
        assertThat(rules.get(1).matchType()).isEqualTo("COUNTRY");
    }

    @Test
    void csv_positional_withCommentsAndBlanks_inferred() {
        String csv = """
                # 정책기관 배포 목록
                203.0.113.9

                198.51.100.0/24,ALLOWLIST
                AS64500
                """;
        List<ParsedFeedRule> rules = parser.parse(csv, "BLOCK");
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).matchType()).isEqualTo("IP");
        assertThat(rules.get(1).matchType()).isEqualTo("CIDR");
        assertThat(rules.get(1).action()).isEqualTo("ALLOWLIST");
        assertThat(rules.get(2).matchType()).isEqualTo("ASN");
    }

    @Test
    void json_array_parses() {
        String json = """
                [
                  {"value":"203.0.113.5","matchType":"IP","action":"BLOCK","reason":"봇"},
                  {"cidr":"10.10.0.0/16"},
                  {"country":"CN"}
                ]
                """;
        List<ParsedFeedRule> rules = parser.parse(json, "BLOCK");
        assertThat(rules).hasSize(3);
        assertThat(rules).allMatch(ParsedFeedRule::valid);
        assertThat(rules.get(1).matchType()).isEqualTo("CIDR"); // cidr 키에서 값 추출 + 추론
        assertThat(rules.get(2).matchType()).isEqualTo("COUNTRY");
    }

    @Test
    void invalidValues_flaggedNotThrown() {
        String csv = "999.999.999.999\nZZZ\ngarbage-value";
        List<ParsedFeedRule> rules = parser.parse(csv, "BLOCK");
        assertThat(rules).hasSize(3);
        assertThat(rules).noneMatch(ParsedFeedRule::valid);
        assertThat(rules).allSatisfy(r -> assertThat(r.error()).isNotBlank());
    }

    @Test
    void cidr_validation_rejectsBadPrefix() {
        List<ParsedFeedRule> rules = parser.parse("1.2.3.0/40", "BLOCK");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).valid()).isFalse();
    }
}
