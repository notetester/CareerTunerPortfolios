package com.careertuner.interview.media;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.interview.domain.InterviewMediaAnalysis;

@Mapper
public interface InterviewMediaMapper {

    void insertMediaAnalysis(InterviewMediaAnalysis analysis);

    List<InterviewMediaAnalysis> findBySessionId(@Param("sessionId") Long sessionId);
}
