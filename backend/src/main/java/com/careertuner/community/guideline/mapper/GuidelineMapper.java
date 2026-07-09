package com.careertuner.community.guideline.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.community.guideline.dto.GuidelineResponse;

@Mapper
public interface GuidelineMapper {

    GuidelineResponse findPublished();
}
