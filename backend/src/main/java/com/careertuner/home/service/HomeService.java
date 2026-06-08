package com.careertuner.home.service;

import com.careertuner.home.dto.HomeSummaryResponse;

public interface HomeService {

    HomeSummaryResponse getSummary(Long userId);
}
