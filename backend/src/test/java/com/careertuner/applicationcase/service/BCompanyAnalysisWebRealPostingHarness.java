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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.runtimesetting.service.RuntimeSettingService;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateOutcome;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanyIdentity;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchResult;
import com.careertuner.companyanalysis.websearch.NaverSearchCategory;
import com.careertuner.companyanalysis.websearch.NaverSearchProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 기업분석 웹검색 실측 검증 하네스(수동 실행 전용) — 재설계 D-6 웹근거(WEB evidence) 경로.
 *
 * <p>{@link BCompanyAnalysisRealPostingHarness}(공고-only 경로)를 미러링하되, target 마다 실제 NAVER 검색을
 * 돌려 WEB evidence 를 만들고 이를 R1 생성과 저장 gate 양쪽에 주입한다. 검색 루프는 프로덕션
 * {@link com.careertuner.companyanalysis.service.CompanyAnalysisService} 의 runSearch 계약을 그대로 재현한다:
 * {@code NaverSearchCategory.values() × buildQueries} → {@code search} → {@code filterObviousMismatches}
 * → URL blank/null 제외 → 정규화 URL putIfAbsent dedup → 호출/결과 상한(각 Math.max(1,…) 클램프) 조기 중단.
 *
 * <p>공고-only 하네스와 산출물이 섞이지 않도록 전용 디렉터리
 * {@code .tmp/job_posting_real_regression_set/company_web_harness/} 하위에만 쓴다(기존 company_harness/ 무접촉).
 * generate 는 3인자({@code (ac, posting, webEvidence)}), canonicalize 는 7인자
 * ({@code (payload, id, rev, posting, companyName, jobTitle, webEvidence)})로 호출한다.
 *
 * <p>검색 루프 뒤에 프로덕션 {@code resolveSearchResults} 와 동일하게 코퍼스 레벨 정체성 게이트
 * ({@link CompanySourceResolver#retainIdentifiableResults})를 caller 층에서 태워 접두충돌 경쟁사를 제거하고
 * anchor 근거가 없으면 빈 목록으로 degrade 한다. 이 게이트된 결과만 evidence collector·R1·저장 gate 로 넘긴다
 * (이슈A 동명 접두충돌 수정 효과를 검증하기 위함).
 *
 * <p><b>hard fail / empty evidence 원칙:</b> 하드 실패는 오직 검색·collector 가 예외를 던질 때만이다 —
 * 검색 예외({@link com.careertuner.companyanalysis.websearch.CompanyWebSearchException})는 삼키지 않고 그대로
 * 드러낸다. 반면 WEB evidence 가 비는 것은 더 이상 하드페일 사유가 아니다. 게이트가 오염을 전부 걸러낸
 * {@code IDENTITY_GATE_DEGRADED}(검색 非0·게이트 후 0)와 {@code NO_WEB_RESULTS}(검색 0건)는 <b>실패로 세지 않고</b>,
 * 그 경우에도 공고-only(webEvidence=[])로 R1 생성·채점 출력을 계속한다. 목적: degrade 된 결과의 자유서술
 * (recentIssues·competitors·interviewPoints·aiInferences)에 타사 오염이 남지 않았는지 채점할 수 있게 하기 위함.
 *
 * <p>NAVER 키는 env {@code NAVER_SEARCH_CLIENT_ID}/{@code NAVER_SEARCH_CLIENT_SECRET} 에서 직접 읽어
 * {@link NaverSearchProperties} 에 세팅한다(생성자 relaxed binding 없음). 키가 없거나 configured()==false 면
 * 즉시 중단한다. <b>Client Secret·키는 어떤 출력에도 절대 싣지 않는다.</b>
 *
 * <p>자동 채점은 하지 않는다. reviewer 가 원문·검색근거 대조로 채우는 빈 수기 채점표만 포함하되, 채점에 필요한
 * 검색 query/category·raw/gated 수집 URL·게이트 제거 URL·webEvidence 항목(title/snippet/url/fetchedAt)·canonical
 * verifiedFacts 의 sourceKind/sourceRef·companyR1 fellBack 여부·rawFact/canonicalFact 수·empty evidence 상태
 * (IDENTITY_GATE_DEGRADED / NO_WEB_RESULTS) 를 남긴다.
 *
 * <p>일반 테스트/CI 에는 영향이 없도록 환경변수 {@code B_REAL_COMPANY_WEB=true} 일 때만 실행된다.
 * 실행(1건): {@code $env:B_REAL_COMPANY_WEB="true"; $env:B_REAL_COMPANY_WEB_TARGET="1"; .\gradlew.bat test --tests *BCompanyAnalysisWebRealPostingHarness}
 * 배치 실행: {@code B_REAL_COMPANY_WEB_TARGET="1-3"} 또는 {@code B_REAL_COMPANY_WEB_TARGET="1,4,10"}.
 * 전체 10건은 실수 방지를 위해 {@code B_REAL_COMPANY_WEB_ALL=true} 를 명시해야 한다.
 * 이미 완료된 case fragment 는 기본 skip 하며, 재실행하려면 {@code B_REAL_COMPANY_WEB_FORCE=true} 를 추가한다.
 * R1 접속은 {@code B_ANALYSIS_OLLAMA_BASE_URL}/{@code B_ANALYSIS_OLLAMA_MODEL}(선택),
 * read timeout 은 {@code B_REAL_COMPANY_WEB_READ_TIMEOUT_SECONDS}(선택)로 조정한다.
 */
@EnabledIfEnvironmentVariable(named = "B_REAL_COMPANY_WEB", matches = "true")
class BCompanyAnalysisWebRealPostingHarness {

    private static final String ENV_TARGET = "B_REAL_COMPANY_WEB_TARGET";
    private static final String ENV_ALL = "B_REAL_COMPANY_WEB_ALL";
    private static final String ENV_FORCE = "B_REAL_COMPANY_WEB_FORCE";
    private static final String ENV_READ_TIMEOUT_SECONDS = "B_REAL_COMPANY_WEB_READ_TIMEOUT_SECONDS";
    private static final String ENV_NAVER_CLIENT_ID = "NAVER_SEARCH_CLIENT_ID";
    private static final String ENV_NAVER_CLIENT_SECRET = "NAVER_SEARCH_CLIENT_SECRET";
    private static final String ENV_OLLAMA_BASE_URL = "B_ANALYSIS_OLLAMA_BASE_URL";
    private static final String ENV_OLLAMA_MODEL = "B_ANALYSIS_OLLAMA_MODEL";

    // 프로덕션 runSearch 와 동일한 카테고리 4종.
    private static final NaverSearchCategory[] SEARCH_CATEGORIES = NaverSearchCategory.values();

    private record TargetCase(int index, String fileName, String companyName, String jobTitle) {
    }

    /** runSearch 재현 결과 — dedup 후 검색결과와 시도한 쿼리 목록(수기 채점 trace 용). */
    private record SearchOutcome(List<CompanyWebSearchResult> results, List<String> queries) {
    }

    // 공고-only 하네스와 동일 10건 세트(순서 유지). 회사명/직무명은 원본 공고 확인 값.
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
    void runRealWebPostings() throws Exception {
        Path root = resolveRoot();
        Path ocrDir = root.resolve(".tmp/job_posting_real_regression_set/ocr");
        if (!Files.isDirectory(ocrDir)) {
            // OCR 세트는 git 에 없는 로컬 전용 자료 — 없으면 조용히 전건 SKIP 되지 않도록 즉시 중단한다.
            throw new AssertionError("OCR 세트 디렉터리가 없어 중단: " + ocrDir.toAbsolutePath()
                    + " (.tmp/job_posting_real_regression_set/ocr 로컬 세트를 먼저 준비해야 한다)");
        }
        // 공고-only 하네스 산출물과 섞이지 않도록 전용 디렉터리에만 쓴다.
        Path harnessDir = root.resolve(".tmp/job_posting_real_regression_set/company_web_harness");
        Path rawDir = harnessDir.resolve("raw_web");
        Path caseDir = harnessDir.resolve("cases_web");
        Path compareDir = harnessDir.resolve("compare_web");
        Path outFile = harnessDir.resolve("company_web_analysis_manual_review.md");
        Path compareFile = harnessDir.resolve("company_web_analysis_regression_compare.md");
        Files.createDirectories(rawDir);
        Files.createDirectories(caseDir);
        Files.createDirectories(compareDir);

        ObjectMapper mapper = new ObjectMapper();
        BCompanyAnalysisCanonicalizer canonicalizer = new BCompanyAnalysisCanonicalizer(mapper);

        // NAVER 키는 env 에서 직접 읽어 세팅한다(생성자 relaxed binding 없음). 미설정이면 즉시 중단.
        NaverSearchProperties naverProperties = new NaverSearchProperties();
        String clientId = System.getenv(ENV_NAVER_CLIENT_ID);
        String clientSecret = System.getenv(ENV_NAVER_CLIENT_SECRET);
        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new AssertionError("NAVER 검색 키 미설정으로 중단: "
                    + ENV_NAVER_CLIENT_ID + "/" + ENV_NAVER_CLIENT_SECRET + " 를 먼저 설정해야 한다.");
        }
        naverProperties.setClientId(clientId.trim());
        naverProperties.setClientSecret(clientSecret.trim());
        if (!naverProperties.configured()) {
            // 시크릿 값은 출력하지 않는다 — configured() 실패 사실만 알린다.
            throw new AssertionError("NAVER 검색 키가 configured()==false 로 판정되어 중단(키/시크릿 확인 필요).");
        }

        // 웹검색 협력자는 전부 public 이라 하네스가 직접 조립한다(프로덕션 runSearch 재현).
        CompanySourceResolver sourceResolver = new CompanySourceResolver();
        CompanyWebSearchClient webSearchClient = new CompanyWebSearchClient(naverProperties, mapper);
        CompanyEvidenceCollector evidenceCollector = new CompanyEvidenceCollector(sourceResolver);
        CompanyWebSearchProperties webSearchProperties = new CompanyWebSearchProperties(); // 상한 기본값(4/12).

        String modelName = firstNonBlank(System.getenv(ENV_OLLAMA_MODEL), "careertuner-b-jobposting-r1");

        StringBuilder report = new StringBuilder();
        StringBuilder compare = new StringBuilder();
        report.append("# 기업분석 웹검색 실측 검증 결과 (재설계 D-6)\n\n");
        report.append("- 실행 시각: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        report.append("- 모델: ").append(modelName).append(", temperature 0, maxRetries 0\n");
        report.append("- WEB evidence = 실제 NAVER 검색(runSearch 재현) → 정체성 게이트(retainIdentifiableResults) → CompanyEvidenceCollector 결과. R1 생성·저장 gate 양쪽 입력.\n");
        report.append("- raw 검색결과 = runSearch 재현 dedup 후 / gated = 정체성 게이트(접두충돌 경쟁사 제거 + anchor 게이트) 통과분\n");
        report.append("- raw = 모델 원문 JSON(chat spy) / final = parseLocalCompanyPayload 통과 후 payload\n");
        report.append("- canonical final = 저장 직전 canonicalizer(evidence gate 2소스[공고+WEB], unknowns 접기, source 보정) 통과 후 payload\n");
        report.append("- IDENTITY_GATE_DEGRADED = 검색은 됐으나 정체성 게이트가 전부 걸러 WEB evidence 0건(실패 아님 — 공고-only 로 채점 계속).\n");
        report.append("- NO_WEB_RESULTS = 검색 0건(실패 아님 — 공고-only 로 채점 계속). 위 두 상태는 자유서술 타사 오염 검증 표본이다.\n");
        report.append("- fellBack=true 인 company 는 self-rules-v1 폴백 결과이며 R1 채점 표본이 아니다.\n\n");

        compare.append("# 기업분석 웹검색 비교표 (D-6)\n\n");
        compare.append("- 기준: 동일 10건 OCR 세트 + 실제 NAVER WEB evidence, D-6 canonicalizer(2소스) 적용 후 계측\n\n");
        compare.append("| case | companyR1 | webEvidence | verifiedFacts raw→canonical | aiInferences raw→storage | virtual unknowns | gate PASSED | gate DEMOTED | gate REMOVED | webStatus |\n");
        compare.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---|\n");

        report.append("## 케이스 매핑 (case index ↔ 원본 공고 ↔ 회사명/직무명)\n\n");
        report.append("| case | 원본 공고 파일 | 회사명 | 직무명 |\n");
        report.append("|---|---|---|---|\n");
        for (TargetCase t : TARGETS) {
            report.append("| %02d | %s | %s | %s |\n".formatted(t.index(), t.fileName(), t.companyName(), t.jobTitle()));
        }
        report.append("\n");

        report.append("## 수기 채점 기준\n\n");
        report.append("- classification: FACT(원문/웹근거에서 직접 확인) / INFERENCE(추론) / UNKNOWN(입력만으로 확인 불가)\n");
        report.append("- rating: SUPPORTED / PARTIAL / UNSUPPORTED / NOT_AVAILABLE / OCR_INPUT_ISSUE\n");
        report.append("- webSupport: 해당 claim 이 WEB evidence(sourceRef URL)로 뒷받침되는지 y/n\n");
        report.append("- claimRef: 구조화 항목 참조(verifiedFacts[0]=V0, aiInferences[1]=I1 등)\n");
        report.append("- 빈칸 = 미채점, PASS 아님. 자동 채점 없음 — reviewer 가 원문·검색근거 대조로 채운다.\n\n");

        String reportHeader = report.toString();
        String compareHeader = compare.toString();
        List<TargetCase> targets = selectedTargets();
        if (targets.isEmpty()) {
            String requested = System.getenv(ENV_TARGET);
            System.out.println("RESULT|" + requested + "|NO_MATCH");
            if (isBlank(requested) && !truthy(System.getenv(ENV_ALL))) {
                report.append("## 실행 대상 없음\n\n")
                        .append("- 10건 전체 실행을 막기 위해 기본 실행은 허용하지 않는다.\n")
                        .append("- 1건 실행: B_REAL_COMPANY_WEB_TARGET=1\n")
                        .append("- 배치 실행: B_REAL_COMPANY_WEB_TARGET=1-3 또는 1,4,10\n")
                        .append("- 전체 실행: B_REAL_COMPANY_WEB_ALL=true\n");
            } else {
                report.append("## NO_MATCH: ").append(requested)
                        .append("\nB_REAL_COMPANY_WEB_TARGET 가 어떤 공고와도 매칭되지 않음\n");
            }
            Files.writeString(outFile, report.toString(), StandardCharsets.UTF_8);
            throw new AssertionError("No target postings selected. Set B_REAL_COMPANY_WEB_TARGET=1, 1-3, 1,4,10, substring, or B_REAL_COMPANY_WEB_ALL=true.");
        }
        writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);

        boolean force = truthy(System.getenv(ENV_FORCE));
        List<String> missingFiles = new ArrayList<>();
        for (TargetCase target : targets) {
            Path caseFile = caseDir.resolve("%02d.md".formatted(target.index()));
            Path compareRowFile = compareDir.resolve("%02d.md".formatted(target.index()));
            if (!force && Files.exists(caseFile) && Files.exists(compareRowFile)) {
                System.out.println("RESULT|" + target.fileName() + "|SKIP(existing fragment; set B_REAL_COMPANY_WEB_FORCE=true to rerun)");
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
                compare.append("| %02d | MISSING | 0 | 0→0 | 0→0 | 0 | 0 | 0 | 0 | - |\n".formatted(target.index()));
                writeCaseFragments(caseFile, report.substring(reportStart), compareRowFile, compare.substring(compareStart));
                writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);
                continue;
            }
            String posting = Files.readString(file, StandardCharsets.UTF_8);

            // --- 1) 실제 NAVER 검색 → 정체성 게이트 → WEB evidence (프로덕션 resolveSearchResults 재현) ---
            // 회사명이 1급 입력 — industry/region 힌트는 공고에 없어 비운다(프로덕션 toCompanyIdentity 와 동일).
            CompanyIdentity identity = new CompanyIdentity(target.companyName(), "", "");
            // CompanyWebSearchException 은 삼키지 않는다 — 그대로 실패로 드러난다(검증용 hard fail).
            SearchOutcome searchOutcome = runSearch(identity, sourceResolver, webSearchClient, webSearchProperties);
            List<CompanyWebSearchResult> rawResults = searchOutcome.results();
            // 코퍼스 레벨 정체성 게이트(D-6 이슈A · 동명 접두충돌): 접두충돌 경쟁사 제거 + anchor 게이트.
            // 프로덕션 resolveSearchResults 가 runSearch 뒤·캐시 put 전에 적용하는 것과 동일하게 caller 층에서 태운다.
            List<CompanyWebSearchResult> gatedResults = sourceResolver.retainIdentifiableResults(identity, rawResults);
            List<CompanyWebEvidence> webEvidence = evidenceCollector.collect(identity, gatedResults);
            // 게이트로 제거된 URL(raw − gated, 정규화 링크 기준) — 이슈A 효과 trace 용.
            List<String> removedLinks = removedLinks(rawResults, gatedResults);

            // empty-evidence 사유 분류: 하드페일이 아니라 채점을 계속할 정상 degrade/무검색 상태다.
            //  - rawResults 非empty & gatedResults empty → IDENTITY_GATE_DEGRADED (이슈A 가 오염을 전부 걸러냄; 실패 아님)
            //  - rawResults empty(검색 0건)          → NO_WEB_RESULTS (실패 아님)
            String emptyStatus = null;
            if (webEvidence.isEmpty()) {
                emptyStatus = rawResults.isEmpty() ? "NO_WEB_RESULTS" : "IDENTITY_GATE_DEGRADED";
            }

            report.append("## ").append(header).append("\n\n");
            report.append("- 회사명: ").append(target.companyName())
                    .append(" / 직무명: ").append(target.jobTitle()).append("\n");
            report.append("### 웹검색 trace\n\n");
            report.append("- 시도 카테고리: ").append(categoryNames()).append("\n");
            report.append("- 시도 쿼리(구체→폴백): ").append(oneLine(String.join(" | ", searchOutcome.queries()))).append("\n");
            report.append("- raw 검색결과 수: ").append(rawResults.size())
                    .append(" / gated 후 수: ").append(gatedResults.size())
                    .append(" / WEB evidence 수: ").append(webEvidence.size()).append("\n");
            if (emptyStatus != null) {
                report.append("- empty evidence 상태: ").append(emptyStatus)
                        .append(" (실패 아님 — R1·채점 계속)\n");
            }
            report.append("- gated 후 수집 URL 목록:\n");
            if (gatedResults.isEmpty()) {
                report.append("  - (없음)\n");
            } else {
                for (CompanyWebSearchResult r : gatedResults) {
                    report.append("  - [").append(r.category()).append("] ").append(oneLine(r.link())).append("\n");
                }
            }
            report.append("- 게이트 제거된 URL 목록(raw − gated):\n");
            if (removedLinks.isEmpty()) {
                report.append("  - (없음)\n");
            } else {
                for (String link : removedLinks) {
                    report.append("  - ").append(oneLine(link)).append("\n");
                }
            }
            report.append("\n");

            if (emptyStatus != null) {
                // WEB evidence 는 비었지만 중단하지 않는다 — 게이트가 오염을 걷어낸 정상 degrade(또는 무검색)이므로
                // 공고-only 로 R1·채점을 계속 진행해 자유서술에 타사 오염이 없는지 검증한다(webEvidence=[] → 3인자 공고-only).
                report.append("### ").append(emptyStatus).append("\n\n");
                if ("IDENTITY_GATE_DEGRADED".equals(emptyStatus)) {
                    report.append("- 정체성 게이트가 검색결과 ").append(rawResults.size())
                            .append("건을 전부 걸러내 WEB evidence 0건(이슈A 정상 degrade — 실패 아님).\n");
                } else {
                    report.append("- 검색결과 0건(NO_WEB_RESULTS — 실패 아님).\n");
                }
                report.append("- 공고-only 로 R1 생성·채점을 계속한다(자유서술 타사 오염 검증).\n\n");
                System.out.println("RESULT|" + target.fileName() + "|" + emptyStatus);
            }

            report.append("### WEB evidence (canonical WEB fact 의 sourceRef 원천)\n\n");
            report.append("| # | url(sourceRef) | title | fetchedAt | snippet |\n");
            report.append("|---|---|---|---|---|\n");
            for (int i = 0; i < webEvidence.size(); i++) {
                CompanyWebEvidence ev = webEvidence.get(i);
                report.append("| ").append(i).append(" | ")
                        .append(oneLine(ev.url())).append(" | ")
                        .append(oneLine(ev.title())).append(" | ")
                        .append(ev.fetchedAt() == null ? "" : ev.fetchedAt().toString()).append(" | ")
                        .append(oneLine(ev.snippet())).append(" |\n");
            }
            report.append("\n");

            // --- 2) R1 기업분석 (공고 + WEB evidence) ---
            BAnalysisProperties properties = new BAnalysisProperties();
            properties.getLocalLlm().setEnabled(true);
            properties.getLocalLlm().setReadTimeout(readTimeout());
            properties.getLocalLlm().setMaxRetries(0); // 검증은 1회 호출만(폴백 관찰 목적).
            applyOllamaEnv(properties);

            // RuntimeSettingService(null): 매퍼 미주입 → getInt 이 항상 fallback(정적 예산)을 돌려줌(동작 불변).
            BLocalLlmClient realClient = new BLocalLlmClient(properties, GpuPermitGate.disabled(), new RuntimeSettingService(null));
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
                    .id((long) target.index()).userId(1L)
                    .companyName(target.companyName()).jobTitle(target.jobTitle()).status("DRAFT")
                    .build();

            long companyT0 = System.currentTimeMillis();
            BAnalysisGenerationService.GeneratedCompanyAnalysis companyResult = null;
            String companyError = null;
            try {
                // 3인자 진입점 — 공고 + WEB evidence.
                companyResult = service.generateCompanyAnalysis(ac, posting, webEvidence);
            } catch (RuntimeException ex) {
                companyError = ex.getMessage();
            }
            long companyMs = System.currentTimeMillis() - companyT0;
            Path companyRawFile = rawDir.resolve("%02d_company.raw.json".formatted(target.index()));
            boolean companyRawSaved = saveRaw(companyRawFile, rawHolder);

            boolean companyR1 = companyResult != null && !companyResult.fellBack();
            // 7인자 canonicalize — WEB evidence 를 저장 gate 에 함께 넘긴다.
            CanonicalCompanyAnalysis canonicalCompany = companyResult == null ? null : canonicalizer.canonicalizeForStorage(
                    companyResult.payload(),
                    (long) target.index(),
                    1,
                    posting,
                    target.companyName(),
                    target.jobTitle(),
                    webEvidence);
            int rawFactCount = companyResult == null ? 0 : arraySize(mapper, companyResult.payload().verifiedFacts());
            int canonicalFactCount = canonicalCompany == null ? 0 : arraySize(mapper, canonicalCompany.payload().verifiedFacts());
            int rawInferenceCount = companyResult == null ? 0 : arraySize(mapper, companyResult.payload().aiInferences());
            int storageInferenceCount = canonicalCompany == null ? 0 : arraySize(mapper, canonicalCompany.payload().aiInferences());
            int virtualUnknownCount = canonicalCompany == null ? 0
                    : arraySize(mapper, canonicalizer.extractUnknowns(canonicalCompany.payload().aiInferences()));
            long passedCount = countGateActions(canonicalCompany, GateOutcome.PASSED);
            long demotedCount = countGateActions(canonicalCompany, GateOutcome.DEMOTED);
            long removedCount = countGateActions(canonicalCompany, GateOutcome.REMOVED);

            // 마지막 컬럼: empty evidence 정상 degrade/무검색은 그 상태 라벨을, 아니면 "no".
            String webStatusLabel = emptyStatus == null ? "no" : emptyStatus;
            compare.append("| %02d | %s | %d | %d→%d | %d→%d | %d | %d | %d | %d | %s |\n".formatted(
                    target.index(),
                    companyR1 ? "Y" : "N",
                    webEvidence.size(),
                    rawFactCount,
                    canonicalFactCount,
                    rawInferenceCount,
                    storageInferenceCount,
                    virtualUnknownCount,
                    passedCount,
                    demotedCount,
                    removedCount,
                    webStatusLabel));

            System.out.println("RESULT|" + target.fileName()
                    + "|companyFellBack=" + (companyResult == null ? "ERROR" : companyResult.fellBack())
                    + "|webEvidence=" + webEvidence.size()
                    + (emptyStatus == null ? "" : "|" + emptyStatus)
                    + "|companyMs=" + companyMs
                    + "|companyRaw=" + companyRawSaved);

            report.append("- company: fellBack=").append(companyResult == null ? "ERROR(" + companyError + ")" : companyResult.fellBack())
                    .append(", ms=").append(companyMs)
                    .append(", raw=").append(companyRawSaved ? rawDir.getFileName() + "/" + companyRawFile.getFileName() : "없음(R1 응답 없음)")
                    .append(companyR1 ? "" : " — **R1 채점 표본 아님**").append("\n");
            if (companyResult != null && companyResult.fellBack()) {
                report.append("- company fallbackReason: ").append(oneLine(companyResult.fallbackReason())).append("\n");
            }
            report.append("\n");

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

                // canonical verifiedFacts 의 sourceKind/sourceRef — WEB fact ↔ webEvidence URL 대조용.
                report.append("#### canonical verifiedFacts sourceKind/sourceRef\n\n");
                report.append("| ref | sourceKind | sourceRef | claim |\n");
                report.append("|---|---|---|---|\n");
                report.append(verifiedFactSourceRows(mapper, canonicalPayload.verifiedFacts()));
                report.append("\n");

                report.append("#### gateActions\n\n");
                report.append("| ref | field | gateAction | detail |\n");
                report.append("|---|---|---|---|\n");
                report.append(gateActionRows(canonicalCompany));
                report.append("\n");
            } else {
                report.append("### 기업분석 final\n\nERROR: ").append(companyError).append("\n\n");
            }

            // 수기 채점: 자동 판정 없이 빈 템플릿만 찍는다. reviewer 가 claim 단위로 채운다.
            report.append("### 수기 채점표 — reviewer 가 채움 〔빈칸 = 미채점, PASS 아님〕\n\n");
            report.append("| caseId | claimRef | field | claim | classification | rating | webSupport | note | reviewer | reviewedAt |\n");
            report.append("|---|---|---|---|---|---|---|---|---|---|\n");
            for (int i = 0; i < 5; i++) {
                report.append("| ").append(target.index()).append(" | | | | | | | | | |\n");
            }
            report.append("\n(행이 부족하면 reviewer 가 caseId=").append(target.index())
                    .append(" 로 행을 추가한다. classification=FACT/INFERENCE/UNKNOWN, ")
                    .append("rating=SUPPORTED/PARTIAL/UNSUPPORTED/NOT_AVAILABLE/OCR_INPUT_ISSUE, webSupport=y/n)\n\n");
            writeCaseFragments(caseFile, report.substring(reportStart), compareRowFile, compare.substring(compareStart));
            writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);
        }

        writeAggregate(outFile, reportHeader, caseDir, compareFile, compareHeader, compareDir);
        System.out.println("REPORT_WRITTEN|" + outFile.toAbsolutePath());
        System.out.println("COMPARE_WRITTEN|" + compareFile.toAbsolutePath());

        if (!missingFiles.isEmpty()) {
            throw new AssertionError("OCR 세트에서 누락된 공고 파일 " + missingFiles.size() + "건: " + missingFiles);
        }
        // empty evidence(IDENTITY_GATE_DEGRADED / NO_WEB_RESULTS)는 더 이상 하드페일 사유가 아니다 —
        // 게이트가 오염을 걷어낸 정상 degrade(또는 무검색)이므로 공고-only 로 R1·채점을 계속했다.
        // 하드 실패는 오직 검색/collector 가 예외를 던질 때만(CompanyWebSearchException 하드페일 계약 유지).
    }

    /**
     * 프로덕션 {@code CompanyAnalysisService#runSearch} 동일 계약 재현(단순 루프 아님).
     * 카테고리 × 쿼리 순회, filterObviousMismatches, URL blank/null 제외, 정규화 URL putIfAbsent dedup,
     * 호출/결과 상한(각 Math.max(1,…) 클램프) 조기 중단(호출 전 검사 + putIfAbsent 후 size>=max 즉시 중단).
     * {@link com.careertuner.companyanalysis.websearch.CompanyWebSearchException} 은 잡지 않고 전파한다.
     */
    private static SearchOutcome runSearch(CompanyIdentity identity,
                                           CompanySourceResolver sourceResolver,
                                           CompanyWebSearchClient webSearchClient,
                                           CompanyWebSearchProperties webSearchProperties) {
        int maxSearchCalls = Math.max(1, webSearchProperties.getMaxSearchCallsPerAnalysis());
        int maxResults = Math.max(1, webSearchProperties.getMaxResultsPerAnalysis());
        List<String> queries = sourceResolver.buildQueries(identity);
        LinkedHashMap<String, CompanyWebSearchResult> byUrl = new LinkedHashMap<>();
        int calls = 0;
        for (String query : queries) {
            for (NaverSearchCategory category : SEARCH_CATEGORIES) {
                if (calls >= maxSearchCalls || byUrl.size() >= maxResults) {
                    return new SearchOutcome(List.copyOf(byUrl.values()), queries);
                }
                calls++;
                List<CompanyWebSearchResult> filtered =
                        sourceResolver.filterObviousMismatches(identity, webSearchClient.search(category, query));
                for (CompanyWebSearchResult result : filtered) {
                    String url = result.link();
                    if (url == null || url.isBlank()) {
                        continue; // WEB 출처는 URL 필수 — evidence 로 못 쓰므로 제외.
                    }
                    // 정규화 URL 기준 dedup(먼저 수집분 우선). 새 결과가 상한에 도달하면 즉시 중단.
                    if (byUrl.putIfAbsent(normalizeUrl(url), result) == null && byUrl.size() >= maxResults) {
                        return new SearchOutcome(List.copyOf(byUrl.values()), queries);
                    }
                }
            }
        }
        return new SearchOutcome(List.copyOf(byUrl.values()), queries);
    }

    /** 정체성 게이트로 제거된 링크(raw − gated) 목록 — 정규화 링크 기준 차집합, 원본 표기 유지. */
    private static List<String> removedLinks(List<CompanyWebSearchResult> rawResults,
                                             List<CompanyWebSearchResult> gatedResults) {
        Set<String> retained = new LinkedHashSet<>();
        for (CompanyWebSearchResult r : gatedResults) {
            if (r.link() != null && !r.link().isBlank()) {
                retained.add(normalizeUrl(r.link()));
            }
        }
        List<String> removed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (CompanyWebSearchResult r : rawResults) {
            String link = r.link();
            if (link == null || link.isBlank()) {
                continue;
            }
            String normalized = normalizeUrl(link);
            if (!retained.contains(normalized) && seen.add(normalized)) {
                removed.add(link);
            }
        }
        return removed;
    }

    /** 프로덕션 runSearch 의 normalizeUrl 과 동일(trim · 후행 / 제거 · lowercase). */
    private static String normalizeUrl(String url) {
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String categoryNames() {
        StringBuilder sb = new StringBuilder();
        for (NaverSearchCategory category : SEARCH_CATEGORIES) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(category.name());
        }
        return sb.toString();
    }

    private void applyOllamaEnv(BAnalysisProperties properties) {
        String baseUrl = System.getenv(ENV_OLLAMA_BASE_URL);
        if (!isBlank(baseUrl)) {
            properties.getLocalLlm().setBaseUrl(baseUrl.trim());
        }
        String model = System.getenv(ENV_OLLAMA_MODEL);
        if (!isBlank(model)) {
            properties.getLocalLlm().setModel(model.trim());
        }
    }

    private String verifiedFactSourceRows(ObjectMapper mapper, String verifiedFactsJson) {
        if (isBlank(verifiedFactsJson)) {
            return "| | | | |\n";
        }
        JsonNode array;
        try {
            array = mapper.readTree(verifiedFactsJson);
        } catch (JacksonException ex) {
            return "| | (parse error) | | |\n";
        }
        if (array == null || !array.isArray() || array.isEmpty()) {
            return "| | | | |\n";
        }
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);
            rows.append("| V").append(i).append(" | ")
                    .append(oneLine(nodeText(node, "sourceKind"))).append(" | ")
                    .append(oneLine(nodeText(node, "sourceRef"))).append(" | ")
                    .append(oneLine(factClaim(node))).append(" |\n");
        }
        return rows.toString();
    }

    /** verifiedFact 의 주장 텍스트 필드명이 스키마에 따라 다를 수 있어 후보를 순차 조회한다. */
    private static String factClaim(JsonNode node) {
        for (String key : List.of("fact", "claim", "text", "statement")) {
            String value = nodeText(node, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String nodeText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return node.get(field).asString("");
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

    private static String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
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
