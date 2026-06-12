package com.careertuner.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.domain.LearningPlan;
import com.careertuner.analysis.domain.LearningPlanTask;
import com.careertuner.analysis.dto.LearningPlanRequest;
import com.careertuner.analysis.dto.LearningPlanTaskRequest;
import com.careertuner.analysis.mapper.CareerPlanMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

class CareerPlanServiceImplTest {

    @Test
    void rejectsLearningPlanWhenEndDatePrecedesStartDate() {
        CareerPlanServiceImpl service = new CareerPlanServiceImpl(mock(CareerPlanMapper.class));

        assertThatThrownBy(() -> service.createPlan(1L, new LearningPlanRequest(
                "TypeScript 계획", "TypeScript", LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 10))))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void createsTaskOnlyForOwnedPlanAndAssignsNextOrder() {
        CareerPlanMapper mapper = mock(CareerPlanMapper.class);
        CareerPlanServiceImpl service = new CareerPlanServiceImpl(mapper);
        LearningPlan plan = LearningPlan.builder().id(10L).userId(1L).title("계획").targetSkill("AWS").status("ACTIVE").build();
        LearningPlanTask existing = LearningPlanTask.builder().id(20L).learningPlanId(10L).task("기존 과제").sortOrder(1).build();
        LearningPlanTask created = LearningPlanTask.builder().id(21L).learningPlanId(10L).task("배포 실습").sortOrder(2).build();

        when(mapper.findPlan(1L, 10L)).thenReturn(plan);
        when(mapper.findTasks(10L)).thenReturn(java.util.List.of(existing));
        doAnswer(invocation -> {
            LearningPlanTask task = invocation.getArgument(0);
            task.setId(21L);
            return null;
        }).when(mapper).insertTask(any(LearningPlanTask.class));
        when(mapper.findTask(10L, 21L)).thenReturn(created);

        var response = service.createTask(1L, 10L, new LearningPlanTaskRequest(" 배포 실습 ", null));

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.sortOrder()).isEqualTo(2);
        verify(mapper).findPlan(1L, 10L);
    }

    @Test
    void rejectsTaskCreationForAnotherUsersPlan() {
        CareerPlanMapper mapper = mock(CareerPlanMapper.class);
        CareerPlanServiceImpl service = new CareerPlanServiceImpl(mapper);
        when(mapper.findPlan(1L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.createTask(1L, 99L, new LearningPlanTaskRequest("과제", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
