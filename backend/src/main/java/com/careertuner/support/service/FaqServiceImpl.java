package com.careertuner.support.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.support.dto.FaqResponse;
import com.careertuner.support.mapper.FaqMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqServiceImpl implements FaqService {

    private final FaqMapper faqMapper;

    @Override
    public List<FaqResponse> getFaqs(String category) {
        String cat = (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category))
                ? category.toUpperCase()
                : null;
        return faqMapper.findAll(cat).stream()
                .map(f -> new FaqResponse(f.getId(), f.getCategory().toLowerCase(), f.getQuestion(), f.getAnswer()))
                .toList();
    }
}
