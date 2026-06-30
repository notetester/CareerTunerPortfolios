package com.careertuner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SpringBeanConventionTests {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");
    private static final Pattern PRIMARY_ANNOTATION =
            Pattern.compile("^\\s*@(org\\.springframework\\.context\\.annotation\\.)?Primary\\b");
    private static final Pattern CLASS_DECLARATION =
            Pattern.compile("\\bclass\\s+(\\w+)\\b([^\\{]*)");
    private static final Pattern IMPLEMENTS_CLAUSE =
            Pattern.compile("\\bimplements\\s+(.+)$");

    @Test
    void primaryStrategyBeansAreUniquePerInterface() throws IOException {
        Map<String, List<PrimaryBean>> primaryBeansByInterface = new TreeMap<>();

        try (var paths = Files.walk(MAIN_SOURCE)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(path);
                for (int index = 0; index < lines.size(); index++) {
                    if (!PRIMARY_ANNOTATION.matcher(lines.get(index)).find()) {
                        continue;
                    }
                    PrimaryBean bean = primaryBean(path, lines, index);
                    if (bean == null) {
                        continue;
                    }
                    for (String interfaceName : bean.interfaces()) {
                        primaryBeansByInterface
                                .computeIfAbsent(interfaceName, ignored -> new ArrayList<>())
                                .add(bean);
                    }
                }
            }
        }

        List<String> violations = primaryBeansByInterface.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "%s -> %s".formatted(
                        entry.getKey(),
                        entry.getValue().stream().map(PrimaryBean::location).toList()))
                .toList();

        assertThat(violations)
                .as("전략/AI provider 인터페이스는 @Primary 구현체를 하나만 가져야 합니다. "
                        + "새 provider는 primary dispatcher 안으로 연결하거나 기존 primary를 제거하세요.")
                .isEmpty();
    }

    private static PrimaryBean primaryBean(Path path, List<String> lines, int annotationIndex) {
        StringBuilder declaration = new StringBuilder();
        for (int index = annotationIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.startsWith("@")) {
                continue;
            }
            declaration.append(' ').append(line);
            if (line.contains("{")) {
                break;
            }
        }

        var classMatcher = CLASS_DECLARATION.matcher(declaration);
        if (!classMatcher.find()) {
            return null;
        }

        var implementsMatcher = IMPLEMENTS_CLAUSE.matcher(classMatcher.group(2).trim());
        if (!implementsMatcher.find()) {
            return new PrimaryBean(classMatcher.group(1), path, annotationIndex + 1, List.of());
        }

        List<String> interfaces = List.of(implementsMatcher.group(1).split(",")).stream()
                .map(SpringBeanConventionTests::simpleTypeName)
                .filter(name -> !name.isBlank())
                .toList();
        return new PrimaryBean(classMatcher.group(1), path, annotationIndex + 1, interfaces);
    }

    private static String simpleTypeName(String value) {
        String out = value.trim()
                .replaceAll("<.*>", "")
                .replaceAll("\\s+", " ");
        int space = out.indexOf(' ');
        if (space >= 0) {
            out = out.substring(0, space);
        }
        int dot = out.lastIndexOf('.');
        if (dot >= 0) {
            out = out.substring(dot + 1);
        }
        return out.trim();
    }

    private record PrimaryBean(String className, Path path, int line, List<String> interfaces) {

        private String location() {
            return "%s:%d(%s)".formatted(path, line, className);
        }
    }
}
