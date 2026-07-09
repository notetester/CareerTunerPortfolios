package com.careertuner.company.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.careertuner.company.domain.CompanyProfile;

@Mapper
public interface CompanyProfileMapper {

    void insert(CompanyProfile profile);

    CompanyProfile findByUserId(Long userId);
}
