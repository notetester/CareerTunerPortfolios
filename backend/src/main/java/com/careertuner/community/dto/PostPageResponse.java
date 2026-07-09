package com.careertuner.community.dto;

import java.util.List;

public record PostPageResponse(
        List<PostListResponse> posts,
        int total,
        int page,
        int size
) {}
