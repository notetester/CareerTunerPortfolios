package com.careertuner.support.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.support.domain.Notice;

@Mapper
public interface NoticeMapper {

    List<Notice> findAllPublished();

    Notice findById(@Param("id") Long id);

    void incrementViewCount(@Param("id") Long id);
}
