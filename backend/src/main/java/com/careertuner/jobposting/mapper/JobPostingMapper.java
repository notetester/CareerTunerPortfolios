package com.careertuner.jobposting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.jobposting.domain.JobPosting;

@Mapper
public interface JobPostingMapper {

    void deleteJobPostingsByCaseId(@Param("applicationCaseId") Long applicationCaseId);

    void insertJobPosting(JobPosting jobPosting);

    JobPosting findLatestJobPostingByCaseId(@Param("applicationCaseId") Long applicationCaseId);
}
