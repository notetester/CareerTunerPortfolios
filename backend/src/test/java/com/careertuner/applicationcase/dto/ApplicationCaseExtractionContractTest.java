package com.careertuner.applicationcase.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ApplicationCaseExtractionContractTest {

    private static final Pattern TS_FIELD = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9]*)\\??:", Pattern.MULTILINE);

    @Test
    void frontendExtractionTypeHasSameFieldsAsBackendResponseRecord() throws Exception {
        String javaSource = Files.readString(Path.of(
                "src/main/java/com/careertuner/applicationcase/dto/ApplicationCaseExtractionResponse.java"));
        String tsSource = Files.readString(Path.of(
                "../frontend/src/features/applications/types/applicationCase.ts"));

        assertThat(tsFields(tsSource)).containsExactlyElementsOf(recordFields(javaSource));
    }

    private static List<String> recordFields(String javaSource) {
        String marker = "public record ApplicationCaseExtractionResponse(";
        int start = javaSource.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int bodyStart = start + marker.length();
        int bodyEnd = javaSource.indexOf(") {", bodyStart);
        assertThat(bodyEnd).isGreaterThan(bodyStart);
        return Arrays.stream(javaSource.substring(bodyStart, bodyEnd).split(","))
                .map(String::trim)
                .filter(component -> !component.isBlank())
                .map(component -> component.substring(component.lastIndexOf(' ') + 1).trim())
                .toList();
    }

    private static List<String> tsFields(String tsSource) {
        String marker = "export interface ApplicationCaseExtraction {";
        int start = tsSource.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int bodyStart = start + marker.length();
        int bodyEnd = tsSource.indexOf("\n}", bodyStart);
        assertThat(bodyEnd).isGreaterThan(bodyStart);
        Matcher matcher = TS_FIELD.matcher(tsSource.substring(bodyStart, bodyEnd));
        return matcher.results()
                .map(result -> result.group(1))
                .toList();
    }
}
