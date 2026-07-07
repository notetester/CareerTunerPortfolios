package com.careertuner.applicationcase.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.applicationcase.domain.ApplicationCase;

import tools.jackson.databind.ObjectMapper;

/**
 * 이슈 D 실공고 브로드 검증 하네스(수동 실행 전용).
 *
 * <p>프로덕션과 동일한 {@link BLocalLlmClient}(Ollama /api/chat, temperature 0, experienceLevel enum 강제)로
 * 실제 R1 모델을 호출하고, Mockito spy 로 R1 raw 응답을 1회 호출에서 가로채(before) 그대로
 * {@link BAnalysisGenerationService} 후처리(reconcileExperienceLevel / filterSkillItems)를 태운 최종값(after)을
 * 함께 기록한다. anthropic/openai 는 mock(미설정)이라 그쪽으로는 폴백되지 않는다. 다만 R1 호출이 실패하면
 * {@link BAnalysisGenerationService} 가 self-rules-v1 로 폴백하므로, 결과의 {@code fellBack} 으로 구분한다:
 * R1 성공 건은 raw/after(후처리) 가 기록되고, R1 실패 건은 self-rules 폴백 결과로 표시된다(후처리 검증 표본 아님).
 *
 * <p>일반 테스트/CI 에는 영향이 없도록 환경변수 {@code B_REAL_R1=true} 일 때만 실행된다.
 * 실행: {@code $env:B_REAL_R1="true"; .\gradlew.bat test --tests *BAnalysisIssueDRealPostingHarness}
 */
@EnabledIfEnvironmentVariable(named = "B_REAL_R1", matches = "true")
class BAnalysisIssueDRealPostingHarness {

    // 검증 대상 공고(카테고리 분산: 5년+ / 1~4년 / 신입·무관 / 날짜오탐 스트레스 / 스킬나열형).
    private static final List<String> TARGETS = List.of(
            "가온테크 시스템엔지니어 - 고용24.txt",
            "포스타입 프론트엔드 엔지니어 공고문 - 원티드.txt",
            "티시스아이티 AI서비스기획 공고문 - 원티드.txt",
            "모스원 마케팅기획 공고문 - 인크루트.txt",
            "금융21 공고문 - 고용24.txt",
            "카카오 모빌리티 QA 엔지니어 공고문 - 자체공고.txt",
            "위버스컴퍼니 백엔드 공고문 - 원티드.txt",
            "딥그로브 AI엔지니어 인턴 공고문 - 원티드.txt",
            "백패커 클라우드 엔지니어 - 원티드.txt",
            "동국제약 공고문-사람인.txt");

