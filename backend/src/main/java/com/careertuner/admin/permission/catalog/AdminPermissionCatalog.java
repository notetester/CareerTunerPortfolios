package com.careertuner.admin.permission.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관리자 세부 권한의 코드 정본.
 *
 * <p>권한 코드는 {@code 도메인_행위} 형식으로만 정의한다. 감사 로그는 append-only 데이터이므로
 * READ만 제공한다. SUPER_ADMIN은 인터셉터에서 우회하고, 일반 ADMIN은 이 카탈로그 중 실제로
 * 부여된 코드만 사용할 수 있다.</p>
 */
public final class AdminPermissionCatalog {

    public enum Domain {
        USER,
        SECURITY,
        BILLING,
        CONTENT,
        AI,
        POLICY,
        ADMIN_PERMISSION,
        AUDIT
    }

    public enum Action {
        READ,
        CREATE,
        UPDATE,
        DELETE
    }

    public record Definition(
            String code,
            Domain domain,
            Action action,
            String displayName,
            String description,
            boolean adminAssignable) {
    }

    private static final List<Definition> DEFINITIONS = List.of(
            definition(Domain.USER, Action.READ, "회원 조회", "회원 목록·상세·로그인 이력 조회", true),
            definition(Domain.USER, Action.CREATE, "회원 생성", "관리자 콘솔에서 일반 회원 생성", true),
            definition(Domain.USER, Action.UPDATE, "회원 수정", "회원 상태·운영 속성 변경", true),
            definition(Domain.USER, Action.DELETE, "회원 삭제", "회원 계정 소프트 삭제", true),

            definition(Domain.SECURITY, Action.READ, "보안 조회", "보안 현황·규칙·위험 정책 조회", true),
            definition(Domain.SECURITY, Action.CREATE, "보안 항목 생성", "차단 규칙·검토·배치 생성", true),
            definition(Domain.SECURITY, Action.UPDATE, "보안 설정 수정", "차단·WAF·위험 정책·검토 상태 변경", true),
            definition(Domain.SECURITY, Action.DELETE, "보안 항목 삭제", "보안 항목 소프트 삭제", true),

            definition(Domain.BILLING, Action.READ, "결제 조회", "결제·환불·요금제·크레딧·리워드 조회", true),
            definition(Domain.BILLING, Action.CREATE, "결제 정책 생성", "요금제 변경안·리워드·쿠폰 생성", true),
            definition(Domain.BILLING, Action.UPDATE, "결제 정책 수정", "결제·환불·크레딧·리워드 상태 변경", true),
            definition(Domain.BILLING, Action.DELETE, "결제 정책 삭제", "결제·리워드 정책 소프트 삭제", true),

            definition(Domain.CONTENT, Action.READ, "콘텐츠 조회", "커뮤니티·공지·FAQ·문의·광고·협업 조회", true),
            definition(Domain.CONTENT, Action.CREATE, "콘텐츠 생성", "공지·FAQ·광고·캠페인 등 콘텐츠 생성", true),
            definition(Domain.CONTENT, Action.UPDATE, "콘텐츠 수정", "콘텐츠 상태·검토·게시·답변 변경", true),
            definition(Domain.CONTENT, Action.DELETE, "콘텐츠 삭제", "콘텐츠 소프트 삭제", true),

            definition(Domain.AI, Action.READ, "AI 운영 조회", "분석·면접·프롬프트·챗봇 운영 데이터 조회", true),
            definition(Domain.AI, Action.CREATE, "AI 운영 항목 생성", "프롬프트·지식·운영 메모·실행 생성", true),
            definition(Domain.AI, Action.UPDATE, "AI 운영 수정", "AI 설정·재실행·검토·색인 상태 변경", true),
            definition(Domain.AI, Action.DELETE, "AI 운영 항목 삭제", "AI 운영 항목 소프트 삭제", true),

            definition(Domain.POLICY, Action.READ, "운영 정책 조회", "시스템 정책·런타임 설정·직원 정책 조회", true),
            definition(Domain.POLICY, Action.CREATE, "운영 정책 생성", "운영 정책·설정 생성", true),
            definition(Domain.POLICY, Action.UPDATE, "운영 정책 수정", "운영 정책·설정·직원 정책 변경", true),
            definition(Domain.POLICY, Action.DELETE, "운영 정책 삭제", "운영 정책 소프트 삭제", true),

            definition(Domain.ADMIN_PERMISSION, Action.READ, "관리자 권한 조회", "관리자·권한·그룹·감사 내역 조회", false),
            definition(Domain.ADMIN_PERMISSION, Action.CREATE, "관리자 권한 생성", "사전 정의된 권한·그룹 배정 생성", false),
            definition(Domain.ADMIN_PERMISSION, Action.UPDATE, "관리자 권한 수정", "역할·권한·그룹·요청 상태 변경", false),
            definition(Domain.ADMIN_PERMISSION, Action.DELETE, "관리자 권한 회수", "권한·그룹·관리자 역할 회수", false),

            definition(Domain.AUDIT, Action.READ, "감사 로그 조회", "로그인·이메일·보안·관리자 행위 감사 조회", true)
    );

    private static final Map<String, Definition> BY_CODE = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(Definition::code, Function.identity()));
    private static final Set<String> CODES = BY_CODE.keySet();
    private static final List<String> ADMIN_ASSIGNABLE_CODES = DEFINITIONS.stream()
            .filter(Definition::adminAssignable)
            .map(Definition::code)
            .toList();
    private static final List<String> ALL_CODES = DEFINITIONS.stream().map(Definition::code).toList();

    private AdminPermissionCatalog() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Set<String> codes() {
        return CODES;
    }

    public static List<String> adminAssignableCodes() {
        return ADMIN_ASSIGNABLE_CODES;
    }

    public static List<String> allCodes() {
        return ALL_CODES;
    }

    public static boolean contains(String code) {
        return code != null && BY_CODE.containsKey(code);
    }

    public static Optional<Definition> find(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static String code(Domain domain, Action action) {
        return domain.name() + "_" + action.name();
    }

    private static Definition definition(Domain domain, Action action, String displayName,
                                         String description, boolean adminAssignable) {
        return new Definition(code(domain, action), domain, action, displayName, description, adminAssignable);
    }
}
