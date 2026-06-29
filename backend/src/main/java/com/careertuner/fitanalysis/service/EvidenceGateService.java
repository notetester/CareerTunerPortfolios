package com.careertuner.fitanalysis.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.careertuner.fitanalysis.ai.FitAnalysisAiCommand;
import com.careertuner.fitanalysis.ai.FitAnalysisAiResult;

/**
 * review-first evidence gate(R3) — 적합도 분석 AI 설명 출력의 <b>결정론 후처리 안전층</b>.
 *
 * <p>R2b~R2f 실측 결론(reports/54~60): 3B 모델에 retrievedContext 를 주입해도 grounding(미보유 역량을 '보유'로 단정)이
 * 개선되지 않았다. 따라서 모델을 바꾸는 대신, 모델 호출 + 기존 {@code OssFitAnalysisAiService} 의 E1 grounding guard
 * <b>이후</b>에 출력을 다시 결정론으로 검사해 검토 상태(gateStatus)만 표시한다.
 *
 * <p>이 서비스는 순수 함수다(외부 호출 없음). 점수/applyDecision/matchedSkills/missingSkills 를 <b>읽기만</b> 하고
 * 절대 바꾸지 않는다. RAG runtime 자동주입·rewrite 자동노출은 하지 않는다(설계 비목표).
 *
 * <p>E1 guard 와의 관계: E1 은 AI 서비스 내부의 hard guard(위반 시 재호출→폴백)이고, 이 gate 는 그 위에 얹는 soft
 * review 층이다. 둘은 독립이며 서로를 약화하지 않는다. 휴리스틱(보유/결핍 표현)은 의도적으로 E1 과 동일 기준을 쓰되
 * E1 코드는 손대지 않기 위해 self-contained 로 복제한다.
 */
@Service
public class EvidenceGateService {

    /** '보유'를 뜻하는 표현(E1 과 동일 기준). */
    private static final String[] POSSESSION = {
            "보유", "갖춤", "갖추고", "갖추어", "강점", "경험 있", "경험을 보유",
            "활용 가능", "숙련", "기반이 있", "능숙", "능통"};
    /** 결핍·부정 표현(같은 문장에 있으면 위반 아님 — false-positive 방지, E1 과 동일 기준). */
    private static final String[] LACK = {
            "부족", "없", "미보유", "부재", "않", "못", "결여", "갖추지", "보유하지", "미흡", "전무"};

    /**
     * 적합도 분석 입력({@code command})과 AI 결과({@code ai})로 gate 를 결정한다.
     *
     * <p>핵심 계약 필드(점수 범위·applyDecision·matched/missing)가 깨졌으면 {@code REJECTED}(자동 확정 금지).
     * 그 외에는 설명 free-text(fitSummary)에서 '보유로 단정했지만 userEvidence 에 없는 역량'을 찾아
     * 있으면 {@code REVIEW_REQUIRED}, 없으면 {@code PASSED}.
     */
    public EvidenceGateDecision evaluate(FitAnalysisAiCommand command, FitAnalysisAiResult ai) {
        List<String> userEvidence = distinct(concat(
                ai.matchedSkills(), command.profileSkills(), command.profileCertificates()));
        List<String> jobRequirements = distinct(concat(
                command.requiredSkills(), command.preferredSkills(), ai.missingSkills()));
        // 현 runtime 은 RAG off 라 카탈로그/회사 컨텍스트는 모델 입력에 없다(빈 버킷으로 명시).
        List<EvidenceGateDecision.EvidenceSource> sources = List.of(
                new EvidenceGateDecision.EvidenceSource("userEvidence", true, userEvidence),
                new EvidenceGateDecision.EvidenceSource("jobRequirements", false, jobRequirements),
                new EvidenceGateDecision.EvidenceSource("catalogFacts", false, List.of()),
                new EvidenceGateDecision.EvidenceSource("companyContext", false, List.of()));

        // 1) 구조 무결성: 핵심 계약 필드가 깨졌으면 REJECTED(내용 검사 이전 단계).
        if (ai.matchedSkills() == null || ai.missingSkills() == null || ai.applyDecision() == null
                || ai.fitScore() < 0 || ai.fitScore() > 100) {
            return new EvidenceGateDecision(
                    EvidenceGateDecision.STATUS_REJECTED, true, EvidenceGateDecision.SEVERITY_CRITICAL,
                    List.of(new EvidenceGateDecision.Reason(
                            "structural", "-", "핵심 계약 필드 누락 또는 점수 범위 위반",
                            EvidenceGateDecision.SEVERITY_CRITICAL)),
                    sources);
        }

        // 2) free-text(fitSummary=strategy) 에서 unsupported user-owned claim 탐지.
        Set<String> userEvidenceLower = lower(userEvidence);
        Set<String> requiredLower = lower(nullSafe(command.requiredSkills()));
        List<EvidenceGateDecision.Reason> reasons = auditClaims(
                ai.strategy(), jobRequirements, userEvidenceLower, requiredLower);

        if (reasons.isEmpty()) {
            return new EvidenceGateDecision(
                    EvidenceGateDecision.STATUS_PASSED, false, null, List.of(), sources);
        }
        boolean critical = reasons.stream()
                .anyMatch(r -> EvidenceGateDecision.SEVERITY_CRITICAL.equals(r.severity()));
        String maxSeverity = critical
                ? EvidenceGateDecision.SEVERITY_CRITICAL : EvidenceGateDecision.SEVERITY_WARNING;
        // review-first: 내용 claim 은(치명도와 무관하게) 폐기하지 않고 REVIEW_REQUIRED 로 검토 라우팅한다.
        return new EvidenceGateDecision(
                EvidenceGateDecision.STATUS_REVIEW_REQUIRED, true, maxSeverity, reasons, sources);
    }

