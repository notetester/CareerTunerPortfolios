package com.careertuner.admin.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.securityops.controller.AdminBlockController;
import com.careertuner.admin.securityops.controller.AdminLoginRiskController;
import com.careertuner.admin.securityops.controller.AdminSecurityOpsController;

class AdminPermissionDeclarationContractTest {

    private static final Pattern ADMIN_MAPPING = Pattern.compile(
            "@RequestMapping\\(\\s*\\\"/api/admin[^\\\"]*\\\"\\s*\\)");
    private static final Set<String> ROLE_ONLY_CLASSES = Set.of(
            "AdminMeController.java", "AdminHomeController.java",
            "AdminDashboardController.java", "AdminPendingCountController.java");
    private static final Set<String> CATALOG = Set.of(
            "USER_READ", "USER_STATUS_WRITE", "PROFILE_READ", "CONSENT_READ",
            "AI_USAGE_READ", "SECURITY_LOG_READ", "POLICY_MANAGE", "ADMIN_PERMISSION_MANAGE",
            "MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN", "POLICY_ADMIN",
            "BLOCK_MANAGE", "EMAIL_AUDIT_READ", "ADMIN_AUDIT_READ", "BILLING_READ", "BILLING_WRITE",
            "CONTENT_MANAGE", "AI_OPERATION_MANAGE", "ANALYSIS_READ", "INTERVIEW_READ");
    private static final Pattern PERMISSION_ANNOTATION = Pattern.compile(
            "RequireAdminPermission\\s*\\(\\s*\\{([^}]*)}");
    private static final Pattern QUOTED_CODE = Pattern.compile("\\\"([A-Z_]+)\\\"");
    private static final Set<String> SECURITY_READ_ONLY_CODES = Set.of(
            "SECURITY_LOG_READ", "ADMIN_AUDIT_READ", "EMAIL_AUDIT_READ", "AUDIT_ADMIN");
    private static final Set<String> SECURITY_WRITE_CODES = Set.of("BLOCK_MANAGE", "POLICY_MANAGE");
    private static final Set<Class<?>> SECURITY_MUTATION_CONTROLLERS = Set.of(
            AdminSecurityOpsController.class, AdminLoginRiskController.class, AdminBlockController.class);

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

        assertThat(declared).isNotEmpty().isSubsetOf(CATALOG);
    }

    @Test
    void securityMutationHandlersRequireDedicatedWritePermission() {
        List<String> missing = new ArrayList<>();
        List<String> readPermissionLeaks = new ArrayList<>();
        List<String> invalidWritePolicies = new ArrayList<>();

        for (Class<?> controller : SECURITY_MUTATION_CONTROLLERS) {
            for (var method : controller.getDeclaredMethods()) {
                if (!isMutationHandler(method)) {
                    continue;
                }
                String handler = controller.getSimpleName() + "#" + method.getName();
                RequireAdminPermission required = method.getAnnotation(RequireAdminPermission.class);
                if (required == null || required.value().length == 0) {
                    missing.add(handler);
                    continue;
                }
                Set<String> codes = Set.copyOf(Arrays.asList(required.value()));
                if (!java.util.Collections.disjoint(codes, SECURITY_READ_ONLY_CODES)) {
                    readPermissionLeaks.add(handler + "=" + codes);
                }
                if (java.util.Collections.disjoint(codes, SECURITY_WRITE_CODES)) {
                    invalidWritePolicies.add(handler + "=" + codes);
                }
            }
        }

        assertThat(missing).as("쓰기 핸들러의 메서드 권한 선언 누락").isEmpty();
        assertThat(readPermissionLeaks).as("읽기 전용 권한의 쓰기 API 허용").isEmpty();
        assertThat(invalidWritePolicies).as("쓰기 전용 권한 코드 누락").isEmpty();
    }

    private static boolean isMutationHandler(java.lang.reflect.Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
