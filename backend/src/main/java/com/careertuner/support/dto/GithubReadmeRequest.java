package com.careertuner.support.dto;

import jakarta.validation.constraints.NotBlank;

public record GithubReadmeRequest(@NotBlank String repoUrl) {}
