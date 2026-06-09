package com.careertuner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class JacksonUsageConventionTests {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");

    @Test
    void applicationCodeUsesBootManagedJackson3Mapper() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(MAIN_SOURCE)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(path);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index).trim();
                    if (line.startsWith("import com.fasterxml.jackson.core.")
                            || line.startsWith("import com.fasterxml.jackson.databind.")
                            || line.contains("new ObjectMapper(")) {
                        violations.add("%s:%d: %s".formatted(path, index + 1, line));
                    }
                }
            }
        }

        assertThat(violations)
                .as("애플리케이션 코드는 Jackson 3 타입과 Spring 관리 ObjectMapper Bean을 사용해야 합니다.")
                .isEmpty();
    }
}
