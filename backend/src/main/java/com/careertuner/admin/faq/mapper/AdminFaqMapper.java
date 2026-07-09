package com.careertuner.admin.faq.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.faq.dto.AdminFaqResponse;

@Mapper
public interface AdminFaqMapper {

    List<AdminFaqResponse> findAll();

    AdminFaqResponse findById(@Param("id") Long id);

    void insert(@Param("category") String category,
                @Param("question") String question,
                @Param("answer") String answer,
                @Param("published") boolean published,
                @Param("sortOrder") int sortOrder,
                @Param("adminId") Long adminId);

    void update(@Param("id") Long id,
                @Param("category") String category,
                @Param("question") String question,
                @Param("answer") String answer,
                @Param("published") boolean published,
                @Param("sortOrder") int sortOrder);

    void delete(@Param("id") Long id);

    Long lastInsertId();
}
