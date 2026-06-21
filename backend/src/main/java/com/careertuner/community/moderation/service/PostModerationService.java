package com.careertuner.community.moderation.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityInterviewReview;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostCategory;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.mapper.CommunityTagMapper;
import com.careertuner.community.moderation.client.OllamaClient;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.dto.InterviewExtractionResult;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.dto.TagResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.interview.domain.InterviewKnowledge;
import com.careertuner.interview.rag.InterviewKnowledgeMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 게시글 AI 검열 서비스.
 *
 * - judge(): 순수 판정만 (DB 접근 없음). 테스트 엔드포인트용.
 * - moderate(): 전체 파이프라인 (UPSERT → 판정 → 결과 저장 → 숨김).
 *
 * @Transactional 금지 — Ollama 호출(최대 30초)을 트랜잭션으로 묶으면
 * DB 커넥션을 오래 점유하여 커넥션 풀 고갈 위험.
 */
@Service
public class PostModerationService {

    private static final Logger log = LoggerFactory.getLogger(PostModerationService.class);

    private static final int MAX_TEXT_LENGTH = 8000;

    /** 카테고리 라벨 — AI 태그에서 제거할 금지어 */
    private static final List<String> CATEGORY_LABELS = java.util.Arrays.stream(PostCategory.values())
            .map(PostCategory::getLabel)
            .toList();

