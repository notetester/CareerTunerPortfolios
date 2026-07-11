package com.careertuner.admin.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.permission.catalog.AdminPermissionCatalog;

class AdminPermissionDeclarationContractTest {

    private static final Pattern ADMIN_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*\\\"/api/admin[^\\\"]*\\\"\\s*\\)");
    private static final Set<String> ROLE_ONLY_CLASSES = Set.of("AdminMeController.java");
    private static final Pattern PERMISSION_ANNOTATION = Pattern.compile(
            "RequireAdminPermission\\s*\\(\\s*\\{([^}]*)}");
    private static final Pattern QUOTED_CODE = Pattern.compile("\\\"([A-Z_]+)\\\"");
    private static final Pattern MUTATION_MAPPING = Pattern.compile("@(Post|Put|Patch|Delete)Mapping\\b");
    private static final Pattern METHOD_NAME = Pattern.compile(
            "public\\s+[\\w<>, ?\\[\\].]+\\s+(\\w+)\\s*\\(", Pattern.DOTALL);
    private static final Set<String> NON_DELETE_DELETE_MAPPINGS = Set.of(
            "CollaborationAdminController.java#unban");
    private static final Set<String> READ_ONLY_POST_MAPPINGS = Set.of(
            "AdminStaffGradeController.java#previewImport");
    private static final Map<String, Set<String>> ACTION_SCOPED_MUTATIONS = Map.of(
            "AdminReportController.java#takeAction", Set.of("CONTENT_UPDATE", "CONTENT_DELETE"));

    @Test
    void everyAdminControllerDeclaresPermissionOrExplicitRoleOnly() throws Exception {
        Path root = Path.of("src/main/java");
        List<String> missing = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var matcher = ADMIN_MAPPING.matcher(source);
                if (!matcher.find()) {
                    continue;
                }
                int classIndex = source.indexOf("class ", matcher.end());
                String declaration = source.substring(matcher.start(), classIndex);
                if (!declaration.contains("RequireAdminPermission")
                        && !declaration.contains("AdminRoleOnly")) {
                    missing.add(file.toString());
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void classLevelRoleOnlyAllowlistStaysNarrow() throws Exception {
        Path root = Path.of("src/main/java");
        List<String> roleOnlyClasses = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                int mapping = source.indexOf("@RequestMapping(\"/api/admin");
                if (mapping < 0) {
                    continue;
                }
                int classIndex = source.indexOf("class ", mapping);
                if (source.substring(mapping, classIndex).contains("@AdminRoleOnly")) {
                    roleOnlyClasses.add(file.getFileName().toString());
                }
            }
        }
        assertThat(roleOnlyClasses).containsExactlyInAnyOrderElementsOf(ROLE_ONLY_CLASSES);
    }

    @Test
    void declaredPermissionsComeFromExistingCatalog() throws Exception {
        Set<String> declared = new TreeSet<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                var annotations = PERMISSION_ANNOTATION.matcher(source);
                while (annotations.find()) {
                    var codes = QUOTED_CODE.matcher(annotations.group(1));
                    while (codes.find()) {
                        declared.add(codes.group(1));
                    }
                }
            }
        }

        assertThat(declared).isNotEmpty().isSubsetOf(AdminPermissionCatalog.codes());
    }

    @Test
    void everyAdminMutationDeclaresOneExactMethodPermissionOrRoleOnly() throws Exception {
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith("Controller.java")).toList()) {
                String source = Files.readString(file);
                if (!ADMIN_MAPPING.matcher(source).find()) {
                    continue;
                }
                Matcher mapping = MUTATION_MAPPING.matcher(source);
                while (mapping.find()) {
                    int methodStart = source.indexOf("public ", mapping.end());
                    int bodyStart = methodStart < 0 ? -1 : source.indexOf('{', methodStart);
                    if (methodStart < 0 || bodyStart < 0) {
                        invalid.add(file + ": 쓰기 핸들러 선언을 해석할 수 없음");
                        continue;
                    }
                    String declaration = source.substring(mapping.start(), bodyStart);
                    Matcher methodName = METHOD_NAME.matcher(declaration);
                    String handler = file.getFileName() + "#"
                            + (methodName.find() ? methodName.group(1) : "unknown");

                    if (declaration.contains("@AdminRoleOnly")) {
                        continue;
                    }
                    Matcher annotation = PERMISSION_ANNOTATION.matcher(declaration);
                    if (!annotation.find()) {
                        missing.add(handler);
                        continue;
                    }
                    List<String> codes = new ArrayList<>();
                    Matcher code = QUOTED_CODE.matcher(annotation.group(1));
                    while (code.find()) {
                        codes.add(code.group(1));
                    }
                    Set<String> actionScoped = ACTION_SCOPED_MUTATIONS.get(handler);
                    if (actionScoped != null) {
                        if (!Set.copyOf(codes).equals(actionScoped)) {
                            invalid.add(handler + "=" + codes + " (기대: " + actionScoped + ")");
                        }
                        continue;
                    }
                    if (codes.size() != 1 || !AdminPermissionCatalog.contains(codes.get(0))) {
                        invalid.add(handler + "=" + codes);
                        continue;
                    }
                    String required = codes.get(0);
                    if (required.endsWith("_READ") && !READ_ONLY_POST_MAPPINGS.contains(handler)) {
                        invalid.add(handler + "=읽기 권한으로 쓰기 허용(" + required + ")");
                    }
                    if ("Delete".equals(mapping.group(1))) {
                        boolean semanticUpdate = NON_DELETE_DELETE_MAPPINGS.contains(handler);
                        String expectedSuffix = semanticUpdate ? "_UPDATE" : "_DELETE";
                        if (!required.endsWith(expectedSuffix)) {
                            invalid.add(handler + "=" + required + " (기대: " + expectedSuffix + ")");
                        }
                    }
                }
            }
        }

        assertThat(missing).as("쓰기 핸들러의 메서드 권한 선언 누락").isEmpty();
        assertThat(invalid).as("쓰기 핸들러의 exact 권한 계약 위반").isEmpty();
    }
}
