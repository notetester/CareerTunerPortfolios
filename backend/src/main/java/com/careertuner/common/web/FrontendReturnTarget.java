package com.careertuner.common.web;

/** 서버가 신뢰하는 프런트엔드 클라이언트와 그 정확한 origin의 조합. */
public record FrontendReturnTarget(String client, String baseUrl) {

    /** 사전에 정한 상대 경로를 현재 프런트엔드 origin에 붙인다. */
    public String absoluteUrl(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("Frontend return path must start with a single slash");
        }
        return baseUrl + path;
    }
}
