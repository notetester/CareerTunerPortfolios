package com.careertuner.correction.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CorrectionMapperXmlTest {

    @Test
    void requestKeyPersistenceAndSchemaStayAligned() throws IOException {
        String mapper = Files.readString(Path.of(
                "src/main/resources/mapper/correction/CorrectionMapper.xml"));
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260708_e_correction_request_idempotency.sql"));

        assertThat(mapper).contains("request_key", "findByUserIdAndRequestKey");
        assertThat(schema).contains("request_key         VARCHAR(120) NULL");
        assertThat(schema).contains("UNIQUE KEY uk_correction_request_user_key (user_id, request_key)");
        assertThat(patch).contains("CREATE UNIQUE INDEX uk_correction_request_user_key");
    }
}
