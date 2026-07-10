package com.careertuner.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.planner.domain.PlannerMemo;
import com.careertuner.planner.dto.PlannerMemoRequest;
import com.careertuner.planner.mapper.PlannerMapper;

import tools.jackson.databind.ObjectMapper;

class PlannerServiceImplTest {

    private final PlannerMapper plannerMapper = Mockito.mock(PlannerMapper.class);
    private final PlannerServiceImpl service = new PlannerServiceImpl(plannerMapper, new ObjectMapper());

    @Test
    void createMemoRejectsFitAnalysisFromAnotherApplicationCase() {
        when(plannerMapper.countApplicationCase(1L, 10L)).thenReturn(1);
        when(plannerMapper.countFitAnalysis(1L, 20L, 10L)).thenReturn(0);

        PlannerMemoRequest request = new PlannerMemoRequest(
                "면접 준비", "적합도 분석 기반 액션", "yellow", false, false, 1.0, 10L, 20L);

        assertThatThrownBy(() -> service.createMemo(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));

        verify(plannerMapper, never()).insertMemo(Mockito.any(PlannerMemo.class));
    }
}
