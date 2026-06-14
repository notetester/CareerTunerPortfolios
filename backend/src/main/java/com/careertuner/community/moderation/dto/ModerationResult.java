package com.careertuner.community.moderation.dto;

public record ModerationResult(boolean toxic, String category, double confidence) {}