    @Test
    void runRealPostings() throws Exception {
        Path root = resolveRoot();
        Path ocrDir = root.resolve(".tmp/job_posting_real_regression_set/ocr");
        Path outFile = root.resolve(".tmp/job_posting_real_regression_set/issue_d_broad_result.txt");

        ObjectMapper mapper = new ObjectMapper();
        StringBuilder report = new StringBuilder();
        report.append("# 이슈 D 실공고 브로드 검증 결과\n\n");

        List<String> targets = selectedTargets();
        if (targets.isEmpty()) {
            String requested = System.getenv("B_REAL_R1_TARGET");
            System.out.println("RESULT|" + requested + "|NO_MATCH");
            report.append("## NO_MATCH: ").append(requested).append("\nB_REAL_R1_TARGET 가 어떤 공고와도 매칭되지 않음\n");
            Files.writeString(outFile, report.toString(), StandardCharsets.UTF_8);
            throw new AssertionError("B_REAL_R1_TARGET '" + requested + "' matched no target posting");
        }

        for (String name : targets) {
            Path file = ocrDir.resolve(name);
            if (!Files.exists(file)) {
                System.out.println("RESULT|" + name + "|SKIP(missing)");
                report.append("## ").append(name).append("\nSKIP (파일 없음)\n\n");
                continue;
            }
            String posting = Files.readString(file, StandardCharsets.UTF_8);

            BAnalysisProperties properties = new BAnalysisProperties();
            properties.getLocalLlm().setEnabled(true);
            // 모델은 application.yaml 기본값(careertuner-b-jobposting-r1)을 그대로 사용.
            properties.getLocalLlm().setMaxRetries(0); // 검증은 1회 호출만(폴백 관찰 목적).

            BLocalLlmClient realClient = new BLocalLlmClient(properties, GpuPermitGate.disabled());
            BLocalLlmClient spy = Mockito.spy(realClient);
            final String[] rawHolder = new String[1];
            doAnswer(invocation -> {
                String raw = (String) invocation.callRealMethod();
                rawHolder[0] = raw;
                return raw;
            }).when(spy).chat(Mockito.anyString(), Mockito.anyString(), Mockito.any());

            BAnalysisGenerationService service = new BAnalysisGenerationService(
                    properties,
                    spy,
                    new BJobSentenceClassifier(),
                    mapper,
                    mock(BAnthropicClient.class),
                    mock(OpenAiResponsesClient.class));

            ApplicationCase ac = ApplicationCase.builder()
                    .id(1L).userId(1L)
                    .companyName("unknown").jobTitle("unknown").status("DRAFT")
                    .build();

            long t0 = System.currentTimeMillis();
            BAnalysisGenerationService.GeneratedJobAnalysis result;
            try {
                result = service.generateJobAnalysis(ac, posting);
            } catch (RuntimeException ex) {
                System.out.println("RESULT|" + name + "|ERROR|" + ex.getMessage());
                report.append("## ").append(name).append("\nERROR: ").append(ex.getMessage()).append("\n\n");
                continue;
            }
            long dt = System.currentTimeMillis() - t0;

            Integer years = invokeMaxStatedYears(service, posting);
            String raw = rawHolder[0] == null ? "" : rawHolder[0];
            String rawExp = jsonField(mapper, raw, "experienceLevel");
            String rawReq = jsonArray(mapper, raw, "requiredSkills");
            String rawPref = jsonArray(mapper, raw, "preferredSkills");

            var p = result.payload();
            System.out.println("RESULT|" + name
                    + "|years=" + years
                    + "|rawExp=" + rawExp
                    + "|finalExp=" + p.experienceLevel()
                    + "|emp=" + p.employmentType()
                    + "|diff=" + p.difficulty()
                    + "|fellBack=" + result.fellBack()
                    + "|ms=" + dt
                    + "\n   rawReq=" + rawReq
                    + "\n   finalReq=" + p.requiredSkills()
                    + "\n   rawPref=" + rawPref
                    + "\n   finalPref=" + p.preferredSkills());

            report.append("## ").append(name).append("\n");
            report.append("- maxStatedYears: ").append(years).append("\n");
            report.append("- experienceLevel: raw=`").append(rawExp).append("` -> final=`")
                    .append(p.experienceLevel()).append("`\n");
            report.append("- employmentType: ").append(p.employmentType())
                    .append(" / difficulty: ").append(p.difficulty())
                    .append(" / fellBack: ").append(result.fellBack()).append("\n");
            report.append("- requiredSkills raw: ").append(rawReq).append("\n");
            report.append("- requiredSkills final: ").append(p.requiredSkills()).append("\n");
            report.append("- preferredSkills raw: ").append(rawPref).append("\n");
            report.append("- preferredSkills final: ").append(p.preferredSkills()).append("\n");
            report.append("- duties: ").append(oneLine(p.duties())).append("\n");
            report.append("- qualifications: ").append(oneLine(p.qualifications())).append("\n");
            report.append("- summary: ").append(oneLine(p.summary())).append("\n");
            // grounding 수기 평가: 자동 판정값을 계산하지 않고, reviewer 가 원문 대조로 채울 빈 템플릿만 찍는다.
            // (하네스가 점수/판정을 만들면 "로그 없는 PASS"가 자동 생성되므로 의도적으로 빈칸.)
            // 채점 기준(자급자족): 필드를 원자 주장으로 분해 → SUPPORTED/PARTIAL/UNSUPPORTED(원문에 없음 또는 원문과 모순=U).
            //   OCR로 깨진 원문을 충실 반영한 claim 은 OCR_INPUT_ISSUE=y 로 S/P/U 집계에서 제외(표에 N/A).
            //   posting PASS = 모든 필드 U=0 & S/(S+P+U) ≥ 90% & N/A(전부-OCR) 필드 없음.
            report.append("\n#### grounding 수기 채점 — reviewer 가 채움 〔빈칸 = 미채점, PASS 아님〕\n");
            report.append("| field | claims(원자 주장) | source quote(근거 원문) | S/P/U | OCR_INPUT_ISSUE(y/n) | note | reviewer |\n");
            report.append("|---|---|---|---|---|---|---|\n");
            report.append("| duties | | | | | | |\n");
            report.append("| qualifications | | | | | | |\n");
            report.append("| summary | | | | | | |\n");
            report.append("verdict(posting): 〔미채점 — PASS / 보완 / N/A_INPUT_ISSUE 중 기입. "
                    + "보완=어느 필드든 U≥1 또는 S/(S+P+U)<90%; N/A_INPUT_ISSUE=결함 없으나 전부-OCR 필드 있음; "
                    + "PASS=모든 필드 채점가능(N/A 없음)&U=0&소프트 통과. OCR_INPUT_ISSUE=y 는 S/P/U 집계 제외·N/A. 빈칸은 PASS 아님〕\n\n");
        }

        Files.writeString(outFile, report.toString(), StandardCharsets.UTF_8);
        System.out.println("REPORT_WRITTEN|" + outFile.toAbsolutePath());
    }

    private static List<String> selectedTargets() {
        String target = System.getenv("B_REAL_R1_TARGET");
        if (target == null || target.isBlank()) {
            return TARGETS;
        }
        return TARGETS.stream()
                .filter(name -> name.contains(target.trim()))
                .toList();
    }

    private static Path resolveRoot() {
        // gradle test 작업 디렉터리는 backend/ 이므로 상위가 프로젝트 루트. 안전하게 .tmp 존재하는 곳을 찾는다.
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && cur != null; i++) {
            if (Files.exists(cur.resolve(".tmp/job_posting_real_regression_set"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Paths.get("").toAbsolutePath().getParent();
    }

    private Integer invokeMaxStatedYears(BAnalysisGenerationService service, String posting) {
        try {
            Method m = BAnalysisGenerationService.class.getDeclaredMethod("maxStatedYears", String.class);
            m.setAccessible(true);
            return (Integer) m.invoke(service, posting);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private String jsonField(ObjectMapper mapper, String json, String field) {
        try {
            var node = mapper.readTree(json).path(field);
            return node.isMissingNode() ? "<missing>" : node.asText();
        } catch (RuntimeException ex) {
            return "<parse-fail>";
        }
    }

    private String jsonArray(ObjectMapper mapper, String json, String field) {
        try {
            var node = mapper.readTree(json).path(field);
            if (!node.isArray()) {
                return "<not-array:" + node.toString() + ">";
            }
            List<String> items = new ArrayList<>();
            node.forEach(n -> items.add(n.asText()));
            return items.toString();
        } catch (RuntimeException ex) {
            return "<parse-fail>";
        }
    }

    private String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 1200 ? flat.substring(0, 1200) + "…" : flat;
    }
}
