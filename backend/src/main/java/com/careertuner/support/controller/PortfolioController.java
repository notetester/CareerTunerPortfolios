package com.careertuner.support.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.support.dto.GithubReadmeRequest;
import com.careertuner.support.dto.GithubReadmeResponse;
import com.careertuner.support.service.GithubReadmeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/support/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final GithubReadmeService githubReadmeService;

    @PostMapping("/github-readme")
    public ApiResponse<GithubReadmeResponse> fetchGithubReadme(@Validated @RequestBody GithubReadmeRequest request) {
        return ApiResponse.ok(githubReadmeService.fetchReadme(request.repoUrl()));
    }
}
