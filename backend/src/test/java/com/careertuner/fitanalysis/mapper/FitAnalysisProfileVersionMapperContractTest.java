package com.careertuner.fitanalysis.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class FitAnalysisProfileVersionMapperContractTest {

    @Test
    void generationSourceUsesAndIdentifiesTheLatestActiveProfileSnapshot() {
        String xml = normalize(resource("mapper/fitanalysis/FitAnalysisMapper.xml"));

        assertThat(xml)
                .contains("upv.id AS profile_version_id")
                .contains("upv.version_no AS profile_version_no")
                .contains("LEFT JOIN user_profile_version upv")
                .contains("latest_profile.user_id = ac.user_id")
                .contains("latest_profile.deleted_at IS NULL")
                .contains("ORDER BY latest_profile.version_no DESC, latest_profile.id DESC")
                .contains("COALESCE(upv.skills, up.skills) AS profile_skills")
                .contains("COALESCE(upv.certificates, up.certificates) AS profile_certificates")
                .contains("COALESCE(upv.desired_job, up.desired_job) AS desired_job");
    }

    private static String resource(String path) {
        try (InputStream input = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(path),
                "Missing resource: " + path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read resource: " + path, exception);
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
