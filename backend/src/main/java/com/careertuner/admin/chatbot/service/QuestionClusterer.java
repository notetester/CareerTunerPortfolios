package com.careertuner.admin.chatbot.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.careertuner.admin.chatbot.dto.UnansweredRow;
import com.careertuner.support.chatbot.CosineSimilarity;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 미응답 질문을 임베딩 코사인 유사도로 의미 군집화(운영 패널 3단계-1).
 * 그리디 단일패스: 각 행을 기존 군집 대표 벡터와 비교해 임계 이상이면 합치고, 아니면 새 군집.
 * <p>임베딩이 없는 행(수집 시 직렬화 실패/스키마 미적용 구간 등)은 question_norm 정확매칭으로 폴백.
 */
@Component
@RequiredArgsConstructor
public class QuestionClusterer {

    private static final Logger log = LoggerFactory.getLogger(QuestionClusterer.class);

    /** 같은 군집으로 볼 최소 코사인 유사도. 보수적으로 잡아 과병합(서로 다른 주제 묶임)을 막는다. */
    private static final double CLUSTER_THRESHOLD = 0.80;

    private final ObjectMapper objectMapper;

    /** 군집 1개 = 멤버 행 + 대표 벡터(첫 멤버). */
    private static final class Bucket {
        final double[] centroid;
        final List<UnansweredRow> members = new ArrayList<>();
        Bucket(double[] centroid) { this.centroid = centroid; }
    }

    /**
     * 행 목록을 군집으로 묶어 반환. 입력은 최신순 가정(대표 tie-break 안정화).
     * @return 각 군집의 멤버 행 리스트(군집 간 순서는 무의미 — 정렬은 호출부).
     */
    public List<List<UnansweredRow>> cluster(List<UnansweredRow> rows) {
        List<Bucket> buckets = new ArrayList<>();
        // 임베딩 없는 행은 정확매칭 폴백 — norm → 군집.
        Map<String, List<UnansweredRow>> normFallback = new LinkedHashMap<>();

        for (UnansweredRow row : rows) {
            double[] vec = parse(row.getEmbedding());
            if (vec == null) {
                normFallback.computeIfAbsent(
                        row.getQuestionNorm() == null ? "" : row.getQuestionNorm(),
                        k -> new ArrayList<>()).add(row);
                continue;
            }
            Bucket best = null;
            double bestSim = CLUSTER_THRESHOLD;
            for (Bucket b : buckets) {
                if (b.centroid.length != vec.length) {
                    continue;
                }
                double sim = CosineSimilarity.compute(vec, b.centroid);
                if (sim >= bestSim) {
                    bestSim = sim;
                    best = b;
                }
            }
            if (best == null) {
                Bucket nb = new Bucket(vec);
                nb.members.add(row);
                buckets.add(nb);
            } else {
                best.members.add(row);
            }
        }

        List<List<UnansweredRow>> result = new ArrayList<>();
        for (Bucket b : buckets) {
            result.add(b.members);
        }
        result.addAll(normFallback.values());
        return result;
    }

    private double[] parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            return arr.length == 0 ? null : arr;
        } catch (Exception e) {
            log.warn("미응답 질문 임베딩 파싱 실패(정확매칭 폴백): {}", e.getMessage());
            return null;
        }
    }
}
