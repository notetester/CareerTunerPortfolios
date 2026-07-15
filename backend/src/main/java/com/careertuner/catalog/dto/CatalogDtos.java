package com.careertuner.catalog.dto;

import java.util.List;

/**
 * NCS·자격증 카탈로그 검색/조회 DTO 모음.
 * 검색 결과는 요약(summary), 상세는 전체 필드 + (NCS)중첩 구조 / (자격)시험일정을 담는다.
 */
public final class CatalogDtos {

    private CatalogDtos() {}

    /** NCS 세분류 검색 결과 요약. */
    public record NcsSearchItem(
            Long id, String ncsCode, String majorName, String middleName, String minorName,
            String subName, Integer unitCount, Integer elementCount, Integer minLevel, Integer maxLevel) {}

    /** NCS 세분류 상세(units 는 detail_json 을 파싱한 구조). */
    public record NcsDetail(
            Long id, String ncsCode, String majorName, String middleName, String minorName,
            String subName, Integer unitCount, Integer elementCount, Integer minLevel, Integer maxLevel,
            Object units) {}

    /** 자격증 검색 결과 요약(설명은 스니펫). */
    public record CertSearchItem(
            Long id, String certType, String name, String grade, String authority,
            String official, Integer hasSchedule, String descriptionSnippet) {}

    /** 자격증 상세(DB row). */
    public record CertDetail(
            Long id, String certType, String name, String grade, String authority, String issuerOrg,
            String series, String jmCd, String regNo, String official, String status,
            String description, String ncsSubName, Integer hasSchedule) {}

    /** 자격증 상세 응답 = row + (국가)시험일정. */
    public record CertDetailResponse(CertDetail certificate, List<ExamSchedule> schedules) {}

    /** 국가 시험일정 회차. */
    public record ExamSchedule(
            String certName, Integer year, String roundName,
            String docRegStart, String docRegEnd, String docExam, String docPass,
            String pracExamStart, String pracExamEnd, String pracPass) {}
}
