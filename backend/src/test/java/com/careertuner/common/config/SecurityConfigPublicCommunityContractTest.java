package com.careertuner.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SecurityConfigPublicCommunityContractTest {

    @Test
    void publicUserActivityMatchesNullableViewerControllerContract() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/careertuner/common/config/SecurityConfig.java"));

        assertThat(source)
                .contains("\"/api/community/users/*/activity\"")
                .contains("\"/api/community/users/*/activity-tabs\"");
    }
}
