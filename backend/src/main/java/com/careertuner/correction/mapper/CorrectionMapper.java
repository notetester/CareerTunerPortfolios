package com.careertuner.correction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.correction.domain.CorrectionRequest;

@Mapper
public interface CorrectionMapper {

    void insert(CorrectionRequest correctionRequest);

    CorrectionRequest findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    CorrectionRequest findByUserIdAndRequestKey(@Param("userId") Long userId,
                                                @Param("requestKey") String requestKey);

    List<CorrectionRequest> findByUserId(@Param("userId") Long userId,
                                         @Param("applicationCaseId") Long applicationCaseId,
                                         @Param("correctionType") String correctionType,
                                         @Param("limit") int limit);
}
