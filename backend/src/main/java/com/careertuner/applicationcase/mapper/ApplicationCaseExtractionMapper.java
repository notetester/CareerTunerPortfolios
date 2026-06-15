package com.careertuner.applicationcase.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.applicationcase.domain.ApplicationCaseExtraction;

@Mapper
public interface ApplicationCaseExtractionMapper {

    void insertApplicationCaseExtraction(ApplicationCaseExtraction extraction);

    List<ApplicationCaseExtraction> findActiveExtractionsByUserId(@Param("userId") Long userId);

    ApplicationCaseExtraction findLatestExtractionByApplicationCaseId(@Param("applicationCaseId") Long applicationCaseId);

    List<ApplicationCaseExtraction> findLatestExtractionsByApplicationCaseIdsAndUserId(
            @Param("userId") Long userId,
            @Param("applicationCaseIds") List<Long> applicationCaseIds);

    List<ApplicationCaseExtraction> findStaleRunningExtractions(@Param("startedBefore") LocalDateTime startedBefore,
                                                                @Param("limit") int limit);

    List<ApplicationCaseExtraction> findQueuedExtractions(@Param("limit") int limit);

    ApplicationCaseExtraction findRunningExtractionForUpdate(@Param("id") Long id);

    int claimQueuedExtraction(@Param("id") Long id);

    int markExtractionSucceeded(@Param("id") Long id, @Param("jobPostingId") Long jobPostingId);

    int markExtractionFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    int countActiveExtractionsByApplicationCaseId(@Param("applicationCaseId") Long applicationCaseId);
}
