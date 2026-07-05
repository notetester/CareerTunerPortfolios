package com.careertuner.companyanalysis.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 기업분석 payload 저장 전 정규화기(6단계 1차안 · DB 무변경).
 *
 * <p>사용자 직접 생성({@code CompanyAnalysisService})과 자동 파이프라인
 * ({@code ApplicationCaseAutoPipelineService}) 두 저장 경로가 이 helper 하나를 공유한다.
 * provider(자체모델/Claude/OpenAI)와 무관하게 보정 정책은 동일하다.
 *
 * <p>책임:
 * <ul>
 *   <li>verifiedFacts evidence gate — evidence 가 입력 원문에 정규화 매칭되는지 확인하고,
 *       실패한 fact 는 제거하거나 {@code aiInferences confidence=LOW} 로 강등한다.</li>
 *   <li>2소스 확장(235 §3 · D-2) — WEB 근거({@code CompanyWebEvidence})가 주어지면 공고 corpus
 *       매칭 실패 fact 를 WEB 스니펫과 추가 대조해 SUPPORTED 경로를 넓히고, WEB 통과 fact 는
 *       {@code sourceKind=WEB} + URL sourceRef 를 보존한다. WEB 근거가 없으면 종전과 동일하며
 *       기존 강등/제거 판정은 완화하지 않는다. 자유서술 guard 는 공고 corpus 기준 유지(D-3 이후 확장).</li>
 *   <li>누락/중복 factId·inferenceId 보정, sourceKind 허용값 제한, sourceRef 보정.</li>
 *   <li>basedOn 이 존재하지 않는 factId 를 참조하면 제거하고, 근거 없는 추론은 LOW 로 강등.</li>
 *   <li>unknowns 를 저장 직전 {@code aiInferences kind=UNKNOWN} 마커로 접고(DB 무변경),
 *       조회/응답 직전 다시 분리해 표시용 virtual unknowns 로 펼친다.</li>
 *   <li>자유서술(companySummary/recentIssues/interviewPoints)은 기계적으로 원문 대조 가능한
 *       항목(URL, 숫자+단위)만 문장 단위로 차단한다. 완전한 자연어 claim parser 는 아니다.</li>
 *   <li>sources 를 provider 와 무관하게 {@code {type,label}} 객체 배열로 통일한다.</li>
 * </ul>
 *
 * <p>하위 호환: 기존 레코드에 새 키가 없어도 읽기 경로(unfold/strip)는 그대로 동작하고,
 * sourceKind 부재 시 공고문-only 레코드는 {@code JOB_POSTING} 으로 간주한다.
 *
 * <p>unknown 마커의 소유권은 서버에 있다. 응답에서는 마커를 aiInferences 에서 분리해 내리고,
 * 사용자 검수 저장 시 {@link #mergeUnknownMarkers} 가 기존 마커를 재부착하므로,
 * 프런트 additive key 보존 여부와 무관하게 마커가 일반 추론으로 오염되거나 유실되지 않는다
 * (231 문서 5-7 의 "프런트 보존 배포 전 마커 저장 비활성" 릴리스 제약을 구조로 해소).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BCompanyAnalysisCanonicalizer {

    public static final String KIND_UNKNOWN = "UNKNOWN";
    public static final String SOURCE_KIND_JOB_POSTING = "JOB_POSTING";
    public static final String SOURCE_KIND_WEB = "WEB";

    /** gate 처리 결과 — 하네스 계측과 로그용. */
    public enum GateOutcome { PASSED, DEMOTED, REMOVED }

    public record GateAction(String ref, String field, GateOutcome action, String detail) {
    }

    public record CanonicalCompanyAnalysis(CompanyAnalysisPayload payload, List<GateAction> gateActions) {
    }

    private static final Set<String> ALLOWED_SOURCE_KINDS =
            Set.of("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO", "WEB");
    private static final Set<String> CONFIDENCE_VALUES = Set.of("HIGH", "MEDIUM", "LOW");

    // evidence 정규화 매칭 파라미터. 너무 엄격하면 OCR spacing 으로 정상 fact 를 과제거하고,
    // 너무 느슨하면 "가상화"→"가상화폐" 같은 의미 확장을 통과시킨다(231 문서 12 리스크).
    private static final int MIN_EVIDENCE_NORMALIZED_LENGTH = 6;
    private static final int MIN_SEGMENT_NORMALIZED_LENGTH = 6;
    private static final int MIN_LONG_SEGMENT_NORMALIZED_LENGTH = 10;
    private static final double SEGMENT_COVERAGE_THRESHOLD = 0.7;
    private static final double FACT_GROUNDING_THRESHOLD = 0.5;

    private static final Pattern NON_MATCH_CHARS = Pattern.compile("[^0-9a-z가-힣]+");
    private static final Pattern EVIDENCE_SEGMENT_SPLIT = Pattern.compile("[\\r\\n,.;:·•|/()\\[\\]{}\"'‘’“”]+");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^0-9a-zA-Z가-힣]+");
    // 요건 강도 표현 — fact 와 evidence 의 강도가 서로 다르면(필수→선호/우대) 왜곡으로 본다.
    private static final Pattern REQUIRED_STRENGTH = Pattern.compile("필수");
    private static final Pattern PREFERRED_STRENGTH = Pattern.compile("우대|선호");
    // 자유서술 기계 대조 대상: URL 토큰과 숫자+단위. 고유명사/문장 단위 claim 연결은
    // 1차안에서 자동 차단하지 않고 하네스 계측으로 남긴다(231 문서 5-5).
    private static final Pattern URL_TOKEN = Pattern.compile(
            "(?i)(?:https?://\\S+|www\\.[\\w.\\-]+|[\\w\\-]+\\.(?:com|net|io|ai|kr|co\\.kr)(?:/[\\w./\\-]*)?)");
    private static final Pattern NUMBER_UNIT = Pattern.compile(
            "\\d[\\d,.]*\\s*(?:년|명|억|조|만원|백만|천만|%|퍼센트|위|개국|호점)");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?…])\\s+|(?<=다\\.)\\s*|\\n+");
    private static final Pattern FACT_ID_FORMAT = Pattern.compile("F\\d{1,4}");
    private static final Pattern INFERENCE_ID_FORMAT = Pattern.compile("I\\d{1,4}");
    private static final String DUPLICATE_REMOVAL_DETAIL = "반복 중복 제거";
    // D-6 이슈B: 저장 자유서술·텍스트 필드에 누출된 입력블록 라벨([웹 검색 근거]) 결정적 제거용.
    // 대괄호 안 '웹/검색/근거' 사이 공백을 관대하게 매칭한다 — 대괄호 없는 정상 source 라벨("웹검색")은
    // 대괄호가 없어 이 패턴에 걸리지 않으므로 보존된다.
    private static final Pattern INPUT_BLOCK_LABEL = Pattern.compile("\\[\\s*웹\\s*검색\\s*근거\\s*\\]");
    // 라벨 제거로 생긴 연속 공백/탭만 정리(개행은 보존). 문법 정리는 best-effort.
    private static final Pattern COLLAPSE_SPACES = Pattern.compile("[ \\t\\x0B\\f]{2,}");

    private final ObjectMapper objectMapper;

    /**
     * 모델 출력 payload 를 저장 직전 한 번 정규화한다. unknowns 는 aiInferences 의
     * {@code kind=UNKNOWN} 마커로 접혀 반환 payload 의 {@code unknowns} 는 빈 배열이 된다.
     *
     * <p>기존 단일소스(공고) 진입점 — WEB 근거 없이 호출하면 종전 동작과 완전히 동일하다.
     */
    public CanonicalCompanyAnalysis canonicalizeForStorage(CompanyAnalysisPayload payload,
                                                           Long jobPostingId,
                                                           Integer jobPostingRevision,
                                                           String postingText,
                                                           String companyName,
                                                           String jobTitle) {
        return canonicalizeForStorage(payload, jobPostingId, jobPostingRevision,
                postingText, companyName, jobTitle, List.of());
    }

    /**
     * 2소스 확장 진입점(235 §3 · D-2). verifiedFacts 대조에 WEB 근거를 <b>추가</b>한다 —
     * 공고 corpus 또는 WEB 근거 어느 쪽으로든 SUPPORTED 면 통과하고, WEB 으로 통과한 fact 는
     * {@code sourceKind=WEB} + URL sourceRef 를 보존한다. WEB 근거가 빈 목록이면 기존
     * 단일소스 경로와 동일하게 동작하며, 기존 DEMOTE/REMOVE 판정은 완화하지 않는다.
     * 자유서술 guard({@link #guardFreeText})는 공고 corpus 기준을 유지한다(WEB 확장은 D-3 이후).
     */
    public CanonicalCompanyAnalysis canonicalizeForStorage(CompanyAnalysisPayload payload,
                                                           Long jobPostingId,
                                                           Integer jobPostingRevision,
                                                           String postingText,
                                                           String companyName,
                                                           String jobTitle,
                                                           List<CompanyWebEvidence> webEvidence) {
        List<GateAction> actions = new ArrayList<>();
        // 회사명/직무명은 기업분석의 1급 입력이라 원문 매칭 코퍼스에 포함한다(프롬프트 계약의 source 후보와 동일).
        String rawCorpus = String.join("\n", nullToEmpty(postingText), nullToEmpty(companyName), nullToEmpty(jobTitle));
        String corpus = normalizeForMatch(rawCorpus);

        ArrayNode facts = parseArray(payload.verifiedFacts());
        ArrayNode inferences = parseArray(payload.aiInferences());
        ArrayNode unknowns = parseArray(payload.unknowns());

        ArrayNode keptFacts = objectMapper.createArrayNode();
        List<ObjectNode> demoted = new ArrayList<>();
        if (facts != null) {
            gateVerifiedFacts(facts, corpus, buildWebCorpus(webEvidence), keptFacts, demoted, actions);
        }
        assignSequentialIds(keptFacts, "factId", "F", FACT_ID_FORMAT);
        Set<String> factIds = collectIds(keptFacts, "factId");
        String sourceRef = jobPostingId == null
                ? null
                : "jobPosting:%d#rev%d".formatted(jobPostingId, jobPostingRevision == null ? 1 : jobPostingRevision);
        for (JsonNode node : keptFacts) {
            canonicalizeFactSource((ObjectNode) node, sourceRef);
        }

        ArrayNode keptInferences = objectMapper.createArrayNode();
        ArrayNode unknownMarkers = objectMapper.createArrayNode();
        if (inferences != null) {
            canonicalizeInferences(inferences, factIds, keptInferences, unknownMarkers, actions);
        }
        for (ObjectNode node : demoted) {
            keptInferences.add(node);
        }
        assignSequentialIds(keptInferences, "inferenceId", "I", INFERENCE_ID_FORMAT);
        if (unknowns != null) {
            foldUnknowns(unknowns, unknownMarkers);
        }
        keptInferences.addAll(unknownMarkers);

        // D-6 이슈B: gate·ID·구조 필드 확정 이후, 저장 직전 텍스트 필드에서만 대괄호 입력블록 라벨을 제거한다.
        // 구조 필드(sourceRef/sourceKind/factId/inferenceId/basedOn/confidence)는 건드리지 않으며 gate 판정도 불변이다.
        for (JsonNode node : keptFacts) {
            ObjectNode fact = (ObjectNode) node;
            sanitizeTextField(fact, "fact");
            sanitizeTextField(fact, "evidence");
            sanitizeTextField(fact, "source");
        }
        for (JsonNode node : keptInferences) {
            ObjectNode inference = (ObjectNode) node;
            sanitizeTextField(inference, "inference");
            sanitizeTextField(inference, "basis");
            // 접힌 UNKNOWN 마커의 표시 텍스트 필드도 정리한다(topic/neededSource 는 마커에만 존재).
            sanitizeTextField(inference, "topic");
            sanitizeTextField(inference, "neededSource");
        }

        String companySummary = stripInputBlockLabels(guardFreeText("companySummary", payload.companySummary(), corpus, actions));
        if (isBlank(companySummary)) {
            companySummary = CompanyAnalysisPromptCatalog.COMPANY_SUMMARY_UNAVAILABLE_NOTICE;
        }
        String recentIssues = stripInputBlockLabels(guardFreeText("recentIssues", payload.recentIssues(), corpus, actions));
        if (isBlank(recentIssues)) {
            recentIssues = CompanyAnalysisPromptCatalog.RECENT_ISSUES_UNAVAILABLE_NOTICE;
        }
        String interviewPoints = stripInputBlockLabels(guardFreeText("interviewPoints", payload.interviewPoints(), corpus, actions));

        CompanyAnalysisPayload canonical = new CompanyAnalysisPayload(
                companySummary,
                recentIssues,
                payload.industry(),
                sanitizeCompetitors(payload.competitors()),
                interviewPoints,
                canonicalizeSources(payload.sources()),
                writeJson(keptFacts, payload.verifiedFacts()),
                writeJson(keptInferences, payload.aiInferences()),
                "[]",
                payload.usage());
        return new CanonicalCompanyAnalysis(canonical, actions);
    }

    // ── unknowns 접기/펼치기 (읽기 경로 · 하위 호환: 마커 없는 기존 레코드는 그대로 통과) ──

    /** 저장된 aiInferences 에서 {@code kind=UNKNOWN} 마커를 표시용 unknowns 배열로 펼친다. */
    public String extractUnknowns(String aiInferencesJson) {
        ArrayNode array = parseArray(aiInferencesJson);
        if (array == null) {
            return "[]";
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode node : array) {
            if (!isUnknownMarker(node)) {
                continue;
            }
            ObjectNode unknown = objectMapper.createObjectNode();
            unknown.put("topic", firstNonBlank(text(node, "topic"), text(node, "inference")));
            unknown.put("reason", firstNonBlank(text(node, "reason"), text(node, "basis")));
            String neededSource = text(node, "neededSource");
            if (!isBlank(neededSource)) {
                unknown.put("neededSource", neededSource);
            }
            out.add(unknown);
        }
        return writeJson(out, "[]");
    }

    /** 저장된 aiInferences 에서 {@code kind=UNKNOWN} 마커를 제거한 표시/편집용 배열을 반환한다. */
    public String withoutUnknownMarkers(String aiInferencesJson) {
        ArrayNode array = parseArray(aiInferencesJson);
        if (array == null) {
            return aiInferencesJson;
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode node : array) {
            if (!isUnknownMarker(node)) {
                out.add(node);
            }
        }
        return writeJson(out, aiInferencesJson);
    }

    /**
     * 사용자 검수 저장용 병합 — 편집된 aiInferences 에 기존 레코드의 unknown 마커를 재부착한다.
     * 편집본에 마커가 섞여 들어와도(비정상 클라이언트) 중복 방지를 위해 먼저 걷어낸다.
     */
    public String mergeUnknownMarkers(String editedAiInferencesJson, String previousAiInferencesJson) {
        ArrayNode edited = parseArray(editedAiInferencesJson);
        if (edited == null) {
            return editedAiInferencesJson;
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode node : edited) {
            if (!isUnknownMarker(node)) {
                out.add(node);
            }
        }
        ArrayNode previous = parseArray(previousAiInferencesJson);
        if (previous != null) {
            for (JsonNode node : previous) {
                if (isUnknownMarker(node)) {
                    out.add(node);
                }
            }
        }
        return writeJson(out, editedAiInferencesJson);
    }

    // ── verifiedFacts evidence gate ──

    private void gateVerifiedFacts(ArrayNode facts,
                                   String corpus,
                                   List<WebCorpusEntry> webCorpus,
                                   ArrayNode kept,
                                   List<ObjectNode> demoted,
                                   List<GateAction> actions) {
        int index = 0;
        Set<String> seenFactTexts = new LinkedHashSet<>();
        Set<String> seenFactEvidencePairs = new LinkedHashSet<>();
        for (JsonNode item : facts) {
            String ref = "verifiedFacts[" + index++ + "]";
            if (!item.isObject()) {
                actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.REMOVED, "객체가 아닌 항목"));
                continue;
            }
            ObjectNode fact = (ObjectNode) item.deepCopy();
            String factText = text(fact, "fact");
            if (isBlank(factText)) {
                actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.REMOVED, "fact 누락"));
                continue;
            }
            String evidence = text(fact, "evidence");
            String normalizedFact = normalizeForMatch(factText);
            String normalizedEvidence = normalizeForMatch(evidence);
            String factEvidenceKey = normalizedFact + "|" + normalizedEvidence;
            if ((!isBlank(normalizedFact) && !seenFactTexts.add(normalizedFact))
                    || !seenFactEvidencePairs.add(factEvidenceKey)) {
                actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.REMOVED, DUPLICATE_REMOVAL_DETAIL));
                continue;
            }
            if (isBlank(evidence)) {
                // evidence 미제공(구 스키마·self-rules 포함): fact 자체가 원문에 접지되면 유지, 아니면 강등.
                if (groundingRatio(factText, corpus) >= FACT_GROUNDING_THRESHOLD) {
                    actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.PASSED, "evidence 없음 — fact 원문 접지 확인"));
                    clearUnverifiedWebClaim(fact);
                    kept.add(fact);
                    continue;
                }
                // 2소스 확장(D-2): 공고 접지 실패 시 WEB 근거 접지를 추가로 본다. 실패 판정은 기존 그대로.
                WebMatch webMatch = matchWebGrounding(factText, webCorpus);
                if (webMatch.supported()) {
                    actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.PASSED,
                            "evidence 없음 — WEB 근거 접지 확인: " + webMatch.url()));
                    markWebSupported(fact, webMatch.url());
                    kept.add(fact);
                } else {
                    actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.DEMOTED,
                            appendWebRefMissing("evidence 없음 + fact 원문 미접지", webMatch)));
                    demoted.add(demoteFactToInference(factText,
                            "공고문 등 입력 원문에서 근거 인용을 확인하지 못해 검증된 사실에서 강등되었습니다."));
                }
                continue;
            }
            if (!evidenceMatches(evidence, corpus)) {
                // 2소스 확장(D-2): 공고 corpus 매칭 실패 시 WEB 근거 매칭을 추가로 본다.
                WebMatch webMatch = matchWebEvidence(evidence, webCorpus);
                if (webMatch.supported()) {
                    String webDistortion = strengthDistortion(factText, evidence);
                    if (webDistortion != null) {
                        actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.DEMOTED, webDistortion));
                        demoted.add(demoteFactToInference(factText,
                                "원문의 요건 강도 표현과 다르게 서술되어 검증된 사실에서 강등되었습니다. 원문 근거: " + truncate(evidence, 120)));
                    } else {
                        actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.PASSED,
                                "WEB 근거 매칭: " + webMatch.url()));
                        markWebSupported(fact, webMatch.url());
                        kept.add(fact);
                    }
                    continue;
                }
                if (groundingRatio(factText, corpus) >= FACT_GROUNDING_THRESHOLD) {
                    actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.DEMOTED,
                            appendWebRefMissing("evidence 원문 매칭 실패: " + truncate(evidence, 80), webMatch)));
                    demoted.add(demoteFactToInference(factText,
                            "제시된 근거 인용이 입력 원문과 일치하지 않아 검증된 사실에서 강등되었습니다."));
                } else {
                    actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.REMOVED,
                            appendWebRefMissing("evidence·fact 모두 원문 미확인: " + truncate(evidence, 80), webMatch)));
                }
                continue;
            }
            String distortion = strengthDistortion(factText, evidence);
            if (distortion != null) {
                actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.DEMOTED, distortion));
                demoted.add(demoteFactToInference(factText,
                        "원문의 요건 강도 표현과 다르게 서술되어 검증된 사실에서 강등되었습니다. 원문 근거: " + truncate(evidence, 120)));
                continue;
            }
            actions.add(new GateAction(ref, "verifiedFacts", GateOutcome.PASSED, null));
            clearUnverifiedWebClaim(fact);
            kept.add(fact);
        }
    }

    // ── WEB 근거 corpus (2소스 확장 · D-2) ──

    /** WEB 근거 corpus 항목 — 정규화된 대조 텍스트와 sourceRef 용 URL. */
    private record WebCorpusEntry(String url, String normalizedText) {
        boolean hasUrl() {
            return url != null && !url.isBlank();
        }
    }

    /**
     * WEB 대조 결과. {@code url != null} 이면 SUPPORTED. {@code blankUrlMatch} 는 텍스트는 매칭됐지만
     * URL 이 없는 근거뿐인 상태 — SUPPORTED 로 치지 않고 기존 강등/제거 판정에 사유만 남긴다
     * (WEB fact 의 sourceRef 는 URL 이어야 한다는 계약).
     */
    private record WebMatch(String url, boolean blankUrlMatch) {
        static final WebMatch NONE = new WebMatch(null, false);

        boolean supported() {
            return url != null;
        }
    }

    private List<WebCorpusEntry> buildWebCorpus(List<CompanyWebEvidence> webEvidence) {
        if (webEvidence == null || webEvidence.isEmpty()) {
            return List.of();
        }
        List<WebCorpusEntry> entries = new ArrayList<>();
        for (CompanyWebEvidence evidence : webEvidence) {
            if (evidence == null) {
                continue;
            }
            String normalized = normalizeForMatch(
                    nullToEmpty(evidence.title()) + "\n" + nullToEmpty(evidence.snippet()));
            if (normalized.isEmpty()) {
                continue;
            }
            entries.add(new WebCorpusEntry(evidence.url(), normalized));
        }
        return entries;
    }

    /** evidence 인용이 어떤 WEB 근거 텍스트에 매칭되는지 — 판정 기준은 공고 corpus 와 동일한 {@link #evidenceMatches}. */
    private WebMatch matchWebEvidence(String evidence, List<WebCorpusEntry> webCorpus) {
        boolean blankUrlMatch = false;
        for (WebCorpusEntry entry : webCorpus) {
            if (evidenceMatches(evidence, entry.normalizedText())) {
                if (entry.hasUrl()) {
                    return new WebMatch(entry.url(), false);
                }
                blankUrlMatch = true;
            }
        }
        return blankUrlMatch ? new WebMatch(null, true) : WebMatch.NONE;
    }

    /** fact 텍스트가 어떤 WEB 근거에 접지되는지 — 판정 기준은 공고 corpus 와 동일한 {@link #groundingRatio}. */
    private WebMatch matchWebGrounding(String factText, List<WebCorpusEntry> webCorpus) {
        boolean blankUrlMatch = false;
        for (WebCorpusEntry entry : webCorpus) {
            if (groundingRatio(factText, entry.normalizedText()) >= FACT_GROUNDING_THRESHOLD) {
                if (entry.hasUrl()) {
                    return new WebMatch(entry.url(), false);
                }
                blankUrlMatch = true;
            }
        }
        return blankUrlMatch ? new WebMatch(null, true) : WebMatch.NONE;
    }

    /** WEB 근거로 SUPPORTED 확정 — gate 가 sourceKind=WEB 과 URL sourceRef 를 직접 기록한다. */
    private static void markWebSupported(ObjectNode fact, String url) {
        fact.put("sourceKind", SOURCE_KIND_WEB);
        fact.put("sourceRef", url);
    }

    /**
     * 공고 corpus 로 통과한 fact 의 모델 주장 {@code sourceKind=WEB} 은 걷어낸다.
     * 불변식: 저장되는 sourceKind=WEB ⟺ gate 가 검증한 URL sourceRef. (걷어낸 fact 는
     * {@link #canonicalizeFactSource} 기본값 JOB_POSTING 을 받아 종전 동작과 동일해진다.)
     */
    private static void clearUnverifiedWebClaim(ObjectNode fact) {
        if (SOURCE_KIND_WEB.equals(text(fact, "sourceKind"))) {
            fact.remove("sourceKind");
        }
    }

    private static String appendWebRefMissing(String detail, WebMatch webMatch) {
        return webMatch.blankUrlMatch() ? detail + " (WEB sourceRef 누락)" : detail;
    }

    private ObjectNode demoteFactToInference(String factText, String basis) {
        ObjectNode inference = objectMapper.createObjectNode();
        inference.put("inference", factText);
        inference.put("basis", basis);
        inference.put("confidence", "LOW");
        inference.set("basedOn", objectMapper.createArrayNode());
        return inference;
    }

    /**
     * evidence 가 입력 원문에 존재하는지 보수적으로 판정한다.
     * whitespace/개행/문장부호(OCR spacing) 차이는 정규화로 허용하되, 정규화 후 전체 문자열
     * 또는 의미 있는 긴 부분 문자열이 실제로 원문에 존재해야 한다. 접두어만 같은 의미 확장
     * ("가상화"→"가상화폐")과 OCR 파편 임의 보정("Wwwkaoncokr"→"www.kaoon.com.kr")은 통과하지 않는다.
     */
    boolean evidenceMatches(String evidence, String normalizedCorpus) {
        String normalized = normalizeForMatch(evidence);
        if (normalized.length() < MIN_EVIDENCE_NORMALIZED_LENGTH) {
            // 짧은 토큰 하나만으로는 통과시키지 않는다.
            return false;
        }
        if (normalizedCorpus.contains(normalized)) {
            return true;
        }
        // 부분 매칭: evidence 를 문장부호 기준 조각으로 나눠, 의미 있는 길이의 조각이
        // 충분히(길이 가중 70% 이상 + 긴 조각 1개 이상) 원문에 존재해야 한다.
        int totalLength = 0;
        int matchedLength = 0;
        int longestMatched = 0;
        for (String segment : EVIDENCE_SEGMENT_SPLIT.split(evidence)) {
            String normalizedSegment = normalizeForMatch(segment);
            if (normalizedSegment.length() < MIN_SEGMENT_NORMALIZED_LENGTH) {
                continue;
            }
            totalLength += normalizedSegment.length();
            if (normalizedCorpus.contains(normalizedSegment)) {
                matchedLength += normalizedSegment.length();
                longestMatched = Math.max(longestMatched, normalizedSegment.length());
            }
        }
        return totalLength > 0
                && longestMatched >= MIN_LONG_SEGMENT_NORMALIZED_LENGTH
                && (double) matchedLength / totalLength >= SEGMENT_COVERAGE_THRESHOLD;
    }

    /** fact 와 evidence 의 요건 강도(필수 vs 우대/선호)가 다르면 왜곡 사유를 반환한다. */
    private String strengthDistortion(String factText, String evidence) {
        boolean factRequired = REQUIRED_STRENGTH.matcher(factText).find();
        boolean factPreferred = PREFERRED_STRENGTH.matcher(factText).find();
        boolean evidenceRequired = REQUIRED_STRENGTH.matcher(evidence).find();
        boolean evidencePreferred = PREFERRED_STRENGTH.matcher(evidence).find();
        if (evidenceRequired && !evidencePreferred && factPreferred && !factRequired) {
            return "요건 강도 왜곡: 원문 '필수' → 출력 '선호/우대'";
        }
        if (evidencePreferred && !evidenceRequired && factRequired && !factPreferred) {
            return "요건 강도 왜곡: 원문 '우대/선호' → 출력 '필수'";
        }
        return null;
    }

    /** fact 텍스트 토큰이 원문에 얼마나 접지되는지(0~1). 짧은 조사·기호는 제외한다. */
    private double groundingRatio(String text, String normalizedCorpus) {
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return 0;
        }
        long hits = tokens.stream().filter(normalizedCorpus::contains).count();
        return (double) hits / tokens.size();
    }

    // ── fact source/aiInferences 보정 ──

    private void canonicalizeFactSource(ObjectNode fact, String sourceRef) {
        String sourceKind = text(fact, "sourceKind");
        if (SOURCE_KIND_WEB.equals(sourceKind)) {
            // gate 가 WEB SUPPORTED 로 확정하며 URL sourceRef 를 직접 기록한 fact —
            // jobPosting sourceRef 로 덮지 않는다(URL 보존, 235 §3). 모델이 주장만 한 WEB 표기는
            // gate 의 clearUnverifiedWebClaim 에서 이미 걷혀 이 분기에 오지 않는다.
            if (isBlank(text(fact, "source"))) {
                fact.put("source", "웹검색");
            }
            return;
        }
        if (!ALLOWED_SOURCE_KINDS.contains(sourceKind)) {
            // 공고문-only 1차안 기본값. sourceKind 부재 레코드도 JOB_POSTING 으로 간주한다.
            fact.put("sourceKind", SOURCE_KIND_JOB_POSTING);
        }
        if (sourceRef != null) {
            fact.put("sourceRef", sourceRef);
        }
        if (isBlank(text(fact, "source"))) {
            // 기존 source 문자열 필드는 하위 호환을 위해 유지·보충한다.
            fact.put("source", "채용공고");
        }
    }

    private void canonicalizeInferences(ArrayNode inferences,
                                        Set<String> factIds,
                                        ArrayNode kept,
                                        ArrayNode unknownMarkers,
                                        List<GateAction> actions) {
        int index = 0;
        Set<String> seenInferenceBasisPairs = new LinkedHashSet<>();
        for (JsonNode item : inferences) {
            String ref = "aiInferences[" + index++ + "]";
            if (!item.isObject()) {
                actions.add(new GateAction(ref, "aiInferences", GateOutcome.REMOVED, "객체가 아닌 항목"));
                continue;
            }
            ObjectNode inference = (ObjectNode) item.deepCopy();
            if (isUnknownMarker(inference)) {
                // 모델이 마커 형태로 직접 출력한 경우 — 일반 추론으로 오염시키지 않고 마커로 유지.
                unknownMarkers.add(inference);
                continue;
            }
            if (isBlank(text(inference, "inference"))) {
                actions.add(new GateAction(ref, "aiInferences", GateOutcome.REMOVED, "inference 누락"));
                continue;
            }
            String inferenceKey = normalizeForMatch(text(inference, "inference"));
            String basisKey = normalizeForMatch(text(inference, "basis"));
            String inferenceBasisKey = inferenceKey + "|" + basisKey;
            if (!seenInferenceBasisPairs.add(inferenceBasisKey)) {
                actions.add(new GateAction(ref, "aiInferences", GateOutcome.REMOVED, DUPLICATE_REMOVAL_DETAIL));
                continue;
            }
            boolean brokenBasedOn = filterBasedOn(inference, factIds);
            String confidence = text(inference, "confidence").toUpperCase(Locale.ROOT);
            boolean noGrounding = basedOnIsEmpty(inference) && isBlank(text(inference, "basis"));
            if (brokenBasedOn || noGrounding) {
                inference.put("confidence", "LOW");
                actions.add(new GateAction(ref, "aiInferences", GateOutcome.DEMOTED,
                        brokenBasedOn ? "basedOn 참조 factId 없음 → confidence=LOW" : "근거 없는 추론 → confidence=LOW"));
            } else if (CONFIDENCE_VALUES.contains(confidence)) {
                inference.put("confidence", confidence);
            } else {
                inference.put("confidence", basedOnIsEmpty(inference) ? "LOW" : "MEDIUM");
            }
            kept.add(inference);
        }
    }

    /** basedOn 에서 존재하지 않는 factId 참조를 제거한다. 원래 있었는데 전부 깨졌으면 true. */
    private boolean filterBasedOn(ObjectNode inference, Set<String> factIds) {
        JsonNode basedOn = inference.path("basedOn");
        if (!basedOn.isArray()) {
            inference.remove("basedOn");
            return false;
        }
        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode refNode : basedOn) {
            if (refNode.isString() && factIds.contains(refNode.asText())) {
                filtered.add(refNode.asText());
            }
        }
        inference.set("basedOn", filtered);
        return basedOn.size() > 0 && filtered.isEmpty();
    }

    private boolean basedOnIsEmpty(ObjectNode inference) {
        JsonNode basedOn = inference.path("basedOn");
        return !basedOn.isArray() || basedOn.isEmpty();
    }

    private void foldUnknowns(ArrayNode unknowns, ArrayNode unknownMarkers) {
        for (JsonNode item : unknowns) {
            if (!item.isObject()) {
                continue;
            }
            String topic = text(item, "topic");
            if (isBlank(topic)) {
                continue;
            }
            String reason = firstNonBlank(text(item, "reason"), "공고문에 관련 정보가 없습니다.");
            ObjectNode marker = objectMapper.createObjectNode();
            marker.put("inference", topic + koreanTopicParticle(topic) + " 현재 입력 자료로 확인되지 않습니다.");
            marker.put("basis", reason);
            marker.put("kind", KIND_UNKNOWN);
            marker.put("topic", topic);
            String neededSource = text(item, "neededSource");
            if (!isBlank(neededSource)) {
                marker.put("neededSource", neededSource);
            }
            unknownMarkers.add(marker);
        }
    }

    private boolean isUnknownMarker(JsonNode node) {
        return node.isObject() && KIND_UNKNOWN.equals(node.path("kind").asText(""));
    }

    // ── ID 보정 ──

    /** 형식에 맞고 중복되지 않은 기존 ID 는 유지하고, 누락/중복 항목에만 빈 번호를 순서대로 배정한다. */
    private void assignSequentialIds(ArrayNode items, String idField, String prefix, Pattern format) {
        Set<String> used = new LinkedHashSet<>();
        for (JsonNode node : items) {
            String id = text(node, idField);
            if (format.matcher(id).matches() && !used.contains(id)) {
                used.add(id);
            }
        }
        int next = 1;
        for (JsonNode node : items) {
            ObjectNode item = (ObjectNode) node;
            String id = text(item, idField);
            if (format.matcher(id).matches() && used.contains(id)) {
                used.remove(id);
                continue;
            }
            String candidate;
            do {
                candidate = prefix + next++;
            } while (used.contains(candidate) || containsId(items, idField, candidate));
            item.put(idField, candidate);
        }
    }

    private boolean containsId(ArrayNode items, String idField, String candidate) {
        for (JsonNode node : items) {
            if (candidate.equals(text(node, idField))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> collectIds(ArrayNode items, String idField) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode node : items) {
            String id = text(node, idField);
            if (!isBlank(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    // ── 자유서술 guard (기계 대조 가능한 항목만) ──

    /**
     * URL·숫자+단위처럼 기계적으로 원문 대조가 가능한 단정이 원문에 없으면 해당 문장만 제거한다.
     * 문장 전체의 fact/inference 연결 여부는 자동 차단하지 않는다(하네스 계측 대상).
     */
    private String guardFreeText(String field, String value, String normalizedCorpus, List<GateAction> actions) {
        if (isBlank(value)) {
            return value;
        }
        List<String> keptSentences = new ArrayList<>();
        boolean changed = false;
        for (String sentence : SENTENCE_SPLIT.split(value)) {
            if (sentence.isBlank()) {
                continue;
            }
            String violation = mechanicalViolation(sentence, normalizedCorpus);
            if (violation == null) {
                keptSentences.add(sentence.trim());
            } else {
                changed = true;
                actions.add(new GateAction(field, field, GateOutcome.REMOVED,
                        violation + " — 문장 제거: " + truncate(sentence.trim(), 100)));
            }
        }
        if (!changed) {
            return value;
        }
        return String.join(" ", keptSentences);
    }

    private String mechanicalViolation(String sentence, String normalizedCorpus) {
        Matcher url = URL_TOKEN.matcher(sentence);
        while (url.find()) {
            String normalized = normalizeForMatch(url.group());
            if (!normalized.isEmpty() && !normalizedCorpus.contains(normalized)) {
                return "원문에 없는 URL(" + url.group() + ")";
            }
        }
        Matcher numberUnit = NUMBER_UNIT.matcher(sentence);
        while (numberUnit.find()) {
            String normalized = normalizeForMatch(numberUnit.group());
            if (!normalized.isEmpty() && !normalizedCorpus.contains(normalized)) {
                return "원문에 없는 수치(" + numberUnit.group() + ")";
            }
        }
        return null;
    }

    // ── sources 통일 ──

    /** OpenAI string[] 과 로컬/Claude {type,label} 혼재를 {type,label} 객체 배열로 통일한다. */
    String canonicalizeSources(String sourcesJson) {
        ArrayNode array = parseArray(sourcesJson);
        if (array == null) {
            return sourcesJson;
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode item : array) {
            if (item.isString()) {
                ObjectNode source = objectMapper.createObjectNode();
                source.put("type", SOURCE_KIND_JOB_POSTING);
                source.put("label", item.asText());
                out.add(source);
                continue;
            }
            if (item.isObject()) {
                ObjectNode itemObject = (ObjectNode) item;
                ObjectNode source = objectMapper.createObjectNode();
                String type = firstNonBlank(text(itemObject, "type"), SOURCE_KIND_JOB_POSTING);
                String label = firstNonBlank(text(itemObject, "label"), type);
                source.put("type", type);
                source.put("label", label);
                out.add(source);
            }
        }
        return writeJson(out, sourcesJson);
    }

    // ── 입력블록 라벨 sanitize (D-6 이슈B) ──

    /**
     * 대괄호 입력블록 라벨(예: {@code [웹 검색 근거]} 및 공백 변형 {@code [웹검색 근거]}·{@code [웹 검색근거]}·
     * {@code [웹검색근거]})을 결정적으로 제거한다. <b>대괄호가 필수</b>라 정상 source 라벨 {@code "웹검색"}
     * (대괄호 없음)은 절대 제거되지 않는다. 제거로 생긴 연속 공백만 정리하고(개행 보존), 그 외 문법 정리는
     * 하지 않는다(best-effort). 라벨이 없으면 원본을 그대로 반환한다.
     */
    static String stripInputBlockLabels(String value) {
        if (value == null || value.indexOf('[') < 0) {
            return value;
        }
        String stripped = INPUT_BLOCK_LABEL.matcher(value).replaceAll("");
        if (stripped.equals(value)) {
            return value;
        }
        return COLLAPSE_SPACES.matcher(stripped).replaceAll(" ").trim();
    }

    /**
     * ObjectNode 의 문자열 텍스트 필드 하나만 in-place sanitize 한다(값이 변한 경우에만 교체).
     * 구조 필드(sourceRef/sourceKind/factId/inferenceId/basedOn 등)는 호출부에서 대상에 넣지 않는다.
     */
    private void sanitizeTextField(ObjectNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || !child.isString()) {
            return;
        }
        String original = child.asString("");
        String sanitized = stripInputBlockLabels(original);
        if (!sanitized.equals(original)) {
            node.put(field, sanitized);
        }
    }

    /**
     * competitors(문자열 배열) 각 원소에서 입력블록 라벨을 제거한다. 배열이 아니면 문자열 자체에서 라벨만
     * 제거한다(라벨은 값 내부 리터럴이라 통째 제거해도 JSON 구조를 깨지 않는다). 변화가 없으면 원본을 반환한다.
     */
    String sanitizeCompetitors(String competitorsJson) {
        if (isBlank(competitorsJson)) {
            return competitorsJson;
        }
        ArrayNode array = parseArray(competitorsJson);
        if (array == null) {
            return stripInputBlockLabels(competitorsJson);
        }
        boolean changed = false;
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode item : array) {
            if (item.isString()) {
                String original = item.asString("");
                String sanitized = stripInputBlockLabels(original);
                if (!sanitized.equals(original)) {
                    changed = true;
                }
                out.add(sanitized);
            } else {
                // 문자열이 아닌 원소(비표준 provider 출력)는 구조 보존을 위해 그대로 둔다.
                out.add(item);
            }
        }
        return changed ? writeJson(out, competitorsJson) : competitorsJson;
    }

    // ── 공용 헬퍼 ──

    /** whitespace/대소문자/문장부호를 제거해 OCR spacing·개행 차이를 흡수하는 매칭용 정규화. */
    static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return NON_MATCH_CHARS.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** 받침 유무에 따른 주제 조사(은/는). 한글이 아니면 병기한다. */
    private static String koreanTopicParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "은(는)";
        }
        return (last - 0xAC00) % 28 == 0 ? "는" : "은";
    }

    private ArrayNode parseArray(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isArray() ? (ArrayNode) node : null;
        } catch (JacksonException ex) {
            return null;
        }
    }

    private String writeJson(JsonNode node, String fallback) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException ex) {
            log.warn("Company analysis canonicalizer serialization failed; keeping original", ex);
            return fallback;
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
