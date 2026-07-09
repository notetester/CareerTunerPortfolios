package com.careertuner.fitanalysis.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p><b>userEvidence 기준(#174 후속 hotfix, reports/62)</b>: 사용자 보유 근거는 <b>사용자 원본 입력</b>
 * (`profileSkills` + `profileCertificates`)만으로 한정한다. AI 출력 {@code matchedSkills} 는 AI 파생 결과이므로
 * userEvidence 가 아니다 — matchedSkills 를 보유 근거로 신뢰하면 AI 가 잘못 만든 매칭을 다시 신뢰하는 순환 오류가 된다.
 * matchedSkills 는 별도 derived 버킷으로 분리하고, 사용자 원본 근거에 없는 matched 항목은 검토 후보로 본다.
 *
 * <p>E1 guard 와의 관계: E1 은 AI 서비스 내부의 hard guard(위반 시 재호출→폴백)이고, 이 gate 는 그 위에 얹는 soft
 * review 층이다. 둘은 독립이며 서로를 약화하지 않는다. 휴리스틱(보유/결핍 표현)은 의도적으로 E1 과 동일 기준을 쓰되
 * E1 코드는 손대지 않기 위해 self-contained 로 복제한다.
 */
@Service
public class EvidenceGateService {

    /**
     * 게이트 감사 추적(계측). 결정별로 <b>R3 가 실제 감사한 텍스트</b>(userFacingTexts)와 판정·사유를 남긴다.
     * 외부 캡처(API 응답)는 조립 후 뷰라 R3 감사입력과 다르므로, FP/FN 을 깨끗이 측정하려면 이 로그가 필요하다.
     * 순수 관측 — 동작·판정값에 영향 없음. 활성화: 이 logger 를 DEBUG 로.
     */
    private static final Logger AUDIT = LoggerFactory.getLogger("careertuner.evidencegate.audit");

    private final SkillAliasNormalizer skillAliasNormalizer = new SkillAliasNormalizer();

    /** '보유'를 뜻하는 표현(E1 과 동일 기준). */
    private static final String[] POSSESSION = {
            "보유", "갖춤", "갖추고", "갖추어", "강점", "경험 있", "경험을 보유",
            "활용 가능", "숙련", "기반이 있", "능숙", "능통"};
    /** 결핍·부정 표현(같은 문장에 있으면 위반 아님 — false-positive 방지, E1 과 동일 기준). */
    private static final String[] LACK = {
            "부족", "없", "미보유", "부재", "않", "못", "결여", "갖추지", "보유하지", "미흡", "전무"};

    /** AI 매칭이 사용자 원본 근거에 없을 때(순환 오류 방지). */
    private static final String TYPE_MATCHED_WITHOUT_EVIDENCE = "matched_skill_without_user_evidence";
    /** 사용자 노출 텍스트가 공고 요구 역량을 보유로 단정했으나 사용자 원본 근거가 없을 때. */
    private static final String TYPE_REQUIREMENT_AS_OWNED = "requirement_as_owned";

    /**
     * 적합도 분석 입력({@code command})과 AI 결과({@code ai})로 gate 를 결정한다.
     *
     * <p>핵심 계약 필드(점수 범위·applyDecision·matched/missing)가 깨졌으면 {@code REJECTED}(자동 확정 금지).
     * 그 외에는 (1) AI matchedSkills 중 사용자 원본 근거에 없는 항목, (2) 사용자 노출 텍스트에서 '보유로 단정'했으나
     * 사용자 원본 근거에 없는 공고 요구 역량을 찾아 있으면 {@code REVIEW_REQUIRED}, 없으면 {@code PASSED}.
     */
    public EvidenceGateDecision evaluate(FitAnalysisAiCommand command, FitAnalysisAiResult ai) {
        // userEvidence = 사용자 원본 입력만(프로필 스킬/자격). AI 파생 matchedSkills 는 제외한다.
        List<String> userEvidence = distinct(concat(command.profileSkills(), command.profileCertificates()));
        List<String> derivedMatched = distinct(nullSafe(ai.matchedSkills()));
        List<String> jobRequirements = distinct(concat(command.requiredSkills(), command.preferredSkills()));
        List<String> missingSkills = distinct(nullSafe(ai.missingSkills()));

        // evidence 버킷 스냅샷: userEvidence 만 userOwned=true, derived/missing/jobRequirements 는 false.
        // 현 runtime 은 RAG off 라 catalogFacts/companyContext 는 빈 버킷.
        List<EvidenceGateDecision.EvidenceSource> sources = List.of(
                new EvidenceGateDecision.EvidenceSource("userEvidence", true, userEvidence),
                new EvidenceGateDecision.EvidenceSource("derivedMatchedSkills", false, derivedMatched),
                new EvidenceGateDecision.EvidenceSource("jobRequirements", false, jobRequirements),
                new EvidenceGateDecision.EvidenceSource("missingSkills", false, missingSkills),
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

        Set<String> userEvidenceKeys = canonicalKeys(userEvidence);
        Set<String> requiredKeys = canonicalKeys(nullSafe(command.requiredSkills()));
        // 텍스트 보유단정 탐지 대상: 공고 요구(required+preferred) + AI 가 부족이라 한 역량(missing). 보유로 단정되면 위반.
        List<SkillClaim> detectionRequirements = skillClaims(distinct(concat(jobRequirements, missingSkills, List.of())));

        Map<String, EvidenceGateDecision.Reason> byClaim = new LinkedHashMap<>();
        // 2) AI matchedSkills 순환 오류: matched 인데 사용자 원본 근거에 없으면 검토 후보(텍스트 단정과 무관하게).
        auditMatchedSkills(derivedMatched, userEvidenceKeys, requiredKeys, byClaim);
        // 3) 사용자 노출 텍스트(strategy/scoreBasis/strategyActions/applyDecision)에서 보유 단정 탐지.
        List<String> auditedTexts = userFacingTexts(ai);
        auditTextClaims(auditedTexts, detectionRequirements, userEvidenceKeys, requiredKeys, byClaim);

        List<EvidenceGateDecision.Reason> reasons = new ArrayList<>(byClaim.values());
        // 계측(관측 전용): R3 가 실제로 감사한 텍스트·탐지대상·판정. FP/FN 측정 시 이 로그를 켜서 판정단과 대조한다.
        if (AUDIT.isDebugEnabled()) {
            AUDIT.debug("gate status={} reasons={} detection={} auditedTexts={}",
                    reasons.isEmpty() ? "PASSED" : "REVIEW_REQUIRED",
                    reasons.stream().map(r -> r.type() + ":" + r.claim()).toList(),
                    detectionRequirements.stream().map(SkillClaim::claim).toList(),
                    auditedTexts);
        }
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
     * AI matchedSkills 중 사용자 원본 근거(profileSkills/profileCertificates)에 없는 항목을 검토 후보로 만든다.
     * 텍스트에 보유 서술이 없어도 매칭 자체가 근거 없는 단정이므로 검출한다(순환 오류 차단의 핵심).
     */
    private void auditMatchedSkills(List<String> derivedMatched,
                                    Set<String> userEvidenceKeys,
                                    Set<String> requiredKeys,
                                    Map<String, EvidenceGateDecision.Reason> byClaim) {
        for (String skill : derivedMatched) {
            String key = skillAliasNormalizer.canonicalize(skill);
            if (key.isBlank() || userEvidenceKeys.contains(key)) {
                continue; // 사용자 원본 근거에 실제로 있으면 정상
            }
            String severity = severityFor(key, requiredKeys);
            String reason = EvidenceGateDecision.SEVERITY_CRITICAL.equals(severity)
                    ? "AI 매칭 역량이 필수 요구이나 사용자 원본 근거(프로필 스킬/자격)에 없음"
                    : "AI 매칭 역량이 사용자 원본 근거(프로필 스킬/자격)에 없음";
            putReason(byClaim, key, new EvidenceGateDecision.Reason(
                    TYPE_MATCHED_WITHOUT_EVIDENCE, skill, reason, severity));
        }
    }

    /**
     * 사용자 노출 텍스트 문장들에서 '보유로 단정'(보유 표현 있고 결핍·부정 표현 없음)했는데 사용자 원본 근거에 없는
     * 공고 요구 역량을 찾아 reason 으로 만든다. claim 기준 중복 제거(최고 심각도 유지).
     */
    private void auditTextClaims(List<String> texts,
                                 List<SkillClaim> detectionRequirements,
                                 Set<String> userEvidenceKeys,
                                 Set<String> requiredKeys,
                                 Map<String, EvidenceGateDecision.Reason> byClaim) {
        if (detectionRequirements.isEmpty()) {
            return;
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String sentence : text.split("\\.(?![A-Za-z0-9])|[!?。\\n]")) {
                if (sentence == null || sentence.isBlank()) {
                    continue;
                }
                if (firstContaining(sentence, POSSESSION) == null || firstContaining(sentence, LACK) != null) {
                    continue; // 보유 표현이 없거나 결핍·부정 문맥이면 위반 아님
                }
                for (SkillClaim skill : detectionRequirements) {
                    if (userEvidenceKeys.contains(skill.key())
                            || !skillAliasNormalizer.containsCanonicalMention(sentence, skill.key())) {
                        continue; // 실제 보유했거나 문장에 안 나오면 위반 아님
                    }
                    String severity = severityFor(skill.key(), requiredKeys);
                    String reason = EvidenceGateDecision.SEVERITY_CRITICAL.equals(severity)
                            ? "필수 요구 역량을 보유로 단정했으나 사용자 원본 근거 없음"
                            : "공고 요구 역량을 보유로 단정했으나 사용자 원본 근거 없음";
                    putReason(byClaim, skill.key(), new EvidenceGateDecision.Reason(
                            TYPE_REQUIREMENT_AS_OWNED, skill.claim(), reason, severity));
                }
            }
        }
    }

    /** 사용자에게 노출될 가능성이 높은 텍스트(명확한 accessor 만 — reflection 미사용). */
    private static List<String> userFacingTexts(FitAnalysisAiResult ai) {
        List<String> texts = new ArrayList<>();
        addText(texts, ai.strategy());
        addTexts(texts, ai.scoreBasis());
        addTexts(texts, ai.strategyActions());
        if (ai.applyDecision() != null) {
            addTexts(texts, ai.applyDecision().reasons());
            addTexts(texts, ai.applyDecision().actions());
        }
        return texts;
    }

    /** required 면 critical, 그 외(우대 또는 공고 요구 밖)면 warning. */
    private static String severityFor(String key, Set<String> requiredKeys) {
        return requiredKeys.contains(key)
                ? EvidenceGateDecision.SEVERITY_CRITICAL : EvidenceGateDecision.SEVERITY_WARNING;
    }

    /** claim 중복 제거: 비어 있으면 추가, 기존이 warning 이고 신규가 critical 이면 교체. */
    private static void putReason(Map<String, EvidenceGateDecision.Reason> byClaim,
                                  String key,
                                  EvidenceGateDecision.Reason reason) {
        EvidenceGateDecision.Reason existing = byClaim.get(key);
        if (existing == null
                || (EvidenceGateDecision.SEVERITY_CRITICAL.equals(reason.severity())
                    && !EvidenceGateDecision.SEVERITY_CRITICAL.equals(existing.severity()))) {
            byClaim.put(key, reason);
        }
    }

    private static void addText(List<String> out, String value) {
        if (value != null && !value.isBlank()) {
            out.add(value);
        }
    }

    private static void addTexts(List<String> out, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addText(out, value);
        }
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

    private static List<String> concat(List<String> a, List<String> b) {
        return concat(a, b, List.of());
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

    private Set<String> canonicalKeys(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                String key = skillAliasNormalizer.canonicalize(value);
                if (!key.isBlank()) {
                    out.add(key);
                }
            }
        }
        return out;
    }

    private List<SkillClaim> skillClaims(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<SkillClaim> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                String key = skillAliasNormalizer.canonicalize(value);
                if (!key.isBlank() && seen.add(value.trim().toLowerCase(Locale.ROOT) + "\n" + key)) {
                    out.add(new SkillClaim(value.trim(), key));
                }
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

    private record SkillClaim(String claim, String key) {
    }
}
