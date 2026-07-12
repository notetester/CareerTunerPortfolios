package com.careertuner.applicationcase.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ApplicationCaseSoftDeleteMapperContractTest {

    private static final String[] B_HISTORY_MAPPERS = {
            "mapper/applicationcase/ApplicationCaseMapper.xml",
            "mapper/jobposting/JobPostingMapper.xml",
            "mapper/jobanalysis/JobAnalysisMapper.xml",
            "mapper/companyanalysis/CompanyAnalysisMapper.xml"
    };

    @Test
    void applicationCaseAndAnalysisHistoryMappersDoNotExposePhysicalDelete() throws IOException {
        for (String resourcePath : B_HISTORY_MAPPERS) {
            String xml = read(resourcePath);
            assertThat(xml)
                    .as("B 영역 지원 건·공고 revision·분석 이력은 물리 삭제 SQL을 노출하지 않아야 한다: %s", resourcePath)
                    .doesNotContainIgnoringCase("DELETE FROM")
                    .doesNotContain("<delete");
        }
    }

    @Test
    void applicationCaseDeletionRemainsTimestampBasedAndRecoverable() throws IOException {
        String xml = read("mapper/applicationcase/ApplicationCaseMapper.xml");
        assertThat(xml)
                .contains("<update id=\"softDeleteApplicationCase\">")
                .contains("SET deleted_at = CURRENT_TIMESTAMP")
                .contains("<update id=\"restoreDeletedApplicationCase\">")
                .contains("SET deleted_at = NULL")
                .contains("<update id=\"hideApplicationCaseFromTrash\">")
                .contains("SET deleted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 31 DAY)");
    }

    private static String read(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
