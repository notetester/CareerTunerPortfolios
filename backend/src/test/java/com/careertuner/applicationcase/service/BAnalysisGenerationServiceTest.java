package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
