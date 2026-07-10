package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;

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

    @Test
    void shortNonSkillTokensFiltered() {
        // 이슈 D 후속 #3: 연차("경력5년")·OCR 깨짐("|T장비기술지원")·직무명 접미사("전산운영직"/"백업전문가")
        // 같은 짧은 비스킬 토큰은 제거하고 정상 스킬은 유지한다.
        assertSkillsFiltered(
                new String[]{"Java", "경력5년", "전산운영직", "|T장비기술지원", "VMware", "백업전문가"},
                "Java 와 VMware 기반 서버 운영 채용",
                new String[]{"Java", "VMware"},
                new String[]{"경력5년", "전산운영직", "|T장비기술지원", "백업전문가"});
    }

    @Test
    void legitSkillsNotDroppedByNonSkillFilter() {
        // #3 과제거 방지 가드: 한/영 정상 스킬은 비스킬 필터에 걸리지 않아야 한다.
        assertSkillsFiltered(
                new String[]{"Java", "Spring Boot", "마케팅기획", "프롬프트 엔지니어링", "TypeScript", "Kubernetes"},
                "Java Spring Boot 마케팅기획 프롬프트 엔지니어링 TypeScript Kubernetes 경험자 우대",
                new String[]{"Java", "Spring Boot", "마케팅기획", "프롬프트 엔지니어링", "TypeScript", "Kubernetes"},
                new String[]{});
    }

    @Test
    void preferredSkillsAllNoiseDropsToEmptyArray() {
        // preferredSkills 는 비어도 유효하므로, 전부 잡음이면 원본을 되살리지 않고 빈 배열로 정리한다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "employmentType": "FULL_TIME",
                  "experienceLevel": "MID",
                  "requiredSkills": ["Java"],
                  "preferredSkills": ["경력5년", "|T장비기술지원", "백업전문가"],
                  "duties": "Java 개발 업무",
                  "qualifications": "Java 경험 필수",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다. 상세한 내용을 포함합니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": []
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result =
                service.generateJobAnalysis(applicationCase(), "Java 기반 백엔드 개발자 채용");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().requiredSkills()).contains("Java");
        assertThat(result.payload().preferredSkills()).isEqualTo("[]");
    }

    @Test
    void commaBundleSalvagesRepresentativeSkills() {
        // 이슈 D 후속 #4(동국제약 패턴): R1이 여러 스킬을 한 문자열로 묶어도 대표 스킬을 복구한다.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJob(
                "FULL_TIME", "MID",
                "[\"PHP, Java, JSP, MariaDB 웹/서버 개발\", \"관계형 데이터베이스(RDBMS) 구조 이해\"]",
                "PHP Java JSP MariaDB 웹 서버 개발 관계형 데이터베이스 RDBMS 구조 이해 경험자");
        assertThat(result.fellBack()).isFalse();
        assertThat(skillList(result.payload().requiredSkills()))
                .containsExactlyInAnyOrder("PHP", "Java", "JSP", "MariaDB 웹/서버 개발", "관계형 데이터베이스(RDBMS) 구조 이해");
    }

    @Test
    void parenthesizedBundleKeepsLeadTokenOnly() {
        // #4 과복구 가드: "AWS (CodeDeploy, EC2, ...)" 는 대표 토큰 AWS 만 복구하고 괄호 내부(EC2 등)는 복구하지 않는다.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJob(
                "FULL_TIME", "MID",
                "[\"AWS (CodeDeploy, EC2, CloudFront, Lambda, CloudWatch)\", \"Security (IAM, Secret Management)\", \"Java\"]",
                "AWS Security Java CodeDeploy EC2 CloudFront Lambda CloudWatch IAM 경험");
        assertThat(result.fellBack()).isFalse();
        assertThat(skillList(result.payload().requiredSkills()))
                .containsExactlyInAnyOrder("AWS", "Security", "Java")
                .doesNotContain("EC2", "Lambda", "CodeDeploy", "IAM", "AWS (CodeDeploy");
    }

    @Test
    void parenthesizedBusinessPhraseNotSalvaged() {
        // #4 과복구 방지: "결제 시스템(백엔드 API) 설계 및 개발" 은 업무 문장이므로, 한국어 prefix "결제 시스템" 이
        // 기술 토큰이 아니어서 살아나면 안 된다(AWS/Security 같은 영문 대표 토큰만 복구).
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJob(
                "FULL_TIME", "MID",
                "[\"결제 시스템(백엔드 API) 설계 및 개발\", \"Java\"]",
                "결제 시스템 백엔드 API 설계 및 개발 Java 경험자");
        assertThat(result.fellBack()).isFalse();
        assertThat(skillList(result.payload().requiredSkills()))
                .containsExactlyInAnyOrder("Java")
                .doesNotContain("결제 시스템", "결제 시스템(백엔드 API) 설계 및 개발");
    }

    @Test
    void parenthesizedEnglishBusinessPhraseNotSalvaged() {
        // #4 과복구 방지(영문): "Payment System(Backend API) design and development" 는 업무명이므로,
        // 공백 있는 영문 구문 prefix "Payment System"(기술 신호 없음)이 살아나면 안 된다.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJob(
                "FULL_TIME", "MID",
                "[\"Payment System(Backend API) design and development\", \"Java\"]",
                "Payment System Backend API design and development Java experience");
        assertThat(result.fellBack()).isFalse();
        assertThat(skillList(result.payload().requiredSkills()))
                .containsExactlyInAnyOrder("Java")
                .doesNotContain("Payment System", "Payment System(Backend API) design and development");
    }

    // ── 이슈 D 후속 #2: 고용형태 정규화 + 인턴 경력 캡 ──

    @Test
    void internEmploymentCapsExperienceToJunior() {
        // R1이 연차 없는 인턴 공고를 SENIOR로 매겨도, employmentType=INTERN이면 JUNIOR로 캡(딥그로브 패턴).
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJob(
                "인턴", "SENIOR", "[\"Java\"]",
                "AI 엔지니어 인턴 모집. Java 학습 경험 우대.");
        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().employmentType()).isEqualTo("INTERN");
        assertThat(result.payload().experienceLevel()).isEqualTo("JUNIOR");
    }

    @Test
    void employmentTypeNormalizedToEnum() {
        // R1이 "정규직" 같은 한글 고용형태를 반환해도 enum(FULL_TIME)으로 정규화.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJob(
                "정규직", "MID", "[\"Java\"]",
                "백엔드 개발자 채용. Java 경험 필수.");
        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().employmentType()).isEqualTo("FULL_TIME");
        assertThat(result.payload().experienceLevel()).isEqualTo("MID");
    }

    // ── 이슈 D 후속 #1: 긴 공고 컨텍스트 예산 절단 ──

    @Test
    void longPostingTruncatedToFitContextBudget() {
        // num_ctx(기본 8192)를 넘기면 R1이 400으로 통째 폴백되므로, 매우 긴 공고는 프롬프트가 예산 내로 절단돼야 한다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(validJobJson());
        BAnalysisGenerationService service = service(properties, localLlmClient);

        String hugePosting = "Java Spring Boot Docker 경험 필수. ".repeat(2500); // 수만 자

        service.generateJobAnalysis(applicationCase(), hugePosting);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(localLlmClient).chat(anyString(), userCaptor.capture(), any());
        int len = userCaptor.getValue().length();
        assertThat(len)
                .as("user prompt should be truncated to fit num_ctx budget (was %d chars)", len)
                .isLessThan(9_000)
                .isGreaterThan(2_000);
    }

    // ── 이슈 D 후속 A-1: 필드 오배치 보정 (경력·자격 → duty 오배치 분리·재배치) ──

    @Test
    void a1RelocatesMisplacedRequirementsFromDutiesToQualifications() {
        // 가온테크·금융21 패턴: duties 에 "경력5년"·"건축분야기능사"(요건)가 오배치됨. 실제 업무는 유지.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "서버 유지보수 수행\n경력5년\n건축분야기능사",
                "Window Server, Linux, VMware 운영 경험",
                "시스템 관리자를 채용하는 공고입니다. 서버 운영과 유지보수를 담당합니다.",
                List.of("Linux", "VMware"),
                "서버 유지보수 수행. 경력5년 이상. 건축분야기능사 우대. Window Server, Linux, VMware 운영.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .contains("서버 유지보수 수행")
                .doesNotContain("경력5년")
                .doesNotContain("건축분야기능사");
        assertThat(result.payload().qualifications())
                .contains("경력5년")
                .contains("건축분야기능사");
    }

    @Test
    void a1KeepsRequirementSignalWhenResponsibilityVerbPresent() {
        // "5년 이상 서버 운영 경험 보유"는 요건 신호(5년 이상)가 있어도 업무 동사(운영)가 있어 duty 로 유지.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "5년 이상 서버 운영 경험 보유\nKubernetes 클러스터 구축",
                "클라우드 엔지니어 경력",
                "클라우드 엔지니어를 채용합니다. 서버 운영과 Kubernetes 를 담당합니다.",
                List.of("Kubernetes"),
                "5년 이상 서버 운영 경험 보유. Kubernetes 클러스터 구축. 클라우드 엔지니어 경력.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties()).contains("5년 이상 서버 운영 경험 보유");
    }

    @Test
    void a1DoesNotEmptyDutiesWhenAllSegmentsAreRequirements() {
        // duties 가 전부 요건이면(재배치 후 남는 duty 없음) 재배치하지 않아 duties 를 비우지 않는다(검증 실패 방지).
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "경력5년\n기능사 자격증",
                "Linux 운영 경험",
                "시스템 관리자를 채용하는 공고입니다. 서버 운영을 담당합니다.",
                List.of("Linux"),
                "경력5년 이상. 기능사 자격증 필수. Linux 운영 경험.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .contains("경력5년")
                .contains("기능사 자격증");
    }

    @Test
    void a1LeavesProseDutiesUntouched() {
        // 산문 문단(동국제약류)은 세그먼트가 1개(길이>상한)라 오배치 판정에서 제외 → 그대로 유지.
        String duties = "제약/바이오/헬스케어업계에서 신입 및 경력직원을 채용합니다. "
                + "주요 업무로는 마케팅 전략 수립과 브랜드 포지셔닝 강화가 포함됩니다.";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                duties,
                "학사 이상 학력. SQL 활용 능력.",
                "제약/바이오 업계에서 신입 및 경력직원을 채용하는 공고입니다.",
                List.of("SQL"),
                "제약/바이오/헬스케어업계 신입 및 경력 채용. 마케팅 전략 수립. 학사 이상. SQL 활용.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties()).isEqualTo(duties);
    }

    // ── 이슈 D 후속 A-2: 키워드 나열 문장화 + duties 과소추출 보강 ──

    @Test
    void a2RendersKeywordListDutiesAsSentence() {
        // 가온테크·금융21 패턴: 문장이 아닌 키워드 나열 duties 를 문장으로 렌더한다(토큰은 보존).
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "IT기술지원\nSE 백업전문가\n서버 유지보수",
                "Linux 운영 경험",
                "시스템 관리자를 채용하는 공고입니다. 서버 운영을 담당합니다.",
                List.of("Linux"),
                "IT기술지원 SE 백업전문가 서버 유지보수 Linux 운영");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .endsWith("등의 업무를 담당합니다.")
                .contains("IT기술지원")
                .contains("SE 백업전문가")
                .contains("서버 유지보수")
                .doesNotContain("\n");
    }

    @Test
    void a2SupplementsUnderExtractedDutiesFromResponsibilities() {
        // 포스타입 패턴: duties 가 얇으면 분류기의 주요업무 문장으로 보강한다(원문 근거).
        String posting = """
                주요 업무
                - 사용자 문제를 정의하고 해결합니다
                - 모니터링 시스템을 구축합니다
                - 개발 자동화를 담당합니다
                자격요건: React, TypeScript 경험
                """;
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "프론트엔드 개발",
                "React, TypeScript 경험",
                "프론트엔드 엔지니어를 채용하는 공고입니다. 플랫폼을 개발합니다.",
                List.of("React", "TypeScript"),
                posting);

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .contains("프론트엔드 개발")
                .contains("모니터링 시스템을 구축합니다")
                .contains("개발 자동화를 담당합니다");
    }

    @Test
    void a2DoesNotSupplementUnderExtractedDutiesWithBrokenOcr() {
        // 과소추출이라도 OCR 깨짐(호환 자모) 주요업무 문장은 보강에 쓰지 않는다(백패커 누출 방지).
        String posting = """
                주요 업무
                - 8공Izl 몰르ㄹ 비7럼 응Ho로 룹궁 담당합니다
                - AWS 인프라를 운영합니다
                자격요건: AWS 경험
                """;
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "클라우드 개발",
                "AWS 경험",
                "클라우드 엔지니어를 채용하는 공고입니다. 인프라를 운영합니다.",
                List.of("AWS"),
                posting);

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .contains("AWS 인프라를 운영합니다")
                .doesNotContain("몰르")
                .doesNotContain("룹궁");
    }

    @Test
    void a2LeavesWellExtractedProseDutiesUntouched() {
        // 충분히 추출된 산문 duties(임계 이상)는 과소추출이 아니라 보강·문장화 대상이 아니다(무변경).
        String duties = "웹과 앱 환경 구분없이 콘텐츠를 생산하고 소비할 수 있는 플랫폼을 구축하고, "
                + "성능 모니터링 시스템을 만들며 개발 프로세스 자동화를 담당합니다.";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                duties,
                "컴퓨터공학 관련 전공 학사 이상",
                "프론트엔드 엔지니어를 채용하는 공고입니다. 플랫폼을 개발합니다.",
                List.of("React"),
                "웹과 앱 플랫폼을 구축하고 운영. React 경험 필수. 컴퓨터공학 전공.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties()).isEqualTo(duties);
    }

    // ── 이슈 D 후속 A-3: duties==summary 중복 제거 ──

    @Test
    void a3CondensesSummaryWhenIdenticalToDuties() {
        // 동국제약 패턴: duties 와 summary 가 동일하면 summary 를 첫 문장으로 응축해 구분한다.
        String text = "제약/바이오/헬스케어업계에서 신입 및 경력직원을 채용합니다. "
                + "주요 업무로는 마케팅 전략 수립과 브랜드 포지셔닝 강화가 포함됩니다. "
                + "IT 부문에서는 ERP 시스템 고도화를 수행합니다.";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                text, "학사 이상 학력. SQL 활용 능력.", text,
                List.of("SQL"),
                "제약/바이오/헬스케어 채용. 마케팅 전략 수립. ERP 고도화. SQL 활용.");

        assertThat(result.fellBack()).isFalse();
        // duties 는 상세 원본 유지, summary 는 첫 문장으로 응축되어 서로 달라진다.
        assertThat(result.payload().duties()).isEqualTo(text);
        assertThat(result.payload().summary()).isNotEqualTo(result.payload().duties());
        assertThat(result.payload().summary()).isEqualTo("제약/바이오/헬스케어업계에서 신입 및 경력직원을 채용합니다.");
    }

    @Test
    void a3LeavesDistinctSummaryUntouched() {
        // duties 와 summary 가 다르면 응축하지 않는다(무변경).
        String duties = "웹과 앱 환경 구분없이 콘텐츠를 생산하고 소비할 수 있는 플랫폼을 구축하고, "
                + "성능 모니터링 시스템을 만들며 개발 프로세스 자동화를 담당하고 운영을 개선합니다.";
        String summary = "프론트엔드 엔지니어를 채용하는 공고입니다. 플랫폼 개발과 운영을 맡습니다.";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                duties, "컴퓨터공학 전공 학사 이상", summary,
                List.of("React"),
                "웹과 앱 플랫폼 구축. React 경험. 컴퓨터공학 전공.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().summary()).isEqualTo(summary);
    }

    // ── A-1 보정(하네스 2026-07-03 가온테크): 콤마 단일라인 나열 분해 ──

    @Test
    void a1CommaSingleLineDutiesRelocatedSentencifiedAndSummaryCondensed() {
        // 가온테크형: R1 이 키워드 나열을 줄바꿈 대신 top-level 콤마 단일라인으로 출력 + summary==duties.
        // 기대: 경력5년·3000000원(요건/임금)은 qualifications 로 이동, 남은 나열은 문장화,
        //       summary 는 duties 전체 복붙이 아닌 앞쪽 세그먼트 응축형(부분집합).
        String duties = "시스템관리자, 전산운영직, 기술지원/SE/백업전문가, IT기술지원, 장비기술지원, "
                + "Linux, VMware, Window Server, 유지보수, 경력5년, 3000000원 이상";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                duties,
                "경력 5년 이상 우대",
                duties,
                List.of("Linux", "VMware"),
                "모집직종 IT기술지원 전문가, 시스템관리자, 전산운영직. Linux VMware Window Server 유지보수. "
                        + "경력조건 경력5년, 임금 3000000원 이상.");

        assertThat(result.fellBack()).isFalse();
        // duty 오배치 요건/임금 재배치 + 나열 문장화
        assertThat(result.payload().duties())
                .doesNotContain("경력5년")
                .doesNotContain("3000000원")
                .endsWith("등의 업무를 담당합니다.")
                .contains("시스템관리자")
                .contains("Window Server")
                .contains("유지보수");
        assertThat(result.payload().qualifications())
                .contains("경력5년")
                .contains("3000000원 이상");
        // summary 는 duties 와 단순 != 가 아니라 짧은 응축형(앞쪽 세그먼트 부분집합)이어야 한다.
        assertThat(result.payload().summary())
                .isEqualTo("시스템관리자, 전산운영직, 기술지원/SE/백업전문가 등의 업무를 담당합니다.");
        assertThat(result.payload().summary().length()).isLessThan(result.payload().duties().length());
    }

    @Test
    void a1CommaSplitProtectsBracketedCommas() {
        // 가드 1: 괄호 안 콤마는 분할하지 않는다 — "구축(설계, 운영)" 이 통째로 보존돼야 한다.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "클라우드 인프라 구축(설계, 운영), Linux 서버 가상화 환경 유지보수, 데이터센터 네트워크 모니터링, 경력5년",
                "클라우드 관련 경험",
                "클라우드 엔지니어를 채용하는 공고입니다. 인프라 구축을 담당합니다.",
                List.of("Linux"),
                "Linux 가상화 기술 스택\n클라우드 자격증 우대\n경력5년 이상");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .contains("구축(설계, 운영)")
                .contains("Linux 서버 가상화 환경 유지보수")
                .doesNotContain("경력5년");
        assertThat(result.payload().qualifications()).contains("경력5년");
    }

    @Test
    void a1CommaSplitLeavesProseWithCommasUntouched() {
        // 가드 2: 활용형 동사·목적격 조사가 있는 산문은 콤마가 있어도 분할·문장화하지 않는다(과분해 방지).
        String duties = "결제 시스템 백엔드 구조를 설계하고 구현하며, 대규모 트래픽 환경에서 처리 효율을 "
                + "지속적으로 개선하고 서비스 안정성 확보를 총괄";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                duties,
                "Java 백엔드 경험 필수",
                "결제 시스템 백엔드 개발자를 채용하는 공고입니다. 안정성을 담당합니다.",
                List.of("Java"),
                "결제 시스템 백엔드 개발. Java 경험 필수.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties()).isEqualTo(duties);
    }

    // ── 이슈 D 후속 A-4: OCR 깨진 토큰(자모 깨짐) 필터 ──

    @Test
    void a4DropsBrokenJamoTokensFromSkills() {
        // 백패커·금융21 패턴: OCR 로 자모가 깨진 스킬 토큰("방ㄹ금논")은 제거하고 정상 스킬은 유지한다.
        assertSkillsFiltered(
                new String[]{"Java", "방ㄹ금논", "VMware", "ㄹ금논"},
                "Java 와 VMware 기반 서버 운영 채용",
                new String[]{"Java", "VMware"},
                new String[]{"방ㄹ금논", "ㄹ금논"});
    }

    @Test
    void a4StripsBrokenJamoFromDutiesButKeepsGroundedWords() {
        // 자유서술에 낀 호환 자모 낱자는 인라인 제거하되, 같은 줄의 근거 단어는 보존한다(세그먼트 통째 삭제 아님).
        String duties = "AWS 클라우드 인프라를 설계하고 구축하며 운영ㄹ 하고 장애 대응 및 신뢰성 관리를 담당합니다.";
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                duties,
                "클라우드 엔지니어 경력. AWS 경험.",
                "클라우드 엔지니어를 채용하는 공고입니다. 인프라를 운영합니다.",
                List.of("AWS"),
                "AWS 클라우드 인프라 설계 구축 운영. 장애 대응. 신뢰성 관리. 클라우드 엔지니어 경력.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().duties())
                .doesNotContainPattern("[ㄱ-ㅎㅏ-ㅣ]")
                .contains("AWS 클라우드 인프라")
                .contains("신뢰성 관리를 담당합니다");
    }

    @Test
    void a4StripsBrokenJamoFromQualifications() {
        // qualifications 의 자모 깨짐도 제거하되 근거 단어는 보존한다.
        BAnalysisGenerationService.GeneratedJobAnalysis result = runJobText(
                "AWS 인프라를 설계하고 구축하며 운영하고 신뢰성을 관리하는 업무를 폭넓게 담당합니다.",
                "AWS 실무 경험ㄹ 보유. 컴퓨터공학 전공.",
                "클라우드 엔지니어를 채용하는 공고입니다. 인프라를 운영합니다.",
                List.of("AWS"),
                "AWS 인프라 설계 구축 운영 신뢰성 관리. 실무 경험. 컴퓨터공학 전공.");

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().qualifications())
                .doesNotContainPattern("[ㄱ-ㅎㅏ-ㅣ]")
                .contains("AWS 실무 경험")
                .contains("컴퓨터공학 전공");
    }

    @Test
    void a4CleanSkillsUnaffectedByJamoFilter() {
        // 과제거 방지: 정상 한/영 스킬은 자모 필터에 걸리지 않는다.
        assertSkillsFiltered(
                new String[]{"Java", "Spring Boot", "마케팅기획", "TypeScript", "Kubernetes"},
                "Java Spring Boot 마케팅기획 TypeScript Kubernetes 경험자 우대",
                new String[]{"Java", "Spring Boot", "마케팅기획", "TypeScript", "Kubernetes"},
                new String[]{});
    }

    // ── 헬퍼 메서드 ──

    /**
     * duties/qualifications/summary 를 직접 지정해 R1 raw JSON 을 주입한다(자유서술 후처리 검증용).
     * JSON 이스케이프(따옴표·줄바꿈)는 ObjectMapper 직렬화로 안전하게 처리한다.
     */
    private static BAnalysisGenerationService.GeneratedJobAnalysis runJobText(
            String duties, String qualifications, String summary,
            List<String> requiredSkills, String postingText) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("employmentType", "FULL_TIME");
        json.put("experienceLevel", "MID");
        json.put("requiredSkills", requiredSkills);
        json.put("preferredSkills", List.of());
        json.put("duties", duties);
        json.put("qualifications", qualifications);
        json.put("difficulty", "NORMAL");
        json.put("summary", summary);
        json.put("evidence", List.of(Map.of(
                "field", "requiredSkills",
                "quote", requiredSkills.isEmpty() ? "" : requiredSkills.get(0))));
        json.put("ambiguousConditions", List.of());
        String raw = new ObjectMapper().writeValueAsString(json);

        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(raw);
        return service(properties, localLlmClient).generateJobAnalysis(applicationCase(), postingText);
    }

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
        Map<String, Object> employmentType = (Map<String, Object>) schemaProperties.get("employmentType");
        Map<String, Object> experienceLevel = (Map<String, Object>) schemaProperties.get("experienceLevel");
        assertThat(employmentType.get("enum")).isEqualTo(List.of("FULL_TIME", "CONTRACT", "INTERN", "PART_TIME"));
        assertThat(experienceLevel.get("enum")).isEqualTo(List.of("JUNIOR", "MID", "SENIOR"));
    }

    // ── 6단계: 폴백 게이트 재설계 + canonical contract ──

    @Test
    void blankCompanySummaryDoesNotFallBackAndBecomesUnavailableNotice() {
        // 03/04 패턴: 기업 정보가 부족한 공고에서 summary 를 비운 보수적 R1 출력은 실패가 아니다.
        // self-rules 폴백 대신 확인불가 고지로 대체하고 부분 성공 필드(industry 등)를 보존한다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "companySummary": "",
                  "recentIssues": "",
                  "industry": "IT 서비스",
                  "competitors": [],
                  "interviewPoints": "공고문 담당 업무 중심으로 준비",
                  "sources": [{"type":"JOB_POSTING","label":"채용공고"}],
                  "verifiedFacts": [{"fact":"백엔드 개발자를 채용한다","source":"채용공고","evidence":"Backend Engineer"}],
                  "aiInferences": [],
                  "unknowns": [{"topic":"매출 규모","reason":"공고문에 관련 정보가 없다"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack())
                .as("blank summary must not fall back to self-rules (reason=%s)", result.fallbackReason())
                .isFalse();
        assertThat(result.payload().companySummary()).contains("확인되지 않습니다");
        assertThat(result.payload().recentIssues()).contains("확인");
        assertThat(result.payload().industry()).isEqualTo("IT 서비스");
        assertThat(result.payload().unknowns()).contains("매출 규모");
    }

    @Test
    void companySelfRulesFallbackDoesNotFillBaselessIndustry() {
        // self-rules 폴백이 키워드 근거 없는 "TECH" 기본값을 채우지 않는다(6단계 폴백 게이트).
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("{}");
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result = service.generateCompanyAnalysis(
                applicationCase(), "일반 사무직 채용 공고입니다. 문서 작성과 일정 관리를 담당합니다.");

        assertThat(result.fellBack()).isTrue();
        assertThat(result.payload().industry()).isEmpty();
    }

    @Test
    void companyPayloadCarriesUnknownsFromModelOutput() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("""
                {
                  "companySummary": "Acme 백엔드 채용 공고 기준 기업 요약입니다.",
                  "recentIssues": "확인 불가",
                  "industry": "",
                  "competitors": [],
                  "interviewPoints": "Spring Boot 경험 중심 준비",
                  "sources": [{"type":"JOB_POSTING","label":"채용공고"}],
                  "verifiedFacts": [{"fact":"Spring Boot API 를 다룬다","source":"채용공고","evidence":"build Spring Boot APIs"}],
                  "aiInferences": [{"inference":"백엔드 중심 조직","basis":"요구 기술 구성","basedOn":["F1"],"confidence":"MEDIUM"}],
                  "unknowns": [{"topic":"사원수","reason":"공고문에 없음","neededSource":"회사 소개서"}]
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().unknowns()).contains("사원수").contains("회사 소개서");
        assertThat(result.payload().verifiedFacts()).contains("evidence");
        assertThat(result.payload().aiInferences()).contains("basedOn").contains("MEDIUM");
    }

    @Test
    void duplicateJobEvidenceIsDeduplicated() {
        // 02 반복 루프 최소 대응: 동일 field+quote evidence 는 후처리 dedup 된다.
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
                  "evidence": [
                    {"field":"requiredSkills","quote":"Java"},
                    {"field":"requiredSkills","quote":"Java"},
                    {"field":"requiredSkills","quote":"Spring Boot"},
                    {"field":"requiredSkills","quote":"Java"}
                  ],
                  "ambiguousConditions": []
                }
                """);
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result =
                service.generateJobAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(new ObjectMapper().readTree(result.payload().evidence())).hasSize(2);
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
        assertThat(result.payload().companySummary())
                .contains("Acme")
                .doesNotContain("information was summarized", "No external company API");
    }

    @Test
    void companyAnalysisFallbackDoesNotLeakUnknownPlaceholders() {
        // 회사명/직무명이 미상 placeholder 인 채로 self-rules 폴백을 타도, 영어 보일러플레이트나
        // "기업명 확인 필요" 같은 placeholder 가 사용자 노출 payload(summary·verifiedFacts 등)에 새지 않아야 한다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn("{}");
        BAnalysisGenerationService service = service(properties, localLlmClient);

        ApplicationCase unknownNames = ApplicationCase.builder()
                .id(11L)
                .userId(1L)
                .companyName("기업명 확인 필요")
                .jobTitle("직무명 확인 필요")
                .status("DRAFT")
                .build();

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(unknownNames, postingText());

        assertThat(result.fellBack()).isTrue();
        assertThat(result.payload().companySummary())
                .doesNotContain("기업명 확인 필요", "Target company",
                        "information was summarized", "No external company API")
                .contains("외부 기업 정보나 OpenAI 폴백은 사용하지 않았습니다");
        assertThat(result.payload().recentIssues()).doesNotContain("Not externally researched");
        assertThat(result.payload().sources()).doesNotContain("Uploaded job posting");
        assertThat(result.payload().interviewPoints()).doesNotContain("Prepare to explain");
        assertThat(result.payload().aiInferences())
                .doesNotContain("Interview preparation should focus", "Derived from extracted");
        assertThat(result.payload().verifiedFacts())
                .doesNotContain("기업명 확인 필요", "직무명 확인 필요")
                .contains("품질 게이트");
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

    // ── 기업분석 provider 우선순위 스위치(careertuner.b-analysis.company.provider) ──

    @Test
    void companyProviderOpenAiCallsOpenAiFirstAndSkipsClaudeAndLocal() {
        // provider=openai → OpenAI 를 1순위로 호출하고, local(활성)·Claude(설정됨)는 건너뛴다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        properties.getCompany().setProvider("openai");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(validCompanyJson());
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validCompanyJson());
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(true);
        when(openAiClient.analyzeCompany(any(), anyString(), any(), any())).thenReturn(openAiCompanyPayload("gpt-test"));
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("gpt-test");
        verify(openAiClient).analyzeCompany(any(), anyString(), any(), any());
        verify(anthropicClient, never()).chat(anyString(), anyString(), any());
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
    }

    @Test
    void companyProviderOpenAiPassesModelOverride() {
        // company.openAiModel 이 설정되면 그 값이 OpenAI 기업분석 호출에 그대로 전달된다(다른 OpenAI 호출은 불변).
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(false);
        properties.getCompany().setProvider("openai");
        properties.getCompany().setOpenAiModel("gpt-5.4-mini");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(true);
        when(openAiClient.analyzeCompany(any(), anyString(), any(), any())).thenReturn(openAiCompanyPayload("gpt-5.4-mini"));
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        verify(openAiClient).analyzeCompany(any(), anyString(), eq("gpt-5.4-mini"), any());
    }

    @Test
    void companyProviderClaudeCallsClaudeFirstWithHostedPromptAndSkipsOthers() {
        // provider=claude → Claude 를 1순위로 호출하고, grounding 완화판(HOSTED_SYSTEM_PROMPT)을 system 으로 넘긴다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        properties.getCompany().setProvider("claude");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(validCompanyJson());
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku-test");
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validCompanyJson());
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(true);
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("claude-haiku-test");
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(anthropicClient).chat(systemCaptor.capture(), anyString(), any());
        assertThat(systemCaptor.getValue()).isEqualTo(CompanyAnalysisPromptCatalog.HOSTED_SYSTEM_PROMPT);
        verify(openAiClient, never()).analyzeCompany(any(), anyString(), any(), any());
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
    }

    @Test
    void companyProviderAutoKeepsLocalFirst() {
        // provider 미설정(auto 기본) → 현행대로 자체 R1(local) 이 1순위. 성공하면 hosted 는 호출되지 않는다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(validCompanyJson());
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(true);
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("qwen-test");
        verify(anthropicClient, never()).chat(anyString(), anyString(), any());
        verify(openAiClient, never()).analyzeCompany(any(), anyString(), any(), any());
    }

    @Test
    void companyProviderOpenAiFallsThroughToClaudeOnFailure() {
        // provider=openai 라도 OpenAI 실패 시 다음 순위(Claude)로 폴백한다(순서 리스트 [OPENAI, CLAUDE, LOCAL]).
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(false);
        properties.getCompany().setProvider("openai");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku-test");
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validCompanyJson());
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(true);
        when(openAiClient.analyzeCompany(any(), anyString(), any(), any())).thenThrow(new RuntimeException("openai down"));
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result =
                service.generateCompanyAnalysis(applicationCase(), postingText());

        assertThat(result.fellBack()).isFalse();
        assertThat(result.payload().usage().model()).isEqualTo("claude-haiku-test");
        verify(openAiClient).analyzeCompany(any(), anyString(), any(), any());
        verify(anthropicClient).chat(anyString(), anyString(), any());
    }

    private static String validCompanyJson() {
        return """
                {
                  "companySummary": "Acme 백엔드 채용 공고 기준 기업 요약입니다.",
                  "recentIssues": "확인 불가",
                  "industry": "IT 서비스",
                  "competitors": [],
                  "interviewPoints": "Spring Boot 경험 중심 준비",
                  "sources": [{"type":"JOB_POSTING","label":"채용공고"}],
                  "verifiedFacts": [{"fact":"백엔드 개발자를 채용한다","source":"채용공고","evidence":"Backend Engineer"}],
                  "aiInferences": [],
                  "unknowns": []
                }
                """;
    }

    private static CompanyAnalysisPayload openAiCompanyPayload(String model) {
        return new CompanyAnalysisPayload(
                "OpenAI 기업 요약", "확인 불가", "IT 서비스", "[]",
                "면접 준비 포인트", "[]", "[]", "[]", "[]",
                new OpenAiResponsesClient.Usage(model, 1, 1, 2));
    }

    // ── strict 생성: 단일 provider·LOCAL 재시도·교차 provider/self-rules 미진입·실패 throw ──

    @Test
    void generateJobAnalysisStrictLocalRetriesRecordAllLocalAttempts() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        properties.getLocalLlm().setMaxRetries(1); // 1 재시도 → 최대 2회, 전부 LOCAL
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("first attempt fails"))
                .thenReturn(validJobJson());
        BAnalysisGenerationService service = service(properties, localLlmClient);

        BAnalysisGenerationService.StrictJobResult result = service.generateJobAnalysisStrict(
                applicationCase(), "Java Spring Boot Docker 백엔드 개발 경험 필수", BAnalysisProvider.LOCAL);

        assertThat(result.attempts()).containsExactly(BAnalysisProvider.LOCAL, BAnalysisProvider.LOCAL);
        assertThat(BAnalysisProvider.toAttemptPathJson(result.attempts())).isEqualTo("[\"LOCAL\",\"LOCAL\"]");
    }

    @Test
    void generateJobAnalysisStrictThrowsWithoutSelfRulesOrCrossProviderFallback() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test"); // maxRetries 기본 0 → 단일 시도
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenThrow(new RuntimeException("local down"));
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        OpenAiResponsesClient openAiResponsesClient = mock(OpenAiResponsesClient.class);
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiResponsesClient);

        assertThatThrownBy(() -> service.generateJobAnalysisStrict(
                applicationCase(), "Java Spring Boot posting", BAnalysisProvider.LOCAL))
                .isInstanceOf(RuntimeException.class);

        // 안전망(self-rules)·교차 provider 폴백 없음 — Claude·OpenAI 미호출.
        verify(anthropicClient, never()).chat(anyString(), anyString(), any());
        verify(openAiResponsesClient, never()).analyzeJobPosting(any(), any());
    }

    @Test
    void generateJobAnalysisStrictClaudeUsesOnlySelectedProvider() {
        BAnalysisProperties properties = new BAnalysisProperties(); // LOCAL 비활성이어도 strict 는 선택 provider 만 본다
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku");
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validJobJson());
        OpenAiResponsesClient openAiResponsesClient = mock(OpenAiResponsesClient.class);
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiResponsesClient);

        BAnalysisGenerationService.StrictJobResult result = service.generateJobAnalysisStrict(
                applicationCase(), "Java Spring Boot Docker 백엔드 개발", BAnalysisProvider.CLAUDE);

        assertThat(result.attempts()).containsExactly(BAnalysisProvider.CLAUDE);
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
        verify(openAiResponsesClient, never()).analyzeJobPosting(any(), any());
    }

    // ── 초기 등록 preferred 경로: 선택 우선 + 나머지 기본 체인 폴백 + self-rules 안전망 + provenance ──

    @Test
    void generateJobAnalysisPreferredUsesSelectedProviderFirstWithoutFallback() {
        // 선택=CLAUDE. local 이 활성이어도 preferred 는 CLAUDE 를 먼저 시도하고 성공하면 나머지는 건너뛴다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.model()).thenReturn("claude-haiku-test");
        when(anthropicClient.chat(anyString(), anyString(), any())).thenReturn(validJobJson());
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysisPreferred(
                applicationCase(), postingText(), BAnalysisProvider.CLAUDE);

        assertThat(result.fellBack()).isFalse();
        var provenance = result.provenance();
        assertThat(provenance.requestedProvider()).isEqualTo("CLAUDE");
        assertThat(provenance.actualProvider()).isEqualTo("CLAUDE");
        assertThat(provenance.actualModel()).isEqualTo("claude-haiku-test");
        assertThat(provenance.fallbackUsed()).isFalse();
        assertThat(provenance.attemptPathJson()).isEqualTo("[\"CLAUDE\"]");
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
    }

    @Test
    void generateJobAnalysisPreferredFallsBackThroughChainWhenSelectedFails() {
        // 선택=CLAUDE 가 실패하면 나머지 기본 체인(local→...)으로 폴백한다. actual=LOCAL, fallback_used=true.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(validJobJson());
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.chat(anyString(), anyString(), any())).thenThrow(new RuntimeException("claude down"));
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysisPreferred(
                applicationCase(), postingText(), BAnalysisProvider.CLAUDE);

        assertThat(result.fellBack()).isFalse();
        var provenance = result.provenance();
        assertThat(provenance.requestedProvider()).isEqualTo("CLAUDE");
        assertThat(provenance.actualProvider()).isEqualTo("LOCAL");
        assertThat(provenance.actualModel()).isEqualTo("qwen-test");
        assertThat(provenance.fallbackUsed()).isTrue();
        assertThat(provenance.attemptPathJson()).isEqualTo("[\"CLAUDE\",\"LOCAL\"]");
    }

    @Test
    void generateJobAnalysisPreferredFallsBackToSelfRulesWhenAllProvidersFail() {
        // 선택=CLAUDE 실패 + local 비활성 + openai 미설정 → self-rules 안전망. actual=SELF_RULES, fellBack=true.
        BAnalysisProperties properties = new BAnalysisProperties(); // local 비활성
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        when(anthropicClient.chat(anyString(), anyString(), any())).thenThrow(new RuntimeException("claude down"));
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class); // configured()=false
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedJobAnalysis result = service.generateJobAnalysisPreferred(
                applicationCase(), postingText(), BAnalysisProvider.CLAUDE);

        assertThat(result.fellBack()).isTrue();
        assertThat(result.payload().usage().model()).isEqualTo(BAnalysisGenerationService.SELF_RULES_MODEL);
        var provenance = result.provenance();
        assertThat(provenance.requestedProvider()).isEqualTo("CLAUDE");
        assertThat(provenance.actualProvider()).isEqualTo("SELF_RULES");
        assertThat(provenance.fallbackUsed()).isTrue();
        assertThat(provenance.attemptPathJson()).isEqualTo("[\"CLAUDE\",\"SELF_RULES\"]");
    }

    @Test
    void generateCompanyAnalysisPreferredUsesSelectedProviderFirst() {
        // 선택=OPENAI. local·claude 가 가용이어도 OPENAI 를 먼저 시도하고 성공하면 나머지는 건너뛴다.
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(true);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(true);
        when(openAiClient.analyzeCompany(any(), anyString(), any(), any())).thenReturn(openAiCompanyPayload("gpt-test"));
        BAnalysisGenerationService service = service(properties, localLlmClient, anthropicClient, openAiClient);

        BAnalysisGenerationService.GeneratedCompanyAnalysis result = service.generateCompanyAnalysisPreferred(
                applicationCase(), postingText(), List.of(), BAnalysisProvider.OPENAI);

        assertThat(result.fellBack()).isFalse();
        var provenance = result.provenance();
        assertThat(provenance.requestedProvider()).isEqualTo("OPENAI");
        assertThat(provenance.actualProvider()).isEqualTo("OPENAI");
        assertThat(provenance.actualModel()).isEqualTo("gpt-test");
        assertThat(provenance.fallbackUsed()).isFalse();
        assertThat(provenance.attemptPathJson()).isEqualTo("[\"OPENAI\"]");
        verify(anthropicClient, never()).chat(anyString(), anyString(), any());
        verify(localLlmClient, never()).chat(anyString(), anyString(), any());
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

    /** payload 의 스킬 JSON 문자열을 리스트로 파싱한다(containsExactly 검증용 — contains 보다 강함). */
    private static List<String> skillList(String skillsJson) {
        List<String> out = new ArrayList<>();
        new ObjectMapper().readTree(skillsJson).forEach(node -> out.add(node.asText()));
        return out;
    }

    private static BAnalysisGenerationService.GeneratedJobAnalysis runJob(
            String employmentType, String experienceLevel, String requiredSkillsJson, String postingText) {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setModel("qwen-test");
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        when(localLlmClient.chat(anyString(), anyString(), any())).thenReturn(String.format("""
                {
                  "employmentType": "%s",
                  "experienceLevel": "%s",
                  "requiredSkills": %s,
                  "preferredSkills": [],
                  "duties": "Java 개발 업무",
                  "qualifications": "Java 경험 필수",
                  "difficulty": "NORMAL",
                  "summary": "백엔드 개발자를 위한 공고 분석 요약입니다. 상세한 내용을 포함합니다.",
                  "evidence": [{"field":"requiredSkills","quote":"Java"}],
                  "ambiguousConditions": []
                }
                """, employmentType, experienceLevel, requiredSkillsJson));
        return service(properties, localLlmClient).generateJobAnalysis(applicationCase(), postingText);
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
