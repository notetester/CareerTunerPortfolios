package com.careertuner.support.chatbot;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ①커뮤니티 FAQ 와 ③LLM 인테이크를 한 입구에서 가르는 하이브리드 라우터(첫 턴 판정 전용).
 *
 * <p>Phase 0 실측 근거(2026-06-24):
 * <ul>
 *   <li>순수 임베딩 argmax 는 경계구역(|diff|&lt;0.1, 표본의 42%)에서 화행을 못 가려 오라우팅 → STOP.</li>
 *   <li>명확구역(|diff|≥0.1) 은 argmax 오류 0 → 결정적.</li>
 *   <li>경계구역만 qwen3:8b 화행분류(temp0/think=false) 1회 → 결정성 30/30, 정확도 29/30.</li>
 * </ul>
 * 따라서 명확구역은 임베딩만으로 결정적으로 가르고, 경계구역만 화행분류를 얹는다.</p>
 *
 * <p><b>범위:</b> "이 발화를 ① 로 보낼지 ③ 입구로 보낼지" 첫 턴 판정만 한다.
 * 인테이크 진입 후 머무는 sticky 모드·멀티턴은 이 클래스의 책임이 아니다(다음 PR).</p>
 */
@Component
public class UnifiedChatRouter {

    private static final Logger log = LoggerFactory.getLogger(UnifiedChatRouter.class);

    /** 라우팅 결과. */
    public enum Target {
        /** ① 커뮤니티 FAQ/에이전트 경로. */
        FAQ,
        /** ③ 인테이크 즉시 진입(명확구역 argmax 가 인테이크 우세). */
        INTAKE_DIRECT,
        /** 경계구역 COMMAND — 바로 진입 말고 확인 1턴(오분류 안전판). */
        INTAKE_CONFIRM,
        /** 약신호(둘 다 weakGate 미만) — FAQ도 의도도 불명확 → 정중한 되묻기로 끊는다. */
        FALLBACK
    }

    public record Decision(Target target, double faqScore, double intakeScore,
                           double diff, boolean boundary, String speechAct) {}

    /**
     * ③ 트리거 시드 발화. intakeScore = 사용자 발화와 이 시드들의 max 코사인.
     * 상수로 두어 추후 조정 가능(설정화는 필요 시 ai.chatbot.* 로 승격).
     */
    private static final List<String> SEEDS = List.of(
            "면접 준비해줘",
            "지원 준비 도와줘",
            "자소서 좀 봐줘",
            "이력서 첨삭해줘",
            "면접 예상질문 만들어줘");

    private final ChatbotService chatbotService;
    private final OllamaEmbeddingClient embeddingClient;
    private final SpeechActClassifier speechActClassifier;
    private final ChatbotProperties props;

    private volatile double[][] seedVectors;

    public UnifiedChatRouter(ChatbotService chatbotService,
                             OllamaEmbeddingClient embeddingClient,
                             SpeechActClassifier speechActClassifier,
                             ChatbotProperties props) {
        this.chatbotService = chatbotService;
        this.embeddingClient = embeddingClient;
        this.speechActClassifier = speechActClassifier;
        this.props = props;
    }

    /** 첫 턴 라우팅 판정. 임베딩/Ollama 장애 시 안전하게 FAQ 로 보낸다. */
    public Decision decide(String question) {
        double faqScore;
        double intakeScore;
        try {
            faqScore = chatbotService.topFaqSimilarity(question).orElse(0.0);
            intakeScore = intakeScore(question);
        } catch (Exception e) {
            // 임베딩/Ollama 장애 → 인테이크로 잘못 끌어들이지 말고 ① 로(기존 동작 유지).
            log.warn("라우팅 점수 계산 실패(FAQ 로 폴백): {}", e.getMessage());
            return new Decision(Target.FAQ, 0.0, 0.0, 0.0, false, null);
        }

        double diff = intakeScore - faqScore;

        // 둘 다 약함 → FAQ도 의도도 불명확 → 되묻기(fallback). 코퍼스 밖·무의미 입력 차단.
        double weakGate = props.getWeakGate();
        if (faqScore < weakGate && intakeScore < weakGate) {
            return new Decision(Target.FALLBACK, faqScore, intakeScore, diff, false, null);
        }

        boolean boundary = Math.abs(diff) < props.getRouteBoundary();
        if (!boundary) {
            // 명확구역: argmax 결정적, LLM 호출 없음.
            return new Decision(diff > 0 ? Target.INTAKE_DIRECT : Target.FAQ,
                    faqScore, intakeScore, diff, false, null);
        }

        // 경계구역: 화행분류 1회.
        String act = speechActClassifier.classify(question);
        Target target = SpeechActClassifier.COMMAND.equals(act)
                ? Target.INTAKE_CONFIRM   // 오분류 안전판: 바로 진입 말고 확인 1턴.
                : Target.FAQ;
        return new Decision(target, faqScore, intakeScore, diff, true, act);
    }

    private double intakeScore(String question) {
        double[] qv = embeddingClient.embed(question);
        double best = 0.0;
        for (double[] seed : seedVectors()) {
            double c = CosineSimilarity.compute(qv, seed);
            if (c > best) {
                best = c;
            }
        }
        return best;
    }

    /** 시드 임베딩 1회 캐시(지연 초기화). 실패하면 예외를 전파해 decide 가 FAQ 로 폴백한다. */
    private double[][] seedVectors() {
        double[][] local = seedVectors;
        if (local == null) {
            synchronized (this) {
                local = seedVectors;
                if (local == null) {
                    local = new double[SEEDS.size()][];
                    for (int i = 0; i < SEEDS.size(); i++) {
                        local[i] = embeddingClient.embed(SEEDS.get(i));
                    }
                    seedVectors = local;
                }
            }
        }
        return local;
    }
}
