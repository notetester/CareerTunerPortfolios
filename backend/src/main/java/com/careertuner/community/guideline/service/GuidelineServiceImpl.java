package com.careertuner.community.guideline.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.community.guideline.dto.GuidelineResponse;
import com.careertuner.community.guideline.mapper.GuidelineMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuidelineServiceImpl implements GuidelineService {

    private final GuidelineMapper guidelineMapper;

    @Override
    public GuidelineResponse getPublishedGuideline() {
        return guidelineMapper.findPublished();
    }
}
