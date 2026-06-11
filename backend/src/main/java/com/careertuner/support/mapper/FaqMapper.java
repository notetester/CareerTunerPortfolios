package com.careertuner.support.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.support.domain.Faq;

@Mapper
public interface FaqMapper {

    List<Faq> findAll(@Param("category") String category);
}