    /**
     * fitSummary 문장들에서 '보유로 단정'(보유 표현 있고 결핍·부정 표현 없음)했는데 userEvidence 에 없는
     * 공고 요구 역량을 찾아 reason 으로 만든다. claim 기준 중복 제거(최고 심각도 유지).
     */
    private List<EvidenceGateDecision.Reason> auditClaims(String fitSummary,
                                                          List<String> jobRequirements,
                                                          Set<String> userEvidenceLower,
                                                          Set<String> requiredLower) {
        if (fitSummary == null || fitSummary.isBlank() || jobRequirements.isEmpty()) {
            return List.of();
        }
        Map<String, EvidenceGateDecision.Reason> byClaim = new LinkedHashMap<>();
        for (String sentence : fitSummary.split("[.!?。\\n]")) {
            if (sentence == null || sentence.isBlank()) {
                continue;
            }
            if (firstContaining(sentence, POSSESSION) == null || firstContaining(sentence, LACK) != null) {
                continue; // 보유 표현이 없거나 결핍·부정 문맥이면 위반 아님
            }
            String lower = sentence.toLowerCase(Locale.ROOT);
            for (String skill : jobRequirements) {
                if (skill == null || skill.isBlank()) {
                    continue;
                }
                String key = skill.toLowerCase(Locale.ROOT);
                if (userEvidenceLower.contains(key) || !lower.contains(key)) {
                    continue; // 실제 보유했거나 문장에 안 나오면 위반 아님
                }
                boolean hardRequired = requiredLower.contains(key);
                String severity = hardRequired
                        ? EvidenceGateDecision.SEVERITY_CRITICAL : EvidenceGateDecision.SEVERITY_WARNING;
                String reason = hardRequired
                        ? "필수 요구 역량을 보유로 단정했으나 userEvidence 미지원"
                        : "공고 요구 역량을 보유로 단정했으나 userEvidence 미지원";
                EvidenceGateDecision.Reason existing = byClaim.get(key);
                if (existing == null
                        || (EvidenceGateDecision.SEVERITY_CRITICAL.equals(severity)
                            && !EvidenceGateDecision.SEVERITY_CRITICAL.equals(existing.severity()))) {
                    byClaim.put(key, new EvidenceGateDecision.Reason(
                            "requirement_as_owned", skill, reason, severity));
                }
            }
        }
        return new ArrayList<>(byClaim.values());
    }

    private static List<String> concat(List<String> a, List<String> b, List<String> c) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        if (c != null) {
            out.addAll(c);
        }
        return out;
    }

    private static List<String> distinct(List<String> values) {
        Set<String> seenLower = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            if (seenLower.add(trimmed.toLowerCase(Locale.ROOT))) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static Set<String> lower(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                out.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String firstContaining(String text, String[] needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return needle;
            }
        }
        return null;
    }
}
