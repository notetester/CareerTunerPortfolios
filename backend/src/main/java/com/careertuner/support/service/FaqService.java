package com.careertuner.support.service;

import java.util.List;

import com.careertuner.support.dto.FaqResponse;

public interface FaqService {

    List<FaqResponse> getFaqs(String category);
}
