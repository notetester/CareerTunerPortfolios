package com.careertuner.catalog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.catalog.dto.CatalogDtos.CertDetail;
import com.careertuner.catalog.dto.CatalogDtos.CertSearchItem;
import com.careertuner.catalog.dto.CatalogDtos.ExamSchedule;
import com.careertuner.catalog.dto.CatalogDtos.NcsSearchItem;

/** NCS·자격증 카탈로그 조회(읽기 전용). */
@Mapper
public interface CatalogMapper {

    List<NcsSearchItem> searchNcs(@Param("q") String q, @Param("limit") int limit);

    /** 상세 조회는 요약 + detail_json 을 각각 읽어 service 가 units 로 파싱한다. */
    NcsSearchItem findNcsSummary(@Param("id") Long id);

    String findNcsDetailJson(@Param("id") Long id);

    List<CertSearchItem> searchCertificates(@Param("q") String q, @Param("type") String type,
                                            @Param("limit") int limit);

    CertDetail findCertificateById(@Param("id") Long id);

    List<ExamSchedule> findExamSchedules(@Param("certName") String certName);
}
