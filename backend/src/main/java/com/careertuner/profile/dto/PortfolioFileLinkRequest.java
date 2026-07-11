package com.careertuner.profile.dto;

import java.util.List;

/** 기존에 업로드된 PORTFOLIO 파일을 현재 사용자 프로필에 입양하는 요청. */
public record PortfolioFileLinkRequest(List<Long> fileIds) {
}
