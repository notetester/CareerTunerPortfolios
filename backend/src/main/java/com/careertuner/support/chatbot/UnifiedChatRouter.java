package com.careertuner.support.chatbot;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ① 커뮤니티 FAQ/에이전트 와 ③ LLM 인테이크를 한 입구에서 가르는 하이브리드 라우터(첫 턴 판정 전용).
 *
 * <p><b>3-신호 판정(2026-06-25 회귀수정):</b> faqScore·intakeScore·communityScore 를 비교한다.
 * intakeScore 단독으론 "행위 의도(면접 준비해줘)" 와 "커뮤니티 글 조회(면접 후기 보여줘)"·인사가
 * 임베딩상 겹쳐(실측: 진짜 인테이크 0.78~1.0 vs 커뮤니티 0.54~0.79) 후자가 ③로 새던 회귀가 있었다.
 * communityScore(조회 의도 시드)를 veto 로 써서 <b>intake 가 community 를 boundary 이상 앞설 때만</b>
 * ③로 보내고, 나머지(커뮤니티 조회·인사·잡담·애매)는 커뮤니티 에이전트(catch-all)로 보낸다.
 * 에이전트는 글 검색(searchCommunityPosts)과 인사/잡담 자연응답을 스스로 판단해 처리한다.</p>
 *
 * <p>실측 분리축(2026-06-25): intake−community 가 진짜 인테이크 ≥+0.131 / 커뮤니티·잡담 ≤+0.081 로
 * boundary 0.10 에서 깔끔히 갈린다(intakeScore 단독은 0.79 부근 겹침).</p>
 */
@Component
public class UnifiedChatRouter {

    private static final Logger log = LoggerFactory.getLogger(UnifiedChatRouter.class);

    /** 라우팅 결과. */
    public enum Target {
        /** ① FAQ 즉답 경로(faqPath — 임베딩 게이트). */
        FAQ,
        /** ① 커뮤니티 에이전트 직행(FAQ 게이트 우회) — catch-all(글 검색·인사·잡담). */
        AGENT,
        /** ③ 인테이크 즉시 진입(intake 가 community·faq 를 확실히 앞섬). */
        INTAKE_DIRECT,
        /** 행위 vs FAQ 경계 COMMAND — 바로 진입 말고 확인 1턴(오분류 안전판). */
        INTAKE_CONFIRM,
        /** 약신호(셋 다 weakGate 미만) — 정중한 되묻기로 끊는다. */
        FALLBACK
    }

    public record Decision(Target target, double faqScore, double intakeScore,
                           double communityScore, boolean usedSpeechAct, String speechAct) {}

    /** ③ "행위 의도" 시드. intakeScore = 사용자 발화와 이 시드들의 max 코사인. */
    private static final List<String> INTAKE_SEEDS = List.of(
            "면접 준비해줘",
            "지원 준비 도와줘",
            "자소서 좀 봐줘",
            "이력서 첨삭해줘",
            "면접 예상질문 만들어줘");

    /**
     * ① 커뮤니티 "글 조회 의도" 시드. communityScore = 사용자 발화와 이 시드들의 max 코사인.
     * 인테이크 시드와 주제어(면접·자소서)는 겹치되 "조회/추천(보여줘·찾아줘·게시글·후기)" 표현으로 분리한다.
     * 상수로 두어 추후 조정 가능(필요 시 ai.chatbot.* 로 승격).
     */
    private static final List<String> COMMUNITY_SEEDS = List.of(
            "면접 후기 글 추천해줘",
            "취업 성공 사례 게시글 찾아줘",
            "다른 사람 자소서 예시 보고 싶어",
            "합격 수기 커뮤니티 글",
            "현직자 후기 게시판");

    private final ChatbotService chatbotService;
    private final OllamaEmbeddingClient embeddingClient;
    private final SpeechActClassifier speechActClassifier;
    private final ChatbotProperties props;

