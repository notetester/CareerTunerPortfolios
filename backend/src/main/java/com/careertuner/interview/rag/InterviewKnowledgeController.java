package com.careertuner.interview.rag;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.interview.domain.InterviewKnowledge;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 면접 RAG 지식베이스 관리. */
@RestController
@RequestMapping("/api/admin/interview/knowledge")
@RequiredArgsConstructor
public class InterviewKnowledgeController {

    private final InterviewKnowledgeService service;

    @PostMapping
    public ApiResponse<InterviewKnowledge> add(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody AddKnowledgeRequest request) {
        return ApiResponse.ok(service.addDocument(authUser, request.kind(), request.title(),
                request.content(), request.source()));
    }

    @GetMapping
    public ApiResponse<List<InterviewKnowledge>> list(@AuthenticationPrincipal AuthUser authUser,
                                                      @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.list(authUser, limit));
    }

    @PostMapping("/reindex")
    public ApiResponse<Map<String, Integer>> reindex(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(Map.of("reindexed", service.reindexAll(authUser)));
    }
}
