package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * 적합도 분석 AI의 mock 구현 (API 키 미발급 단계 기본 동작).
 *
 * <p>외부 호출 없이 공고 필수/우대 역량과 프로필 보유 기술을 비교해 결정적(deterministic) 결과를 만든다.
 * 같은 입력은 항상 같은 결과를 주므로 화면/관리자 통계 흐름을 그대로 검증할 수 있다.
 * 실제 LLM 연동은 {@link FitAnalysisAiService} 문서의 교체 절차를 따른다.
 */
@Service
public class MockFitAnalysisAiService implements FitAnalysisAiService {

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        List<String> required = clean(command.requiredSkills());
        List<String> preferred = clean(command.preferredSkills());
        List<String> profile = clean(command.profileSkills());
        Set<String> profileLower = lower(profile);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (required.isEmpty()) {
            // 공고 분석 전이라면 기본 가이드 역량으로 안내한다.
            missing.addAll(List.of("공고 분석 먼저 실행", "필수 역량 확인 필요"));
        } else if (profile.isEmpty()) {
            // 프로필 미입력: 절반 보유로 가정해 보완 방향을 보여준다.
            for (int i = 0; i < required.size(); i++) {
                (i % 2 == 0 ? matched : missing).add(required.get(i));
            }
        } else {
            for (String skill : required) {
                (profileLower.contains(skill.toLowerCase(Locale.ROOT)) ? matched : missing).add(skill);
            }
        }

        // 우대 역량 중 미보유는 장기 보완 항목으로 분류한다.
        for (String skill : preferred) {
            if (!profileLower.contains(skill.toLowerCase(Locale.ROOT)) && !missing.contains(skill)) {
                missing.add(skill);
            }
        }

        int fitScore = score(required, profileLower, profile.isEmpty(), matched.size());
        List<String> study = missing.stream().limit(4).map(skill -> skill + " 집중 학습").toList();
        List<String> certificates = recommendCertificates(command.desiredJob());
        String strategy = strategy(command, matched, missing, fitScore);

        return new FitAnalysisAiResult(fitScore, matched, missing, study, certificates, strategy);
    }

    private int score(List<String> required, Set<String> profileLower, boolean profileEmpty, int matchedSize) {
        if (required.isEmpty()) {
            return 0;
        }
        double ratio;
        if (profileEmpty) {
            ratio = 0.5;
        } else {
            long matchedRequired = required.stream()
                    .filter(skill -> profileLower.contains(skill.toLowerCase(Locale.ROOT)))
                    .count();
            ratio = (double) matchedRequired / required.size();
        }
        int base = (int) Math.round(45 + ratio * 50);
        if (matchedSize >= 3) {
            base += 3;
        }
        return Math.max(0, Math.min(100, base));
    }

    private List<String> recommendCertificates(String desiredJob) {
        String job = desiredJob == null ? "" : desiredJob.toLowerCase(Locale.ROOT);
        if (job.contains("데이터") || job.contains("data") || job.contains("ml") || job.contains("ai")) {
            return List.of("SQLD", "ADsP", "빅데이터분석기사");
        }
        if (job.contains("클라우드") || job.contains("cloud") || job.contains("devops") || job.contains("인프라")) {
            return List.of("AWS Solutions Architect Associate", "정보처리기사", "리눅스마스터");
        }
        if (job.contains("보안") || job.contains("security")) {
            return List.of("정보보안기사", "정보처리기사", "CPPG");
        }
        // 일반 개발 직무 기본 추천.
        return List.of("정보처리기사", "SQLD");
    }

    private String strategy(FitAnalysisAiCommand command, List<String> matched, List<String> missing, int fitScore) {
        String company = command.companyName() == null ? "지원 기업" : command.companyName();
        String job = command.jobTitle() == null ? "해당 직무" : command.jobTitle();
        StringBuilder sb = new StringBuilder();
        sb.append("%s %s 지원 적합도는 %d점입니다. ".formatted(company, job, fitScore));
        if (!matched.isEmpty()) {
            sb.append("강점인 %s 경험을 지원서와 면접에서 우선 강조하세요. "
                    .formatted(String.join(", ", matched.subList(0, Math.min(3, matched.size())))));
        }
        if (!missing.isEmpty()) {
            sb.append("부족한 %s 은(는) 단기 프로젝트나 학습으로 빠르게 보완하는 것이 좋습니다. "
                    .formatted(String.join(", ", missing.subList(0, Math.min(3, missing.size())))));
        }
        if (fitScore >= 70) {
            sb.append("현재 수준으로도 지원 가능성이 높으니 마감 전 지원을 권장합니다.");
        } else if (fitScore >= 50) {
            sb.append("핵심 부족 역량을 1~2개 보완하면 합격 가능성이 올라갑니다.");
        } else {
            sb.append("기본 요구 역량을 먼저 채운 뒤 재분석하는 전략을 추천합니다.");
        }
        return sb.toString();
    }

    private List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private Set<String> lower(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
