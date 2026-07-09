package com.careertuner.interview.training;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.interview.domain.InterviewTrainingSample;

@Mapper
public interface InterviewTrainingMapper {

    void insert(InterviewTrainingSample sample);

    List<InterviewTrainingSample> findAll(@Param("limit") int limit);

    long count();

    Double averageScore();
}
