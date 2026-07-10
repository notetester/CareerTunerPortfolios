package com.careertuner.fitanalysis.certificate;

import java.util.List;
import java.util.Locale;

/**
 * 희망직무(desiredJob) → 자격증 후보 매핑(결정론 카탈로그). 규칙엔진의 공고별 추천(MockFitAnalysisAiService)과
 * 장기 커리어 전략이 <b>같은 소스</b>를 쓰도록 한곳에 둔다(두 경로의 후보 불일치 방지).
 *
 * <p>이 카탈로그는 후보 이름의 whitelist 일 뿐 일정/취득난이도 정보가 아니다 — 일정은 근거 조회(provider) 확인
 * 전까지 말하지 않는다. 자격증은 보조 전략이므로 후보가 있어도 프로젝트/실무경험 우선 원칙이 앞선다.
 */
public final class CertificateCareerCatalog {

    private CertificateCareerCatalog() {
    }

    /** 희망직무 기준 자격증 후보(없으면 일반 개발 기본). */
    public static List<String> candidatesFor(String desiredJob) {
        String job = desiredJob == null ? "" : desiredJob.toLowerCase(Locale.ROOT);
        if (job.contains("데이터") || job.contains("data") || job.contains("ml") || job.contains("ai")) {
            return List.of("SQLD", "ADsP", "빅데이터분석기사");
        }
        if (job.contains("클라우드") || job.contains("cloud") || job.contains("devops") || job.contains("인프라")) {
            return List.of("AWS Solutions Architect Associate", "정보처리기사", "리눅스마스터");
        }
        if (job.contains("보안") || job.contains("security")) {
            return List.of("정보보안기사", "정보처리기사", "CPPG");
        }
        // 일반 개발 직무 기본 후보.
        return List.of("정보처리기사", "SQLD");
    }
}
