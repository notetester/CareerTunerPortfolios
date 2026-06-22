package com.careertuner.interview.rag;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.interview.domain.InterviewKnowledge;

@Mapper
public interface InterviewKnowledgeMapper {

    void insert(InterviewKnowledge knowledge);

    InterviewKnowledge findById(@Param("id") Long id);

    List<InterviewKnowledge> findAll(@Param("limit") int limit);

    void markIndexed(@Param("id") Long id);

    void update(InterviewKnowledge knowledge);

    void delete(@Param("id") Long id);
}
