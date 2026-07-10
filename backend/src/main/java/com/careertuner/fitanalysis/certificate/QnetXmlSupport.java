package com.careertuner.fitanalysis.certificate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * q-net(공공데이터) REST/XML 응답 공용 파싱 헬퍼. 국가자격 provider(목록 getList / 시험일정 getJMList)가 동일한
 * 방어 규칙(게이트웨이 오류 감지·정상 확증·자격명 정확매칭·목록 잘림 감지)을 한곳에서 쓰도록 모은다.
 *
 * <p><b>오류≠부재 원칙</b>을 위해 상태 판정 헬퍼를 제공한다: 정상(resultCode 00) 확증 없이 매칭이 없다고 해서
 * '부재'로 단정하지 않도록 {@link #normalConfirmed}·{@link #truncated} 를 함께 확인한다.
 */
final class QnetXmlSupport {

    private QnetXmlSupport() {
    }

    /** data.go.kr/q-net 공통 오류 envelope(HTTP 200 + resultCode 없는 인증·쿼터 오류) 감지. */
    static boolean isGatewayError(String xml) {
        String lower = xml.toLowerCase(Locale.ROOT);
        return lower.contains("openapi_serviceresponse") || lower.contains("cmmmsgheader")
                || lower.contains("returnreasoncode") || lower.contains("returnauthmsg");
    }

    /** resultCode 가 정상(00/0)으로 확증됐는지. */
    static boolean normalConfirmed(String xml) {
        String rc = tagValue(xml, "resultCode");
        return "00".equals(rc) || "0".equals(rc);
    }

    /** resultCode 가 존재하고 정상(00/0)이 아닌지(명시적 오류코드). */
    static boolean explicitErrorCode(String xml) {
        String rc = tagValue(xml, "resultCode");
        return rc != null && !rc.equals("00") && !rc.equals("0");
    }

    /** totalCount 가 수신 item 수보다 커서 목록이 잘렸는지 — 잘린 목록에서의 무매칭을 '부재'로 단정하지 않기 위함. */
    static boolean truncated(String xml) {
        String tc = tagValue(xml, "totalCount");
        if (tc == null) {
            return false;
        }
        try {
            return Integer.parseInt(tc.trim()) > allBlocks(xml, "item").size();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 자격명 정확 매칭(공백 제거·소문자) — 과매칭으로 다른 종목을 섞지 않기 위함. */
    static boolean nameMatches(String responseName, String query) {
        return responseName != null && query != null && norm(responseName).equals(norm(query));
    }

    /** 태그 값(첫 매치, 대소문자 무시). 빈 값은 null. */
    static String tagValue(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    /** 지정 태그 블록 내부 내용 전체(중첩 무관). */
    static List<String> allBlocks(String xml, String tag) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        return blocks;
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
