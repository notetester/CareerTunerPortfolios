package com.careertuner.dashboard.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUserSource {

    private Long id;
    private String name;
    private String plan;
    private int credit;
}
