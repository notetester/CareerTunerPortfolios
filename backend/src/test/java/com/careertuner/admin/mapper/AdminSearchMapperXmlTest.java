package com.careertuner.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminSearchMapperXmlTest {

    @Test
    void listMappersUseChooseSortAndOffsetWithoutRawSortInsertion() throws Exception {
        for (String xml : adminSearchMapperXmls()) {
            String content = read(xml);

            assertThat(content)
                    .contains("<choose>")
                    .contains("LIMIT #{criteria.limit} OFFSET #{criteria.offset}")
                    .doesNotContain("${sort")
                    .doesNotContain("${criteria.sort");
        }
    }

    @Test
    void summaryQueriesAndNewFiltersExist() throws Exception {
        assertThat(read("applicationcase/AdminApplicationCaseMapper.xml"))
                .contains("summarizeApplicationCases")
                .contains("criteria.sourceType")
                .contains("criteria.favorite")
                .contains("criteria.analysisState == 'COMPLETE_ANALYSIS'");

        assertThat(read("jobanalysis/AdminJobAnalysisMapper.xml"))
                .contains("summarizeJobAnalyses")
                .contains("criteria.difficulty")
                .contains("criteria.hasMemo")
                .contains("criteria.applicationCaseId");

        assertThat(read("companyanalysis/AdminCompanyAnalysisMapper.xml"))
                .contains("summarizeCompanyAnalyses")
                .contains("criteria.refreshDue")
                .contains("criteria.checked")
                .contains("criteria.industry");

        assertThat(read("aiusage/AdminAiUsageMapper.xml"))
                .contains("summarizeBUsageLogs")
                .contains("criteria.keyword")
                .contains("criteria.model")
                .contains("criteria.applicationCaseId");
    }

    @Test
    void analysisMappersProjectLatestPostingRevisionAndStaleFlag() throws Exception {
        assertThat(read("jobanalysis/AdminJobAnalysisMapper.xml"))
                .contains("MAX(revision) AS latest_job_posting_revision")
                .contains("latest_posting.latest_job_posting_revision")
                .contains("WHEN latest_posting.latest_job_posting_revision IS NOT NULL")
                .contains("AND ja.job_posting_revision IS NOT NULL")
                .contains("ja.job_posting_revision &lt; latest_posting.latest_job_posting_revision")
                .contains("ELSE FALSE")
                .contains("AS stale_against_latest_posting");

        assertThat(read("companyanalysis/AdminCompanyAnalysisMapper.xml"))
                .contains("MAX(revision) AS latest_job_posting_revision")
                .contains("latest_posting.latest_job_posting_revision")
                .contains("WHEN latest_posting.latest_job_posting_revision IS NOT NULL")
                .contains("AND ca.job_posting_revision IS NOT NULL")
                .contains("ca.job_posting_revision &lt; latest_posting.latest_job_posting_revision")
                .contains("ELSE FALSE")
                .contains("AS stale_against_latest_posting");
    }

    private static String[] adminSearchMapperXmls() {
        return new String[] {
                "applicationcase/AdminApplicationCaseMapper.xml",
                "jobanalysis/AdminJobAnalysisMapper.xml",
                "companyanalysis/AdminCompanyAnalysisMapper.xml",
                "aiusage/AdminAiUsageMapper.xml"
        };
    }

    private static String read(String fileName) throws Exception {
        return Files.readString(Path.of("src/main/resources/mapper/admin", fileName));
    }
}
