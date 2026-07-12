package com.careertuner.jobposting.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.jobposting.domain.JobPosting;

@Mapper
public interface JobPostingMapper {

    int nextRevisionForCase(@Param("applicationCaseId") Long applicationCaseId);

    void insertJobPosting(JobPosting jobPosting);

    JobPosting findJobPostingByIdAndCaseId(@Param("id") Long id, @Param("applicationCaseId") Long applicationCaseId);

    JobPosting findLatestJobPostingByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    List<JobPosting> findJobPostingRevisionsByCaseId(@Param("applicationCaseId") Long applicationCaseId);
}
