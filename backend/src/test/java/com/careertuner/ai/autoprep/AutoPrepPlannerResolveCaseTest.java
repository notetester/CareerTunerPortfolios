package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.interview.service.InterviewLlmGateway;

/**
 * #3 회사고정 완화 검증. resolveCase 가 "회사 모호(query 미언급)"일 때 멋대로 최근 건을 고르지 않고,
 * 1건이면 자동선택·2건 이상이면 null(되묻기) 인지 박는다. query=null 이라 LLM(parseIntent) 미호출.
 */
@ExtendWith(MockitoExtension.class)
class AutoPrepPlannerResolveCaseTest {

    @Mock
    InterviewLlmGateway llmGateway;
    @Mock
    ApplicationCaseService caseService;
    @InjectMocks
    AutoPrepPlanner planner;

    /** query=null → parseIntent 빈 의도(company 모호). caseId/mode 미지정. */
    private static AutoPrepRequest ambiguous() {
        return new AutoPrepRequest(null, null, null, null, null);
    }

    @DisplayName("회사 모호 + 0건 → caseId null")
    @Test
    void ambiguous_zeroCase_null() {
        when(caseService.list(1L, null, false)).thenReturn(List.of());
        PrepPlan plan = planner.plan(1L, ambiguous());
        assertThat(plan.slots().applicationCaseId()).isNull();
    }

    @DisplayName("회사 모호 + 1건 → 그 건 자동선택")
    @Test
    void ambiguous_oneCase_autoPick() {
        ApplicationCaseResponse c = mock(ApplicationCaseResponse.class);
        when(c.id()).thenReturn(5L);
        when(c.companyName()).thenReturn("네이버");
        when(c.jobTitle()).thenReturn("백엔드");
        when(caseService.list(1L, null, false)).thenReturn(List.of(c));

        PrepPlan plan = planner.plan(1L, ambiguous());
        assertThat(plan.slots().applicationCaseId()).isEqualTo(5L);
    }

    @DisplayName("회사 모호 + 2건 이상 → null(되묻기, 최근 건 안 고름)")
    @Test
    void ambiguous_multiCase_null() {
        ApplicationCaseResponse c1 = mock(ApplicationCaseResponse.class);
        ApplicationCaseResponse c2 = mock(ApplicationCaseResponse.class);
        when(caseService.list(1L, null, false)).thenReturn(List.of(c1, c2));

        PrepPlan plan = planner.plan(1L, ambiguous());
        assertThat(plan.slots().applicationCaseId()).isNull();
    }
}
