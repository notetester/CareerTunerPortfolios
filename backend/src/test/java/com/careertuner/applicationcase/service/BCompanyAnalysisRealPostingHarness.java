package com.careertuner.applicationcase.service;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateOutcome;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 기업분석 실측 검증 하네스(수동 실행 전용) — 기업분석 재설계 6단계 1차안.
 *
 * <p>{@link BAnalysisIssueDRealPostingHarness} 와 같은 방식으로 프로덕션 {@link BLocalLlmClient}(Ollama /api/chat)로
 * 실제 R1 모델을 호출하되, 같은 공고 10건에 대해 {@link BAnalysisGenerationService#generateCompanyAnalysis} 를 실행하고
 * 같은 실행에서 {@link BAnalysisGenerationService#generateJobAnalysis} final 도 함께 기록한다(충돌 검토용).
 * raw = 모델 원문 JSON(chat spy 가로채기), final = parseLocalCompanyPayload 통과 후 payload,
 * canonical final = 저장 직전 {@link BCompanyAnalysisCanonicalizer} 통과 후 payload.
 *
 * <p>job/company 가 같은 chat() spy 를 공유하므로, raw 는 각 generate 호출 직후 즉시 파일로 저장하고
 * holder 를 비워 덮어쓰기를 방지한다. fellBack 도 job/company 각각 분리 기록한다:
 * 하나라도 fellBack=true 면 해당 부분은 R1 모델 채점 표본이 아니고, conflictWithJob 판정은
 * job/company 둘 다 R1 성공일 때만 유효하다(아니면 N/A).
 *
 * <p>공고분석 하네스와 달리 회사명/직무명을 실제 값으로 넣는다 — 기업분석은 회사명이 1급 입력이다.
 *
 * <p>자동 채점은 하지 않는다. 리포트에 빈 수기 채점표만 포함하고 reviewer 가 원문 대조로 채운다
 * (빈칸 = 미채점, PASS 아님). 산출물은 전부 {@code .tmp/job_posting_real_regression_set/company_harness/}
 * 하위에만 쓰며 main repo 에 커밋하지 않는다.
 *
 * <p>일반 테스트/CI 에는 영향이 없도록 환경변수 {@code B_REAL_COMPANY=true} 일 때만 실행된다.
 * 실행(1건): {@code $env:B_REAL_COMPANY="true"; $env:B_REAL_COMPANY_TARGET="1"; .\gradlew.bat test --tests *BCompanyAnalysisRealPostingHarness}
 * 배치 실행: {@code B_REAL_COMPANY_TARGET="1-3"} 또는 {@code B_REAL_COMPANY_TARGET="1,4,10"}.
 * 전체 10건은 실수 방지를 위해 {@code B_REAL_COMPANY_ALL=true} 를 명시해야 한다.
 * 이미 완료된 case fragment 는 기본 skip 하며, 재실행하려면 {@code B_REAL_COMPANY_FORCE=true} 를 추가한다.
 */
@EnabledIfEnvironmentVariable(named = "B_REAL_COMPANY", matches = "true")
class BCompanyAnalysisRealPostingHarness {

    private static final String ENV_TARGET = "B_REAL_COMPANY_TARGET";
    private static final String ENV_ALL = "B_REAL_COMPANY_ALL";
    private static final String ENV_FORCE = "B_REAL_COMPANY_FORCE";
    private static final String ENV_READ_TIMEOUT_SECONDS = "B_REAL_COMPANY_READ_TIMEOUT_SECONDS";

    private record TargetCase(int index, String fileName, String companyName, String jobTitle) {
    }

    // 이슈 D 하네스의 TARGETS 10건과 동일 세트(공고분석 결과와의 충돌 검토를 위해 순서까지 유지).
    // 회사명/직무명은 원본 공고 확인 값. OCR 원문이 깨진 건(금융21 등)은 파일명·원문 대조로 확정했다.
    private static final List<TargetCase> TARGETS = List.of(
            new TargetCase(1, "가온테크 시스템엔지니어 - 고용24.txt", "가온테크", "시스템엔지니어"),
            new TargetCase(2, "포스타입 프론트엔드 엔지니어 공고문 - 원티드.txt", "포스타입", "프론트엔드 엔지니어"),
            new TargetCase(3, "티시스아이티 AI서비스기획 공고문 - 원티드.txt", "티시스아이티", "AI서비스기획"),
            new TargetCase(4, "모스원 마케팅기획 공고문 - 인크루트.txt", "모스원", "마케팅기획"),
            new TargetCase(5, "금융21 공고문 - 고용24.txt", "금융21", "건설수주 영업"),
            new TargetCase(6, "카카오 모빌리티 QA 엔지니어 공고문 - 자체공고.txt", "카카오모빌리티", "QA 엔지니어"),
            new TargetCase(7, "위버스컴퍼니 백엔드 공고문 - 원티드.txt", "위버스컴퍼니", "백엔드 개발자"),
            new TargetCase(8, "딥그로브 AI엔지니어 인턴 공고문 - 원티드.txt", "딥그로브", "AI엔지니어 인턴"),
            new TargetCase(9, "백패커 클라우드 엔지니어 - 원티드.txt", "백패커", "클라우드 엔지니어"),
            new TargetCase(10, "동국제약 공고문-사람인.txt", "동국제약", "신입/경력 공채(글로벌/개발/디자인/영업/마케팅/관리)"));

    @Test
    void runRealPostings() throws Exception {
        Path root = resolveRoot();
        Path ocrDir = root.resolve(".tmp/job_posting_real_regression_set/ocr");
        if (!Files.isDirectory(ocrDir)) {
            // OCR 세트는 git 에 없는 로컬 전용 자료 — 없으면 조용히 전건 SKIP 되지 않도록 즉시 중단한다.
            throw new AssertionError("OCR 세트 디렉터리가 없어 중단: " + ocrDir.toAbsolutePath()
                    + " (.tmp/job_posting_real_regression_set/ocr 로컬 세트를 먼저 준비해야 한다)");
        }
        Path harnessDir = root.resolve(".tmp/job_posting_real_regression_set/company_harness");
        Path rawDir = harnessDir.resolve("raw_after_fix");
        Path caseDir = harnessDir.resolve("cases_after_fix");
        Path compareDir = harnessDir.resolve("compare_after_fix");
        Path outFile = harnessDir.resolve("company_analysis_manual_review_after_fix.md");
        Path compareFile = harnessDir.resolve("company_analysis_regression_compare_20260702.md");
        Files.createDirectories(rawDir);
        Files.createDirectories(caseDir);
        Files.createDirectories(compareDir);

        ObjectMapper mapper = new ObjectMapper();
        BCompanyAnalysisCanonicalizer canonicalizer = new BCompanyAnalysisCanonicalizer(mapper);
        StringBuilder report = new StringBuilder();
        StringBuilder compare = new StringBuilder();
        report.append("# 기업분석 실측 검증 결과 (재설계 6단계 1차안 after-fix)\n\n");
        report.append("- 실행 시각: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        report.append("- 모델: application.yaml 기본값(careertuner-b-jobposting-r1), temperature 0, maxRetries 0\n");
        report.append("- raw = 모델 원문 JSON / final = parseLocalCompanyPayload(기업) · parseLocalJobPayload(공고) 통과 후 payload\n");
        report.append("- canonical final = 저장 직전 canonicalizer(evidence gate, unknowns 접기, source 보정) 통과 후 payload\n");
        report.append("- fellBack=true 인 부분은 self-rules-v1 폴백 결과이며 R1 채점 표본이 아니다.\n");
        report.append("- conflictWithJob 판정은 job/company 둘 다 R1 성공(fellBack=false)일 때만 유효하다. 아니면 N/A.\n\n");

        compare.append("# 기업분석 재검증 비교표 (2026-07-02)\n\n");
        compare.append("- 기준: 동일 10건 OCR 세트, 6단계 1차안 canonicalizer 적용 후 계측\n");
        compare.append("- 기존 4단계 수기 검증표는 수정하지 않고, 이 파일은 after-fix 실행 결과의 요약 비교용이다.\n\n");
        compare.append("| case | jobR1 | companyR1 | verifiedFacts raw→canonical | aiInferences raw→storage | virtual unknowns | gate PASSED | gate DEMOTED | gate REMOVED |\n");
        compare.append("|---|---|---|---:|---:|---:|---:|---:|---:|\n");

        report.append("## 케이스 매핑 (case index ↔ 원본 공고 ↔ 회사명/직무명)\n\n");
        report.append("| case | 원본 공고 파일 | 회사명 | 직무명 |\n");
        report.append("|---|---|---|---|\n");
        for (TargetCase t : TARGETS) {
            report.append("| %02d | %s | %s | %s |\n".formatted(t.index(), t.fileName(), t.companyName(), t.jobTitle()));
        }
        report.append("\n");

        report.append("## 수기 채점 기준\n\n");
        report.append("- classification: FACT(원문에서 직접 확인) / INFERENCE(원문 기반 추론) / UNKNOWN(입력만으로 확인 불가)\n");
        report.append("- rating: SUPPORTED / PARTIAL / UNSUPPORTED / NOT_AVAILABLE / OCR_INPUT_ISSUE\n");
        report.append("- conflictWithJob: y/n — 단, 해당 케이스의 job 또는 company 가 fellBack=true 면 N/A 로 기입\n");
        report.append("- claimRef: 구조화 항목 참조(verifiedFacts[0]=V0, aiInferences[1]=I1 등). 자유서술 필드 발췌 claim 은 비워두고 field+claim 으로 특정\n");
        report.append("- 빈칸 = 미채점, PASS 아님. 자동 채점 없음 — reviewer 가 원문 대조로 채운다.\n\n");

        String reportHeader = report.toString();
        String compareHeader = compare.toString();
        List<TargetCase> targets = selectedTargets();
        if (targets.isEmpty()) {
            String requested = System.getenv(ENV_TARGET);
            System.out.println("RESULT|" + requested + "|NO_MATCH");
            if (isBlank(requested) && !truthy(System.getenv(ENV_ALL))) {
                report.append("## 실행 대상 없음\n\n")
                        .append("- 10건 전체 실행을 막기 위해 기본 실행은 허용하지 않는다.\n")
                        .append("- 1건 실행: B_REAL_COMPANY_TARGET=1\n")
                        .append("- 배치 실행: B_REAL_COMPANY_TARGET=1-3 또는 1,4,10\n")
                        .append("- 전체 실행: B_REAL_COMPANY_ALL=true\n");
            } else {
                report.append("## NO_MATCH: ").append(requested)
                        .append("\nB_REAL_COMPANY_TARGET 가 어떤 공고와도 매칭되지 않음\n");
            }
            Files.writeString(outFile, report.toString(), StandardCharsets.UTF_8);
            throw new AssertionError("No target postings selected. Set B_REAL_COMPANY_TARGET=1, 1-3, 1,4,10, substring, or B_REAL_COMPANY_ALL=true.");
        }
        writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);

        boolean force = truthy(System.getenv(ENV_FORCE));
        List<String> missingFiles = new ArrayList<>();
        for (TargetCase target : targets) {
            Path caseFile = caseDir.resolve("%02d.md".formatted(target.index()));
            Path compareRowFile = compareDir.resolve("%02d.md".formatted(target.index()));
            if (!force && Files.exists(caseFile) && Files.exists(compareRowFile)) {
                System.out.println("RESULT|" + target.fileName() + "|SKIP(existing fragment; set B_REAL_COMPANY_FORCE=true to rerun)");
                continue;
            }
            int reportStart = report.length();
            int compareStart = compare.length();
            Path file = ocrDir.resolve(target.fileName());
            String header = "%02d. %s".formatted(target.index(), target.fileName());
            if (!Files.exists(file)) {
                missingFiles.add(target.fileName());
                System.out.println("RESULT|" + target.fileName() + "|SKIP(missing)");
                report.append("## ").append(header).append("\nSKIP (파일 없음)\n\n");
                compare.append("| %02d | MISSING | MISSING | 0→0 | 0→0 | 0 | 0 | 0 | 0 |\n".formatted(target.index()));
                writeCaseFragments(caseFile, report.substring(reportStart), compareRowFile, compare.substring(compareStart));
                writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);
                continue;
            }
            String posting = Files.readString(file, StandardCharsets.UTF_8);

            BAnalysisProperties properties = new BAnalysisProperties();
            properties.getLocalLlm().setEnabled(true);
            properties.getLocalLlm().setReadTimeout(readTimeout());
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

            // 기업분석은 회사명이 1급 입력 — 공고분석 하네스의 "unknown"을 재사용하지 않는다.
            ApplicationCase ac = ApplicationCase.builder()
                    .id((long) target.index()).userId(1L)
                    .companyName(target.companyName()).jobTitle(target.jobTitle()).status("DRAFT")
                    .build();

            // --- 1) 공고분석 (final 기록: 기업분석과의 충돌 검토용) ---
            long jobT0 = System.currentTimeMillis();
            BAnalysisGenerationService.GeneratedJobAnalysis jobResult = null;
            String jobError = null;
            try {
                jobResult = service.generateJobAnalysis(ac, posting);
            } catch (RuntimeException ex) {
                jobError = ex.getMessage();
            }
            long jobMs = System.currentTimeMillis() - jobT0;
            // raw 는 즉시 저장하고 holder 를 비운다(job/company 가 같은 spy 를 공유 → 덮어쓰기 방지).
            Path jobRawFile = rawDir.resolve("%02d_job.raw.json".formatted(target.index()));
            boolean jobRawSaved = saveRaw(jobRawFile, rawHolder);

            // --- 2) 기업분석 ---
            long companyT0 = System.currentTimeMillis();
            BAnalysisGenerationService.GeneratedCompanyAnalysis companyResult = null;
            String companyError = null;
            try {
                companyResult = service.generateCompanyAnalysis(ac, posting);
            } catch (RuntimeException ex) {
                companyError = ex.getMessage();
            }
            long companyMs = System.currentTimeMillis() - companyT0;
            Path companyRawFile = rawDir.resolve("%02d_company.raw.json".formatted(target.index()));
            boolean companyRawSaved = saveRaw(companyRawFile, rawHolder);

            boolean jobR1 = jobResult != null && !jobResult.fellBack();
            boolean companyR1 = companyResult != null && !companyResult.fellBack();
            CanonicalCompanyAnalysis canonicalCompany = companyResult == null ? null : canonicalizer.canonicalizeForStorage(
                    companyResult.payload(),
                    (long) target.index(),
                    1,
                    posting,
                    target.companyName(),
                    target.jobTitle());
            int rawFactCount = companyResult == null ? 0 : arraySize(mapper, companyResult.payload().verifiedFacts());
            int canonicalFactCount = canonicalCompany == null ? 0 : arraySize(mapper, canonicalCompany.payload().verifiedFacts());
            int rawInferenceCount = companyResult == null ? 0 : arraySize(mapper, companyResult.payload().aiInferences());
            int storageInferenceCount = canonicalCompany == null ? 0 : arraySize(mapper, canonicalCompany.payload().aiInferences());
            int virtualUnknownCount = canonicalCompany == null ? 0
                    : arraySize(mapper, canonicalizer.extractUnknowns(canonicalCompany.payload().aiInferences()));
            long passedCount = countGateActions(canonicalCompany, GateOutcome.PASSED);
            long demotedCount = countGateActions(canonicalCompany, GateOutcome.DEMOTED);
            long removedCount = countGateActions(canonicalCompany, GateOutcome.REMOVED);
            String conflictEligibility = jobR1 && companyR1
                    ? "유효 (job/company 둘 다 R1 성공)"
                    : "N/A (R1 성공이 아닌 쪽이 있음 — 채점표 conflictWithJob 에 N/A 기입)";

            compare.append("| %02d | %s | %s | %d→%d | %d→%d | %d | %d | %d | %d |\n".formatted(
                    target.index(),
                    jobR1 ? "Y" : "N",
                    companyR1 ? "Y" : "N",
                    rawFactCount,
                    canonicalFactCount,
                    rawInferenceCount,
                    storageInferenceCount,
                    virtualUnknownCount,
                    passedCount,
                    demotedCount,
                    removedCount));

            System.out.println("RESULT|" + target.fileName()
                    + "|jobFellBack=" + (jobResult == null ? "ERROR" : jobResult.fellBack())
                    + "|companyFellBack=" + (companyResult == null ? "ERROR" : companyResult.fellBack())
                    + "|jobMs=" + jobMs + "|companyMs=" + companyMs
                    + "|jobRaw=" + jobRawSaved + "|companyRaw=" + companyRawSaved);

            report.append("## ").append(header).append("\n\n");
            report.append("- 회사명: ").append(target.companyName())
                    .append(" / 직무명: ").append(target.jobTitle()).append("\n");
            report.append("- job: fellBack=").append(jobResult == null ? "ERROR(" + jobError + ")" : jobResult.fellBack())
                    .append(", ms=").append(jobMs)
                    .append(", raw=").append(jobRawSaved ? rawDir.getFileName() + "/" + jobRawFile.getFileName() : "없음(R1 응답 없음)")
                    .append(jobR1 ? "" : " — **R1 채점 표본 아님**").append("\n");
            report.append("- company: fellBack=").append(companyResult == null ? "ERROR(" + companyError + ")" : companyResult.fellBack())
                    .append(", ms=").append(companyMs)
                    .append(", raw=").append(companyRawSaved ? rawDir.getFileName() + "/" + companyRawFile.getFileName() : "없음(R1 응답 없음)")
                    .append(companyR1 ? "" : " — **R1 채점 표본 아님**").append("\n");
            if (jobResult != null && jobResult.fellBack()) {
                report.append("- job fallbackReason: ").append(oneLine(jobResult.fallbackReason())).append("\n");
            }
            if (companyResult != null && companyResult.fellBack()) {
                report.append("- company fallbackReason: ").append(oneLine(companyResult.fallbackReason())).append("\n");
            }
            report.append("- conflictWithJob 판정 유효성: ").append(conflictEligibility).append("\n\n");

            if (jobResult != null) {
                var jp = jobResult.payload();
                report.append("### 공고분석 final (충돌 검토용)\n\n");
                report.append("- experienceLevel: ").append(jp.experienceLevel())
                        .append(" / employmentType: ").append(jp.employmentType())
                        .append(" / difficulty: ").append(jp.difficulty()).append("\n");
                report.append("- requiredSkills: ").append(oneLine(jp.requiredSkills())).append("\n");
                report.append("- preferredSkills: ").append(oneLine(jp.preferredSkills())).append("\n");
                report.append("- duties: ").append(oneLine(jp.duties())).append("\n");
                report.append("- qualifications: ").append(oneLine(jp.qualifications())).append("\n");
                report.append("- summary: ").append(oneLine(jp.summary())).append("\n\n");
            } else {
                report.append("### 공고분석 final (충돌 검토용)\n\nERROR: ").append(jobError).append("\n\n");
            }

            if (companyResult != null) {
                var cp = companyResult.payload();
                report.append("### 기업분석 final (모델 parse)\n\n");
                report.append("- companySummary: ").append(oneLine(cp.companySummary())).append("\n");
                report.append("- industry: ").append(oneLine(cp.industry())).append("\n");
                report.append("- recentIssues: ").append(oneLine(cp.recentIssues())).append("\n");
                report.append("- competitors: ").append(oneLine(cp.competitors())).append("\n");
                report.append("- interviewPoints: ").append(oneLine(cp.interviewPoints())).append("\n");
                report.append("- sources: ").append(oneLine(cp.sources())).append("\n");
                report.append("- verifiedFacts: ").append(oneLine(cp.verifiedFacts())).append("\n");
                report.append("- aiInferences: ").append(oneLine(cp.aiInferences())).append("\n");
                report.append("- unknowns: ").append(oneLine(cp.unknowns())).append("\n\n");

                var canonicalPayload = canonicalCompany.payload();
                String visibleAiInferences = canonicalizer.withoutUnknownMarkers(canonicalPayload.aiInferences());
                String virtualUnknowns = canonicalizer.extractUnknowns(canonicalPayload.aiInferences());
                report.append("### 기업분석 canonical final (저장 직전)\n\n");
                report.append("- companySummary: ").append(oneLine(canonicalPayload.companySummary())).append("\n");
                report.append("- industry: ").append(oneLine(canonicalPayload.industry())).append("\n");
                report.append("- recentIssues: ").append(oneLine(canonicalPayload.recentIssues())).append("\n");
                report.append("- competitors: ").append(oneLine(canonicalPayload.competitors())).append("\n");
                report.append("- interviewPoints: ").append(oneLine(canonicalPayload.interviewPoints())).append("\n");
                report.append("- sources: ").append(oneLine(canonicalPayload.sources())).append("\n");
                report.append("- verifiedFacts: ").append(oneLine(canonicalPayload.verifiedFacts())).append("\n");
                report.append("- aiInferences(storage): ").append(oneLine(canonicalPayload.aiInferences())).append("\n");
                report.append("- aiInferences(response visible): ").append(oneLine(visibleAiInferences)).append("\n");
                report.append("- virtual unknowns(response): ").append(oneLine(virtualUnknowns)).append("\n");
                report.append("- gateAction summary: PASSED=").append(passedCount)
                        .append(", DEMOTED=").append(demotedCount)
                        .append(", REMOVED=").append(removedCount).append("\n\n");
                report.append("#### gateActions\n\n");
                report.append("| ref | field | gateAction | detail |\n");
                report.append("|---|---|---|---|\n");
                report.append(gateActionRows(canonicalCompany));
                report.append("\n");
            } else {
                report.append("### 기업분석 final\n\nERROR: ").append(companyError).append("\n\n");
            }

            // 수기 채점: 자동 판정값을 계산하지 않고 빈 템플릿만 찍는다(이슈 D 하네스 관례와 동일).
            // reviewer 가 claim 단위로 행을 추가하며 채운다. 빈칸 = 미채점, PASS 아님.
            report.append("### 수기 채점표 — reviewer 가 채움 〔빈칸 = 미채점, PASS 아님〕\n\n");
            report.append("| caseId | claimRef | field | claim | classification | rating | conflictWithJob | note | reviewer | reviewedAt |\n");
            report.append("|---|---|---|---|---|---|---|---|---|---|\n");
            for (int i = 0; i < 5; i++) {
                report.append("| ").append(target.index()).append(" | | | | | | | | | |\n");
            }
            report.append("\n(행이 부족하면 reviewer 가 caseId=").append(target.index())
                    .append(" 로 행을 추가한다. classification=FACT/INFERENCE/UNKNOWN, ")
                    .append("rating=SUPPORTED/PARTIAL/UNSUPPORTED/NOT_AVAILABLE/OCR_INPUT_ISSUE, ")
                    .append("conflictWithJob=y/n/N/A)\n\n");
            writeCaseFragments(caseFile, report.substring(reportStart), compareRowFile, compare.substring(compareStart));
            writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);
        }

        writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);
        System.out.println("REPORT_WRITTEN|" + outFile.toAbsolutePath());
        System.out.println("COMPARE_WRITTEN|" + compareFile.toAbsolutePath());

        if (!missingFiles.isEmpty()) {
            // 처리된 케이스의 리포트/raw 는 남기되, 고정 세트 누락은 조용한 성공으로 끝내지 않는다.
            throw new AssertionError("OCR 세트에서 누락된 공고 파일 " + missingFiles.size() + "건: " + missingFiles);
        }
    }

    /** raw 를 즉시 파일로 저장하고 holder 를 비운다. 저장했으면 true. */
    private static boolean saveRaw(Path file, String[] rawHolder) throws java.io.IOException {
        String raw = rawHolder[0];
        rawHolder[0] = null;
        if (raw == null || raw.isBlank()) {
            return false;
        }
        Files.writeString(file, raw, StandardCharsets.UTF_8);
        return true;
    }

    private static List<TargetCase> selectedTargets() {
        String target = System.getenv(ENV_TARGET);
        if (truthy(System.getenv(ENV_ALL))) {
            return TARGETS;
        }
        if (isBlank(target)) {
            return List.of();
        }
        Set<TargetCase> selected = new LinkedHashSet<>();
        for (String rawToken : target.split(",")) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.matches("\\d+\\s*-\\s*\\d+")) {
                String[] bounds = token.split("\\s*-\\s*");
                int start = Integer.parseInt(bounds[0]);
                int end = Integer.parseInt(bounds[1]);
                int from = Math.min(start, end);
                int to = Math.max(start, end);
                TARGETS.stream()
                        .filter(t -> t.index() >= from && t.index() <= to)
                        .forEach(selected::add);
                continue;
            }
            if (token.matches("\\d+")) {
                int index = Integer.parseInt(token);
                TARGETS.stream()
                        .filter(t -> t.index() == index)
                        .forEach(selected::add);
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            TARGETS.stream()
                    .filter(t -> t.fileName().toLowerCase(Locale.ROOT).contains(normalized)
                            || t.companyName().toLowerCase(Locale.ROOT).contains(normalized)
                            || t.jobTitle().toLowerCase(Locale.ROOT).contains(normalized))
                    .forEach(selected::add);
        }
        return new ArrayList<>(selected);
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

    private static int arraySize(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = mapper.readTree(json);
            return node.isArray() ? node.size() : 0;
        } catch (JacksonException ex) {
            return 0;
        }
    }

    private static long countGateActions(CanonicalCompanyAnalysis result, GateOutcome outcome) {
        if (result == null) {
            return 0;
        }
        return result.gateActions().stream()
                .filter(action -> action.action() == outcome)
                .count();
    }

    private static void writeCaseFragments(Path caseFile,
                                           String caseContent,
                                           Path compareRowFile,
                                           String compareContent) throws java.io.IOException {
        Files.writeString(caseFile, caseContent, StandardCharsets.UTF_8);
        Files.writeString(compareRowFile, compareContent, StandardCharsets.UTF_8);
    }

    private static void writeAggregate(Path outFile,
                                       String reportHeader,
                                       Path caseDir,
                                       Path compareFile,
                                       String compareHeader,
                                       Path compareDir) throws java.io.IOException {
        StringBuilder report = new StringBuilder(reportHeader);
        for (Path fragment : sortedMarkdownFiles(caseDir)) {
            report.append(Files.readString(fragment, StandardCharsets.UTF_8));
        }
        Files.writeString(outFile, report.toString(), StandardCharsets.UTF_8);

        StringBuilder compare = new StringBuilder(compareHeader);
        for (Path row : sortedMarkdownFiles(compareDir)) {
            compare.append(Files.readString(row, StandardCharsets.UTF_8));
        }
        Files.writeString(compareFile, compare.toString(), StandardCharsets.UTF_8);
    }

    private static List<Path> sortedMarkdownFiles(Path dir) throws java.io.IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean truthy(String value) {
        if (isBlank(value)) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "y", "yes", "on" -> true;
            default -> false;
        };
    }

    private static Duration readTimeout() {
        String raw = System.getenv(ENV_READ_TIMEOUT_SECONDS);
        if (isBlank(raw)) {
            return Duration.ofSeconds(480);
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            if (seconds <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ex) {
            throw new AssertionError(ENV_READ_TIMEOUT_SECONDS + " must be positive seconds: " + raw, ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String gateActionRows(CanonicalCompanyAnalysis result) {
        if (result == null || result.gateActions().isEmpty()) {
            return "| | | | |\n";
        }
        StringBuilder rows = new StringBuilder();
        for (var action : result.gateActions()) {
            rows.append("| ")
                    .append(oneLine(action.ref())).append(" | ")
                    .append(oneLine(action.field())).append(" | ")
                    .append(action.action()).append(" | ")
                    .append(oneLine(action.detail())).append(" |\n");
        }
        return rows.toString();
    }

    private String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 1200 ? flat.substring(0, 1200) + "…" : flat;
    }
}
