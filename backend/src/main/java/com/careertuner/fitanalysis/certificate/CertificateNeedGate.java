package com.careertuner.fitanalysis.certificate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 자격증 전략을 켤지 결정하는 <b>결정론적 게이트</b>. 기본값은 <b>OFF</b> — CareerTuner 는 맞춤 취업전략이지
 * 자격증 추천 AI 가 아니므로, 아래 신호 중 하나라도 명확히 성립할 때만 자격증 전략을 활성화한다.
 *
 * <ol>
 *   <li>{@code POSTING_NAMES_CERTIFICATE} — 공고(필수/우대/업무)에 특정 자격증이 명시됨</li>
 *   <li>{@code LICENSED_JOB} — 자격증이 법·제도적으로 강하게 요구되는 면허형 직무</li>
 *   <li>{@code GAP_CERTIFIABLE} — 부족 역량이 자격증으로 객관화하기 좋음</li>
 *   <li>{@code HELD_CERT_RELEVANT} — 사용자 보유 자격증이 공고 맥락에서 강점</li>
 *   <li>{@code USER_REQUESTED} — 사용자가 자격증 전략을 명시 요청(호출부 플래그)</li>
 * </ol>
 *
 * <p>게이트 OFF 면 상위(규칙엔진)는 자격증 목록·일정 컨텍스트를 만들거나 프롬프트에 주입하지 않는다
 * (프롬프트에 자격증 목록이 들어가면 3B 가 과대반영하므로 원천 차단). 게이트 ON 이어도 신호 강도에 따라
 * {@link CertificateStrategyStatus} 를 낮춰(OPTIONAL_LOW_PRIORITY 등) 과추천을 막는다.
 *
 * <p>설계 원칙: <b>보수적 기본 OFF</b>. 놓침(필요할 수도 있는데 OFF)은 허용하지만, 헛발화(불필요한데 ON →
 * 3B 과대반영)를 피한다. 순수 함수이므로 자격증 필요 판단은 규칙 소유이고 모델은 설명만 한다(뉴로-심볼릭).
 */
public final class CertificateNeedGate {

    private CertificateNeedGate() {
    }

    /** 게이트 결정: 활성 여부 + 자격증 전략 status + 발화한 신호 코드(설명/로그용). */
    public record Decision(boolean active, CertificateStrategyStatus status, List<String> triggeredSignals) {
    }

    // 공고 텍스트에서 '자격증 명시'로 볼 명확한 자격증 이름 토큰(IT + 대표 면허형). 부분 일치(소문자).
    private static final List<String> CERT_NAME_TOKENS = List.of(
            "정보처리기사", "정보처리산업기사", "정보보안기사", "빅데이터분석기사", "정보통신기사",
            "sqld", "sqlp", "adsp", "adp", "리눅스마스터", "네트워크관리사", "컴퓨터활용능력",
            "aws certified", "solutions architect", "toeic", "opic", "cppg", "cissp", "정보처리기능사",
            "전기기사", "전기산업기사", "소방설비기사", "건축기사", "토목기사", "산업안전기사", "위험물산업기사");

    // 국가기술자격 등급 접미사(앞에 한글 2자 이상이 붙은 'OO기술사/기능장/산업기사/기능사'만 인정 → 오탐 최소화).
    // 바 '기사' 단독은 오탐(예: 복사기 사용)이 많아 제외하고, 명시 이름은 CERT_NAME_TOKENS 로 커버한다.
    private static final Pattern NATIONAL_TECH_GRADE =
            Pattern.compile("[가-힣]{2,}(기술사|기능장|산업기사|기능사)");

    // 자격증이 면허/법정요건인 직무(강한 필요). IT 일반 직무에서 발화하지 않도록 좁게 유지.
    private static final List<String> LICENSED_JOB_TOKENS = List.of(
            "전기공사", "소방", "건축사", "토목", "산업안전", "위험물", "가스기사", "환경기사",
            "세무사", "회계사", "변리사", "감정평가", "공인중개", "간호사", "약사");

