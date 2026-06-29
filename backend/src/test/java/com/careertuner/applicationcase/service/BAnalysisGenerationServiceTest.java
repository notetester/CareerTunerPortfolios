package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.ApplicationCase;

import tools.jackson.databind.ObjectMapper;

class BAnalysisGenerationServiceTest {

    @Test
    void localLlmDisabledUsesSelfRulesWithoutCallingOllama() {
        BAnalysisProperties properties = new BAnalysisProperties();
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo(BAnalysisGenerationService.SELF_RULES_MODEL);
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot", "MySQL");
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
    }

    @Test
    void localLlmValidJsonProducesQwenPayload() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": ["Docker"],
                  "duties": "Spring API 개발과 운영",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java와 Spring Boot 경험"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("qwen-test");
        assertThat(result.payload().summary()).contains("백엔드 개발자");
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot");
    }

    @Test
    void localLlmExperienceLevelIsCorrectedFromStatedYears() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "JUNIOR",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": [],
                  "duties": "Spring API 개발과 운영",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(
                applicationCase(), "Java 백엔드 개발 경력 5년 이상. Spring Boot 경험 필수.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().experienceLevel()).isEqualTo("SENIOR");
    }

    @Test
    void localLlmRequiredSkillsDropBusinessSentences() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java", "Spring Boot", "결제 시스템 백엔드 API 설계 및 개발"],
                  "preferredSkills": [],
                  "duties": "결제 시스템 백엔드 API 설계 및 개발",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "결제 시스템 백엔드 개발자 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(
                applicationCase(), "결제 시스템 백엔드 API 설계 및 개발. Java, Spring Boot 경험 필수.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot");
        assertThat(result.payload().requiredSkills()).doesNotContain("결제 시스템 백엔드 API 설계 및 개발");
    }

    // ── 실공고 패턴 검증: 경력 보정 ──

    @Test
    void seniorExperienceFromKoreanYearsAbove() {
        // "경력 7년 이상" → SENIOR (R1이 JUNIOR로 줄 경우 보정)
        assertExperienceLevel("JUNIOR",
                "채용부문: Java 백엔드 개발\n지원자격: 관련 경력 7년 이상\n우대: AWS 경험자",
                "SENIOR");
    }

    @Test
    void seniorExperienceFromExactFiveYears() {
        // "경력 5년 이상" → 정확히 임계값, SENIOR
        assertExperienceLevel("JUNIOR",
                "모집분야: 결제 시스템 개발\n필수: 경력 5년 이상\n기술: Java, Spring Boot",
                "SENIOR");
    }

    @Test
    void midExperienceFromThreeYearsWhenModelSaysJunior() {
        // "경력 3년 이상"인데 모델이 JUNIOR → MID로 보정
        assertExperienceLevel("JUNIOR",
                "자격요건\n- 웹 개발 경력 3년 이상\n- Java, React, TypeScript 능숙",
                "MID");
    }

    @Test
    void experienceLevelPreservedWhenNoYearsStated() {
        // 연차 미언급 → 모델 값 유지
        assertExperienceLevel("JUNIOR",
                "신입/경력 무관\nJava, Spring Boot 개발자 모집\n복리후생: 4대보험",
                "JUNIOR");
    }

    @Test
    void seniorNotDowngradedByModelMid() {
        // 모델이 MID인데 원문 "10년 이상" → SENIOR
        assertExperienceLevel("MID",
                "PL급 시니어 개발자\n경력 10년 이상\nJava MSA 설계 경험 필수",
                "SENIOR");
    }

    @Test
    void yearInDateNotConfusedWithExperience() {
        // "2024년" 같은 날짜는 경력 연차로 오탐하면 안 됨 (MAX_REALISTIC_YEARS=30 넘어서 무시)
        assertExperienceLevel("JUNIOR",
                "입사일: 2024년 3월\n신입 개발자 모집\n기술: Java, Python, Django",
                "JUNIOR");
    }

    @Test
    void nonEnumExperienceLevelNormalizedToMid() {
        // R1이 "intermediate" 같은 비표준 값 반환 + 연차 미언급 → MID로 정규화
        assertExperienceLevel("intermediate",
                "백엔드 개발자 채용\nJava, Spring Boot 개발\n복리후생: 4대보험",
                "MID");
    }

    @Test
    void nonEnumExperienceLevelMediumWithThreeYearsNormalized() {
        // R1이 "MEDIUM" 반환 + 경력 3년 → MID (정규화, 연차로 SENIOR 승격 안 됨)
        assertExperienceLevel("MEDIUM",
                "백엔드 개발자\n경력 3년 이상\nJava, Spring Boot 능숙",
                "MID");
    }

    @Test
    void nonEnumExperienceLevelIntermediateWithSevenYearsBecomesSenior() {
        // R1이 "intermediate" 반환해도 원문 7년 → SENIOR
        assertExperienceLevel("intermediate",
                "플랜트 설계 경력직\n유관 경력 7년 이상\nJava AutoCAD 설계 경험",
                "SENIOR");
    }

    @Test
    void entryLevelNormalizedToJunior() {
        // "entry-level" → JUNIOR
        assertExperienceLevel("entry-level",
                "신입 개발자 채용\nJava 기초 학습자 환영",
                "JUNIOR");
    }

    // ── 경력 연차 문맥 인식 (비경력 연차 오탐 방지) ──

    @Test
    void companyTenureNotTreatedAsExperience() {
        // "설립 10년" 같은 회사 연혁은 경력 연차로 보지 않음 → 모델값(MID) 유지
        assertExperienceLevel("MID",
                "설립 10년차 안정적인 기업\nJava 백엔드 개발자 채용\nSpring Boot 능숙자 우대",
                "MID");
    }

    @Test
    void serviceOperationYearsNotTreatedAsExperience() {
        // "서비스 운영 5년"은 경력이 아니라 서비스 기간 → SENIOR 보정 안 함
        assertExperienceLevel("MID",
                "서비스 운영 5년의 핀테크 스타트업\nJava 백엔드 개발자 모집\nSpring Boot 활용",
                "MID");
    }

    @Test
    void consecutiveGrowthYearsNotTreatedAsExperience() {
        // "5년 연속 성장"은 경력 연차가 아님
        assertExperienceLevel("MID",
                "5년 연속 성장한 기업\nJava 개발자 채용\nSpring Boot 우대",
                "MID");
    }

    @Test
    void experienceKeywordOnDifferentLineNotLinked() {
        // 다음 줄의 "경험자 우대"는 "10년차"(연혁)와 무관 → 같은 라인 제한으로 SENIOR 오판 안 함
        assertExperienceLevel("MID",
                "설립 10년차 기업\nJava 경험자 우대\nSpring Boot 활용",
                "MID");
    }

    @Test
    void englishTenureWithYrsNotTreatedAsExperience() {
        // "operating for 5 yrs" — 기간 단위 yrs는 경력 신호 아님 → SENIOR 보정 안 함
        assertExperienceLevel("MID",
                "A startup operating for 5 yrs\nHiring Java backend engineer\nSpring Boot skills",
                "MID");
    }

    @Test
    void experienceIrrelevantWithTenureOnSameLineNotSenior() {
        // 같은 라인에 "경력 무관"(부정어)과 "설립 10년차"(연혁)가 섞여도 결합 패턴이라 오결합 안 함
        assertExperienceLevel("JUNIOR",
                "경력 무관, 설립 10년차 기업\nJava 개발자 채용\nSpring Boot 활용",
                "JUNIOR");
    }

    @Test
    void experienceIrrelevantAfterYearsNotSenior() {
        // 역방향 결합에서도 "10년차 … 경력 무관"의 부정어는 경력으로 오인하지 않음
        assertExperienceLevel("JUNIOR",
                "설립 10년차 경력 무관 채용\nJava 개발자 모집\nSpring Boot 활용",
                "JUNIOR");
    }

    @Test
    void koreanExperienceIrrelevantVariantsNotSenior() {
        // "경력 무관"의 흔한 변형(조사·띄어쓰기·다른 어휘)이 연혁 연차와 결합돼도 SENIOR로 오인하지 않음
        String[] postings = {
                "설립 10년차, 경력은 무관\nJava 개발자 채용\nSpring Boot 활용",
                "경력 제한 없음, 설립 10년차 기업\nJava 백엔드 모집\nSpring Boot",
                "경력 불문 채용, 운영 10년\nJava 개발\nSpring Boot 활용",
                "경력 상관 없음, 10년차 스타트업\nJava 개발자\nSpring Boot",
                // 라벨형 구분자 표기
                "설립 10년차 경력: 무관 채용\nJava 개발자\nSpring Boot 활용",
                "10년차 기업 경력-무관\nJava 백엔드\nSpring Boot 모집",
                // 조사 에/과 + 분리된 부정 어휘
                "경력에 상관없이 지원 가능, 운영 10년\nJava 백엔드\nSpring Boot",
                "경력과 무관하게 채용, 10년차 기업\nJava 개발\nSpring Boot",
        };
        for (String posting : postings) {
            assertExperienceLevel("JUNIOR", posting, "JUNIOR");
        }
    }

    @Test
    void labeledExperienceYearsStillDetected() {
        // "경력: 7년" 라벨형 표기는 부정어가 아니므로 경력 연차로 정상 감지 → SENIOR
        assertExperienceLevel("JUNIOR",
                "모집부문\n경력: 7년 이상\nJava, Spring Boot 능숙",
                "SENIOR");
    }

    @Test
    void expPrefixWordNotMatchedAsExperience() {
        // "exported"의 "exp"는 단어 경계 때문에 경력 키워드로 잡히지 않음
        assertExperienceLevel("JUNIOR",
                "Company exported products for 5 yrs\nHiring Java backend dev\nSpring Boot",
                "JUNIOR");
    }

    @Test
    void englishYearsExperienceDetected() {
        // "7 years experience" 영어 경력 표현 → SENIOR
        assertExperienceLevel("JUNIOR",
                "Backend engineer with 7 years experience required\nJava, Spring Boot",
                "SENIOR");
    }

    @Test
    void englishYrsExpAbbreviationDetected() {
        // "5+ yrs exp" 축약형 영어 경력 표현 → SENIOR
        assertExperienceLevel("JUNIOR",
                "Looking for 5+ yrs exp in backend\nJava, Spring Boot needed",
                "SENIOR");
    }

    @Test
    void yearsBeforeExperienceKeywordStillDetected() {
        // "5년 이상의 실무 경력" — 연차가 키워드 앞에 와도 문맥 윈도우로 포착 → SENIOR
        assertExperienceLevel("JUNIOR",
                "5년 이상의 실무 경력 보유자\nJava, Spring Boot 개발",
                "SENIOR");
    }

    // ── 실공고 패턴 검증: 스킬 필터 ──

    @Test
    void longDutySentenceFilteredFromSkills() {
        // 길고 단어 많은 업무 문장이 스킬에 섞인 경우
        assertSkillsFiltered(
                new String[]{"Java", "Spring Boot", "대규모 트래픽 처리를 위한 서버 아키텍처 설계 및 개발 경험"},
                "Java, Spring Boot 경험 필수",
                new String[]{"Java", "Spring Boot"},
                new String[]{"대규모 트래픽 처리를 위한 서버 아키텍처 설계 및 개발 경험"});
    }

    @Test
    void mixedRealAndSentenceSkillsFiltered() {
        // 실제 스킬 + 업무 문장 혼합
        assertSkillsFiltered(
                new String[]{"MySQL", "Redis", "결제 시스템 백엔드 API 설계 및 개발", "Docker"},
                "MySQL, Redis, Docker 필수",
                new String[]{"MySQL", "Redis", "Docker"},
                new String[]{"결제 시스템 백엔드 API 설계 및 개발"});
    }

    @Test
    void shortKoreanSkillsPreserved() {
        // 짧은 한국어 스킬은 정상 유지
        assertSkillsFiltered(
                new String[]{"자바", "스프링", "마이바티스", "도커"},
                "자바 스프링 마이바티스 도커 개발자 채용",
                new String[]{"자바", "스프링", "마이바티스", "도커"},
                new String[]{});
    }

    @Test
    void allSentencesFilteredFallsBackToRuleExtraction() {
        // 전부 업무 문장이면 규칙 추출 폴백
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["결제 시스템 백엔드 API 설계 및 개발", "대규모 트래픽 환경에서의 서버 운영 및 모니터링 담당"],
                  "preferredSkills": [],
                  "duties": "결제 시스템 개발",
                  "qualifications": "경력 3년 이상",
                  "difficulty": "NORMAL",
                  "summary": "결제 시스템 백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": []
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);
        String posting = "결제 시스템 백엔드 개발자 채용\n필수: Java, Spring Boot, MySQL\n우대: Kafka, Redis";

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), posting);

        // self-rules 전체 폴백이 아니라 R1 경로의 스킬 규칙 추출 폴백으로 채워져야 한다(우연한 통과 방지).
        assertThat(result.fellBack())
                .as("R1 should not fall back to self-rules (fellBack=%s, reason=%s)", result.fellBack(), result.fallbackReason())
                .isFalse();
        // 전부 걸러져서 규칙 추출 폴백 → Java, Spring Boot 등이 들어있어야 함
        assertThat(result.payload().requiredSkills()).doesNotContain("결제 시스템 백엔드 API 설계 및 개발");
        assertThat(result.payload().requiredSkills()).isNotEmpty();
    }

    // ── 헬퍼 메서드 ──

    private void assertExperienceLevel(String modelReturns, String postingText, String expected) {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(String.format("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "%s",
                  "requiredSkills": ["Java"],
                  "preferredSkills": [],
                  "duties": "개발",
                  "qualifications": "경력자",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다. 상세한 내용을 포함합니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": []
                }
                """, modelReturns));
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), postingText);

        assertThat(result.fellBack())
                .as("R1 should not fall back to self-rules (fellBack=%s, reason=%s)", result.fellBack(), result.fallbackReason())
                .isFalse();
        assertThat(result.payload().experienceLevel()).isEqualTo(expected);
    }

    private void assertSkillsFiltered(String[] modelSkills, String postingText,
                                       String[] expectedPresent, String[] expectedAbsent) {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        String skillsJson = "[" + String.join(",", java.util.Arrays.stream(modelSkills)
                .map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(String.format("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": %s,
                  "preferredSkills": [],
                  "duties": "개발",
                  "qualifications": "경력자",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다. 상세한 내용을 포함합니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": []
                }
                """, skillsJson));
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysis(applicationCase(), postingText);

        assertThat(result.fellBack())
                .as("R1 should not fall back to self-rules (fellBack=%s, reason=%s)", result.fellBack(), result.fallbackReason())
                .isFalse();
        for (String s : expectedPresent) {
            assertThat(result.payload().requiredSkills()).contains(s);
        }
        for (String s : expectedAbsent) {
            assertThat(result.payload().requiredSkills()).doesNotContain(s);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jobAnalysisSchemaConstrainsExperienceLevelToEnum() {
        // R1 호출 시 전달되는 JSON 스키마가 experienceLevel을 JUNIOR/MID/SENIOR enum으로 제약하는지 검증.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": [],
                  "duties": "Spring API 개발과 운영",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": []
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        service.generateJobAnalysis(applicationCase(), postingText());

        ArgumentCaptor<Map<String, Object>> schemaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(localLlmClient).chat(anyString(), anyString(), schemaCaptor.capture());
        Map<String, Object> schema = schemaCaptor.getValue();
        Map<String, Object> schemaProperties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> experienceLevel = (Map<String, Object>) schemaProperties.get("experienceLevel");
        assertThat(experienceLevel.get("enum")).isEqualTo(List.of("JUNIOR", "MID", "SENIOR"));
    }

    @Test
    void localLlmInvalidJsonFallsBackToSelfRules() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("{}");
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result = service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isTrue();
        assertThat(result.fallbackAttemptedModel()).isEqualTo("qwen-test");
        assertThat(result.fallbackReason()).contains("fallback to self-rules-v1");
        assertThat(result.payload().usage().model()).isEqualTo(BAnalysisGenerationService.SELF_RULES_MODEL);
        assertThat(result.payload().companySummary()).contains("Acme");
    }

    @Test
    void claudeUsedWhenLocalDisabledAndClaudeConfigured() {
        // 자체모델 비활성 + Claude 키 있음 → Claude(Haiku)로 1차 폴백, self-rules 로 안 떨어진다.
        BAnalysisProperties properties = new BAnalysisProperties(); // localLlm 비활성
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku-test");
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validJobJson());
        BAnalysisGenerationService service =
                service(properties, localLlmClient, anthropicClient, mock(OpenAiResponsesClient.class));

        BAnalysisGenerationService.GeneratedJobAnalysis result =
                service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("claude-haiku-test");
        assertThat(result.payload().requiredSkills()).contains("Java", "Spring Boot");
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
    }

    @Test
    void claudeUsedWhenOllamaFails() {
        // 자체모델이 깨진 JSON 으로 실패 → Claude(Haiku)로 폴백 성공.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("{}");
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku-test");
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validJobJson());
        BAnalysisGenerationService service =
                service(properties, localLlmClient, anthropicClient, mock(OpenAiResponsesClient.class));

        BAnalysisGenerationService.GeneratedJobAnalysis result =
                service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("claude-haiku-test");
    }

    private static String validJobJson() {
        return """
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "preferredSkills": ["Docker"],
                  "duties": "Spring API 개발과 운영",
                  "qualifications": "Java와 Spring Boot 경험",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java와 Spring Boot 경험"}],
                  "ambiguousConditions": [{"condition":"salary","assumption":"not specified"}]
                }
                """;
    }

    private static BAnalysisGenerationService service(BAnalysisProperties properties, BLocalLlmClient localLlmClient) {
        return service(properties, localLlmClient, mock(BAnthropicClient.class), mock(OpenAiResponsesClient.class));
    }

    private static BAnalysisGenerationService service(BAnalysisProperties properties, BLocalLlmClient localLlmClient,
                                                      BAnthropicClient anthropicClient,
                                                      OpenAiResponsesClient openAiResponsesClient) {
        return new BAnalysisGenerationService(
                properties,
                localLlmClient,
                new BJobSentenceClassifier(),
                new ObjectMapper(),
                anthropicClient,
                openAiResponsesClient);
    }

    private static ApplicationCase applicationCase() {
        return ApplicationCase.builder()
                .id(10L)
                .userId(1L)
                .companyName("Acme")
                .jobTitle("Backend Engineer")
                .status("DRAFT")
                .build();
    }

    private static String postingText() {
        return """
                Acme is hiring a Backend Engineer.
                Responsibilities: build Spring Boot APIs, operate MySQL services, and improve Docker deployment.
                Qualifications: Java, Spring Boot, MyBatis, MySQL, REST, Docker, Testing.
                Preferred: React and TypeScript collaboration experience.
                """;
    }
}
