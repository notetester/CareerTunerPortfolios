package com.careertuner.credit.service;

import java.util.List;

import com.careertuner.credit.dto.CreditProductResponse;

public interface CreditProductService {

    List<CreditProductResponse> listEnabledProducts();
}