    // 부족 역량 → 자격증으로 객관화하기 좋은 토큰(부분 일치). 보수적으로 IT 핵심만.
    private static final List<String> CERT_OBJECTIFIABLE_SKILL_TOKENS = List.of(
            "sql", "데이터분석", "빅데이터", "정보보안", "리눅스", "네트워크", "정보처리");

    /**
     * @param requiredSkills      공고 필수 역량
     * @param preferredSkills     공고 우대 역량
     * @param duties              담당 업무
     * @param jobTitle            직무명
     * @param profileCertificates 지원자 보유 자격증
     * @param missingSkills       규칙엔진이 계산한 부족 역량(필수+우대 미매칭)
     * @param userRequested       사용자가 자격증 전략을 명시 요청했는지(없으면 {@code false})
     */
    public static Decision evaluate(List<String> requiredSkills, List<String> preferredSkills,
                                    String duties, String jobTitle,
                                    List<String> profileCertificates, List<String> missingSkills,
                                    boolean userRequested) {
        List<String> signals = new ArrayList<>();

        String postingText = lower(join(requiredSkills) + " " + join(preferredSkills) + " " + safe(duties));
        boolean postingNamesCert = containsAny(postingText, CERT_NAME_TOKENS)
                || NATIONAL_TECH_GRADE.matcher(postingText).find();
        if (postingNamesCert) {
            signals.add("POSTING_NAMES_CERTIFICATE");
        }

        String jobText = lower(safe(jobTitle) + " " + safe(duties));
        boolean licensedJob = containsAny(jobText, LICENSED_JOB_TOKENS);
        if (licensedJob) {
            signals.add("LICENSED_JOB");
        }

        boolean gapCertifiable = anyContainsToken(missingSkills, CERT_OBJECTIFIABLE_SKILL_TOKENS);
        if (gapCertifiable) {
            signals.add("GAP_CERTIFIABLE");
        }

        boolean heldCertRelevant = heldCertMatchesPosting(profileCertificates, postingText);
        if (heldCertRelevant) {
            signals.add("HELD_CERT_RELEVANT");
        }

        if (userRequested) {
            signals.add("USER_REQUESTED");
        }

        boolean active = !signals.isEmpty();
        CertificateStrategyStatus status = decideStatus(active, postingNamesCert, licensedJob,
                gapCertifiable, heldCertRelevant);
        return new Decision(active, status, List.copyOf(signals));
    }

    /** 신호 강도로 status 결정 — 강한 근거(공고 명시/면허)면 강하게, 약하면(요청만) 후순위로. */
    private static CertificateStrategyStatus decideStatus(boolean active, boolean postingNamesCert,
            boolean licensedJob, boolean gapCertifiable, boolean heldCertRelevant) {
        if (!active) {
            return CertificateStrategyStatus.NOT_NEEDED;
        }
        if (postingNamesCert || licensedJob) {
            return CertificateStrategyStatus.REQUIRED_OR_STRONGLY_PREFERRED;
        }
        if (gapCertifiable) {
            return CertificateStrategyStatus.RECOMMENDED;
        }
        if (heldCertRelevant) {
            return CertificateStrategyStatus.USE_EXISTING_AS_STRENGTH;
        }
        // 사용자 요청만 있고 객관적 근거가 약함 → 후순위(과추천 방지).
        return CertificateStrategyStatus.OPTIONAL_LOW_PRIORITY;
    }

    /** 보유 자격증명이 공고 텍스트에 등장하면 관련 있다고 본다(≥2자, 부분 일치). */
    private static boolean heldCertMatchesPosting(List<String> heldCerts, String postingText) {
        if (heldCerts == null || postingText.isBlank()) {
            return false;
        }
        for (String cert : heldCerts) {
            if (cert == null) {
                continue;
            }
            String c = cert.trim().toLowerCase(Locale.ROOT);
            if (c.length() >= 2 && postingText.contains(c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyContainsToken(List<String> values, List<String> tokens) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String v = value.toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (v.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String join(List<String> values) {
        return values == null ? "" : String.join(" ", values.stream().filter(v -> v != null).toList());
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
