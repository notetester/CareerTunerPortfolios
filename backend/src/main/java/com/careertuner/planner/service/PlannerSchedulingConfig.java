package com.careertuner.planner.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "careertuner.planner.reminder.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PlannerSchedulingConfig {
}