    private volatile double[][] intakeSeedVectors;
    private volatile double[][] communitySeedVectors;

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
        double communityScore;
        try {
            // 질문 임베딩 1회 → intake·community 모두 이 벡터로 계산(임베딩 호출 추가 없음).
            double[] qv = embeddingClient.embed(question);
            faqScore = chatbotService.topFaqSimilarity(question).orElse(0.0);
            intakeScore = maxCosine(qv, intakeSeedVectors());
            communityScore = maxCosine(qv, communitySeedVectors());
        } catch (Exception e) {
            // 임베딩/Ollama 장애 → 인테이크로 잘못 끌어들이지 말고 ① FAQ 로(기존 동작 유지).
            log.warn("라우팅 점수 계산 실패(FAQ 로 폴백): {}", e.getMessage());
            return new Decision(Target.FAQ, 0.0, 0.0, 0.0, false, null);
        }

        double weakGate = props.getWeakGate();
        double b = props.getRouteBoundary();

        // 1) 진짜 바닥: faq·intake·community 가 모두 weakGate 미만 → 되묻기(코퍼스 밖·무의미 입력).
        if (faqScore < weakGate && intakeScore < weakGate && communityScore < weakGate) {
            return new Decision(Target.FALLBACK, faqScore, intakeScore, communityScore, false, null);
        }

        // 2) 확실한 행위 의도(인테이크): intake 가 community·faq 를 모두 boundary 이상 앞섬 → ③ 직행.
        //    (조회 의도 community 가 가까우면 여기서 걸러져 ③로 안 샌다 — 회귀 핵심.)
        if (intakeScore - communityScore >= b && intakeScore - faqScore >= b) {
            return new Decision(Target.INTAKE_DIRECT, faqScore, intakeScore, communityScore, false, null);
        }

        // 3) 확실한 FAQ: faq 가 intake·community 를 모두 boundary 이상 앞섬 → FAQ(게이트가 즉답/미달 처리).
        if (faqScore - intakeScore >= b && faqScore - communityScore >= b) {
            return new Decision(Target.FAQ, faqScore, intakeScore, communityScore, false, null);
        }

        // 4) 안전판(행위 vs FAQ 경계): intake 가 community 는 확실히 앞서나(=조회 아님) faq 와는 애매 →
        //    화행분류 1회로 COMMAND 면 확인 1턴(③ 후보), 질문형이면 FAQ 로.
        if (intakeScore - communityScore >= b) {
            String act = speechActClassifier.classify(question);
            if (SpeechActClassifier.COMMAND.equals(act)) {
                return new Decision(Target.INTAKE_CONFIRM, faqScore, intakeScore, communityScore, true, act);
            }
            return new Decision(Target.FAQ, faqScore, intakeScore, communityScore, true, act);
        }

        // 5) 그 외(커뮤니티 조회·인사·잡담·애매) → 커뮤니티 에이전트(catch-all).
        return new Decision(Target.AGENT, faqScore, intakeScore, communityScore, false, null);
    }

    private static double maxCosine(double[] qv, double[][] seeds) {
        double best = 0.0;
        for (double[] seed : seeds) {
            double c = CosineSimilarity.compute(qv, seed);
            if (c > best) {
                best = c;
            }
        }
        return best;
    }

    /** ③ 행위 시드 임베딩 1회 캐시(지연 초기화). 실패하면 예외 전파해 decide 가 FAQ 로 폴백한다. */
    private double[][] intakeSeedVectors() {
        double[][] local = intakeSeedVectors;
        if (local == null) {
            synchronized (this) {
                local = intakeSeedVectors;
                if (local == null) {
                    local = embedSeeds(INTAKE_SEEDS);
                    intakeSeedVectors = local;
                }
            }
        }
        return local;
    }

    /** ① 커뮤니티 조회 시드 임베딩 1회 캐시(지연 초기화). */
    private double[][] communitySeedVectors() {
        double[][] local = communitySeedVectors;
        if (local == null) {
            synchronized (this) {
                local = communitySeedVectors;
                if (local == null) {
                    local = embedSeeds(COMMUNITY_SEEDS);
                    communitySeedVectors = local;
                }
            }
        }
        return local;
    }

    private double[][] embedSeeds(List<String> seeds) {
        double[][] vectors = new double[seeds.size()][];
        for (int i = 0; i < seeds.size(); i++) {
            vectors[i] = embeddingClient.embed(seeds.get(i));
        }
        return vectors;
    }
}
