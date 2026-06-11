package com.careertuner.interview.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 면접 RAG 설정. Qdrant 미기동/미설정 환경에서도 앱이 정상 동작하도록 기본 enabled=false.
 * Qdrant 를 띄우고 enabled=true 로 켜면 검색 근거가 평가/질문에 주입된다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.interview.rag")
public class InterviewRagProperties {

    private boolean enabled = false;
    private String qdrantUrl = "http://localhost:6333";
    private String collection = "interview_knowledge";
    private String embeddingModel = "text-embedding-3-small";
    private int dimension = 1536;
    private int topK = 4;
}
