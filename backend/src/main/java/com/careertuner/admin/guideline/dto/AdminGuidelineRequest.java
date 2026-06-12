package com.careertuner.admin.guideline.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminGuidelineRequest(
    String versionLabel,
    String summary,
    String lede,
    List<String> oks,
    List<String> nos,
    List<RuleItem> rules,
    ParamValues params,
    String enforceType,
    LocalDateTime scheduledAt
) {
    public record RuleItem(String t, int s, String b) {}
    public record ParamValues(int blind, int sla, int expire, int s1, int s2, int appeal) {}
}
