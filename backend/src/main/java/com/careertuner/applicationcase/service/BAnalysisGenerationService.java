package com.careertuner.applicationcase.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.BJobSentenceClassifier.Classification;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class BAnalysisGenerationService {

    public static final String SELF_RULES_MODEL = "self-rules-v1";
    private static final int COMPANY_VERIFIED_FACTS_MAX_ITEMS = 8;
    private static final int COMPANY_AI_INFERENCES_MAX_ITEMS = 4;
    private static final int COMPANY_UNKNOWNS_MAX_ITEMS = 5;
    // [웹 검색 근거] 블록 상한(무한 프롬프트 방지) — 항목 수·스니펫 길이 캡.
    private static final int WEB_EVIDENCE_BLOCK_MAX_ITEMS = 6;
    private static final int WEB_EVIDENCE_SNIPPET_MAX_CHARS = 200;

    // 경력 연차는 연차 숫자가 경력 키워드와 "결합"된 경우만 인정한다(단순 "N년" 근접 매칭은 오탐이 많음).
    // "설립 10년차"·"서비스 운영 5년"·"5년 연속 성장"(연혁/기간), "operating for 5 yrs"(단위만 있는 영어 기간)는
    // 구조적으로 배제하고, "5+ yrs exp" 같은 영어 결합은 인정한다.
    // - NOT_IRRELEVANT: 경력 키워드 뒤 부정어를 경력 연차로 오인("10년차 경력 무관")하지 않도록 배제.
    //   조사(은/는/이/가/도/에/과/와)·라벨 구분자([:：-])·띄어쓰기 변형과
    //   "무관/관계 없/상관 없/제한 없/불문" 어휘까지 포괄("경력: 무관"·"경력에 상관없이"·"경력과 무관하게" 등).
    // - \b...\b: "exp"가 "exported"·"expert" 같은 다른 단어에 걸리지 않도록 단어 경계 강제.
    // - [^\d\n]{0,N}: 다른 숫자나 줄바꿈을 넘지 않는 좁은 간격만 허용.
    private static final String NOT_IRRELEVANT =
            "(?!(?:은|는|이|가|도|에|과|와)?\\s*[:：\\-]?\\s*(?:무관|관계\\s*없|상관\\s*없|제한\\s*없|불문))";
    private static final Pattern EXPERIENCE_YEARS_PATTERN = Pattern.compile(
            "(?i)"
            + "(?:경력|경험|실무)" + NOT_IRRELEVANT + "[^\\d\\n]{0,6}(\\d+)\\s*년"            // 1: 경력 … N년
            + "|(\\d+)\\s*년[^\\d\\n]{0,8}(?:경력|경험|실무)" + NOT_IRRELEVANT                 // 2: N년 … 경력
            + "|\\b(?:experience|exp)\\b[^\\d\\n]{0,12}(\\d+)\\s*\\+?\\s*(?:years?|yrs?)"      // 3: experience … N years
            + "|(\\d+)\\s*\\+?\\s*(?:years?|yrs?)[^\\d\\n]{0,8}\\b(?:experience|exp)\\b");     // 4: N years … experience
    private static final int MAX_PROMPT_TEXT_LENGTH = 12_000;
    // 컨텍스트 예산 절단(이슈 D 후속 #1): 공고 원문이 길어 num_ctx 를 넘기면 R1 이 400 으로 통째 폴백되므로,
    // 원문을 윈도 예산에 맞춰 미리 절단해 R1 경로를 유지한다. 한글은 토큰 밀도가 높아(실측 ~1.6자/토큰)
    // 보수적으로 1.4자/토큰을 가정한다(가정이 실제보다 작을수록 더 안전: 윈도에 여유가 생김).
    private static final double CHARS_PER_TOKEN = 1.4;
    // 공고 원문 외 고정 오버헤드(토큰): 시스템 프롬프트 + JSON 스키마(format) + 라벨/템플릿.
    // 6단계 canonical contract 로 시스템 프롬프트·스키마가 커져 600 → 1000 으로 상향(원문 예산 보호).
    private static final int FIXED_PROMPT_OVERHEAD_TOKENS = 1_000;
    // 문장 분류 신호는 보조 힌트라 원문보다 작게 캡한다(기존 4000 → 컨텍스트 예산 보호).
    private static final int CLASSIFICATION_CHAR_CAP = 2_000;
    // 컨텍스트가 아무리 작아도 최소한 이만큼은 원문을 남긴다.
    private static final int MIN_POSTING_CHARS = 2_000;
    // 경력 연차 보정: 5년 이상이면 SENIOR로 본다(self-rules experienceLevel과 동일 기준).
    private static final int SENIOR_YEARS_THRESHOLD = 5;
    // "2024년" 같은 날짜/연도 오탐을 거르기 위한 현실적인 경력 연차 상한.
    private static final int MAX_REALISTIC_YEARS = 30;
    // requiredSkills 문장 필터: 이보다 길거나 단어가 많으면 스킬이 아니라 업무 문장으로 본다.
    private static final int SKILL_MAX_LENGTH = 30;
    private static final int SKILL_MAX_WORDS = 4;
    private static final Pattern SKILL_SENTENCE_PATTERN =
            Pattern.compile("및|또는|등의|에 대한|설계 및|개발 및|구축 및|담당");
    // 짧은 비스킬 토큰 제거(이슈 D 후속 #3): 길이/단어/문장 필터를 통과하지만 스킬이 아닌 항목.
    // - "경력"·"5년"·"경력5년" 등 연차 토큰
    // - "|T장비기술지원" 처럼 OCR이 'I'를 '|'로 깨뜨린 잡음
    // - "전산운영직"·"전산관리자"·"백업전문가" 처럼 직무명/역할 접미사로 끝나는 항목
    // 보수적으로 명백한 비스킬만 제거한다(정상 스킬 과제거 방지).
    private static final Pattern NON_SKILL_PATTERN =
            Pattern.compile("경력|\\d\\s*년|[|]|(?:직|관리자|전문가|담당자|사원)$");
    // 묶음 스킬 salvage(이슈 D 후속 #4) 시 top-level 분할 구분자. 슬래시(/)는 CI/CD·TCP/IP·웹/서버 보존 위해 제외한다.
    private static final String TOP_LEVEL_SKILL_DELIMITERS = ",;·ㆍ";
    // 괄호 앞 대표 토큰 salvage 의 regex fallback 신호(과복구 방지). 공백 없는 단일 토큰에서:
    // 기술 기호(. + # / -)/숫자가 있거나(Node.js·CI/CD·C#), 전부 대문자 약어(AWS·GCP·SQL)일 때만 기술 토큰으로 본다.
    // "Payment System"(공백 영문 구문)·"결제 시스템"(한국어)처럼 기술 신호 없는 업무명 조각은 배제된다.
    private static final Pattern TECH_SIGNAL_PATTERN = Pattern.compile("[0-9.+#/\\-]");
    private static final Pattern UPPER_ACRONYM_PATTERN = Pattern.compile("[A-Z][A-Z0-9]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");
    // ── 공고분석 자유서술(duties/qualifications/summary) 후처리(이슈 D 후속, 233 문서 3절) ──
    // grounding gate 는 스킬만 검사하므로 자유서술의 오배치·나열·중복·OCR 깨짐은 못 잡는다. 근거 있는 내용은
    // 삭제하지 않고 '이동/응축'만 하며, OCR 깨짐 같은 무의미 토큰만 제거해 근거성 회귀(SUPPORTED 소실)를 피한다.
    // 자유서술은 줄바꿈·불릿 단위로만 세그먼트 분해한다(콤마로는 자르지 않아 산문 문장 훼손 방지).
    private static final Pattern JOB_TEXT_BULLET_PATTERN = Pattern.compile("[•ㆍ·▪◦‣]");
    // A-1: duty 로 오배치되기 쉬운 경력·자격·임금 '요건/조건' 신호(요건 명사·연차·금액). 아래 업무 동사와
    // 겹치지 않을 때만 요건으로 본다. 금액("3000000원 이상")은 임금조건이라 duty 가 아니다(하네스 가온테크).
    private static final Pattern QUALIFICATION_SIGNAL_PATTERN = Pattern.compile(
            "경력\\s*\\d+\\s*년|\\d+\\s*년\\s*이상|자격증|기능사|기사\\s*자격|건설기술인|학사|석사|박사|학위|전공자?|졸업"
            + "|\\d[\\d,]*\\s*원");
    // duty(담당업무) 신호 동사 — 요건 신호와 겹쳐도 실제 수행 업무면 duty 로 유지("서버 유지보수 수행").
    private static final Pattern RESPONSIBILITY_VERB_PATTERN = Pattern.compile(
            "수행|담당|개발|운영|구축|관리|설계|지원|분석|기획|제작|유지보수|대응|추진|수립");
    // 요건 재배치·문장화 대상 세그먼트 길이 상한(이보다 길면 문장으로 보고 건드리지 않음).
    private static final int JOB_SEGMENT_MAX_LENGTH = 40;
    // A-2: duties 총 길이가 이보다 짧으면 과소추출로 보고 주요업무(RESPONSIBILITY) 문장으로 보강한다.
    private static final int DUTIES_UNDEREXTRACT_THRESHOLD = 60;
    // A-2/A-4: 정상 조합형 한글엔 나타나지 않는 호환 자모 낱자 = OCR 손상 신호. 보강·필터에서 배제한다.
    private static final Pattern BROKEN_JAMO_PATTERN = Pattern.compile("[ㄱ-ㅎㅏ-ㅣ]");
    // A-2: 과소추출 보강으로 붙이는 주요업무 문장의 최대 개수·길이 범위(과보강 방지).
    private static final int DUTIES_SUPPLEMENT_MAX = 3;
    private static final int SUPPLEMENT_SENTENCE_MIN_LENGTH = 8;
    private static final int SUPPLEMENT_SENTENCE_MAX_LENGTH = 120;
    private static final Pattern LEADING_LIST_MARKER_PATTERN = Pattern.compile("^[-*•ㆍ·\\s]+");
    // A-1 보정(하네스 2026-07-03 가온테크): 콤마 단일라인 분해 시 산문 제외 신호. 활용형 동사(하고/하며/…)·
    // 목적격 조사(을/를+공백)·종결형(합니다 등)이 있으면 산문으로 보고 콤마 분할하지 않는다.
    // "유지보수·관리·운영" 같은 명사형 키워드는 이 신호에 걸리지 않아 나열형 분해가 가능하다.
    private static final Pattern PROSE_COMMA_GUARD_PATTERN = Pattern.compile(
            "하고|하며|하여|하는|하거나|되고|되며|되는|합니다|입니다|됩니다|[을를]\\s");
    // A-3: 나열형 duties==summary 중복 시 summary 를 앞쪽 세그먼트 몇 개로 응축할지(부분집합 응축).
    private static final int DUTIES_SUMMARY_CONDENSE_SEGMENTS = 3;
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring", "Spring Boot", "MyBatis", "JPA", "SQL", "MySQL", "PostgreSQL",
            "Redis", "Kafka", "RabbitMQ", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
            "Linux", "React", "TypeScript", "JavaScript", "Vue", "Node.js", "Python",
            "Django", "FastAPI", "REST", "GraphQL", "Git", "CI/CD", "Jenkins", "GitHub Actions",
            "JUnit", "Mockito", "Testing", "Monitoring", "Prometheus", "Grafana", "Elasticsearch",
            "Security", "OAuth", "JWT", "Agile", "Scrum", "Figma");

    private final BAnalysisProperties properties;
    private final BLocalLlmClient localLlmClient;
    private final BJobSentenceClassifier sentenceClassifier;
    private final ObjectMapper objectMapper;
    private final BAnthropicClient anthropicClient;
    private final OpenAiResponsesClient openAiResponsesClient;

    /**
     * 공고 분석 폴백 체인: 자체모델(Ollama) → Claude(Haiku) → OpenAI → self-rules-v1.
     * self-rules-v1 은 외부 호출 없는 결정적 규칙 생성기라 절대 예외로 끝나지 않는 최종 안전망이다.
     * 어떤 AI 도 시도되지 않은 경우(전부 비활성/미설정)는 의도된 기본 동작이라 fallback 으로 표시하지 않는다.
     */
    public GeneratedJobAnalysis generateJobAnalysis(ApplicationCase applicationCase, String postingText) {
        Classification classification = sentenceClassifier.classify(postingText);
        String lastError = null;

        // 1) 자체모델(Ollama) — provider 활성 시 재시도. 실패하면 아래로 폴백.
        if (properties.getLocalLlm().isEnabled()) {
            int maxAttempts = 1 + properties.getLocalLlm().getMaxRetries();
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    String content = localLlmClient.chat(
                            JobAnalysisPromptCatalog.SYSTEM_PROMPT,
                            jobPrompt(applicationCase, postingText, classification),
                            jobAnalysisSchema());
                    JobAnalysisPayload payload = parseLocalJobPayload(content, postingText,
                            properties.getLocalLlm().getModel(), classification);
                    validateGrounding(payload, postingText);
                    log.info("Local LLM job analysis succeeded (attempt={}/{})", attempt, maxAttempts);
                    return new GeneratedJobAnalysis(payload, null, null);
                } catch (RuntimeException ex) {
                    lastError = safeMessage(ex);
                    log.warn("Local LLM job analysis attempt {}/{} failed: {}", attempt, maxAttempts, lastError);
                }
            }
        }

        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 같은 스키마/검증 재사용.
        if (anthropicClient.configured()) {
            try {
                String content = anthropicClient.chat(
                        JobAnalysisPromptCatalog.SYSTEM_PROMPT,
                        jobPrompt(applicationCase, postingText, classification),
                        jobAnalysisSchema());
                JobAnalysisPayload payload = parseLocalJobPayload(content, postingText, anthropicClient.model(),
                        classification);
                validateGrounding(payload, postingText);
                log.info("Claude job analysis succeeded");
                return new GeneratedJobAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("Claude job analysis failed: {}", lastError);
            }
        }

        // 3) 2차 폴백: OpenAI.
        if (openAiResponsesClient.configured()) {
            try {
                JobAnalysisPayload payload = withDedupedEvidence(
                        openAiResponsesClient.analyzeJobPosting(applicationCase, postingText));
                log.info("OpenAI job analysis succeeded");
                return new GeneratedJobAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("OpenAI job analysis failed: {}", lastError);
            }
        }

        // 4) 최종 안전망: self-rules-v1.
        if (lastError == null) {
            // 아무 AI provider 도 시도되지 않음(전부 비활성/미설정) → 의도된 기본 동작.
            return new GeneratedJobAnalysis(
                    selfRulesJobAnalysis(applicationCase, postingText, classification), null, null);
        }
        String reason = "Local/Claude/OpenAI job analysis unavailable; fallback to self-rules-v1: " + lastError;
        log.warn("{}", reason);
        return new GeneratedJobAnalysis(
                selfRulesJobAnalysis(applicationCase, postingText, classification),
                reason,
                properties.getLocalLlm().getModel());
    }

    /** 웹 근거 없는 기존 진입점(auto pipeline 등). 빈 목록으로 overload 에 위임해 현행 동작을 유지한다. */
    public GeneratedCompanyAnalysis generateCompanyAnalysis(ApplicationCase applicationCase, String postingText) {
        return generateCompanyAnalysis(applicationCase, postingText, List.of());
    }

    /** 기업분석 provider. 우선순위는 {@code careertuner.b-analysis.company.provider} 로 결정한다. */
    private enum CompanyProvider {
        LOCAL("Local LLM"),
        CLAUDE("Claude"),
        OPENAI("OpenAI");

        private final String label;

        CompanyProvider(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * 회사 분석 폴백 체인(공고+WEB 2소스 · 235 §1·§3). 1순위 provider 는
     * {@code careertuner.b-analysis.company.provider}(auto/openai/claude)로 정하고, 최종 안전망은 항상 self-rules-v1 이다.
     * webEvidence 중 URL 보유분이 있으면 local/Claude user 입력에 {@code [웹 검색 근거]} 블록({url,snippet})을 붙이고
     * company schema sourceKind enum 에 WEB 를 additive 로 연다. 빈 목록/URL 없음이면 현행과 완전히 동일하다.
     * hosted 경로는 grounding 완화판({@link CompanyAnalysisPromptCatalog#HOSTED_SYSTEM_PROMPT})을 쓰고,
     * R1(local)은 현행 {@link CompanyAnalysisPromptCatalog#SYSTEM_PROMPT} 를 유지한다(canonical eval 불변).
     * hosted(OpenAI) 경로의 웹 입력은 D-4c 범위 — 이번 배치는 공고문만 넘긴다.
     */
    public GeneratedCompanyAnalysis generateCompanyAnalysis(ApplicationCase applicationCase, String postingText,
                                                            List<CompanyWebEvidence> webEvidence) {
        Classification classification = sentenceClassifier.classify(postingText);
        List<CompanyWebEvidence> usableWeb = usableWebEvidence(webEvidence);
        boolean includeWeb = !usableWeb.isEmpty();
        String lastError = null;

        for (CompanyProvider provider : companyProviderOrder()) {
            if (!companyProviderAvailable(provider)) {
                continue;
            }
            try {
                CompanyAnalysisPayload payload = switch (provider) {
                    case LOCAL -> attemptLocalCompany(applicationCase, postingText, classification, usableWeb, includeWeb);
                    case CLAUDE -> attemptClaudeCompany(applicationCase, postingText, classification, usableWeb, includeWeb);
                    case OPENAI -> openAiResponsesClient.analyzeCompany(applicationCase, postingText,
                            properties.getCompany().getOpenAiModel());
                };
                log.info("{} company analysis succeeded", provider.label());
                return new GeneratedCompanyAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("{} company analysis failed: {}", provider.label(), lastError);
            }
        }

        // 최종 안전망: self-rules-v1. 아무 provider 도 시도되지 않았으면(전부 비활성/미설정) 의도된 기본 동작이라
        // fallback 으로 표시하지 않는다.
        if (lastError == null) {
            return new GeneratedCompanyAnalysis(
                    selfRulesCompanyAnalysis(applicationCase, postingText, classification), null, null);
        }
        String reason = "Company analysis providers unavailable; fallback to self-rules-v1: " + lastError;
        log.warn("{}", reason);
        return new GeneratedCompanyAnalysis(
                selfRulesCompanyAnalysis(applicationCase, postingText, classification),
                reason,
                properties.getLocalLlm().getModel());
    }

    /**
     * 기업분석 provider 시도 순서를 config 로 정한다. 어떤 값이든 세 provider 를 모두 포함하되 순서만 바꾸며,
     * 미설정/비활성 provider 는 상위 루프에서 건너뛴다. 기본(auto)은 현행과 동일(R1 → Claude → OpenAI).
     */
    private List<CompanyProvider> companyProviderOrder() {
        String raw = properties.getCompany().getProvider();
        String provider = raw == null ? "auto" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "openai" -> List.of(CompanyProvider.OPENAI, CompanyProvider.CLAUDE, CompanyProvider.LOCAL);
            case "claude", "anthropic" -> List.of(CompanyProvider.CLAUDE, CompanyProvider.OPENAI, CompanyProvider.LOCAL);
            default -> List.of(CompanyProvider.LOCAL, CompanyProvider.CLAUDE, CompanyProvider.OPENAI);
        };
    }

    private boolean companyProviderAvailable(CompanyProvider provider) {
        return switch (provider) {
            case LOCAL -> properties.getLocalLlm().isEnabled();
            case CLAUDE -> anthropicClient.configured();
            case OPENAI -> openAiResponsesClient.configured();
        };
    }

    /** 자체 R1(local) 기업분석 1회 시도 — 현행 {@link CompanyAnalysisPromptCatalog#SYSTEM_PROMPT} 유지. */
    private CompanyAnalysisPayload attemptLocalCompany(ApplicationCase applicationCase, String postingText,
                                                       Classification classification,
                                                       List<CompanyWebEvidence> usableWeb, boolean includeWeb) {
        String content = localLlmClient.chat(
                CompanyAnalysisPromptCatalog.SYSTEM_PROMPT,
                companyPrompt(applicationCase, postingText, classification, usableWeb),
                companyAnalysisSchema(includeWeb));
        return parseLocalCompanyPayload(content, applicationCase, postingText, properties.getLocalLlm().getModel());
    }

    /** Claude(hosted) 기업분석 1회 시도 — grounding 완화판 프롬프트를 쓴다(파싱/검증은 local 과 공유). */
    private CompanyAnalysisPayload attemptClaudeCompany(ApplicationCase applicationCase, String postingText,
                                                        Classification classification,
                                                        List<CompanyWebEvidence> usableWeb, boolean includeWeb) {
        String content = anthropicClient.chat(
                CompanyAnalysisPromptCatalog.HOSTED_SYSTEM_PROMPT,
                companyPrompt(applicationCase, postingText, classification, usableWeb),
                companyAnalysisSchema(includeWeb));
        return parseLocalCompanyPayload(content, applicationCase, postingText, anthropicClient.model());
    }

    private JobAnalysisPayload selfRulesJobAnalysis(ApplicationCase applicationCase,
                                                   String postingText,
                                                   Classification classification) {
        List<String> requiredSkills = extractRequiredSkills(postingText);
        List<String> preferredSkills = extractPreferredSkills(postingText, requiredSkills);
        String duties = joinClassified(classification, BJobSentenceClassifier.RESPONSIBILITY);
        String qualifications = firstNonBlank(
                joinClassified(classification, BJobSentenceClassifier.REQUIRED),
                joinClassified(classification, BJobSentenceClassifier.QUALIFICATION),
                extractSection(postingText, List.of("qualifications", "requirements", "required", "skills", "자격", "요건", "필수")));
        String summary = "%s %s posting extracted by the self-hosted pipeline. Required signals: %s."
                .formatted(defaultText(applicationCase.getCompanyName(), "Target company"),
                        defaultText(applicationCase.getJobTitle(), "Target role"),
                        joinPreview(requiredSkills));
        return new JobAnalysisPayload(
                employmentType(postingText),
                experienceLevel(postingText),
                toJson(requiredSkills),
                toJson(preferredSkills),
                defaultText(duties, firstSentences(postingText, 2)),
                defaultText(qualifications, "Review the posting text for detailed qualification wording."),
                difficulty(postingText, requiredSkills),
                summary,
                toJson(evidence(requiredSkills, postingText)),
                toJson(ambiguousConditions(postingText)),
                usage(SELF_RULES_MODEL, postingText.length(), summary.length() + estimateLength(requiredSkills) + estimateLength(preferredSkills)));
    }

    private CompanyAnalysisPayload selfRulesCompanyAnalysis(ApplicationCase applicationCase,
                                                           String postingText,
                                                           Classification classification) {
        String knownCompany = knownCompanyName(applicationCase.getCompanyName());
        String companyLabel = knownCompany == null ? "해당 기업" : knownCompany;
        String companyInfo = joinClassified(classification, BJobSentenceClassifier.COMPANY_INFO);
        String summary = companyInfo.isBlank()
                ? "%s 정보는 업로드된 공고문만으로 정리했습니다. 외부 기업 정보나 OpenAI 폴백은 사용하지 않았습니다."
                        .formatted(companyLabel)
                : "%s 정보를 업로드된 공고문 기준으로 요약했습니다: %s"
                        .formatted(companyLabel, truncate(companyInfo, 260));
        String interviewPoints = "이 직무가 본인 경험과 어떻게 맞는지, 명시된 담당 업무를 어떻게 수행할지, "
                + "요구 기술을 뒷받침할 근거가 무엇인지 설명할 수 있도록 준비하세요: " + joinPreview(extractRequiredSkills(postingText)) + ".";
        return new CompanyAnalysisPayload(
                summary,
                CompanyAnalysisPromptCatalog.RECENT_ISSUES_UNAVAILABLE_NOTICE,
                industry(postingText),
                toJson(List.of()),
                interviewPoints,
                toJson(List.of(Map.of(
                        "type", "JOB_POSTING",
                        "label", "업로드한 공고문"))),
                toJson(verifiedFacts(applicationCase, postingText)),
                toJson(List.of(Map.of(
                        "inference", "면접 준비는 공고문의 담당 업무와 요구 기술 근거에 집중하는 것이 좋습니다.",
                        "basis", "추출된 공고문 섹션을 로컬 규칙으로 도출했습니다."))),
                toJson(List.of()),
                usage(SELF_RULES_MODEL, postingText.length(), summary.length() + interviewPoints.length()));
    }

    private JobAnalysisPayload parseLocalJobPayload(String content, String postingText, String modelLabel,
                                                    Classification classification) {
        JsonNode root = parseObject(content);
        String employmentType = normalizeEmploymentType(text(root, "employmentType", employmentType(postingText)), postingText);
        String experienceLevel = capExperienceForEmploymentType(
                reconcileExperienceLevel(text(root, "experienceLevel", experienceLevel(postingText)), postingText),
                employmentType);
        JobText jobText = postProcessJobText(
                requiredText(root, "duties"),
                requiredText(root, "qualifications"),
                requiredText(root, "summary"),
                classification);
        JobAnalysisPayload payload = new JobAnalysisPayload(
                employmentType,
                experienceLevel,
                filterSkillItems(arrayJson(root, "requiredSkills"), postingText),
                filterSkillItems(arrayJson(root, "preferredSkills"), null),
                jobText.duties(),
                jobText.qualifications(),
                normalizeDifficulty(text(root, "difficulty", "NORMAL")),
                jobText.summary(),
                dedupEvidenceJson(objectArrayJson(root, "evidence", "field", "quote")),
                objectArrayJson(root, "ambiguousConditions", "condition", "assumption"),
                usage(modelLabel, postingText.length(), content.length()));
        validateJobPayload(payload);
        return payload;
    }

    /**
     * 공고분석 자유서술(duties/qualifications/summary) 후처리(이슈 D 후속, 233 문서 3절).
     * grounding gate 가 못 잡는 품질 결함을 보정하되 근거 있는 내용은 삭제하지 않고 '이동/응축'만 한다.
     *
     * <p>A-1 필드 오배치 보정: duty 로 잘못 배치된 경력·자격 '요건'을 qualifications 로 재배치한다
     * (가온테크 "경력5년"·금융21 "건축분야기능사" 등). 재배치가 duties 를 통째로 비우면 근거 유실·검증 실패
     * 위험이 있어(전부 요건인 경우) 재배치하지 않는다(보수적).
     *
     * <p>A-2 키워드 나열 문장화 + 과소추출 보강: duties 가 짧아 과소추출로 판단되면 분류기의 주요업무
     * (RESPONSIBILITY) 문장으로 보강하고(포스타입), 세그먼트가 키워드 나열이면 문장으로 렌더한다
     * (가온테크·금융21). 보강 문장은 원문 근거(분류 문장)만 쓰고 OCR 깨짐(호환 자모)은 배제한다.
     */
    private JobText postProcessJobText(String duties, String qualifications, String summary,
                                       Classification classification) {
        // A-4: OCR 깨짐(호환 자모)을 자유서술에서 먼저 제거한다. 주변 근거 단어는 보존하도록 낱자만 인라인 제거한다
        //      (세그먼트 통째 삭제 시 같은 줄의 근거 내용까지 유실될 수 있어 근거성 회귀 위험).
        duties = stripBrokenJamo(duties);
        qualifications = stripBrokenJamo(qualifications);
        summary = stripBrokenJamo(summary);

        List<String> segments = splitJobTextSegments(duties);
        String newQualifications = qualifications;
        boolean changed = false;

        // A-1: duty 로 오배치된 요건을 qualifications 로 재배치(전부 요건이면 생략).
        List<String> relocated = new ArrayList<>();
        List<String> remaining = new ArrayList<>();
        for (String segment : segments) {
            if (isMisplacedQualification(segment)) {
                relocated.add(segment);
            } else {
                remaining.add(segment);
            }
        }
        if (!relocated.isEmpty() && !remaining.isEmpty()) {
            segments = remaining;
            newQualifications = appendMissing(qualifications, relocated);
            changed = true;
        }

        // A-2 과소추출 보강: duties 가 얇으면 주요업무 분류 문장으로 채운다(원문 근거·중복/깨짐 제외).
        if (isDutiesUnderExtracted(segments)) {
            List<String> supplement = supplementFromResponsibilities(segments, classification);
            if (!supplement.isEmpty()) {
                segments.addAll(supplement);
                changed = true;
            }
        }

        // A-2 문장화: 세그먼트가 키워드 나열이면 문장으로, 그 외엔 무변경 시 원본 유지.
        String newDuties;
        if (isKeywordList(segments)) {
            newDuties = String.join(", ", segments) + " 등의 업무를 담당합니다.";
            changed = true;
        } else if (changed) {
            newDuties = String.join("\n", segments);
        } else {
            newDuties = duties;
        }

        // A-3: duties==summary 중복이면 summary 를 응축해 두 필드를 구분한다(동국제약·가온테크).
        // duties 가 위에서 변형됐을 수 있으므로 raw/변형본 양쪽과 대조한다. 응축본은 출력의 부분집합이라
        // 근거성이 유지되며, 길이 하한(검증 통과) 아래로는 응축하지 않는다.
        String newSummary = dedupSummaryFromDuties(duties, newDuties, segments, summary);

        return new JobText(newDuties, newQualifications, newSummary);
    }

    /**
     * duties 와 summary 가 사실상 동일하면 summary 를 응축해 중복을 제거한다.
     * <ul>
     * <li>산문형(동국제약): 첫 문장으로 응축한다(부분집합). 실제로 짧아졌을 때만 채택한다.</li>
     * <li>키워드 나열형(가온테크 — 문장부호가 없어 첫 문장 응축 불가): duty 세그먼트 앞쪽
     *     {@value #DUTIES_SUMMARY_CONDENSE_SEGMENTS}개만으로 응축한다(부분집합).</li>
     * </ul>
     * duties 가 후처리로 변형됐을 수 있어 중복 판정은 raw/변형본 양쪽과 대조한다.
     * 응축본이 검증 하한(20자) 미만이거나 duties 와 여전히 동일해지면 원본 summary 를 유지한다.
     */
    private String dedupSummaryFromDuties(String rawDuties, String newDuties, List<String> dutySegments,
                                          String summary) {
        if (isBlank(rawDuties) || isBlank(summary)) {
            return summary;
        }
        String compactSummary = compactWhitespace(summary);
        if (!compactSummary.equals(compactWhitespace(rawDuties))
                && !compactSummary.equals(compactWhitespace(newDuties))) {
            return summary;
        }
        String condensed = firstSentences(summary, 1);
        if (condensed.length() >= 20 && condensed.length() < summary.length()
                && !compactWhitespace(condensed).equals(compactWhitespace(newDuties))) {
            return condensed;
        }
        if (dutySegments.size() > DUTIES_SUMMARY_CONDENSE_SEGMENTS) {
            String subset = String.join(", ", dutySegments.subList(0, DUTIES_SUMMARY_CONDENSE_SEGMENTS))
                    + " 등의 업무를 담당합니다.";
            if (subset.length() >= 20 && !compactWhitespace(subset).equals(compactWhitespace(newDuties))) {
                return subset;
            }
        }
        return summary;
    }

    private static String compactWhitespace(String text) {
        return WHITESPACE_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * OCR 깨짐 신호인 호환 자모 낱자를 인라인 제거한다(정상 조합형 한글엔 없음). 낱자만 지워 주변 근거 단어를
     * 보존하고, 제거로 생긴 공백 잡음만 정리한다(줄바꿈 보존). 낱자가 없거나 제거 후 공백뿐이면 원본을 유지한다.
     */
    private String stripBrokenJamo(String text) {
        if (text == null || !BROKEN_JAMO_PATTERN.matcher(text).find()) {
            return text;
        }
        String stripped = BROKEN_JAMO_PATTERN.matcher(text).replaceAll("");
        stripped = stripped.replaceAll("[ \\t]{2,}", " ");
        stripped = stripped.replaceAll(" +(\\R)", "$1");
        stripped = stripped.replaceAll("(\\R) +", "$1");
        stripped = stripped.trim();
        return stripped.isBlank() ? text : stripped;
    }

    /** duties 총 길이가 임계 미만이면 과소추출로 본다(주요업무 보강 대상). */
    private boolean isDutiesUnderExtracted(List<String> segments) {
        int total = segments.stream().mapToInt(String::length).sum();
        return total < DUTIES_UNDEREXTRACT_THRESHOLD;
    }

    /**
     * 분류기의 주요업무(RESPONSIBILITY) 문장 중 아직 duties 에 반영되지 않은 것을 최대 N개 보강한다.
     * 원문에서 분류된 문장이라 근거가 있고(환각 아님), OCR 깨짐(호환 자모)·과도한 길이·기존 중복은 제외한다.
     */
    private List<String> supplementFromResponsibilities(List<String> existing, Classification classification) {
        List<String> added = new ArrayList<>();
        if (classification == null) {
            return added;
        }
        for (String sentence : classification.textsByLabel(BJobSentenceClassifier.RESPONSIBILITY)) {
            if (added.size() >= DUTIES_SUPPLEMENT_MAX) {
                break;
            }
            String candidate = LEADING_LIST_MARKER_PATTERN.matcher(sentence.trim()).replaceAll("").trim();
            if (candidate.length() < SUPPLEMENT_SENTENCE_MIN_LENGTH
                    || candidate.length() > SUPPLEMENT_SENTENCE_MAX_LENGTH
                    || BROKEN_JAMO_PATTERN.matcher(candidate).find()
                    || isCovered(existing, candidate) || isCovered(added, candidate)) {
                continue;
            }
            added.add(candidate);
        }
        return added;
    }

    /** 후보 문장이 기존 세그먼트 중 하나에 포함(부분 문자열)되면 이미 반영된 것으로 본다. */
    private boolean isCovered(List<String> segments, String candidate) {
        for (String segment : segments) {
            if (segment.contains(candidate) || candidate.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    /** 세그먼트가 전부 짧은 비문(키워드 나열)이면 문장화 대상으로 본다(2개 이상). */
    private boolean isKeywordList(List<String> segments) {
        if (segments.size() < 2) {
            return false;
        }
        for (String segment : segments) {
            if (isSentenceLike(segment) || segment.length() > JOB_SEGMENT_MAX_LENGTH) {
                return false;
            }
        }
        return true;
    }

    /**
     * 자유서술을 줄바꿈·불릿 단위 세그먼트로 분해하고, 키워드 나열형일 때만 top-level 콤마로 추가 분해한다
     * (A-1 보정 — 하네스 2026-07-03 가온테크: R1 이 나열을 줄바꿈 대신 콤마 단일라인으로 출력).
     * 산문 문장은 콤마로 자르지 않는다({@link #splitKeywordCommaList} 가드).
     */
    private List<String> splitJobTextSegments(String text) {
        List<String> segments = new ArrayList<>();
        for (String line : text.split("\\R")) {
            for (String part : JOB_TEXT_BULLET_PATTERN.split(line)) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    segments.addAll(splitKeywordCommaList(trimmed));
                }
            }
        }
        return segments;
    }

    /**
     * 키워드 나열형 세그먼트만 top-level ASCII 콤마로 분해한다(가온테크형 콤마 단일라인). 산문 훼손 방지 가드:
     * <ul>
     * <li>문장형({@link #isSentenceLike})·활용형 동사/목적격 조사({@link #PROSE_COMMA_GUARD_PATTERN})가 있으면
     *     산문으로 보고 분할하지 않는다.</li>
     * <li>괄호/브래킷({@code () [] {}}) depth 내부의 콤마는 분할하지 않는다("구축(설계, 운영)" 보존).</li>
     * <li>분할 결과가 전부 키워드형(짧은 비문, {@link #isKeywordList})일 때만 채택한다 — 하나라도 산문형이면
     *     원본을 그대로 둔다(과분해 방지). ASCII 콤마 외 문자(/·전각 콤마 등)로는 넓히지 않는다(TCP/IP 등 보존).</li>
     * </ul>
     */
    private List<String> splitKeywordCommaList(String segment) {
        if (segment.indexOf(',') < 0
                || isSentenceLike(segment)
                || PROSE_COMMA_GUARD_PATTERN.matcher(segment).find()) {
            return List.of(segment);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && c == ',') {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());
        parts.removeIf(String::isEmpty);
        if (parts.size() < 2 || !isKeywordList(parts)) {
            return List.of(segment);
        }
        return parts;
    }

    /**
     * duty 세그먼트가 실제로는 경력·자격 '요건'인지 판정한다(오배치). 짧은 비문(非문장) 세그먼트 중
     * 요건 신호가 있고 업무 수행 동사가 없을 때만 요건으로 본다("경력5년"·"건축분야기능사"). 길이 상한을
     * 넘거나 문장형이거나 "서버 유지보수 수행"처럼 업무 동사가 있으면 duty 로 유지한다.
     */
    private boolean isMisplacedQualification(String segment) {
        if (segment.length() > JOB_SEGMENT_MAX_LENGTH || isSentenceLike(segment)) {
            return false;
        }
        return QUALIFICATION_SIGNAL_PATTERN.matcher(segment).find()
                && !RESPONSIBILITY_VERB_PATTERN.matcher(segment).find();
    }

    /** 종결어미/문장부호로 끝나면 문장으로 본다(문장은 세그먼트 이동·문장화 대상에서 제외). */
    private boolean isSentenceLike(String segment) {
        String trimmed = segment.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        return last == '.' || last == '!' || last == '?'
                || trimmed.endsWith("다") || trimmed.endsWith("요") || trimmed.endsWith("음");
    }

    /** qualifications 원문에 없는 재배치 항목만 줄바꿈으로 덧붙인다(중복 방지). */
    private String appendMissing(String base, List<String> additions) {
        StringBuilder sb = new StringBuilder(base == null ? "" : base);
        for (String addition : additions) {
            if (!sb.toString().contains(addition)) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(addition);
            }
        }
        return sb.toString();
    }

    /**
     * R1 모델이 "경력 5년 이상" 공고를 JUNIOR로 오분류하는 사례가 있어, 공고 원문에 명시된
     * 경력 연차를 정규식으로 파싱해 experienceLevel을 보정한다. 명시된 연차가 없으면 모델 값을 그대로 둔다.
     */
    private String reconcileExperienceLevel(String llmValue, String postingText) {
        String normalized = normalizeExperienceLevel(llmValue);
        Integer years = maxStatedYears(postingText);
        if (years == null) {
            return normalized;
        }
        if (years >= SENIOR_YEARS_THRESHOLD) {
            return "SENIOR";
        }
        if (years >= 1 && "JUNIOR".equals(normalized)) {
            return "MID";
        }
        return normalized;
    }

    /**
     * R1 모델이 employmentType에 "인턴", "정규직", "일반근로자" 등 enum 밖 자유 텍스트를 반환하는 사례가 있어,
     * FULL_TIME/CONTRACT/INTERN/PART_TIME 표준 enum으로 정규화한다. 분류 불가 값은 원문 규칙 추출로 폴백한다.
     */
    private String normalizeEmploymentType(String value, String postingText) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("full_time") || lower.equals("contract") || lower.equals("intern") || lower.equals("part_time")) {
            return lower.toUpperCase(Locale.ROOT);
        }
        if (lower.contains("intern") || lower.contains("인턴")) {
            return "INTERN";
        }
        if (lower.contains("contract") || lower.contains("계약")) {
            return "CONTRACT";
        }
        if (lower.contains("part") || lower.contains("시간제") || lower.contains("아르바이트") || lower.contains("알바")) {
            return "PART_TIME";
        }
        if (lower.contains("full") || lower.contains("정규")) {
            return "FULL_TIME";
        }
        return employmentType(postingText);
    }

    /**
     * 인턴 공고는 시니어/미드급 경력을 요구하지 않는다. R1이 연차가 명시되지 않은 인턴 공고를 SENIOR/MID로
     * 잘못 매기는 사례(이슈 D 검증: 딥그로브)가 있어, 고용형태가 INTERN이면 experienceLevel을 JUNIOR로 상한 보정한다.
     */
    private String capExperienceForEmploymentType(String experienceLevel, String employmentType) {
        if ("INTERN".equals(employmentType)) {
            return "JUNIOR";
        }
        return experienceLevel;
    }

    /**
     * R1 모델이 enum 대신 "intermediate", "MEDIUM", 숫자 등 비표준 값을 반환하는 사례가 있어,
     * experienceLevel을 JUNIOR/MID/SENIOR 표준 값으로 정규화한다. 분류 불가 값은 MID로 수렴한다.
     */
    private String normalizeExperienceLevel(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("junior") || lower.equals("mid") || lower.equals("senior")) {
            return lower.toUpperCase(Locale.ROOT);
        }
        if (lower.contains("senior") || lower.contains("시니어") || lower.contains("lead")
                || lower.contains("principal") || lower.contains("staff") || lower.contains("expert")) {
            return "SENIOR";
        }
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("intern")
                || lower.contains("new grad") || lower.contains("신입") || lower.contains("주니어")) {
            return "JUNIOR";
        }
        return "MID";
    }

    /**
     * 공고 원문에서 경력 연차를 추출한다. 연차 숫자가 경력 키워드(경력/경험/실무, experience/exp)와 결합된
     * 경우만 인정하므로, "2024년"(날짜)·"설립 10년차"(연혁)·"서비스 운영 5년"(기간)·"5년 연속 성장",
     * "operating for 5 yrs"(영어 기간)는 자연히 배제된다. 여러 건이면 가장 큰 연차를 반환한다.
     */
    private Integer maxStatedYears(String text) {
        Matcher matcher = EXPERIENCE_YEARS_PATTERN.matcher(text);
        Integer max = null;
        while (matcher.find()) {
            String digits = firstNonNull(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
            if (digits == null || digits.length() > 2) {
                // 2자리 초과(>=100)는 경력 연차로 비현실적 → 무시(파싱 오버플로 방지 겸).
                continue;
            }
            int value = Integer.parseInt(digits);
            if (value < 1 || value > MAX_REALISTIC_YEARS) {
                continue;
            }
            if (max == null || value > max) {
                max = value;
            }
        }
        return max;
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * R1 모델이 requiredSkills/preferredSkills에 "결제 시스템 백엔드 API 설계 및 개발" 같은 업무 문장을
     * 스킬로 섞어 넣는 경우가 있어, 스킬처럼 보이지 않는 항목을 후처리로 걸러낸다.
     * 모든 항목이 걸러졌을 때: fallbackPostingText 가 있으면(requiredSkills 경로) 규칙 추출로 폴백해 빈 배열을 막고,
     * 없으면(preferredSkills 경로) 빈 배열을 반환한다(preferredSkills 는 비어도 유효하므로 잡음 원본을 되살리지 않는다).
     */
    private String filterSkillItems(String skillsJson, String fallbackPostingText) {
        List<String> skills = parseList(skillsJson);
        if (skills.isEmpty()) {
            return skillsJson;
        }
        // 통과 항목은 그대로 두고, 탈락 항목만 salvage(묶음에서 대표 스킬 복구)한다. LinkedHashSet 으로
        // 순서 유지 + 중복 제거(정상 "AWS" 와 salvage 로 나온 "AWS" 가 겹쳐도 하나로 수렴).
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        boolean changed = false;
        for (String skill : skills) {
            if (looksLikeSkill(skill)) {
                kept.add(skill.trim());
            } else {
                changed = true;
                kept.addAll(salvageSkillBundle(skill));
            }
        }
        if (!changed) {
            return skillsJson;
        }
        if (kept.isEmpty()) {
            // requiredSkills 경로(fallback 텍스트 있음)는 규칙 추출로 폴백, preferredSkills 경로는 빈 배열.
            return fallbackPostingText != null ? toJson(extractRequiredSkills(fallbackPostingText)) : toJson(List.of());
        }
        log.debug("Filtered/salvaged LLM skills: {} -> {}", skills, kept);
        return toJson(new ArrayList<>(kept));
    }

    /**
     * looksLikeSkill 에서 탈락한 항목이 여러 스킬을 한 문자열에 묶은 경우("PHP, Java, JSP, MariaDB 웹/서버 개발",
     * "AWS (CodeDeploy, EC2, ...)")에서 깨끗한 대표 스킬만 복구한다.
     * - top-level 구분자({@code , ; · ㆍ})로만 분할한다. 괄호 안 콤마는 분할하지 않는다(AWS (CodeDeploy, EC2) 보호).
     * - 슬래시(/)는 분할하지 않는다(CI/CD·TCP/IP·웹/서버 보존).
     * - 분할 후에도 탈락하고 '('를 포함하면 '(' 앞 대표 토큰만 남기되, **기술 토큰 계열일 때만** 복구한다.
     *   looksLikeSkill 은 짧은 한국어 명사구에 관대해, "결제 시스템(백엔드 API) 설계 및 개발" 의 prefix "결제 시스템"
     *   같은 업무 문장 조각이 살아나는 과복구를 막기 위함이다("AWS (…)" -> "AWS" 만 허용). 괄호 안 상세는 미복구.
     * - 아무 것도 못 살리면 빈 리스트(잡음 항목 제거).
     */
    private List<String> salvageSkillBundle(String item) {
        List<String> recovered = new ArrayList<>();
        for (String part : splitTopLevelDelimiters(item)) {
            String candidate = part.trim();
            if (looksLikeSkill(candidate)) {
                recovered.add(candidate);
                continue;
            }
            // 괄호 앞 대표 토큰 salvage: 과복구 방지 위해 기술 토큰(KNOWN_SKILLS 또는 영문/기술 토큰 계열)만.
            if (candidate.contains("(")) {
                String lead = candidate.substring(0, candidate.indexOf('(')).trim();
                if (looksLikeTechToken(lead) && looksLikeSkill(lead)) {
                    recovered.add(lead);
                }
            }
        }
        return recovered;
    }

    /**
     * 괄호 앞 대표 토큰 복구 대상인지 판정한다(과복구 방지). 1차는 KNOWN_SKILLS 정확 매칭(예측 가능 — 미등록 기술은
     * KNOWN_SKILLS 에 추가하는 편이 안전). 2차 regex fallback 은 공백 없는 단일 토큰 중 기술 신호가 있는 경우만:
     * 기술 기호(. + # / -)/숫자 포함(Node.js·CI/CD·C#) 또는 전부 대문자 약어(AWS·GCP·SQL).
     * "Payment System"(공백 영문 구문)·"결제 시스템"(한국어)처럼 기술 신호 없는 업무명 조각은 배제된다.
     */
    private boolean looksLikeTechToken(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (KNOWN_SKILLS.stream().anyMatch(skill -> skill.equalsIgnoreCase(trimmed))) {
            return true;
        }
        if (WHITESPACE_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        return TECH_SIGNAL_PATTERN.matcher(trimmed).find()
                || UPPER_ACRONYM_PATTERN.matcher(trimmed).matches();
    }

    /** 괄호 depth 0 인 위치의 top-level 구분자({@code , ; · ㆍ})로만 분할한다. */
    private List<String> splitTopLevelDelimiters(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && TOP_LEVEL_SKILL_DELIMITERS.indexOf(c) >= 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private boolean looksLikeSkill(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > SKILL_MAX_LENGTH) {
            return false;
        }
        if (trimmed.split("\\s+").length > SKILL_MAX_WORDS) {
            return false;
        }
        // A-4: OCR 깨짐(호환 자모 낱자)이 섞인 토큰은 스킬이 아니다(백패커·금융21). NON_SKILL_PATTERN 계열 확장.
        if (BROKEN_JAMO_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        if (NON_SKILL_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        return !SKILL_SENTENCE_PATTERN.matcher(trimmed).find();
    }

    private CompanyAnalysisPayload parseLocalCompanyPayload(String content,
                                                           ApplicationCase applicationCase,
                                                           String postingText,
                                                           String modelLabel) {
        JsonNode root = parseObject(content);
        // 폴백 게이트 재설계(6단계): 기업 정보가 부족한 공고에서 summary 를 비우는 보수적 출력은
        // 실패가 아니라 정상이다. 빈/짧은 summary 를 이유로 self-rules 로 폴백하지 않고,
        // 빈 값만 확인불가 고지 문구로 대체해 부분 성공 필드(industry/interviewPoints 등)를 보존한다.
        String companySummary = text(root, "companySummary", "");
        if (isBlank(companySummary)) {
            companySummary = CompanyAnalysisPromptCatalog.COMPANY_SUMMARY_UNAVAILABLE_NOTICE;
        }
        String recentIssues = text(root, "recentIssues", "");
        if (isBlank(recentIssues)) {
            recentIssues = CompanyAnalysisPromptCatalog.RECENT_ISSUES_UNAVAILABLE_NOTICE;
        }
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                companySummary,
                recentIssues,
                text(root, "industry", industry(postingText)),
                arrayJson(root, "competitors"),
                requiredText(root, "interviewPoints"),
                objectArrayJson(root, "sources", "type", "label"),
                objectArrayJson(root, "verifiedFacts", "fact", "source"),
                objectArrayJson(root, "aiInferences", "inference", "basis"),
                optionalArrayJson(root, "unknowns"),
                usage(modelLabel, postingText.length(), content.length()));
        validateCompanyPayload(payload, applicationCase);
        return payload;
    }

    private void validateJobPayload(JobAnalysisPayload payload) {
        if (parseList(payload.requiredSkills()).isEmpty()) {
            throw new IllegalStateException("Local LLM job analysis has no requiredSkills.");
        }
        if (isBlank(payload.summary()) || payload.summary().length() < 20) {
            throw new IllegalStateException("Local LLM job analysis summary is too short.");
        }
        if (isBlank(payload.duties()) || isBlank(payload.qualifications())) {
            throw new IllegalStateException("Local LLM job analysis is missing duties or qualifications.");
        }
    }

    private void validateGrounding(JobAnalysisPayload payload, String postingText) {
        List<String> allSkills = new ArrayList<>();
        allSkills.addAll(parseList(payload.requiredSkills()));
        allSkills.addAll(parseList(payload.preferredSkills()));
        if (allSkills.isEmpty()) {
            return;
        }
        String normalized = postingText.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        int grounded = 0;
        for (String skill : allSkills) {
            if (isSkillGrounded(skill, normalized)) {
                grounded++;
            }
        }
        double ratio = (double) grounded / allSkills.size();
        double threshold = properties.getLocalLlm().getGroundingThreshold();
        if (ratio < threshold) {
            throw new IllegalStateException(
                    "Grounding check failed: %.0f%% < %.0f%% threshold (%d/%d skills grounded)"
                            .formatted(ratio * 100, threshold * 100, grounded, allSkills.size()));
        }
        log.debug("Grounding check passed: {}/{} skills ({}%)", grounded, allSkills.size(), (int) (ratio * 100));
    }

    private boolean isSkillGrounded(String skill, String normalizedSource) {
        String[] tokens = skill.toLowerCase(Locale.ROOT).split("[^0-9a-zA-Z가-힣]+");
        List<String> meaningful = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                meaningful.add(token);
            }
        }
        if (meaningful.isEmpty()) {
            return false;
        }
        int hits = 0;
        for (String token : meaningful) {
            if (normalizedSource.contains(token)) {
                hits++;
            }
        }
        return meaningful.size() <= 2 ? hits >= 1 : (double) hits / meaningful.size() >= 0.5;
    }

    private void validateCompanyPayload(CompanyAnalysisPayload payload, ApplicationCase applicationCase) {
        // summary 길이 게이트는 6단계에서 제거 — 빈 값은 parseLocalCompanyPayload 가 확인불가 고지로
        // 대체하며, 짧지만 보수적인 결과는 폴백 사유가 아니다(231 문서 5-6).
        if (isBlank(payload.interviewPoints())) {
            throw new IllegalStateException("Local LLM company analysis is missing interviewPoints.");
        }
        if (!hasArrayItems(payload.verifiedFacts()) && isBlank(applicationCase.getCompanyName())) {
            throw new IllegalStateException("Local LLM company analysis has no verifiable company fact.");
        }
    }

    private String jobPrompt(ApplicationCase applicationCase, String postingText, Classification classification) {
        int budget = contentCharBudget();
        int classificationBudget = Math.max(0, Math.min(CLASSIFICATION_CHAR_CAP, budget - MIN_POSTING_CHARS));
        String classificationJson = truncate(toJson(classification.asMap()), classificationBudget);
        String posting = truncate(postingText, clampPostingChars(budget - classificationJson.length()));
        return """
                회사명: %s
                직무명: %s

                문장 분류 신호(JSON):
                %s

                채용공고 원문:
                %s
                """.formatted(
                defaultText(applicationCase.getCompanyName(), "unknown"),
                defaultText(applicationCase.getJobTitle(), "unknown"),
                classificationJson,
                posting);
    }

    private String companyPrompt(ApplicationCase applicationCase, String postingText, Classification classification) {
        return companyPromptBody(applicationCase, postingText, classification, 0);
    }

    /**
     * 회사 프롬프트 본문. {@code reservedChars} 는 뒤에 붙일 {@code [웹 검색 근거]} 블록이 차지할 길이로,
     * 그만큼 입력 예산에서 먼저 차감해 공고 본문을 자른다 — 웹 블록이 num_ctx 예산 밖에서 더해지지 않도록 한다
     * (리뷰 반영). 공고 본문은 최소 {@code MIN_POSTING_CHARS} 를 보장한다.
     */
    private String companyPromptBody(ApplicationCase applicationCase, String postingText, Classification classification,
                                     int reservedChars) {
        int budget = Math.max(MIN_POSTING_CHARS, contentCharBudget() - Math.max(0, reservedChars));
        int classificationBudget = Math.max(0, Math.min(CLASSIFICATION_CHAR_CAP, budget - MIN_POSTING_CHARS));
        String companyInfo = truncate(
                String.join("\n", classification.textsByLabel(BJobSentenceClassifier.COMPANY_INFO)),
                classificationBudget);
        String posting = truncate(postingText, clampPostingChars(budget - companyInfo.length()));
        return """
                회사명: %s
                직무명: %s

                COMPANY_INFO 분류 문장:
                %s

                채용공고 원문:
                %s
                """.formatted(
                defaultText(applicationCase.getCompanyName(), "unknown"),
                defaultText(applicationCase.getJobTitle(), "unknown"),
                companyInfo,
                posting);
    }

    /**
     * WEB 입력 배선(local/Claude 한정 · 235 §1). usableWeb(URL 보유분)가 있으면 {@code [웹 검색 근거]} 블록을
     * 먼저 만들어 그 길이를 공고 본문 예산에서 차감한 뒤 붙인다(웹 블록이 num_ctx 예산을 초과하지 않도록 — 리뷰 반영).
     * 빈 목록이면 base 를 그대로 반환한다(=현행 프롬프트 완전 불변). b-v6 프롬프트가 이 블록 형식을 기대한다.
     * URL blank/null evidence 는 상위 usableWebEvidence 에서 이미 제외됐다.
     */
    private String companyPrompt(ApplicationCase applicationCase, String postingText, Classification classification,
                                 List<CompanyWebEvidence> usableWeb) {
        String block = webEvidenceBlock(usableWeb);
        if (block.isEmpty()) {
            return companyPromptBody(applicationCase, postingText, classification, 0);
        }
        return companyPromptBody(applicationCase, postingText, classification, block.length()) + block;
    }

    /** {@code [웹 검색 근거]} 블록 문자열({url,snippet} 목록). usableWeb 가 비면 "" 를 반환한다(블록 없음). */
    private String webEvidenceBlock(List<CompanyWebEvidence> usableWeb) {
        if (usableWeb.isEmpty()) {
            return "";
        }
        StringBuilder block = new StringBuilder("\n[웹 검색 근거]\n");
        int count = 0;
        for (CompanyWebEvidence evidence : usableWeb) {
            if (count++ >= WEB_EVIDENCE_BLOCK_MAX_ITEMS) {
                break;
            }
            String snippet = firstNonBlank(evidence.snippet(), evidence.title()).replaceAll("\\s+", " ").trim();
            block.append("- {url: ").append(evidence.url().trim())
                    .append(", snippet: ").append(truncate(snippet, WEB_EVIDENCE_SNIPPET_MAX_CHARS))
                    .append("}\n");
        }
        return block.toString();
    }

    /** URL 보유 evidence 만 남긴다(D-2: WEB 출처는 URL 필수 — 블록/enum 게이트 기준). */
    private List<CompanyWebEvidence> usableWebEvidence(List<CompanyWebEvidence> webEvidence) {
        if (webEvidence == null || webEvidence.isEmpty()) {
            return List.of();
        }
        return webEvidence.stream()
                .filter(evidence -> evidence != null && evidence.url() != null && !evidence.url().isBlank())
                .toList();
    }

    /**
     * num_ctx(입력+출력 토큰 합) 예산에서 출력분(numPredict)과 고정 오버헤드를 뺀 뒤, 보조 신호+원문에 쓸 수 있는
     * 문자 수로 환산한다. 이 예산 안에서 분류 신호를 먼저 캡하고 나머지를 원문에 배정한다(jobPrompt/companyPrompt).
     */
    private int contentCharBudget() {
        BAnalysisProperties.LocalLlm local = properties.getLocalLlm();
        int contentTokens = local.getNumCtx() - local.getNumPredict() - FIXED_PROMPT_OVERHEAD_TOKENS;
        int chars = (int) (Math.max(0, contentTokens) * CHARS_PER_TOKEN);
        return Math.max(MIN_POSTING_CHARS, chars);
    }

    private int clampPostingChars(int proposed) {
        return Math.max(MIN_POSTING_CHARS, Math.min(MAX_PROMPT_TEXT_LENGTH, proposed));
    }

    private Map<String, Object> jobAnalysisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("employmentType", Map.of("type", "string", "enum", List.of("FULL_TIME", "CONTRACT", "INTERN", "PART_TIME")));
        properties.put("experienceLevel", Map.of("type", "string", "enum", List.of("JUNIOR", "MID", "SENIOR")));
        properties.put("requiredSkills", stringArraySchema());
        properties.put("preferredSkills", stringArraySchema());
        properties.put("duties", stringSchema());
        properties.put("qualifications", stringSchema());
        properties.put("difficulty", Map.of("type", "string", "enum", List.of("EASY", "NORMAL", "HARD")));
        properties.put("summary", stringSchema());
        properties.put("evidence", objectArraySchema(Map.of("field", stringSchema(), "quote", stringSchema()), List.of("field", "quote")));
        properties.put("ambiguousConditions", objectArraySchema(Map.of("condition", stringSchema(), "assumption", stringSchema()), List.of("condition", "assumption")));
        return objectSchema(properties, List.of(
                "employmentType", "experienceLevel", "requiredSkills", "preferredSkills",
                "duties", "qualifications", "difficulty", "summary", "evidence", "ambiguousConditions"));
    }

    /**
     * 기업분석 canonical contract 의 로컬/Claude 표현. 모델 JSON 실패율을 낮추기 위해 required 는
     * 최소(fact/source/evidence, inference/basis)로 두고, factId·sourceKind·sourceRef·inferenceId·
     * basedOn·confidence 는 properties 에만 열어 둔 뒤 저장 시 서버 canonicalizer 가 보정한다.
     * OpenAI 경로({@code OpenAiResponsesClient#companyAnalysisSchema})는 strict=true 제약 때문에
     * 같은 계약을 required + nullable 타입으로 표현한다. 두 경로의 필드 집합은 동일해야 한다.
     */
    Map<String, Object> companyAnalysisSchema() {
        return companyAnalysisSchema(false);
    }

    /**
     * WEB evidence(URL 보유) 존재 경로에서만 {@code includeWebSourceKind=true} 로 sourceKind enum 에 WEB 를
     * additive 추가한다. no-arg 는 {@code false} 위임이라 "WEB empty = schema 완전 불변"이 오버로드로 고정된다.
     */
    Map<String, Object> companyAnalysisSchema(boolean includeWebSourceKind) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("companySummary", stringSchema());
        properties.put("recentIssues", stringSchema());
        properties.put("industry", stringSchema());
        properties.put("competitors", stringArraySchema());
        properties.put("interviewPoints", stringSchema());
        properties.put("sources", objectArraySchema(Map.of("type", stringSchema(), "label", stringSchema()), List.of("type", "label")));
        Map<String, Object> factProperties = new LinkedHashMap<>();
        factProperties.put("fact", stringSchema());
        factProperties.put("source", stringSchema());
        factProperties.put("evidence", stringSchema());
        factProperties.put("factId", stringSchema());
        List<String> sourceKinds = includeWebSourceKind
                ? List.of("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO", "WEB")
                : List.of("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO");
        factProperties.put("sourceKind", Map.of("type", "string", "enum", sourceKinds));
        factProperties.put("sourceRef", stringSchema());
        properties.put("verifiedFacts", objectArraySchema(
                factProperties,
                List.of("fact", "source", "evidence"),
                COMPANY_VERIFIED_FACTS_MAX_ITEMS));
        Map<String, Object> inferenceProperties = new LinkedHashMap<>();
        inferenceProperties.put("inference", stringSchema());
        inferenceProperties.put("basis", stringSchema());
        inferenceProperties.put("inferenceId", stringSchema());
        inferenceProperties.put("basedOn", stringArraySchema());
        inferenceProperties.put("confidence", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW")));
        properties.put("aiInferences", objectArraySchema(
                inferenceProperties,
                List.of("inference", "basis"),
                COMPANY_AI_INFERENCES_MAX_ITEMS));
        Map<String, Object> unknownProperties = new LinkedHashMap<>();
        unknownProperties.put("topic", stringSchema());
        unknownProperties.put("reason", stringSchema());
        unknownProperties.put("neededSource", stringSchema());
        properties.put("unknowns", objectArraySchema(
                unknownProperties,
                List.of("topic", "reason"),
                COMPANY_UNKNOWNS_MAX_ITEMS));
        return objectSchema(properties, List.of(
                "companySummary", "recentIssues", "industry", "competitors", "interviewPoints",
                "sources", "verifiedFacts", "aiInferences"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required);
    }

    private Map<String, Object> objectArraySchema(Map<String, Object> properties, List<String> required) {
        return objectArraySchema(properties, required, null);
    }

    private Map<String, Object> objectArraySchema(Map<String, Object> properties, List<String> required, Integer maxItems) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", objectSchema(properties, required));
        if (maxItems != null) {
            schema.put("maxItems", maxItems);
        }
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", stringSchema());
    }

    private List<String> extractRequiredSkills(String text) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        if (found.isEmpty()) {
            found.add("Job posting analysis");
        }
        return found.stream().limit(10).toList();
    }

    private List<String> extractPreferredSkills(String text, List<String> requiredSkills) {
        String preferredText = extractSection(text, List.of("preferred", "nice to have", "plus", "우대"));
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = preferredText.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT)) && !containsIgnoreCase(requiredSkills, skill)) {
                found.add(skill);
            }
        }
        return found.stream().limit(6).toList();
    }

    private String extractSection(String text, List<String> markers) {
        if (isBlank(text)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && markers.stream().anyMatch(marker -> lower.contains(marker.toLowerCase(Locale.ROOT)))) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines.subList(0, Math.min(6, lines.size())));
    }

    private String employmentType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("intern") || lower.contains("인턴")) {
            return "INTERN";
        }
        if (lower.contains("contract") || lower.contains("계약직")) {
            return "CONTRACT";
        }
        if (lower.contains("part-time") || lower.contains("part time")) {
            return "PART_TIME";
        }
        return "FULL_TIME";
    }

    private String experienceLevel(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Integer years = maxStatedYears(text);
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("시니어")
                || (years != null && years >= SENIOR_YEARS_THRESHOLD)) {
            return "SENIOR";
        }
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("new grad")
                || lower.contains("신입") || lower.contains("주니어")) {
            return "JUNIOR";
        }
        return "MID";
    }

    private String difficulty(String text, List<String> requiredSkills) {
        String level = experienceLevel(text);
        if ("SENIOR".equals(level) || requiredSkills.size() >= 8) {
            return "HARD";
        }
        if ("JUNIOR".equals(level) && requiredSkills.size() <= 4) {
            return "EASY";
        }
        return "NORMAL";
    }

    /**
     * 공고 원문 키워드로 접지되는 industry 만 반환한다. 키워드 근거가 없으면 빈 값(확인불가)을
     * 반환한다 — self-rules 폴백이 무근거 "TECH" 기본값을 채워 grounding 을 악화시키던 문제의
     * 6단계 수정(231 문서 5-6). 빈 값은 저장 시 null 컬럼으로 정리된다.
     */
    private String industry(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fintech") || lower.contains("payment") || lower.contains("bank") || lower.contains("금융")) {
            return "FINTECH";
        }
        if (lower.contains("commerce") || lower.contains("retail") || lower.contains("marketplace") || lower.contains("커머스")) {
            return "COMMERCE";
        }
        if (lower.contains("health") || lower.contains("medical") || lower.contains("헬스케어")) {
            return "HEALTHCARE";
        }
        if (lower.contains("education") || lower.contains("learning") || lower.contains("교육")) {
            return "EDTECH";
        }
        return "";
    }

    private List<Map<String, String>> evidence(List<String> requiredSkills, String postingText) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String skill : requiredSkills.stream().limit(5).toList()) {
            rows.add(Map.of(
                    "field", "requiredSkills",
                    "quote", quoteFor(skill, postingText)));
        }
        return rows;
    }

    private List<Map<String, String>> verifiedFacts(ApplicationCase applicationCase, String postingText) {
        List<Map<String, String>> rows = new ArrayList<>();
        String jobTitle = knownJobTitle(applicationCase.getJobTitle());
        if (jobTitle != null) {
            rows.add(Map.of(
                    "fact", "직무명: " + jobTitle,
                    "source", "직무명",
                    "evidence", jobTitle));
        }
        String companyName = knownCompanyName(applicationCase.getCompanyName());
        if (companyName != null) {
            rows.add(Map.of(
                    "fact", "기업명: " + companyName,
                    "source", "회사명",
                    "evidence", companyName));
        }
        // 회사·직무가 미상이어도 품질 신호로 쓰이는 추출 사실은 항상 남긴다(validateCompanyPayload 의존).
        String postingEvidence = quoteFor(jobTitle == null ? "" : jobTitle, postingText);
        rows.add(Map.of(
                "fact", "공고문이 추출되어 품질 게이트를 통과한 뒤 분석되었습니다.",
                "source", "채용공고",
                "evidence", postingEvidence));
        return rows;
    }

    private List<Map<String, String>> ambiguousConditions(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (!text.toLowerCase(Locale.ROOT).contains("salary") && !text.contains("연봉")) {
            rows.add(Map.of("condition", "salary", "assumption", "not specified in posting"));
        }
        if (!text.toLowerCase(Locale.ROOT).contains("remote") && !text.contains("재택")) {
            rows.add(Map.of("condition", "work location", "assumption", "confirm during interview"));
        }
        return rows;
    }

    private String quoteFor(String target, String text) {
        if (isBlank(target) || isBlank(text)) {
            return firstSentences(text, 1);
        }
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).contains(lowerTarget)) {
                return truncate(trimmed, 240);
            }
        }
        return firstSentences(text, 1);
    }

    private String joinClassified(Classification classification, String label) {
        return String.join("\n", classification.textsByLabel(label).stream().limit(8).toList());
    }

    private JsonNode parseObject(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject()) {
                throw new IllegalStateException("Local LLM response is not a JSON object.");
            }
            return root;
        } catch (JacksonException ex) {
            throw new IllegalStateException("Local LLM response JSON cannot be parsed.", ex);
        }
    }

    private String requiredText(JsonNode root, String field) {
        String value = text(root, field, "");
        if (isBlank(value)) {
            throw new IllegalStateException("Local LLM response is missing " + field + ".");
        }
        return value;
    }

    private String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.path(field);
        if (!value.isString()) {
            return fallback;
        }
        return value.asText().trim();
    }

    private String arrayJson(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("Local LLM response field is not an array: " + field);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Local LLM response array cannot be serialized: " + field, ex);
        }
    }

    /** 선택 배열 필드 — 없거나 배열이 아니면 빈 배열로 둔다(구 모델 출력과의 하위 호환). */
    private String optionalArrayJson(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    /**
     * 02 포스타입 job 반복 루프 최소 대응(231 문서 5-9): 동일 field+quote evidence 를 후처리 dedup 한다.
     */
    private String dedupEvidenceJson(String evidenceJson) {
        try {
            JsonNode root = objectMapper.readTree(evidenceJson);
            if (!root.isArray()) {
                return evidenceJson;
            }
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            List<JsonNode> kept = new ArrayList<>();
            boolean changed = false;
            for (JsonNode item : root) {
                String key = item.path("field").asText("") + "|" + item.path("quote").asText("");
                if (seen.add(key)) {
                    kept.add(item);
                } else {
                    changed = true;
                }
            }
            if (!changed) {
                return evidenceJson;
            }
            log.debug("Deduplicated {} repeated job evidence item(s)", root.size() - kept.size());
            return objectMapper.writeValueAsString(kept);
        } catch (JacksonException ex) {
            return evidenceJson;
        }
    }

    private JobAnalysisPayload withDedupedEvidence(JobAnalysisPayload payload) {
        String deduped = dedupEvidenceJson(payload.evidence());
        if (deduped.equals(payload.evidence())) {
            return payload;
        }
        return new JobAnalysisPayload(
                payload.employmentType(),
                payload.experienceLevel(),
                payload.requiredSkills(),
                payload.preferredSkills(),
                payload.duties(),
                payload.qualifications(),
                payload.difficulty(),
                payload.summary(),
                deduped,
                payload.ambiguousConditions(),
                payload.usage());
    }

    private String objectArrayJson(JsonNode root, String field, String... requiredKeys) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("Local LLM response field is not an array: " + field);
        }
        for (JsonNode item : value) {
            if (!item.isObject()) {
                throw new IllegalStateException("Local LLM response array item is not an object: " + field);
            }
            for (String key : requiredKeys) {
                if (!item.path(key).isString()) {
                    throw new IllegalStateException("Local LLM response object is missing " + field + "." + key);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Local LLM response object array cannot be serialized: " + field, ex);
        }
    }

    private List<String> parseList(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values.stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .toList();
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    private boolean hasArrayItems(String json) {
        if (isBlank(json)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.isArray() && !root.isEmpty();
        } catch (JacksonException ex) {
            return false;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    private Usage usage(String model, int inputSize, int outputSize) {
        int inputTokens = estimateTokens(inputSize);
        int outputTokens = estimateTokens(outputSize);
        return new Usage(model, inputTokens, outputTokens, inputTokens + outputTokens);
    }

    private String firstSentences(String text, int maxSentences) {
        if (isBlank(text)) {
            return "";
        }
        String compact = text.trim().replaceAll("\\s+", " ");
        String[] parts = compact.split("(?<=[.!?])\\s+");
        List<String> selected = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                selected.add(part.trim());
            }
            if (selected.size() >= maxSentences) {
                break;
            }
        }
        String result = selected.isEmpty() ? compact : String.join(" ", selected);
        return truncate(result, 500);
    }

    private String joinPreview(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values.subList(0, Math.min(5, values.size())));
    }

    private int estimateLength(List<String> values) {
        return values == null ? 0 : values.stream().mapToInt(value -> value == null ? 0 : value.length()).sum();
    }

    private int estimateTokens(int chars) {
        return Math.max(0, (int) Math.ceil(Math.max(0, chars) / 4.0));
    }

    private String normalizeDifficulty(String value) {
        return switch (value) {
            case "EASY", "NORMAL", "HARD" -> value;
            default -> "NORMAL";
        };
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    /**
     * 회사명이 미상이면 null 을 반환한다. blank, "기업명 확인 필요" placeholder, "Target company"/"unknown"
     * fallback 값을 미상으로 본다. placeholder 상수를 다른 클래스에서 끌어오지 않고(공통 경계 변경 회피)
     * 회사명 필드 한정 predicate 로 감지한다.
     */
    private static String knownCompanyName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()
                || "Target company".equals(trimmed)
                || "unknown".equals(trimmed)
                || (trimmed.contains("기업명") && trimmed.contains("확인 필요"))) {
            return null;
        }
        return trimmed;
    }

    /** 직무명이 미상이면 null 을 반환한다(blank, "직무명 확인 필요" placeholder, "unknown"). */
    private static String knownJobTitle(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()
                || "unknown".equals(trimmed)
                || (trimmed.contains("직무명") && trimmed.contains("확인 필요"))) {
            return null;
        }
        return trimmed;
    }

    private static String safeMessage(Throwable ex) {
        return isBlank(ex.getMessage()) ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 공고분석 자유서술 후처리 결과 홀더(duties/qualifications/summary). */
    private record JobText(String duties, String qualifications, String summary) {
    }

    public record GeneratedJobAnalysis(
            JobAnalysisPayload payload,
            String fallbackReason,
            String fallbackAttemptedModel
    ) {
        public boolean fellBack() {
            return fallbackReason != null;
        }
    }

    public record GeneratedCompanyAnalysis(
            CompanyAnalysisPayload payload,
            String fallbackReason,
            String fallbackAttemptedModel
    ) {
        public boolean fellBack() {
            return fallbackReason != null;
        }
    }
}
