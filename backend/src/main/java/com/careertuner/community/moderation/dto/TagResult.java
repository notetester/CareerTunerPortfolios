package com.careertuner.community.moderation.dto;

import java.util.List;

public record TagResult(List<String> tags, double confidence) {}