    /** 태깅용 JSON 스키마 */
    private static final Map<String, Object> TAGGING_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "tags", Map.of("type", "array",
                            "items", Map.of("type", "string")),
                    "confidence", Map.of("type", "number")
            ),
            "required", List.of("tags", "confidence")
    );

    /** 면접 질문 추출용 JSON 스키마 */
    private static final Map<String, Object> EXTRACT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "company", Map.of("type", "string"),
                    "position", Map.of("type", "string"),
                    "interviewDate", Map.of("type", "string"),
                    "resultStatus", Map.of("type", "string"),
                    "questions", Map.of("type", "array",
                            "items", Map.of("type", "object",
                                    "properties", Map.of(
                                            "question", Map.of("type", "string"),
                                            "questionType", Map.of("type", "string",
                                                    "enum", List.of("TECH", "PERSONALITY", "SITUATION", "EXPECTED", "FOLLOW_UP")),
                                            "context", Map.of("type", "string"),
                                            "followUps", Map.of("type", "array",
                                                    "items", Map.of("type", "string"))
                                    ),
                                    "required", List.of("question", "questionType")
                            )),
                    "overallNote", Map.of("type", "string")
            ),
            "required", List.of("questions")
    );

    /** Ollama structured output용 JSON 스키마 (gemma4.md 검증 완료 형식) */
    private static final Map<String, Object> MODERATION_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "toxic", Map.of("type", "boolean"),
                    "category", Map.of("type", "string",
                            "enum", List.of("normal", "abuse", "spam", "ad")),
                    "confidence", Map.of("type", "number")
            ),
            "required", List.of("toxic", "category", "confidence")
    );

    private final OllamaClient ollamaClient;
    private final OllamaProperties ollamaProperties;
    private final PostAiResultMapper aiResultMapper;
    private final CommunityPostMapper postMapper;
    private final CommunityTagMapper tagMapper;
    private final NotificationService notificationService;
    private final ModerationSettingService settingService;
    private final UserSanctionService userSanctionService;
    private final ObjectMapper objectMapper;
    private final InterviewKnowledgeMapper interviewKnowledgeMapper;

    /** 기본 시스템 프롬프트 (classpath 파일에서 1회 로드) */
    private final String baseSystemPrompt;

    /** 태깅 시스템 프롬프트 (classpath 파일에서 1회 로드) */
    private final String taggingSystemPrompt;

    /** 면접 질문 추출 시스템 프롬프트 (classpath 파일에서 1회 로드) */
    private final String extractSystemPrompt;

    /** 태깅 신뢰도 임계값 */
    private final double tagConfidenceThreshold;

    /** 엄격도별 지침 텍스트 (classpath 파일에서 1회 로드) */
    private final Map<Strictness, String> strictnessTexts;

    public PostModerationService(
            OllamaClient ollamaClient,
            OllamaProperties ollamaProperties,
            PostAiResultMapper aiResultMapper,
            CommunityPostMapper postMapper,
            CommunityTagMapper tagMapper,
            NotificationService notificationService,
            ModerationSettingService settingService,
            UserSanctionService userSanctionService,
            ObjectMapper objectMapper,
            InterviewKnowledgeMapper interviewKnowledgeMapper,
            ResourceLoader resourceLoader,
            @Value("classpath:prompts/moderation-system.txt") Resource promptResource,
            @Value("classpath:prompts/tagging-system.txt") Resource taggingPromptResource,
            @Value("classpath:prompts/interview-extract-system.txt") Resource extractPromptResource,
            @Value("${ai.tagging.confidence-threshold:0.7}") double tagConfidenceThreshold
    ) {
        this.ollamaClient = ollamaClient;
        this.ollamaProperties = ollamaProperties;
        this.aiResultMapper = aiResultMapper;
        this.postMapper = postMapper;
        this.tagMapper = tagMapper;
        this.notificationService = notificationService;
        this.settingService = settingService;
        this.userSanctionService = userSanctionService;
        this.objectMapper = objectMapper;
        this.interviewKnowledgeMapper = interviewKnowledgeMapper;
        this.tagConfidenceThreshold = tagConfidenceThreshold;

        // 기본 프롬프트 로드
        try {
            this.baseSystemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("moderation-system.txt 로드 실패", e);
        }

        // 태깅 프롬프트 로드
        try {
            this.taggingSystemPrompt = taggingPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("tagging-system.txt 로드 실패", e);
        }

        // 면접 질문 추출 프롬프트 로드
        try {
            this.extractSystemPrompt = extractPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("interview-extract-system.txt 로드 실패", e);
        }

        // 엄격도별 지침 텍스트 로드
        this.strictnessTexts = new EnumMap<>(Strictness.class);
        for (Strictness s : Strictness.values()) {
            Resource res = resourceLoader.getResource(
                    "classpath:prompts/strictness/" + s.name() + ".txt");
            try {
                strictnessTexts.put(s, res.getContentAsString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(s.name() + ".txt 로드 실패", e);
            }
        }
    }

    /**
     * 현재 설정의 엄격도를 반영한 시스템 프롬프트를 조립한다.
     */
    private String buildSystemPrompt() {
        Strictness strictness = settingService.getStrictness();
        String strictnessText = strictnessTexts.get(strictness);
        return baseSystemPrompt + "\n\n[엄격도 지침]\n" + strictnessText;
    }

    /**
     * 순수 판정 — DB 접근 없음. 테스트 엔드포인트가 직접 호출한다.
     * 항상 "현재 설정"으로 판정한다.
     */
    public ModerationResult judge(String title, String content) {
        String text = "제목: " + title + "\n본문: " + content;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        String prompt = buildSystemPrompt();
        String json = ollamaClient.chat(prompt, text, MODERATION_SCHEMA);
        return parseResult(json);
    }

    /**
     * 전체 검열 파이프라인 — 이벤트 리스너가 비동기로 호출한다.
     * @Transactional 금지 (공통 금지사항)
     */
    public void moderate(Long postId) {
        try {
            // 1. UPSERT: PENDING 상태로 기록 (재시도 시 attempt_count 증가)
            aiResultMapper.upsertPending(postId, AiTaskType.MODERATION);

            // 2. 게시글 조회 — null이거나 DELETED면 검열 불필요
            CommunityPost post = postMapper.findById(postId);
            if (post == null || PostStatus.DELETED.name().equals(post.getStatus())) {
                log.info("검열 스킵: postId={} (삭제됨 또는 존재하지 않음)", postId);
                return;
            }

            // 3. 현재 설정 스냅샷
            Strictness currentStrictness = settingService.getStrictness();
            double currentThreshold = settingService.getHideThreshold();

            // 4. 판정
            ModerationResult result = judge(post.getTitle(), post.getContent());

            // 5. 결과 저장 (COMPLETED) — applied 스냅샷 병합
            String resultJson = buildResultJson(result, currentStrictness, currentThreshold);
            aiResultMapper.complete(postId, AiTaskType.MODERATION,
                    resultJson, ollamaProperties.getModel());

            // 6. 숨김 처리 (toxic + 캐시된 threshold 기준)
            if (result.toxic() && result.confidence() >= currentThreshold) {
                int updated = postMapper.hideIfPublished(postId);
                if (updated > 0) {
                    log.warn("게시글 숨김 처리: postId={}, category={}, confidence={}",
                            postId, result.category(), result.confidence());
                    sendHiddenNotification(post);
                    // 검열 누적 → 사용자 단위 자동 제재 (best-effort: 실패해도 검열 결과엔 영향 없음)
                    try {
                        userSanctionService.sanctionIfNeeded(post.getUserId());
                    } catch (Exception e) {
                        log.error("자동 제재 처리 실패: userId={}", post.getUserId(), e);
                    }
                }
            }

            log.info("검열 완료: postId={}, toxic={}, category={}, confidence={}, strictness={}, threshold={}",
                    postId, result.toxic(), result.category(), result.confidence(),
                    currentStrictness, currentThreshold);

        } catch (Exception e) {
            // 7. 실패 기록 — 예외를 다시 던지지 않는다
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.MODERATION, errorMsg);
            log.error("검열 실패: postId={}", postId, e);
        }
    }

    /**
     * 신고된 게시글 AI 분류 — 자동 숨김/알림 없이 판정 결과만 저장한다.
     * 관리자가 신고 처리 시 참고 정보로 활용한다.
     */
    public void classify(Long postId) {
        try {
            aiResultMapper.upsertPending(postId, AiTaskType.REPORT);

            CommunityPost post = postMapper.findById(postId);
            if (post == null || PostStatus.DELETED.name().equals(post.getStatus())) {
                log.info("신고 분류 스킵: postId={} (삭제됨 또는 존재하지 않음)", postId);
                return;
            }

            Strictness currentStrictness = settingService.getStrictness();
            double currentThreshold = settingService.getHideThreshold();

            ModerationResult result = judge(post.getTitle(), post.getContent());

            String resultJson = buildResultJson(result, currentStrictness, currentThreshold);
            aiResultMapper.complete(postId, AiTaskType.REPORT,
                    resultJson, ollamaProperties.getModel());

            log.info("신고 분류 완료: postId={}, toxic={}, category={}, confidence={}",
                    postId, result.toxic(), result.category(), result.confidence());

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.REPORT, errorMsg);
            log.error("신고 분류 실패: postId={}", postId, e);
        }
    }

    /**
     * AI 태깅 파이프라인 — 이벤트 리스너가 비동기로 호출한다.
     * 신뢰도 >= 임계값이면 태그를 자동 적용하고, 미만이면 추천만 저장한다.
     */
    public void tag(Long postId) {
        try {
            aiResultMapper.upsertPending(postId, AiTaskType.TAG);

            CommunityPost post = postMapper.findById(postId);
            if (post == null || PostStatus.DELETED.name().equals(post.getStatus())) {
                log.info("태깅 스킵: postId={} (삭제됨 또는 존재하지 않음)", postId);
                return;
            }

            // AI 태그 추출
            String categoryLabel = PostCategory.valueOf(post.getCategory()).getLabel();
            String text = "카테고리: " + categoryLabel + "\n제목: " + post.getTitle() + "\n본문: " + post.getContent();
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            String json = ollamaClient.chat(taggingSystemPrompt, text, TAGGING_SCHEMA);
            TagResult result = objectMapper.readValue(json, TagResult.class);

            // 카테고리명과 동일한 태그 제거
            List<String> filteredTags = result.tags() == null ? List.of() : result.tags().stream()
                    .filter(tag -> CATEGORY_LABELS.stream().noneMatch(label -> label.equals(tag)))
                    .toList();

            boolean applied = result.confidence() >= tagConfidenceThreshold;

            if (applied && !filteredTags.isEmpty()) {
                applyAiTags(postId, filteredTags);
            }

            // 결과 저장
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("tags", filteredTags);
            resultMap.put("confidence", result.confidence());
            resultMap.put("applied", applied);
            resultMap.put("threshold", tagConfidenceThreshold);
            String resultJson = objectMapper.writeValueAsString(resultMap);
            aiResultMapper.complete(postId, AiTaskType.TAG,
                    resultJson, ollamaProperties.getModel());

            log.info("태깅 완료: postId={}, tags={}, confidence={}, applied={}",
                    postId, result.tags(), result.confidence(), applied);

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.TAG, errorMsg);
            log.error("태깅 실패: postId={}", postId, e);
        }
    }

    /**
     * 면접후기 AI 질문 추출 파이프라인 — 이벤트 리스너가 비동기로 호출한다.
     * INTERVIEW_REVIEW 카테고리 글에서 면접 질문을 구조화 추출하여
     * community_interview_review.ai_extracted_questions + interview_knowledge에 저장한다.
     */
    public void extractInterviewQuestions(Long postId) {
        try {
            aiResultMapper.upsertPending(postId, AiTaskType.INTERVIEW_EXTRACT);

            CommunityPost post = postMapper.findById(postId);
            if (post == null || PostStatus.DELETED.name().equals(post.getStatus())) {
                log.info("면접 질문 추출 스킵: postId={} (삭제됨 또는 존재하지 않음)", postId);
                return;
            }
            if (!PostCategory.INTERVIEW_REVIEW.name().equals(post.getCategory())) {
                log.info("면접 질문 추출 스킵: postId={} (카테고리={})", postId, post.getCategory());
                return;
            }

            // 면접후기 확장 데이터 조회
            CommunityInterviewReview review = postMapper.findInterviewReviewByPostId(postId);

            // 사용자 사전 입력 질문(questions_json: 문자열 배열). AI 호출 후 코드 머지에서도
            // 써야 하므로 블록 밖으로 hoist 한다.
            List<String> userQuestions = review != null
                    ? parseUserQuestions(review.getQuestionsJson())
                    : List.of();

            // userText 조합
            StringBuilder sb = new StringBuilder();
            sb.append("[면접 후기 본문]");
            sb.append("\n제목: ").append(post.getTitle());
            sb.append("\n본문: ").append(post.getContent());
            if (review != null) {
                if (review.getCompanyName() != null) sb.append("\n회사명: ").append(review.getCompanyName());
                if (review.getJobRole() != null) sb.append("\n직무: ").append(review.getJobRole());
                if (review.getInterviewType() != null) sb.append("\n면접유형: ").append(review.getInterviewType());
                if (review.getInterviewDate() != null) sb.append("\n면접일자: ").append(review.getInterviewDate());
                if (review.getResultStatus() != null) sb.append("\n결과: ").append(review.getResultStatus());

                // 사용자 사전 입력 질문을 AI에 함께 전달(중복 제외·유형 분류 참고용). 단, 최종
                // questions 보존은 프롬프트가 아니라 아래 코드 머지(mergeUserAndAiQuestions)가 보증한다.
                if (!userQuestions.isEmpty()) {
                    sb.append("\n\n[사용자 사전 입력 질문]");
                    for (String q : userQuestions) {
                        sb.append("\n- ").append(q);
                    }
                }
            }

            String userText = sb.toString();
            if (userText.length() > MAX_TEXT_LENGTH) {
                userText = userText.substring(0, MAX_TEXT_LENGTH);
            }

            // Ollama 호출
            String json = ollamaClient.chat(extractSystemPrompt, userText, EXTRACT_SCHEMA);
            // gemma가 백틱/인사말을 섞어 반환하는 경우를 대비해 첫 { ~ 마지막 }만 잘라낸다.
            InterviewExtractionResult result = sanitizeExtractionResult(
                    objectMapper.readValue(extractJsonObject(json), InterviewExtractionResult.class));

            // AI가 메타데이터(회사명/직무/결과)를 출력에 echo하지 않는 경우가 잦으므로,
            // null이면 review 행의 확정 값으로 채운다. (AI 출력에 의존하지 않음)
            result = applyReviewFallback(result, review);

            // 사용자 사전 입력 질문을 코드에서 verbatim 시딩하고, AI 추출분 중 중복은 버린다.
            // verbatim 보존을 프롬프트 신뢰가 아니라 코드가 보증한다.
            List<InterviewExtractionResult.ExtractedQuestion> mergedQuestions =
                    mergeUserAndAiQuestions(userQuestions, result.questions());
            result = new InterviewExtractionResult(
                    result.company(),
                    result.position(),
                    result.interviewDate(),
                    result.resultStatus(),
                    mergedQuestions,
                    result.overallNote()
            );

            // sanitize 후 JSON으로 재직렬화하여 저장 (원본 json에 "null" 문자열이 남아있을 수 있으므로)
            String sanitizedJson = objectMapper.writeValueAsString(result);

            // 1. community_interview_review.ai_extracted_questions에 저장
            postMapper.updateAiExtractedQuestions(postId, sanitizedJson);

            // 2. InterviewKnowledge 중복 방지: 기존 행 삭제 후 재삽입
            String source = "CareerTuner 커뮤니티 #" + postId;
            postMapper.deleteInterviewKnowledgeBySource(source);

            if (result.questions() != null && !result.questions().isEmpty()) {
                for (InterviewExtractionResult.ExtractedQuestion q : result.questions()) {
                    InterviewKnowledge knowledge = buildInterviewKnowledge(
                            q, result, postId, source);
                    interviewKnowledgeMapper.insert(knowledge);
                }
            }

            // 결과 저장
            aiResultMapper.complete(postId, AiTaskType.INTERVIEW_EXTRACT,
                    sanitizedJson, ollamaProperties.getModel());

            log.info("면접 질문 추출 완료: postId={}, 추출 질문 수={}",
                    postId, result.questions() == null ? 0 : result.questions().size());

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.INTERVIEW_EXTRACT, errorMsg);
            log.error("면접 질문 추출 실패: postId={}", postId, e);
        }
    }

    /** result_status 코드 → 한국어 라벨 (InterviewKnowledge 가독성·TTS용) */
    private static String resultStatusLabel(String code) {
        if (code == null) return null;
        return switch (code.strip().toUpperCase()) {
            case "PASSED" -> "합격";
            case "FAILED" -> "불합격";
            case "PENDING" -> "대기중";
            case "UNKNOWN" -> "비공개";
            default -> code; // 이미 라벨이거나 알 수 없는 값이면 그대로
        };
    }

    /**
     * AI가 메타데이터를 출력에 echo하지 않은 경우, review 행의 확정 값으로 채운다.
     * 회사명/직무/결과는 review 행에 확실히 있으므로 AI 출력을 기다릴 필요가 없다.
     * (이미 값이 있으면 AI 출력을 그대로 둔다.)
     */
    private static InterviewExtractionResult applyReviewFallback(
            InterviewExtractionResult result, CommunityInterviewReview review) {
        if (review == null) return result;
        String company = result.company() != null ? result.company() : sanitizeString(review.getCompanyName());
        String position = result.position() != null ? result.position() : sanitizeString(review.getJobRole());
        String resultStatus = result.resultStatus() != null
                ? result.resultStatus()
                : resultStatusLabel(sanitizeString(review.getResultStatus()));
        return new InterviewExtractionResult(
                company,
                position,
                result.interviewDate(),
                resultStatus,
                result.questions(),
                result.overallNote()
        );
    }

    /**
     * AI가 "null" 문자열을 반환하는 경우를 실제 null로 정규화한다.
     */
    private static String sanitizeString(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "없음".equals(trimmed)
                || "N/A".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return value;
    }

    /**
     * questions_json(사용자 입력 질문, 문자열 JSON 배열)을 파싱한다.
     * 비어 있거나 파싱 실패 시 빈 목록 반환(기존 동작 유지).
     */
    private List<String> parseUserQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(
                    questionsJson, new TypeReference<List<String>>() {});
            return parsed.stream()
                    .filter(q -> q != null && !q.isBlank())
                    .map(String::strip)
                    .toList();
        } catch (Exception e) {
            log.warn("questions_json 파싱 실패, 사용자 입력 질문 무시: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * LLM 응답에서 첫 '{'부터 마지막 '}'까지만 잘라낸다.
     * Ollama가 ```json 백틱이나 인사말을 섞어 반환해도 정상 파싱되게 하는 방어 로직.
     */
    private static String extractJsonObject(String raw) {
        if (raw == null) return null;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    /**
     * 추출 결과 전체를 sanitize한다. 파싱 직후 1회 호출.
     */
    private static InterviewExtractionResult sanitizeExtractionResult(InterviewExtractionResult raw) {
        List<InterviewExtractionResult.ExtractedQuestion> sanitizedQuestions = null;
        if (raw.questions() != null) {
            sanitizedQuestions = raw.questions().stream()
                    .map(q -> new InterviewExtractionResult.ExtractedQuestion(
                            q.question(),
                            sanitizeString(q.questionType()),
                            sanitizeString(q.context()),
                            sanitizeStringList(q.followUps())
                    ))
                    .toList();
        }
        return new InterviewExtractionResult(
                sanitizeString(raw.company()),
                sanitizeString(raw.position()),
                sanitizeString(raw.interviewDate()),
                sanitizeString(raw.resultStatus()),
                sanitizedQuestions,
                sanitizeString(raw.overallNote())
        );
    }

    /**
     * 문자열 리스트에서 "null"/"없음"/"N/A"/빈문자열·공백 원소를 제거한다.
     * 예: ["트랜잭션 설명", "null", ""] → ["트랜잭션 설명"], ["null"] → [].
     * 입력이 null이면 null 유지.
     */
    private static List<String> sanitizeStringList(List<String> raw) {
        if (raw == null) return null;
        return raw.stream()
                .map(PostModerationService::sanitizeString)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 사용자 사전 입력 질문을 verbatim 시딩하고, AI 추출분 중 중복을 제거해 머지한다.
     * - 사용자 질문은 문장을 절대 수정·삭제하지 않고 그대로 questions에 넣는다(코드가 보존을 보증).
     * - AI가 같은 질문을 echo·분류했으면 questionType/context/followUps만 차용한다.
     * - 정규화(공백·대소문자·문장부호 정리) 비교로 중복을 판정하며, 사용자 질문을 우선한다.
     */
    private static List<InterviewExtractionResult.ExtractedQuestion> mergeUserAndAiQuestions(
            List<String> userQuestions,
            List<InterviewExtractionResult.ExtractedQuestion> aiQuestions) {

        List<InterviewExtractionResult.ExtractedQuestion> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // 1) 사용자 사전 입력 질문 verbatim 시딩
        if (userQuestions != null) {
            for (String uq : userQuestions) {
                String key = normalizeForDedup(uq);
                if (key.isEmpty() || !seen.add(key)) continue; // 사용자 입력 내 중복도 정리
                InterviewExtractionResult.ExtractedQuestion aiMatch = findByNormalized(aiQuestions, key);
                merged.add(new InterviewExtractionResult.ExtractedQuestion(
                        uq, // verbatim — 절대 수정 금지
                        aiMatch != null ? aiMatch.questionType() : null,
                        aiMatch != null ? aiMatch.context() : null,
                        aiMatch != null ? aiMatch.followUps() : null
                ));
            }
        }

        // 2) AI 추출분 중 사용자 질문과 중복되지 않는 것만 추가
        if (aiQuestions != null) {
            for (InterviewExtractionResult.ExtractedQuestion aq : aiQuestions) {
                if (aq == null || aq.question() == null) continue;
                String key = normalizeForDedup(aq.question());
                if (key.isEmpty() || !seen.add(key)) continue;
                merged.add(aq);
            }
        }
        return merged;
    }

    /** 정규화 키가 일치하는 첫 AI 질문을 찾는다(echo된 사용자 질문의 분류 차용용). */
    private static InterviewExtractionResult.ExtractedQuestion findByNormalized(
            List<InterviewExtractionResult.ExtractedQuestion> list, String key) {
        if (list == null) return null;
        for (InterviewExtractionResult.ExtractedQuestion q : list) {
            if (q != null && q.question() != null
                    && normalizeForDedup(q.question()).equals(key)) {
                return q;
            }
        }
        return null;
    }

    /** 중복 비교용 정규화: 앞뒤 공백 제거 + 소문자화 + 문장부호·공백을 단일 공백으로 축약. */
    private static String normalizeForDedup(String s) {
        if (s == null) return "";
        return s.strip()
                .toLowerCase()
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .strip();
    }

    /**
     * 추출된 질문 1건을 InterviewKnowledge 엔티티로 매핑한다.
     */
    private InterviewKnowledge buildInterviewKnowledge(
            InterviewExtractionResult.ExtractedQuestion q,
            InterviewExtractionResult result,
            Long postId, String source) {

        // title: "{company} {position} — {questionType}" (없으면 생략)
        StringBuilder titleSb = new StringBuilder();
        if (result.company() != null) titleSb.append(result.company()).append(" ");
        if (result.position() != null) titleSb.append(result.position()).append(" ");
        if (q.questionType() != null) {
            if (!titleSb.isEmpty()) titleSb.append("— ");
            titleSb.append(q.questionType());
        }

        // content: 구조화 텍스트
        StringBuilder contentSb = new StringBuilder();
        contentSb.append("[면접 질문]\n").append(q.question()).append("\n");
        if (q.questionType() != null) contentSb.append("\n[유형] ").append(q.questionType());
        if (result.company() != null) contentSb.append("\n[회사] ").append(result.company());
        if (result.position() != null) contentSb.append("\n[직무] ").append(result.position());
        if (result.interviewDate() != null) contentSb.append("\n[면접 시기] ").append(result.interviewDate());
        if (result.resultStatus() != null) contentSb.append("\n[면접 결과] ").append(result.resultStatus());

        if (q.context() != null) {
            contentSb.append("\n\n[맥락]\n").append(q.context());
        }
        if (q.followUps() != null && !q.followUps().isEmpty()) {
            contentSb.append("\n\n[꼬리질문]");
            for (String followUp : q.followUps()) {
                contentSb.append("\n- ").append(followUp);
            }
        }
        if (result.overallNote() != null) {
            contentSb.append("\n\n[면접 분위기]\n").append(result.overallNote());
        }

        return InterviewKnowledge.builder()
                .kind("QUESTION_BANK")
                .title(titleSb.toString().strip())
                .content(contentSb.toString())
                .source(source)
                .indexed(false)
                .build();
    }

    /**
     * AI 태그를 게시글에 적용한다.
     * 기존 AI 태그(is_ai=1)를 삭제하고, 새 AI 태그를 삽입한 뒤 tags_json 캐시를 갱신한다.
     */
    private void applyAiTags(Long postId, List<String> tags) {
        // 1. 기존 AI 태그의 usage_count 감소
        List<Long> oldAiTagIds = tagMapper.findAiTagIds(postId);
        for (Long tagId : oldAiTagIds) {
            tagMapper.decrementUsageCount(tagId);
        }

        // 2. 기존 AI 태그 삭제
        tagMapper.deleteAiPostTags(postId);

        // 3. 새 AI 태그 삽입
        for (String tagName : tags) {
            String trimmed = tagName.strip();
            if (trimmed.isEmpty()) continue;

            // community_tag 마스터에 없으면 생성
            tagMapper.insertTag(trimmed); // INSERT IGNORE
            Long tagId = tagMapper.findIdByName(trimmed);
            if (tagId == null) continue;

            tagMapper.insertPostTag(postId, tagId, true);
            tagMapper.incrementUsageCount(tagId);
        }

        // 4. tags_json 캐시 갱신 (사용자 태그 + AI 태그 전체)
        List<String> allTags = tagMapper.findTagNamesByPostId(postId);
        String tagsJson = null;
        if (!allTags.isEmpty()) {
            try {
                tagsJson = objectMapper.writeValueAsString(allTags);
            } catch (Exception e) {
                log.warn("tags_json 직렬화 실패: postId={}", postId, e);
            }
        }
        postMapper.updateTagsJson(postId, tagsJson);
    }

    /**
     * 모델 응답 JSON에 applied 스냅샷을 병합한다.
     */
    @SuppressWarnings("unchecked")
    private String buildResultJson(ModerationResult result,
                                   Strictness strictness, double hideThreshold) {
        try {
            // ModerationResult → Map으로 변환
            String raw = objectMapper.writeValueAsString(result);
            Map<String, Object> map = objectMapper.readValue(raw, LinkedHashMap.class);

            // applied 스냅샷 추가
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("strictness", strictness.name());
            applied.put("hideThreshold", hideThreshold);
            map.put("applied", applied);

            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("applied 병합 실패, 원본 JSON 사용", e);
            try {
                return objectMapper.writeValueAsString(result);
            } catch (Exception ex) {
                throw new IllegalStateException("JSON 직렬화 실패", ex);
            }
        }
    }

    /**
     * 검열 숨김 시 작성자에게 알림 발송.
     */
    private void sendHiddenNotification(CommunityPost post) {
        String postTitle = truncate(post.getTitle(), 30);
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_HIDDEN")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 커뮤니티 가이드라인 검토 대기 상태로 전환되었습니다")
                .message("'" + postTitle + "' 게시글이 자동 검수에 의해 검토 대기 상태로 전환되었습니다. "
                        + "관리자 검토 후 복원되거나 삭제될 수 있습니다.")
                .link("/community?view=guidelines")
                .build();
        notificationService.notify(noti);
    }

    /**
     * 관리자 복원 시 작성자에게 알림 발송.
     */
    void sendRestoredNotification(CommunityPost post) {
        String postTitle = truncate(post.getTitle(), 30);
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_RESTORED")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 복원되었습니다")
                .message("'" + postTitle + "' 게시글이 관리자 검토를 통과하여 복원되었습니다.")
                .link("/community/posts/" + post.getId())
                .build();
        notificationService.notify(noti);
    }

    /**
     * 관리자 삭제 시 작성자에게 알림 발송.
     */
    void sendDeletedNotification(CommunityPost post) {
        String postTitle = truncate(post.getTitle(), 30);
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_REMOVED")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 가이드라인 위반으로 삭제되었습니다")
                .message("'" + postTitle + "' 게시글이 커뮤니티 가이드라인 위반으로 삭제 처리되었습니다.")
                .link("/community/posts/" + post.getId())
                .build();
        notificationService.notify(noti);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private ModerationResult parseResult(String json) {
        try {
            return objectMapper.readValue(json, ModerationResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("검열 결과 JSON 파싱 실패: " + json, e);
        }
    }
}
