package com.careertuner.ai.chat;

import java.util.regex.Pattern;

/**
 * 에이전트가 생성한 message 표시 문자열에서 마크다운 잔재를 제거한다.
 * <p>프롬프트로 못 막은 `**볼드**`·`### 제목`·`-`/`1.` 목록·`[텍스트](링크)` 를 평문으로 정리.
 * <b>단순 문자열 정리만</b> 하며 의미는 바꾸지 않는다. links/quickReplies·FAQ/검색 데이터엔 적용하지 않는다.
 */
public final class MessageSanitizer {

    private MessageSanitizer() {}

    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s*");      // 줄머리 ###
    private static final Pattern BULLET = Pattern.compile("^\\s{0,3}[-*+]\\s+");          // 줄머리 - * +
    private static final Pattern ORDERED = Pattern.compile("^\\s{0,3}\\d+\\.\\s+");       // 줄머리 1. 2.
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");             // **굵게**
    private static final Pattern UNDERLINE_BOLD = Pattern.compile("__(.+?)__");           // __굵게__
    private static final Pattern INLINE_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)"); // [텍스트](url)

    public static String stripMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = HEADING.matcher(line).replaceFirst("");
            line = BULLET.matcher(line).replaceFirst("");
            line = ORDERED.matcher(line).replaceFirst("");
            line = BOLD.matcher(line).replaceAll("$1");
            line = UNDERLINE_BOLD.matcher(line).replaceAll("$1");
            line = INLINE_LINK.matcher(line).replaceAll("$1");
            out.append(line);
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        return out.toString().strip();
    }
}
