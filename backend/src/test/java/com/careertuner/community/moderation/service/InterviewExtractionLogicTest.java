package com.careertuner.community.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.community.domain.CommunityInterviewReview;
import com.careertuner.community.moderation.dto.InterviewExtractionResult;
import com.careertuner.community.moderation.dto.InterviewExtractionResult.ExtractedQuestion;

import tools.jackson.databind.json.JsonMapper;

/**
 * 면접 질문 추출 후처리(sanitize + 코드 머지) 로직 검증.
 *
 * DB/Spring/Ollama 없이 PostModerationService의 private static 후처리만 리플렉션으로 호출한다.
 * 입력으로 쓰는 RAW_AI_JSON은 실제 gemma4가 반환한 출력을 그대로 캡처한 것이다
 * ("context": "null" 문자열, 사용자 질문 echo, 본문 추가질문 포함).
 */
class InterviewExtractionLogicTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 실제 gemma4 출력 캡처본 (사용자 사전 입력 질문 2개 echo + 본문 JPA 질문 추가, context는 문자열 "null") */
    private static final String RAW_AI_JSON = """
            {
              "questions": [
                {
                  "question": "트랜잭션의 ACID 특성에 대해 설명해 주세요.",
                  "questionType": "TECH",
                  "context": "null",
                  "followUps": []
                },
                {
                  "question": "본인의 장점과 단점은 무엇인가요?",
                  "questionType": "PERSONALITY",
                  "context": "null",
                  "followUps": []
                },
                {
                  "question": "JPA에서 N+1 문제를 어떻게 해결하나요?",
                  "questionType": "TECH",
                  "context": "2차 기술면접",
                  "followUps": ["페치조인과 @BatchSize의 차이는 무엇인가요?"]
                }
              ],
              "overallNote": "전반적으로 편안한 분위기였습니다."
            }
            """;

    private static final List<String> USER_QUESTIONS = List.of(
            "트랜잭션의 ACID 특성에 대해 설명해 주세요.",
            "본인의 장점과 단점은 무엇인가요?"
    );

    @Test
    @DisplayName("실제 gemma4 출력 → sanitize + fallback + 머지 후 결과가 깨끗하다")
    void realGemma4Output_isSanitizedMergedAndDeduped() throws Exception {
        InterviewExtractionResult raw = MAPPER.readValue(RAW_AI_JSON, InterviewExtractionResult.class);

        InterviewExtractionResult sanitized = invokeSanitize(raw);

        // 문제 1: "null" 문자열 context가 실제 null로 정규화됨
        assertThat(sanitized.questions())
                .extracting(ExtractedQuestion::context)
                .containsExactly(null, null, "2차 기술면접");

        // AI가 누락한 회사/직무/결과를 review 행에서 보강
        CommunityInterviewReview review = CommunityInterviewReview.builder()
                .companyName("OO전자")
                .jobRole("백엔드 개발자")
                .resultStatus("PENDING")
                .build();
        InterviewExtractionResult filled = invokeFallback(sanitized, review);
        assertThat(filled.company()).isEqualTo("OO전자");
        assertThat(filled.position()).isEqualTo("백엔드 개발자");
        assertThat(filled.resultStatus()).isEqualTo("대기중"); // PENDING → 한국어 라벨

        // 문제 2: 사용자 질문 verbatim 시딩 + AI echo 중복 제거
        List<ExtractedQuestion> merged = invokeMerge(USER_QUESTIONS, filled.questions());

        // 중복 echo가 버려져 총 3건 (사용자 2 + 본문 JPA 1)
        assertThat(merged).hasSize(3);

        // 사용자 질문이 한 글자도 안 변하고 그대로 보존됨
        assertThat(merged.get(0).question()).isEqualTo("트랜잭션의 ACID 특성에 대해 설명해 주세요.");
        assertThat(merged.get(1).question()).isEqualTo("본인의 장점과 단점은 무엇인가요?");
        // AI echo본에서 분류한 type을 차용
        assertThat(merged.get(0).questionType()).isEqualTo("TECH");
        assertThat(merged.get(1).questionType()).isEqualTo("PERSONALITY");

        // 본문에서 새로 추출된 질문은 그대로 추가 (꼬리질문 포함)
        assertThat(merged.get(2).question()).isEqualTo("JPA에서 N+1 문제를 어떻게 해결하나요?");
        assertThat(merged.get(2).followUps()).containsExactly("페치조인과 @BatchSize의 차이는 무엇인가요?");
    }

    @Test
    @DisplayName("문제 1 보강: followUps의 \"null\"/빈문자열/없음 원소는 배열에서 제거된다")
    void followUpsNullElements_areDropped() throws Exception {
        ExtractedQuestion q = new ExtractedQuestion(
                "꼬리질문 테스트?", "TECH", "null",
                List.of("진짜 꼬리질문입니다?", "null", "  ", "없음", "N/A"));
        InterviewExtractionResult raw = new InterviewExtractionResult(
                null, null, null, null, List.of(q), null);

        InterviewExtractionResult sanitized = invokeSanitize(raw);

        assertThat(sanitized.questions().get(0).followUps())
                .containsExactly("진짜 꼬리질문입니다?");
        assertThat(sanitized.questions().get(0).context()).isNull(); // "null" → null
    }

    @Test
    @DisplayName("문제 2: AI가 사용자 질문을 누락해도 코드가 verbatim 보존을 보증한다")
    void userQuestionsPreserved_evenWhenAiDropsThem() throws Exception {
        // AI는 본문 질문 하나만 반환(사용자 질문 echo 실패 시나리오)
        List<ExtractedQuestion> aiOnly = List.of(
                new ExtractedQuestion("본문에서만 나온 질문?", "TECH", null, null));

        List<ExtractedQuestion> merged = invokeMerge(USER_QUESTIONS, aiOnly);

        assertThat(merged).hasSize(3);
        assertThat(merged.get(0).question()).isEqualTo("트랜잭션의 ACID 특성에 대해 설명해 주세요.");
        assertThat(merged.get(1).question()).isEqualTo("본인의 장점과 단점은 무엇인가요?");
        // AI 매칭이 없으면 type은 null (buildInterviewKnowledge가 null-safe 처리)
        assertThat(merged.get(0).questionType()).isNull();
        assertThat(merged.get(2).question()).isEqualTo("본문에서만 나온 질문?");
    }

    // ── 리플렉션 헬퍼 (운영 코드 캡슐화 유지) ──────────────────────────────

    private static InterviewExtractionResult invokeSanitize(InterviewExtractionResult raw) throws Exception {
        Method m = PostModerationService.class.getDeclaredMethod(
                "sanitizeExtractionResult", InterviewExtractionResult.class);
        m.setAccessible(true);
        return (InterviewExtractionResult) m.invoke(null, raw);
    }

    private static InterviewExtractionResult invokeFallback(
            InterviewExtractionResult result, CommunityInterviewReview review) throws Exception {
        Method m = PostModerationService.class.getDeclaredMethod(
                "applyReviewFallback", InterviewExtractionResult.class, CommunityInterviewReview.class);
        m.setAccessible(true);
        return (InterviewExtractionResult) m.invoke(null, result, review);
    }

    @SuppressWarnings("unchecked")
    private static List<ExtractedQuestion> invokeMerge(
            List<String> userQuestions, List<ExtractedQuestion> aiQuestions) throws Exception {
        Method m = PostModerationService.class.getDeclaredMethod(
                "mergeUserAndAiQuestions", List.class, List.class);
        m.setAccessible(true);
        return (List<ExtractedQuestion>) m.invoke(null, userQuestions, aiQuestions);
    }
}
