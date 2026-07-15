package com.careertuner.ai.autoprep.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AutoPrep 텍스트 공고 파일과 자동 생성 지원 건의 영속적인 1:1 연결을 관리한다. */
@Mapper
public interface AutoPrepCaseDedupeMapper {

    /** (사용자, 파일) 예약을 원자적으로 선점한다. 선점 성공 1, 이미 존재하면 0. */
    int insertReservation(@Param("userId") Long userId, @Param("fileId") Long fileId);

    /** 경쟁 트랜잭션이 확정한 지원 건을 current read로 조회한다. */
    Long findApplicationCaseIdForUpdate(@Param("userId") Long userId, @Param("fileId") Long fileId);

    /** 자신이 선점한 미확정 예약에 생성된 지원 건을 연결한다. */
    int bindApplicationCase(@Param("userId") Long userId,
                            @Param("fileId") Long fileId,
                            @Param("applicationCaseId") Long applicationCaseId);

    /** 텍스트를 읽지 못해 생성하지 않은 예약만 해제한다. */
    int deleteUnboundReservation(@Param("userId") Long userId, @Param("fileId") Long fileId);
}
