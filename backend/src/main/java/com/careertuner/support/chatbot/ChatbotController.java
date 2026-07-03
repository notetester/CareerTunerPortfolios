package com.careertuner.support.chatbot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import com.careertuner.ai.chat.ChatAskRequest;
import com.careertuner.ai.chat.ChipSuggestion;
import com.careertuner.ai.chat.QuickReplyParser;
import com.careertuner.ai.chat.ChatAskResponse;
import com.careertuner.ai.chat.ChatHistoryResponse;
import com.careertuner.ai.chat.ChatHistoryResponse.ChatHistoryMessage;
import com.careertuner.ai.chat.ChatSessionSummary;
import com.careertuner.ai.chat.ChatResponse;
import com.careertuner.ai.chat.ChatResponse.SiteLink;
import com.careertuner.ai.chat.ChatSummarizeRequest;
import com.careertuner.ai.chat.CommunityChatAgent;
import com.careertuner.ai.chat.CommunityTools;
import com.careertuner.ai.chat.FastPathService;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.chat.QuickReplyAgent;
import com.careertuner.ai.chat.SearchTrace;
import com.careertuner.ai.chat.SummaryAgent;
import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.intake.IntakeAskService;
import com.careertuner.ai.intake.IntakeSlotTrace;
import com.careertuner.ai.intake.dto.IntakeAskResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseExtractionResponse;
import com.careertuner.applicationcase.dto.CreateApplicationCaseFromJobPostingRequest;
import com.careertuner.applicationcase.dto.UpdateApplicationCaseRequest;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.search.PostHit;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.service.JobPostingService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.profile.service.ProfileService;

import java.util.ArrayList;
import java.util.Arrays;

@RestController
@RequestMapping("/api")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    /** 환각 링크 차단: 실제 커뮤니티 글 경로만 통과 (모델이 만든 임의 url 제거). */
    private static final Pattern LINK_WHITELIST = Pattern.compile("^/community/posts/\\d+$");

    /** (d) 공고 추출 규칙기반 파싱 실패 시 case 에 남는 기본값(B 도메인 ApplicationCaseServiceImpl 와 동일 문구). */
    private static final String ONB_DEFAULT_COMPANY = "기업명 확인 필요";
    private static final String ONB_DEFAULT_JOBTITLE = "직무명 확인 필요";

    /**
     * 확인 응답에서 "시작" 쪽으로 본다(그 외는 안전하게 ① 로). 정규화된 입력과 <b>정확일치</b>로만 판정한다
     * (부분문자열 contains 는 "네이버"가 "네"로 오탐 → 정확매칭이 곧 길이필터 역할).
     */
    private static final Set<String> AFFIRMATIVE = Set.of(
            "시작", "네", "넵", "네네", "예", "응", "응응", "어", "ㅇㅇ", "그래", "그래요",
            "좋아", "좋아요", "할래", "해줘", "ok", "오케이", "yes");

    /** 오케스트레이터 모드 이탈 신호(모드 활성 중에만 판정). 배너 ⏏ 도 이 키워드를 보낸다. */
    private static final List<String> EXIT_COMMANDS = List.of(
            "그만", "취소", "종료", "일반상담", "나가기", "중단", "그만할래");

    /* ── (g) 이탈성 질문 가드 — ③/④ 답변 대기 중 FAQ성 질문이 답으로 삼켜지는 것 차단(구조점검 X항목) ── */

    /**
     * FAQ top-1 코사인이 이 값 이상이면 답변 대기 중이라도 질문으로 본다.
     * 실측(bge-m3·FAQ 40건): 물음표 없는 질문 0.668~0.734("환불은 어떻게 받아" .734) vs
     * 정상 답변 최고 0.609("기본 면접으로 할게" — 0.60 이었을 때 오탐 실측) → 0.65 가 분리선.
     * 낮은 유사도 질문("포인트 어떻게 모아" .511)은 놓친다 — 결정적 가드의 수용 한계(오탐 차단 우선).
     */
    private static final double SIDE_QUESTION_FAQ_GATE = 0.65;
    /** 확인 1턴 칩 문구 — quickReplies 로 그대로 노출되고, 클릭 시 이 텍스트가 다음 발화로 온다. */
    private static final String SIDE_Q_YES = "네, 답해주세요";
    private static final String SIDE_Q_NO = "아니요, 계속할게요";
    /** ④에서 가드를 거는 수집 단계 — 발화가 슬롯/DB에 그대로 저장되는 단계만(오기록 차단이 목적). */
    private static final Set<String> ONB_GUARDED_STEPS = Set.of(
            "JOB", "SKILLS", "AWAIT_POSTING", "AWAIT_COMPANY", "AWAIT_JOBTITLE");

    /* ── (g′) 재시작 화이트리스트 — ④ 수집 대기 중 "면접 해줘"/"다시"류가 데이터로 오기록되는 것 차단 ── */

    /** 재시작 의도 키워드(공백 제거 후 정확일치 기준). isExitCommand 와 동일한 정확일치+접미사 패턴. */
    private static final List<String> RESTART_COMMANDS = List.of(
            "면접해줘", "면접준비해줘", "다시시작", "처음부터", "다시");

    /** 키워드별 허용 접미사(조사·어미) — EXIT_SUFFIXES 와 동일 매칭 원칙(정확일치 또는 키워드+허용접미사만). */
    private static final Map<String, Set<String>> RESTART_SUFFIXES = Map.of(
            "면접해줘", Set.of(),
            "면접준비해줘", Set.of(),
            "다시시작", Set.of("할래요", "할게요", "해줘", "해주세요", "요"),
            "처음부터", Set.of("할래요", "할게요", "해줘", "해주세요", "다시할래요", "다시할게요", "요"),
            "다시", Set.of("시작할래요", "시작할게요", "시작해줘", "시작해주세요", "할래요", "할게요", "해줘", "해주세요"));

    private static final String RESTART_YES = "네, 처음부터";
    private static final String RESTART_NO = "아니요, 이어서";

    /**
     * 이탈 키워드별 허용 접미사(조사·어미). 매칭은 키워드 정확일치 <b>또는</b> "키워드로 시작 + 나머지가 이 집합에
     * 정확히 포함"일 때만 — 단순 startsWith/contains 가 아니다. "그만두지않을래요"는 "그만"으로 시작하지만 접미사
     * "두지않을래요"가 없어 탈락(오탐 차단). "중단된 프로젝트"는 "중단" 시작이나 접미사 "된프로젝트"가 없어 탈락.
     */
    private static final Map<String, Set<String>> EXIT_SUFFIXES = Map.of(
            "그만", Set.of("요", "할래", "할게", "하자", "둘게"),
            "취소", Set.of("요", "할게", "해줘"),
            "종료", Set.of("요", "해줘"),
            "중단", Set.of("요", "할게", "해줘"),
            "나가기", Set.of("요"),
            "일반상담", Set.of("요"),
            "그만할래", Set.of("요"));

    private final CommunityChatAgent agent;
    private final QuickReplyAgent quickReplyAgent;
    private final QuickReplyParser quickReplyParser;
    private final SearchTrace searchTrace;
    private final MyBatisChatMemoryStore memoryStore;
    private final ChatbotService chatbotService;
    private final FastPathService fastPathService;
    private final UnansweredQuestionService unansweredQuestionService;
    private final ResponseLogService responseLogService;
    private final ChatbotProperties chatbotProperties;
    private final UnifiedChatRouter router;
    private final RouteConfirmStore routeConfirmStore;
    private final IntakeAskService intakeAskService;
    private final IntakeModeStore intakeModeStore;
    private final CommunityTools communityTools;
    private final SummaryAgent summaryAgent;
    // (a) 깡통계정 온보딩 게이트용 read 의존(호출만, A·B 도메인 무수정).
    private final ProfileMapper profileMapper;
    private final ApplicationCaseService applicationCaseService;
    // (e) 모은 직무·기술 저장용 A 도메인 write 의존(기존 save 호출만, A 코드 무수정).
    private final ProfileService profileService;
    // (f) mode 칩 제시·ready 판정 재활용(intake() 직접 호출 — agent.chat·라우터 우회). D 코드 무수정.
    private final AutoPrepIntakeService autoPrepIntakeService;
    // (b) 온보딩 수집 슬롯(인메모리·대화키). 직무·기술을 모았다가 (e)에서 저장.
    private final IntakeSlotTrace intakeSlotTrace;
    // (d″) 확인-회사/직무 후보 제시용 공고 제목 read(B.getJobPosting 호출만 — B 도메인 무수정).
    private final JobPostingService jobPostingService;
    // (g) 이탈성 질문 확인 1턴의 보류 발화 저장(인메모리·1턴 소비).
    private final SideQuestionStore sideQuestionStore;
    // (g′) ④ 재시작 확인 1턴의 대기 플래그 저장(인메모리·1턴 소비).
    private final OnboardingRestartStore onboardingRestartStore;

    public ChatbotController(CommunityChatAgent agent,
                            QuickReplyAgent quickReplyAgent,
                            QuickReplyParser quickReplyParser,
                            SearchTrace searchTrace,
                            MyBatisChatMemoryStore memoryStore,
                            ChatbotService chatbotService,
                            FastPathService fastPathService,
                            UnansweredQuestionService unansweredQuestionService,
                            ResponseLogService responseLogService,
                            ChatbotProperties chatbotProperties,
                            UnifiedChatRouter router,
                            RouteConfirmStore routeConfirmStore,
                            IntakeAskService intakeAskService,
                            IntakeModeStore intakeModeStore,
                            CommunityTools communityTools,
                            SummaryAgent summaryAgent,
                            ProfileMapper profileMapper,
                            ApplicationCaseService applicationCaseService,
                            ProfileService profileService,
                            AutoPrepIntakeService autoPrepIntakeService,
                            IntakeSlotTrace intakeSlotTrace,
                            JobPostingService jobPostingService,
                            SideQuestionStore sideQuestionStore,
                            OnboardingRestartStore onboardingRestartStore) {
        this.agent = agent;
        this.quickReplyAgent = quickReplyAgent;
        this.quickReplyParser = quickReplyParser;
        this.searchTrace = searchTrace;
        this.memoryStore = memoryStore;
        this.chatbotService = chatbotService;
        this.fastPathService = fastPathService;
        this.unansweredQuestionService = unansweredQuestionService;
        this.responseLogService = responseLogService;
        this.chatbotProperties = chatbotProperties;
        this.router = router;
        this.routeConfirmStore = routeConfirmStore;
        this.intakeAskService = intakeAskService;
        this.intakeModeStore = intakeModeStore;
        this.communityTools = communityTools;
        this.summaryAgent = summaryAgent;
        this.profileMapper = profileMapper;
        this.applicationCaseService = applicationCaseService;
        this.profileService = profileService;
        this.autoPrepIntakeService = autoPrepIntakeService;
        this.intakeSlotTrace = intakeSlotTrace;
        this.jobPostingService = jobPostingService;
        this.sideQuestionStore = sideQuestionStore;
        this.onboardingRestartStore = onboardingRestartStore;
    }

    /* ── (g) 이탈성 질문 가드 헬퍼 ── */

    /**
     * 이탈성 질문 판정 — 결정적 2신호만(LLM 0): ① 물음표 종결 ② FAQ top-1 코사인 ≥ {@link #SIDE_QUESTION_FAQ_GATE}.
     * 정상 답변("백엔드 개발자", "네이버")은 물음표 없음+FAQ 낮음이라 안 걸린다.
     * 임베딩 장애 시 false — 가드가 정상 흐름을 막는 일은 없게(보수적).
     */
    private boolean looksLikeSideQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String t = question.trim();
        if (t.endsWith("?") || t.endsWith("？")) {
            return true;
        }
        try {
            return chatbotService.topFaqSimilarity(t).orElse(0.0) >= SIDE_QUESTION_FAQ_GATE;
        } catch (Exception e) {
            log.debug("이탈성 질문 FAQ 스코어 계산 실패(가드 미발동): {}", e.getMessage());
            return false;
        }
    }

    /** 확인 응답 "네" 판정 — 칩 문구 정확일치, "답해" 포함, 또는 기존 긍정 화이트리스트(정확일치)만. */
    private static boolean isSideQuestionYes(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return norm.equals(SIDE_Q_YES.replace(" ", "")) || norm.contains("답해") || AFFIRMATIVE.contains(norm);
    }

    /** 확인 응답 "아니요" 판정 — 칩 문구 정확일치, "아니" 시작, 또는 "계속" 포함. */
    private static boolean isSideQuestionNo(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return norm.equals(SIDE_Q_NO.replace(" ", "")) || norm.startsWith("아니") || norm.contains("계속");
    }

    /** 우회 답변 뒤에 복귀 제안 한 줄 — 진행 상태는 그대로라 다음 답변이 대기 중이던 질문의 답으로 이어진다. */
    private static ChatAskResponse withResumeHint(ChatAskResponse answer) {
        return new ChatAskResponse(
                answer.conversationId(),
                answer.message() + "\n\n아까 하던 준비는 그대로예요 — 이어서 답해주시면 계속 진행돼요.",
                answer.links(), answer.quickReplies(), answer.route(),
                answer.intake(), answer.inOrchestration(), answer.summaryChip());
    }

    /* ── (g′) 재시작 화이트리스트 헬퍼 ── */

    /**
     * 재시작 의도 판정 — isExitCommand 와 동일 원칙(정확일치 또는 키워드+허용접미사만, LLM 0).
     * "면접 해줘"를 정확히 다시 치는 경우가 실측 재현 시나리오라 최우선 키워드로 둔다.
     */
    static boolean isRestartIntent(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        for (String kw : RESTART_COMMANDS) {
            if (norm.equals(kw)) {
                return true;
            }
            if (norm.startsWith(kw) && norm.length() > kw.length()
                    && RESTART_SUFFIXES.getOrDefault(kw, Set.of()).contains(norm.substring(kw.length()))) {
                return true;
            }
        }
        return false;
    }

    /** 재시작 확인 "네" 판정 — 칩 문구 정확일치, "처음부터" 포함, 또는 기존 긍정 화이트리스트. */
    private static boolean isRestartConfirmYes(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return norm.equals(RESTART_YES.replace(" ", "")) || norm.contains("처음부터") || AFFIRMATIVE.contains(norm);
    }

    /** 재시작 확인 "아니요" 판정 — 칩 문구 정확일치, "아니" 시작, 또는 "이어서"/"계속" 포함. */
    private static boolean isRestartConfirmNo(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return norm.equals(RESTART_NO.replace(" ", "")) || norm.startsWith("아니")
                || norm.contains("이어서") || norm.contains("계속");
    }

    /** (g′) 확인 헬퍼 공통 응답 조립 — RouteMessage → ChatAskResponse + 로그 적재(온보딩 턴 tail 과 동일 규약). */
    private ChatAskResponse toOnboardingResponse(Long conversationId, Long userId, String question, RouteMessage rm) {
        responseLogService.record(conversationId, userId, question, "ONBOARDING", false, null, null, false);
        return new ChatAskResponse(conversationId, rm.message(), List.of(), rm.quickReplies(),
                rm.route(), rm.intake(), rm.inOrchestration(), null);
    }

    /**
     * "아니요, 이어서" 응답 — 방금 발화(재시작 의도로 오인된 문장)는 버리고 현재 단계 질문을 다시 보여준다.
     * EXTRACTING/AWAIT_MODE 는 부수효과 없는 기존 조회 메서드를 그대로 재사용(재확인 = 재조회와 동일 의미).
     */
    private RouteMessage redisplayCurrentStep(Long conversationId, AuthUser authUser, String step) {
        return switch (step) {
            case "JOB" -> new RouteMessage("④온보딩:직무",
                    "알겠어요, 계속할게요. 어떤 직무로 지원하세요? (예: 프론트엔드 개발자, 백엔드 개발자)");
            case "SKILLS" -> new RouteMessage("④온보딩:기술",
                    "알겠어요, 계속할게요. 주로 다루는 기술을 콤마(,)로 구분해서 알려주세요.");
            case "AWAIT_POSTING" -> new RouteMessage("④온보딩:공고요청",
                    "알겠어요, 계속할게요. 지원할 공고 전문을 붙여넣어 주세요(회사명·직무·자격요건이 담긴 원문이면 좋아요).");
            case "AWAIT_COMPANY" -> new RouteMessage("④온보딩:확인-회사",
                    "알겠어요, 계속할게요. 어느 회사 공고인가요?");
            case "AWAIT_JOBTITLE" -> new RouteMessage("④온보딩:확인-직무",
                    "알겠어요, 계속할게요. 직무명을 입력해 주세요. (예: 백엔드 개발자)");
            case "EXTRACTING" -> onboardingPollExtraction(conversationId, authUser);
            case "AWAIT_MODE" -> onboardingModeStep(conversationId, authUser, null);
            default -> new RouteMessage("④온보딩:재개", "알겠어요, 계속할게요.");
        };
    }

    /**
     * (b) 깡통 온보딩 수집: 직무→기술 순차 질문, 유저 답을 *그대로* 슬롯에 누적(가공 0). 슬롯=인메모리(LLM 40창 미사용·휘발).
     * 결정성(§6-1,2): 챗봇은 질문만 친절(예시·범위), 답 해석/부풀림 금지·스킬추출 AI 미사용. 저장(ProfileService.save)은 (e).
     * 매 턴 재판정(저장 전까지 깡통 유지)이라 step 으로 진행 단계를 이어간다. case 입력(d)·저장(e)·면접합류(f)는 다음 단계.
     */
    private ChatAskResponse onboardingTurn(Long conversationId, AuthUser authUser, String question,
                                           String selectedModeCode, Long selectedCaseId) {
        Long userId = authUser.id();   // 라우팅에서 authUser != null 보장. save((e))가 authUser 를 요구해 끝까지 내린다.
        String step = intakeSlotTrace.onboardingStep(conversationId);

        // ── (g′) 재시작 화이트리스트: 기존 이탈성 질문 가드보다 먼저 판정한다("면접 해줘"/"다시"류가
        //    데이터로 오기록되는 것 차단 — 물음표/FAQ 가드는 우연히 안 걸릴 수 있어 앞단에 별도로 둔다).
        if (onboardingRestartStore.consume(conversationId)) {
            if (isRestartConfirmYes(question)) {
                intakeSlotTrace.clearOnboarding(conversationId); // step·job·skills·caseId 전부 리셋.
                return onboardingTurn(conversationId, authUser, "", selectedModeCode, null); // step==null 진입 경로 재사용.
            }
            if (isRestartConfirmNo(question)) {
                return toOnboardingResponse(conversationId, userId, question,
                        redisplayCurrentStep(conversationId, authUser, step));
            }
            // yes/no 도 아니면(확인을 무시하고 다른 말을 함) → 소비만 하고 그 발화를 정상 흐름으로 처리.
        } else if (step != null && selectedCaseId == null && isRestartIntent(question)) {
            onboardingRestartStore.defer(conversationId);
            return new ChatAskResponse(conversationId,
                    "처음부터 다시 시작할까요? 지금까지 입력한 내용은 사라져요.",
                    List.of(), List.of(RESTART_YES, RESTART_NO), "④온보딩:재시작확인", null, false, null);
        }

        // ── (g) 이탈성 질문 가드: 수집 단계에서 질문이 직무/회사명으로 오기록되는 것 차단.
        //    직전 턴이 확인이었으면 그 응답부터 처리한다(그 턴은 재가드 없음 — 확인 루프 방지).
        String deferred = sideQuestionStore.consume(conversationId);
        if (deferred != null && isSideQuestionYes(question)) {
            // ④ 상태(step·슬롯)는 안 건드림 — 한 턴만 ①(FAQ/에이전트)로 우회하고, 다음 답변이 원래 단계로 이어진다.
            return withResumeHint(faqPath(conversationId, deferred, userId, "④우회①"));
        }
        if (deferred != null && isSideQuestionNo(question)) {
            question = deferred; // 사용자가 "계속"을 골랐다 — 원 발화를 답변으로 저장하고 진행.
        }
        // step==null(진입 턴)은 수집 단계가 아님 + Set.of 는 contains(null) 에 NPE — null 가드 선행.
        if (deferred == null && selectedCaseId == null && step != null
                && ONB_GUARDED_STEPS.contains(step) && looksLikeSideQuestion(question)) {
            sideQuestionStore.defer(conversationId, question);
            responseLogService.record(conversationId, userId, question, "ONBOARDING", false, null, null, false);
            return new ChatAskResponse(conversationId,
                    "잠깐 — 질문이신 것 같아요. 준비를 잠시 멈추고 답해드릴까요?",
                    List.of(), List.of(SIDE_Q_YES, SIDE_Q_NO),
                    "④온보딩:질문확인", null, false, null);
        }

        RouteMessage rm;
        if (step == null) {
            intakeSlotTrace.setOnboardingStep(conversationId, "JOB");
            rm = new RouteMessage("④온보딩:직무",
                    "취업 준비, 막막하시죠? 같이 채워볼게요. 먼저 — 어떤 직무로 지원하세요? (예: 프론트엔드 개발자, 백엔드 개발자)");
        } else if ("JOB".equals(step)) {
            intakeSlotTrace.recordOnboardingJob(conversationId, question);   // 유저 답 그대로(가공 0)
            intakeSlotTrace.setOnboardingStep(conversationId, "SKILLS");
            rm = new RouteMessage("④온보딩:기술",
                    "좋아요. 주로 다루는 기술을 콤마(,)로 구분해서 알려주세요. 3~4개면 충분해요. (예: React, TypeScript, Spring)");
        } else if ("SKILLS".equals(step)) {
            intakeSlotTrace.recordOnboardingSkills(conversationId, question); // 그대로
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_POSTING");
            IntakeSlotTrace.OnboardingCollected got = intakeSlotTrace.onboarding(conversationId);
            rm = new RouteMessage("④온보딩:공고요청",
                    "받았어요 — 직무 \"" + got.job() + "\", 기술 \"" + got.skills()
                            + "\". 이제 지원할 공고 전문을 붙여넣어 주세요(회사명·직무·자격요건이 담긴 원문이면 좋아요).");
        } else if ("AWAIT_POSTING".equals(step)) {
            // 파일 경로(d′): 프론트가 공고 파일을 B 업로드로 먼저 지원 건으로 만들고 id 만 알려준 턴.
            rm = selectedCaseId != null
                    ? onboardingAdoptCase(conversationId, userId, selectedCaseId)
                    : onboardingCreateCase(conversationId, userId, question);
        } else if ("EXTRACTING".equals(step)) {
            rm = onboardingPollExtraction(conversationId, authUser);
        } else if ("AWAIT_COMPANY".equals(step)) {
            rm = onboardingFillField(conversationId, authUser, question, true, "회사명을 입력해 주세요. 어느 회사 공고인가요?");
        } else if ("AWAIT_JOBTITLE".equals(step)) {
            rm = onboardingFillField(conversationId, authUser, question, false, "직무명을 입력해 주세요. (예: 백엔드 개발자)");
        } else if ("AWAIT_MODE".equals(step)) {
            rm = onboardingModeStep(conversationId, authUser, selectedModeCode);
        } else {
            // DONE 등 종단 — 라우터의 sticky 가 여기 도달 전에 풀리는 게 정상(방어용).
            rm = new RouteMessage("④온보딩:완료대기", "면접 준비가 시작됐어요. 잠시만 기다려 주세요.");
        }
        return toOnboardingResponse(conversationId, userId, question, rm);
    }

    /**
     * (d) AWAIT_POSTING: 사용자 공고 원문 → B.createFromJobPosting(sourceType=TEXT) 로 지원 건 생성(비동기 추출 큐잉).
     * AI 가 회사명을 정하지 않는다 — 생성만 하고 추출 결과는 다음 턴 폴링(EXTRACTING)에서 본다. 분석(FIT/면접)은
     * 트리거하지 않음(추출 SUCCEEDED 후 자동 파이프라인 또는 (f) autoPrep 소관 — DEFAULT 회사명 프롬프트 오염 회피).
     */
    private RouteMessage onboardingCreateCase(Long conversationId, Long userId, String question) {
        if (question == null || question.trim().length() < 20) {
            return new RouteMessage("④온보딩:공고요청",
                    "공고 전문이 조금 짧아요. 회사명·직무·자격요건이 담긴 공고 내용을 붙여넣어 주세요.");
        }
        try {
            var created = applicationCaseService.createFromJobPosting(
                    userId,
                    new CreateApplicationCaseFromJobPostingRequest(question, null, null, "TEXT", null));
            intakeSlotTrace.setOnboardingCaseId(conversationId, created.applicationCase().id());
            intakeSlotTrace.setOnboardingStep(conversationId, "EXTRACTING");
            return new RouteMessage("④온보딩:공고생성",
                    "공고 받았어요. 회사·직무 정보를 읽고 있어요(보통 몇 초). 준비되면 아무 메시지나 보내주시면 진행 상황을 알려드릴게요.");
        } catch (RuntimeException ex) {
            log.warn("온보딩 case 생성 실패(공고 재요청): {}", ex.getMessage());
            // step 은 AWAIT_POSTING 유지 — 재시도.
            return new RouteMessage("④온보딩:공고생성실패",
                    "공고를 등록하는 중 문제가 생겼어요. 공고 전문을 다시 붙여넣어 주시겠어요?");
        }
    }

    /**
     * (d′) AWAIT_POSTING 파일 경로: 프론트가 공고 파일(PDF/이미지)을 기존 B 업로드 엔드포인트
     * (from-job-posting/upload)로 먼저 지원 건으로 만들고 selectedCaseId 로 알려준 경우 — 텍스트 생성 대신
     * 그 건을 입양한다(소유 검증 후). 추출은 업로드 시 이미 큐잉됐으므로 기존 EXTRACTING 폴링에 그대로
     * 합류한다. route 는 텍스트 경로(공고생성)와 동일하게 재사용 → 프론트 국면 매핑 무변경.
     */
    private RouteMessage onboardingAdoptCase(Long conversationId, Long userId, Long selectedCaseId) {
        try {
            applicationCaseService.get(userId, selectedCaseId); // 소유 검증 — 타인/미존재 건이면 throw
        } catch (RuntimeException ex) {
            log.warn("온보딩 파일 케이스 입양 실패(공고 재요청): {}", ex.getMessage());
            // step 은 AWAIT_POSTING 유지 — 붙여넣기/재업로드로 재시도.
            return new RouteMessage("④온보딩:공고요청",
                    "공고 파일을 확인하지 못했어요. 공고 전문을 붙여넣거나 파일을 다시 올려주세요.");
        }
        intakeSlotTrace.setOnboardingCaseId(conversationId, selectedCaseId);
        intakeSlotTrace.setOnboardingStep(conversationId, "EXTRACTING");
        return new RouteMessage("④온보딩:공고생성",
                "공고 파일 받았어요. 회사·직무 정보를 읽고 있어요(보통 몇 초). 준비되면 진행 상황을 알려드릴게요.");
    }

    /**
     * (d) EXTRACTING: 비동기 추출 상태를 턴 사이로 폴링(한 턴 블로킹 대기는 지연·타임아웃 위험).
     * SUCCEEDED → case 재조회로 파싱 결과 확인(PASS=자동 채움 / REVIEW_REQUIRED=DEFAULT 유지 → 유저 확정).
     * 미완(QUEUED/RUNNING)=대기, FAILED=공고 재요청. 추출 상태/품질은 B 도메인이 판정 — 챗봇은 결과만 읽는다.
     */
    private RouteMessage onboardingPollExtraction(Long conversationId, AuthUser authUser) {
        Long userId = authUser.id();
        Long caseId = intakeSlotTrace.onboardingCaseId(conversationId);
        if (caseId == null) {
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_POSTING");
            return new RouteMessage("④온보딩:공고요청",
                    "공고 정보를 다시 받아야 할 것 같아요. 공고 전문을 붙여넣어 주세요.");
        }
        ApplicationCaseExtractionResponse ext = null;
        try {
            ext = applicationCaseService.getLatestJobPostingExtraction(userId, caseId);
        } catch (RuntimeException ex) {
            log.warn("온보딩 추출 상태 조회 실패(대기 유지): {}", ex.getMessage());
        }
        String status = ext == null ? null : ext.status();
        if ("FAILED".equals(status)) {
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_POSTING");
            return new RouteMessage("④온보딩:추출실패",
                    "공고 분석에 실패했어요. 공고 전문을 다시 붙여넣어 주시겠어요?");
        }
        if (!"SUCCEEDED".equals(status)) {
            // null/QUEUED/RUNNING — 아직 진행 중. step 유지.
            return new RouteMessage("④온보딩:추출대기",
                    "아직 공고를 분석 중이에요. 잠시 후 다시 메시지를 보내주세요.");
        }
        try {
            return onboardingResolveCase(conversationId, authUser, applicationCaseService.get(userId, caseId));
        } catch (RuntimeException ex) {
            log.warn("온보딩 case 재조회 실패(대기 유지): {}", ex.getMessage());
            return new RouteMessage("④온보딩:추출대기",
                    "공고 정보를 확인하는 중이에요. 잠시 후 다시 메시지를 보내주세요.");
        }
    }

    /**
     * (d) AWAIT_COMPANY/AWAIT_JOBTITLE: 파싱 실패한 회사명/직무명을 유저가 확정 → B.update 로 그 컬럼만 채운다.
     * (update 는 null 인자를 기존값 유지로 처리 — 부분 갱신.) 빈 입력은 재질문. update 는 분석 파이프라인을
     * 트리거하지 않음(컬럼만 패치) — 분석은 (f) autoPrep 에서 정상 회사명으로 생성된다.
     */
    private RouteMessage onboardingFillField(Long conversationId, AuthUser authUser, String question,
                                             boolean company, String reaskMessage) {
        Long userId = authUser.id();
        if (question == null || question.isBlank()) {
            return new RouteMessage(company ? "④온보딩:확인-회사" : "④온보딩:확인-직무", reaskMessage);
        }
        Long caseId = intakeSlotTrace.onboardingCaseId(conversationId);
        if (caseId == null) {
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_POSTING");
            return new RouteMessage("④온보딩:공고요청",
                    "공고 정보를 다시 받아야 할 것 같아요. 공고 전문을 붙여넣어 주세요.");
        }
        String value = question.trim();
        // (d″) "네/맞아요" 류 답변은 제목 후보 수락으로 해석 — 리터럴 "네"가 회사명으로 저장되는 것 방지.
        //   후보를 다시 파싱해(무상태·결정적) 있으면 그 값으로 치환, 없으면 재질문.
        if (isAffirmative(value)) {
            TitleCandidates candidates = onboardingTitleCandidates(userId, caseId);
            String suggested = company ? candidates.company() : candidates.jobTitle();
            if (suggested == null) {
                return new RouteMessage(company ? "④온보딩:확인-회사" : "④온보딩:확인-직무", reaskMessage);
            }
            value = suggested;
        }
        try {
            applicationCaseService.update(userId, caseId, company
                    ? new UpdateApplicationCaseRequest(value, null, null, null, null, null, null, null, null, null)
                    : new UpdateApplicationCaseRequest(null, value, null, null, null, null, null, null, null, null));
            return onboardingResolveCase(conversationId, authUser, applicationCaseService.get(userId, caseId));
        } catch (RuntimeException ex) {
            log.warn("온보딩 case 보정 update 실패(재질문): {}", ex.getMessage());
            return new RouteMessage(company ? "④온보딩:확인-회사" : "④온보딩:확인-직무",
                    "정보를 저장하는 중 문제가 생겼어요. 다시 한 번 입력해 주시겠어요?");
        }
    }

    /**
     * (d) 추출/보정 후 case 상태로 다음 단계 결정: 회사명·직무명이 DEFAULT 면 그 항목만 유저에게 묻고,
     * 둘 다 채워졌으면 프로필 저장 후 AWAIT_MODE(mode 칩)로 넘어간다. 빈 입력으로 DEFAULT 가 남으면 같은 항목 재질문(self-heal).
     * (e) 이 "둘 다 채워짐" 지점이 case 가 확정된 유일한 곳 → 여기서 *딱 한 번* 프로필을 저장한다("늦게 save").
     */
    private RouteMessage onboardingResolveCase(Long conversationId, AuthUser authUser, ApplicationCaseResponse caseNow) {
        if (ONB_DEFAULT_COMPANY.equals(caseNow.companyName())) {
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_COMPANY");
            // (d″) 추출 제목에서 회사 후보를 읽어 칩으로 제시 — 칩 클릭 = 그 텍스트가 답변으로 전송(프로토콜 동일).
            String companyCandidate = onboardingTitleCandidates(authUser.id(), caseNow.id()).company();
            if (companyCandidate != null) {
                return new RouteMessage("④온보딩:확인-회사",
                        "공고는 등록했는데 회사명을 자동으로 못 읽었어요. 제목을 보니 \"" + companyCandidate
                                + "\" 같아요 — 맞으면 아래 버튼을 누르고, 아니면 정확한 회사명을 입력해 주세요.",
                        List.of(companyCandidate));
            }
            return new RouteMessage("④온보딩:확인-회사",
                    "공고는 등록했는데 회사명을 자동으로 못 읽었어요. 어느 회사 공고인가요?");
        }
        if (ONB_DEFAULT_JOBTITLE.equals(caseNow.jobTitle())) {
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_JOBTITLE");
            String jobTitleCandidate = onboardingTitleCandidates(authUser.id(), caseNow.id()).jobTitle();
            if (jobTitleCandidate != null) {
                return new RouteMessage("④온보딩:확인-직무",
                        "회사는 \"" + caseNow.companyName() + "\"로 확인됐어요. 공고 제목에는 \"" + jobTitleCandidate
                                + "\"라고 돼 있어요 — 그대로 쓰려면 아래 버튼을, 아니면 직무명을 입력해 주세요. (예: 백엔드 개발자)",
                        List.of(jobTitleCandidate));
            }
            return new RouteMessage("④온보딩:확인-직무",
                    "회사는 \"" + caseNow.companyName() + "\"로 확인됐어요. 직무명도 알려주세요. (예: 백엔드 개발자)");
        }
        // ★(e) save 타이밍 = case 확정 직후. case 가 이미 있으니 게이트는 false → 이 시점 이탈해도
        //   "프로필O+case0" 막다른 길이 안 생긴다(측정 §5e "늦게 save"). save 는 이 한 곳에서만.
        saveOnboardingProfile(conversationId, authUser);
        // (f) 회사·직무·프로필이 다 찼으니 마지막으로 면접 mode 를 받아 인테이크로 인계한다(같은 턴에 mode 칩 제시).
        intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_MODE");
        return onboardingModeStep(conversationId, authUser, null);
    }

    /* ── (d″) 확인-회사/직무 후보: 공고 "제목 줄" 규칙 파싱 ── */

    /** 제목 꼬리의 사이트명(" | 잡코리아", " - 사람인" 등) 제거용. */
    private static final Pattern TITLE_SITE_SUFFIX = Pattern.compile(
            "\\s*[|\\-–]\\s*(잡코리아|사람인|인크루트|원티드|JobKorea|Saramin|Wanted)\\s*$", Pattern.CASE_INSENSITIVE);
    /** 사람인형: "[{회사}] {공고제목}(D-n)". */
    private static final Pattern TITLE_BRACKET_COMPANY = Pattern.compile("^\\[([^\\]]{2,40})\\]\\s*(.*)$");
    /** 잡코리아형: "{회사} 채용 - {공고제목}" — "채용" 뒤 구분자(또는 끝)가 있어야 매칭("채용공고" 오탐 차단). */
    private static final Pattern TITLE_COMPANY_CHAEYONG = Pattern.compile("^(.{2,40}?)\\s*채용(?:\\s*[-–:|]\\s*(.*))?$");
    /** 제목 꼬리의 마감 배지 "(D-22)" 제거용. */
    private static final Pattern TITLE_D_DAY = Pattern.compile("\\s*\\(D-\\d+\\)\\s*$");

    /** 공고 제목에서 파싱한 회사/직무 후보(못 찾으면 null — 지어내지 않는다). 제안용 칩 — 확정은 항상 사용자. */
    private record TitleCandidates(String company, String jobTitle) {
        static final TitleCandidates EMPTY = new TitleCandidates(null, null);
    }

    /**
     * 최신 공고 텍스트의 제목 줄에서 회사/직무 후보를 결정적으로 파싱한다(read-only·LLM 0).
     * URL 추출 텍스트는 "제목\n\n본문한줄" 구조(JobPostingTextExtractor.extractUrl)라 첫 줄이 페이지 제목.
     * 채용 사이트 제목 관례 2가지(사람인 대괄호형·잡코리아 "회사 채용 -"형)만 보고, 그 외는 후보 없음.
     */
    private TitleCandidates onboardingTitleCandidates(Long userId, Long caseId) {
        String title = onboardingPostingTitle(userId, caseId);
        if (title == null) {
            return TitleCandidates.EMPTY;
        }
        String t = TITLE_SITE_SUFFIX.matcher(title).replaceFirst("").trim();
        Matcher bracket = TITLE_BRACKET_COMPANY.matcher(t);
        if (bracket.matches()) {
            String company = cleanTitleCandidate(bracket.group(1));
            String jobTitle = cleanTitleCandidate(TITLE_D_DAY.matcher(bracket.group(2)).replaceFirst(""));
            return new TitleCandidates(company, stripCompanyDuplication(company, jobTitle));
        }
        Matcher chaeyong = TITLE_COMPANY_CHAEYONG.matcher(t);
        if (chaeyong.matches()) {
            String rest = chaeyong.group(2) == null ? "" : chaeyong.group(2);
            String company = cleanTitleCandidate(chaeyong.group(1));
            String jobTitle = cleanTitleCandidate(TITLE_D_DAY.matcher(rest).replaceFirst(""));
            return new TitleCandidates(company, stripCompanyDuplication(company, jobTitle));
        }
        return TitleCandidates.EMPTY;
    }

    /**
     * 잡코리아형 "{회사} 채용 - {회사} {직무}"처럼 채용 사이트가 직무 문자열 앞에 회사명을 다시 붙이는
     * 경우, 직무 후보에서 그 중복을 제거한다(배너·확인 문구가 "회사 · 회사 직무" 식으로 겹치는 것 방지).
     * 회사명을 떼어내고 남는 게 없으면(완전 동일 문자열) 후보 자체를 버린다(null — 지어내지 않는다).
     */
    private static String stripCompanyDuplication(String company, String jobTitle) {
        if (company == null || jobTitle == null) {
            return jobTitle;
        }
        String stripped = jobTitle;
        if (stripped.equals(company)) {
            return null;
        }
        if (stripped.startsWith(company)) {
            stripped = stripped.substring(company.length()).trim();
        }
        return cleanTitleCandidate(stripped);
    }

    /** 최신 공고의 첫 비공백 줄(=제목). 200자 초과(붙여넣기 본문 덩어리)면 제목이 아니라고 보고 버린다. 실패는 조용히 null. */
    private String onboardingPostingTitle(Long userId, Long caseId) {
        try {
            JobPostingResponse posting = jobPostingService.getJobPosting(userId, caseId);
            String text = posting.extractedText() == null || posting.extractedText().isBlank()
                    ? posting.originalText() : posting.extractedText();
            if (text == null || text.isBlank()) {
                return null;
            }
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed.length() > 200 ? null : trimmed;
                }
            }
            return null;
        } catch (RuntimeException ex) {
            log.debug("온보딩 제목 후보 조회 실패(후보 없이 진행): {}", ex.getMessage());
            return null;
        }
    }

    private static String cleanTitleCandidate(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * (f) AWAIT_MODE: 마지막 단계 — 면접 mode 칩을 받아 autoPrepRequest 를 조립해 프론트 run 으로 인계한다.
     * <p>mode 칩·ready 판정은 인테이크의 권위 서비스 {@code autoPrepIntakeService.intake} 를 *직접* 부른다
     * (agent.chat·라우터 우회 = 갈래2). query=null 이면 {@code AutoPrepPlanner.parseIntent} 가 LLM 미호출로 즉시
     * 빈 의도→전체단계 → caseId+mode 만으로 결정적 ready 산출(로컬 LLM 0 검증 가능). 실제 분석 run 은 프론트가
     * autoPrepRequest 로 이어받아 기존 autoPrep 가 수행(LLM 필요·배포 확인) — 여기 범위는 ready+요청 산출까지.</p>
     * <p>★결정성(§6·발견①): mode 는 칩(selectedModeCode)으로만 확정. 자유텍스트/qwen3 추론 0 — 유효 칩이 아니면
     * 칩을 다시 제시(텍스트로 mode 를 정하지 않는다). 유효 코드 집합은 서비스가 돌려준 칩에서 그대로 가져온다(중복 정의 X).</p>
     */
    private RouteMessage onboardingModeStep(Long conversationId, AuthUser authUser, String selectedModeCode) {
        Long userId = authUser.id();
        Long caseId = intakeSlotTrace.onboardingCaseId(conversationId);
        if (caseId == null) {
            intakeSlotTrace.setOnboardingStep(conversationId, "AWAIT_POSTING");
            return new RouteMessage("④온보딩:공고요청",
                    "공고 정보를 다시 받아야 할 것 같아요. 공고 전문을 붙여넣어 주세요.");
        }
        // case 만 바운드하고 mode 는 비워 부르면(권위 서비스) nextAsk=MODE + 6칩(MODE_OPTIONS)을 결정적으로 돌려준다.
        AutoPrepIntakeResponse modeResp = autoPrepIntakeService.intake(
                userId, new AutoPrepRequest(null, caseId, null, null, null));
        List<AutoPrepIntakeResponse.ModeOption> chips = modeResp.modes() == null ? List.of() : modeResp.modes();
        if (chips.isEmpty()) {   // 방어: case 미소유 등으로 MODE 단계가 안 나오면 재질문
            return new RouteMessage("④온보딩:모드선택",
                    "면접 모드를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        Set<String> validCodes = chips.stream()
                .map(m -> m.code().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String code = selectedModeCode == null ? null : selectedModeCode.trim().toUpperCase(Locale.ROOT);
        if (code == null || !validCodes.contains(code)) {
            // ★발견① — 칩만. 텍스트/qwen3 로 mode 추론하지 않고 칩을 (다시) 제시한다.
            return new RouteMessage("④온보딩:모드선택",
                    "마지막으로, 면접 모드를 골라주세요.",
                    new ChatAskResponse.IntakeStep(false, "MODE", null, List.of(), toModes(chips)),
                    true);
        }
        // 유효 칩 선택 → caseId+mode 로 autoPrepRequest 조립(=enterIntake 와 동일 구조). query=null(전체 준비·LLM 0).
        AutoPrepRequest autoPrepRequest = new AutoPrepRequest(null, caseId, code, null, null);
        intakeSlotTrace.setOnboardingStep(conversationId, "DONE");   // 종단 → 다음 턴부터 sticky 풀림
        // (구조점검 △ 수리B) 종단 직전 — 슬롯이 지워지기 전에 수집 요약을 대화 메모리에 남긴다.
        injectOnboardingSummaryIntoMemory(conversationId, userId, caseId);
        return new RouteMessage("④온보딩:면접인계",
                "면접 준비를 시작할게요!",
                new ChatAskResponse.IntakeStep(true, null, autoPrepRequest, List.of(), List.of()),
                true);
    }

    /**
     * (구조점검 △ 수리B) ④ 완료(DONE) 직전 — 수집한 직무·기술·공고를 대화 메모리에 한 줄 요약으로 남긴다.
     * ④는 전용 인메모리 슬롯(IntakeSlotTrace)만 쓰고 LangChain4j 메모리를 안 거쳐서, sticky 가 풀린 뒤
     * 잡담·커뮤니티 에이전트가 "아까 무슨 직무라고 했는지" 전혀 기억 못 하는 비대칭이 있었다(구조점검
     * §2-6). AiMessage 로 붙여 다음 턴부터 에이전트 컨텍스트 창(20)에 자연스럽게 포함되게 한다.
     * 회사/직무는 B.getApplicationCase read 만(무수정) — DEFAULT("확인 필요") 값이면 생략(지어내지 않음).
     * 실패해도 온보딩 완료 자체는 막지 않는다(요약은 부가 정보 — 조용히 skip).
     */
    private void injectOnboardingSummaryIntoMemory(Long conversationId, Long userId, Long caseId) {
        try {
            IntakeSlotTrace.OnboardingCollected got = intakeSlotTrace.onboarding(conversationId);
            boolean hasJob = got.job() != null && !got.job().isBlank();
            boolean hasSkills = got.skills() != null && !got.skills().isBlank();
            if (!hasJob && !hasSkills) {
                return;
            }
            ApplicationCaseResponse c = applicationCaseService.get(userId, caseId);
            StringBuilder sb = new StringBuilder("(온보딩 수집:");
            if (hasJob) {
                sb.append(" ").append(got.job().trim()).append(" 지망,");
            }
            if (hasSkills) {
                sb.append(" 기술 ").append(got.skills().trim()).append(",");
            }
            if (c.companyName() != null && !ONB_DEFAULT_COMPANY.equals(c.companyName())) {
                sb.append(" 공고 ").append(c.companyName());
                if (c.jobTitle() != null && !ONB_DEFAULT_JOBTITLE.equals(c.jobTitle())) {
                    sb.append(" ").append(c.jobTitle());
                }
                sb.append(",");
            }
            sb.setLength(sb.length() - 1); // 마지막 쉼표 제거
            sb.append(")");

            List<ChatMessage> messages = new ArrayList<>(memoryStore.getMessages(conversationId));
            messages.add(AiMessage.from(sb.toString()));
            memoryStore.updateMessages(conversationId, messages);
        } catch (RuntimeException ex) {
            log.warn("온보딩 요약 메모리 주입 실패(온보딩 완료는 정상 진행): {}", ex.getMessage());
        }
    }

    /**
     * (e) 슬롯에 모은 직무·기술을 A.ProfileService.save 로 *한 번* 저장(전체 덮어쓰기 upsert). A 코드 무수정 — 호출만.
     * ★skills 변환: 슬롯은 자유텍스트("Java, Spring") → 토큰화해 List 로 넘겨야 JSON *배열*로 저장돼 FIT 가 읽는
     *   배열과 일치한다(그대로 String 으로 주면 JSON 문자열로 저장돼 FIT 매칭 깨짐 — 측정). FIT 매칭은 스킬 *완전일치*
     *   집합이라(Mock 엔진) 한 원소에 여러 스킬이 뭉치면 매칭 0 → 콤마 외 줄바꿈·/·중점·;·"및"·"그리고"도 구분자로 보고,
     *   대소문자 무시 중복은 제거한다(공백 분리는 "Spring Boot" 같은 복합어를 깨므로 제외). 직무·기술 외 필드는
     *   null(전 필드 nullable). 저장 실패해도 case 는 이미 만들어졌으므로 CASE_READY 는 진행(로그만) — 프로필은
     *   이후 /profile 에서 보완 가능. 게이트(누가 온보딩 타나)와는 분리된 로직이다.
     */
    private void saveOnboardingProfile(Long conversationId, AuthUser authUser) {
        IntakeSlotTrace.OnboardingCollected got = intakeSlotTrace.onboarding(conversationId);
        String desiredJob = got.job() == null || got.job().isBlank() ? null : got.job().trim();
        List<String> skills = got.skills() == null ? List.of()
                : Arrays.stream(got.skills().split("[,\\n/·;]+|\\s+및\\s+|\\s+그리고\\s+"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toMap(
                                s -> s.toLowerCase(),   // 대소문자 무시 dedup, 첫 등장 원문·순서 보존
                                s -> s,
                                (first, dup) -> first,
                                java.util.LinkedHashMap::new))
                        .values().stream().toList();
        try {
            profileService.save(authUser, new UserProfileRequest(
                    desiredJob, null, null, null, null, skills,
                    null, null, null, null, null, null));
        } catch (RuntimeException ex) {
            log.warn("온보딩 프로필 저장 실패(case 는 생성됨, CASE_READY 진행): {}", ex.getMessage());
        }
    }

    /**
     * (d)(f) 온보딩 턴 결과: 라우트 태그 + 사용자 메시지 + (d″) 후보 칩(quickReplies) + (f) 인계용
     * IntakeStep·오케 배너 플래그. 대부분 분기는 2-arg; 확인-회사/직무 턴은 후보 칩을, mode 칩 제시·면접
     * 인계 턴은 intake/inOrchestration 을 채운다.
     */
    private record RouteMessage(String route, String message, List<String> quickReplies,
                               ChatAskResponse.IntakeStep intake, boolean inOrchestration) {
        RouteMessage(String route, String message) {
            this(route, message, List.of(), null, false);
        }

        RouteMessage(String route, String message, List<String> quickReplies) {
            this(route, message, quickReplies, null, false);
        }

        RouteMessage(String route, String message, ChatAskResponse.IntakeStep intake, boolean inOrchestration) {
            this(route, message, List.of(), intake, inOrchestration);
        }
    }

    /**
     * (a) 깡통계정 온보딩 게이트 판정: 프로필 행 없음 + 지원 건 0건. 코드(DB 조회)로 결정 — 모델(qwen3)에 안 물음(§6-2).
     * 비로그인(userId==null)은 대상 아님(프로필 저장에 사용자 필요 + 챗봇은 인증 필수). 조회 실패 시 false(보수적: 온보딩 미진입, 기존 흐름).
     * A(ProfileMapper)·B(ApplicationCaseService)의 기존 read 메서드 호출만 — 그쪽 코드 무수정.
     */
    /**
     * (d)(f) 온보딩이 진행 중인가 — 단계가 설정됐고 아직 종단(DONE=면접 인계 완료)이 아니면 true.
     * case 생성 후 게이트(깡통 판정)가 false 가 돼도 추출 폴링·보정·mode 선택을 이어가도록 sticky 라우팅의 근거가 된다.
     * 인메모리 슬롯 기준이라 백엔드 재시작 시 휘발(MVP — 온보딩 중간이탈 복원은 명시적 제외, 설계 §1).
     */
    private boolean isOnboardingInProgress(Long conversationId) {
        String step = intakeSlotTrace.onboardingStep(conversationId);
        return step != null && !"DONE".equals(step);
    }

    /** 이 대화가 "그만"으로 온보딩을 거부했는지(DB 영속 권위). 거부 후엔 깡통계정이어도 재진입하지 않는다. */
    private boolean isOnboardingDeclined(Long conversationId) {
        return memoryStore.isOnboardingDeclined(conversationId);
    }

    private boolean isBlankAccountForOnboarding(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return profileMapper.findByUserId(userId) == null
                    && applicationCaseService.list(userId, null, false).isEmpty();
        } catch (RuntimeException ex) {
            log.warn("온보딩 게이트 판정 실패(온보딩 미진입으로 처리): {}", ex.getMessage());
            return false;
        }
    }

    /**
     * (g″) declined(온보딩 그만) + 여전히 깡통계정인 대화에서 재시작 의도 발화를 확인 1턴으로 구제한다.
     * "네"면 declined 를 해제하고 step==null 진입 경로로 바로 온보딩을 시작, "아니요"면 declined 유지 안내.
     * 재시작 의도도 확인 대기도 아니면 null — 호출부가 기존 라우팅(FAQ/에이전트 등)으로 그대로 흘려보낸다.
     */
    private ApiResponse<ChatAskResponse> tryOnboardingRestartFromDeclined(
            Long conversationId, Long userId, AuthUser authUser, String question) {
        if (onboardingRestartStore.consume(conversationId)) {
            if (isRestartConfirmYes(question)) {
                memoryStore.clearOnboardingDeclined(conversationId);
                return ApiResponse.ok(onboardingTurn(conversationId, authUser, "", null, null));
            }
            if (isRestartConfirmNo(question)) {
                return ApiResponse.ok(new ChatAskResponse(conversationId,
                        "알겠어요, 필요하면 언제든 다시 말씀해 주세요.",
                        List.of(), List.of(), "④온보딩:재시작거부", null, false, null));
            }
            return null; // 확인 대기였는데 yes/no 아님 → 소비만 하고 일반 라우팅으로.
        }
        if (isRestartIntent(question)) {
            onboardingRestartStore.defer(conversationId);
            return ApiResponse.ok(new ChatAskResponse(conversationId,
                    "그만두셨었는데, 다시 시작할까요?",
                    List.of(), List.of(RESTART_YES, RESTART_NO), "④온보딩:재시작확인(거부복귀)", null, false, null));
        }
        return null;
    }

    /**
     * 사용자 질문 → 통합 라우팅 → ① 커뮤니티 FAQ/에이전트 또는 ③ 인테이크 입구.
     * POST /api/chatbot/ask  body: { question, conversationId? }
     *
     * <p><b>라우팅(첫 턴 판정 전용):</b> nav fast-path 유지 → faqScore vs intakeScore 비교 →
     * 명확구역(|diff|≥0.1) argmax 로 결정적 라우팅, 경계구역만 화행분류 1회. COMMAND 는 비대칭 안전판으로
     * 명확구역이면 ③ 즉시 진입, 경계구역이면 확인 1턴을 띄운다. 인테이크 진입 후 sticky 유지는 다음 PR.</p>
     */
    @PostMapping("/chatbot/ask")
    public ApiResponse<ChatAskResponse> ask(@RequestBody ChatAskRequest req,
                                            @AuthenticationPrincipal AuthUser authUser) {
        if (req == null || req.question() == null || req.question().isBlank()) {
            return ApiResponse.error("BAD_REQUEST", "질문을 입력해 주세요.");
        }

        // 로그인 시 user_id 기록 → 나중에 "이전 대화 복원" 대상이 된다. 비로그인은 null(익명).
        Long userId = authUser != null ? authUser.id() : null;
        Long conversationId = req.conversationId() != null
                ? req.conversationId()
                : memoryStore.createConversation(userId);
        String question = req.question();

        // 소유권 가드(IDOR 방어): 클라이언트가 '기존' conversationId 를 보냈을 때만 검사한다.
        //  - owner != null(실유저 소유 대화) → 본인만 접근. 비로그인은 로그인 유도(에러 아님), 타유저는 거부.
        //  - owner == null(익명 대화 or 미존재 행) → 통과 = 비로그인 FAQ 다중턴 보존(permitAll 유지).
        //  - 신규(req.conversationId()==null)는 위에서 본인 소유 새 id 가 발급됐으므로 검사 불필요.
        // 소유 판정은 조회(GET conversationMessages)와 동일한 memoryStore.findOwnerUserId 재사용.
        if (req.conversationId() != null) {
            Long owner = memoryStore.findOwnerUserId(conversationId);
            if (owner != null && !owner.equals(userId)) {
                if (userId == null) {
                    return ApiResponse.ok(new ChatAskResponse(
                            conversationId,
                            "로그인하면 이전 대화를 이어갈 수 있어요. 로그인 후 다시 시도해 주세요.",
                            List.of(), List.of("로그인"), "로그인필요", null, false, null));
                }
                return ApiResponse.error("FORBIDDEN", "접근할 수 없는 대화입니다.");
            }
        }

        // 이탈 신호("그만"/⏏): 라우터·온보딩 게이트 전에 즉시 복귀. (status 3단계 — sticky/영속/온보딩을 한 핸들러로 통합)
        //  ⓐ 온보딩(깡통계정 또는 진행 중): "그만" 거부를 DB 에 영속(onboarding_declined_at)해 재시작·재진입까지 막고,
        //     인메모리 onboarding 슬롯은 정리한다(권위 = DB). 이 핸들러가 onboardingTurn 보다 위라 슬롯 오염도 동시 차단.
        //  ⓑ 일반 인테이크(메모리 sticky 또는 DB PENDING/READY): 기존대로 sticky 해제 + 세션 DONE 으로 닫음(재복원·FALLBACK 차단).
        if (req.conversationId() != null && isExitCommand(question)) {
            if (isOnboardingInProgress(conversationId) || isBlankAccountForOnboarding(userId)) {
                memoryStore.markOnboardingDeclined(conversationId);
                intakeSlotTrace.clearOnboarding(conversationId);
                intakeModeStore.exit(conversationId); // 혹시 같이 떠 있던 sticky 도 보수적 정리
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "온보딩을 건너뛸게요. 언제든 다시 시작할 수 있어요. 궁금한 거 있으면 편하게 물어봐 주세요.",
                        List.of(), List.of(), "온보딩이탈", null, false, null));
            }
            if (intakeModeStore.isActive(conversationId)
                    || intakeAskService.hasOpenIntakeSlot(conversationId)) {
                intakeModeStore.exit(conversationId);
                intakeAskService.closeIntakeSession(conversationId);
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "일반 상담 모드로 돌아왔어요. 무엇이든 물어보세요.",
                        List.of(), List.of(), "이탈", null, false, null));
            }
        }

        // 영속 세션 복원: 재시작/재방문으로 메모리 sticky 는 없지만 DB 에 PENDING 인테이크(지원건) 세션이면
        // sticky 로 되살려 아래 블록이 (질문 가드 포함) 동일하게 처리한다. READY/DONE 은 isPersistedIntakeSession=false.
        String intakeRoute = "③(유지)";
        if (!intakeModeStore.isActive(conversationId)
                && req.conversationId() != null
                && intakeAskService.isPersistedIntakeSession(conversationId)) {
            intakeModeStore.enter(conversationId);
            intakeRoute = "③(복원)";
        }

        // sticky 모드(오케스트레이터 유지): 이미 ③ 에 머무는 대화는 라우팅·FAQ·NAV 를 전부 건너뛰고 ③ 직행한다.
        // (이탈은 위에서 이미 처리됨 — 여기 도달하면 이탈 신호 아님.)
        // ── (g) 이탈성 질문 가드: 대기 중 FAQ성 질문은 삼키지 않고 확인 1턴 → "네"면 한 턴만 ① 우회 후 복귀.
        //    인테이크 상태(sticky·슬롯·nextAsk 프로토콜)는 어느 분기에서도 안 건드린다.
        if (intakeModeStore.isActive(conversationId)) {
            String effectiveQuestion = question;
            String deferred = sideQuestionStore.consume(conversationId);
            if (deferred != null && isSideQuestionYes(question)) {
                return ApiResponse.ok(withResumeHint(faqPath(conversationId, deferred, userId, "③우회①")));
            }
            if (deferred != null && isSideQuestionNo(question)) {
                effectiveQuestion = deferred; // 원 발화를 인테이크 답변으로 이어서 처리
            } else if (deferred == null
                    && req.selectedCaseId() == null && req.selectedModeCode() == null
                    && looksLikeSideQuestion(question)) {
                sideQuestionStore.defer(conversationId, question);
                return ApiResponse.ok(new ChatAskResponse(conversationId,
                        "잠깐 — 질문이신 것 같아요. 준비를 잠시 멈추고 답해드릴까요?",
                        List.of(), List.of(SIDE_Q_YES, SIDE_Q_NO),
                        "③질문확인", null, true, null));
            }
            return ApiResponse.ok(enterIntake(conversationId, effectiveQuestion, userId, intakeRoute,
                    req.selectedCaseId(), req.selectedModeCode()));
        }

        // (d) 온보딩 "진행 중"(sticky)은 nav fast-path 보다 먼저 — 진행 중 답변("공고 링크로 올렸어요",
        //     회사명 칩 텍스트 등)이 내비 키워드에 걸려 온보딩 밖으로 새는 것을 막는다
        //     (실측: "공고 링크로 올렸어요" → NAV "지원 관리" 즉답이 턴·selectedCaseId 를 삼킴).
        //     첫 진입(깡통 판정)은 기존 위치 유지 — 순수 내비 질문은 온보딩 시작 전엔 즉답이 맞다.
        if (authUser != null && isOnboardingInProgress(conversationId)) {
            return ApiResponse.ok(onboardingTurn(conversationId, authUser, question,
                    req.selectedModeCode(), req.selectedCaseId()));
        }

        // (g″) declined 재시작 구제책: "그만"으로 거부한 대화(여전히 깡통)에서도 재시작 의도 발화면
        //    확인 1턴으로 declined 를 해제한다. 대상 아니면 null 을 돌려 아래 정상 라우팅으로 통과시킨다.
        if (authUser != null && isOnboardingDeclined(conversationId) && isBlankAccountForOnboarding(userId)) {
            ApiResponse<ChatAskResponse> restart =
                    tryOnboardingRestartFromDeclined(conversationId, userId, authUser, question);
            if (restart != null) {
                return restart;
            }
        }

        // Fast-path: 순수 내비 질의는 LLM·검색 우회 즉답 (서버 신뢰 링크라 화이트리스트 검증 생략).
        Optional<ChatResponse> fast = fastPathService.tryFastPath(question);
        if (fast.isPresent()) {
            ChatResponse fr = fast.get();
            responseLogService.record(conversationId, userId, question,
                    "NAV_FAST", false, null, null, false);
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId, fr.message(), fr.links(), fr.quickReplies(), "NAV", null, false, null));
        }

        // (a)(b)(d) 깡통계정 온보딩 "첫 진입": 프로필 행 없음 + 지원 건 0건 = 순수 깡통 → 온보딩(직무→기술→공고).
        //   여기까지 온 건 exit/sticky/DB복원/fastPath 가 아닌 신규 라우팅 턴 — 비-깡통은 false 로 통과해 아래 기존 흐름.
        //   sticky(intakeModeStore=인테이크 case 흐름) 안 건드림. ★진행 중(step 설정~DONE 전) sticky 는 위(fast-path 앞)
        //   에서 처리 — 여기는 첫 진입 판정만 남는다. (f) 면접 인계로 DONE 되면 sticky 가 풀린다.
        //  ★ 거부 영속 차단: "그만"으로 온보딩을 거부한 대화는 깡통계정이어도(게이트 true) 재진입하지 않는다.
        //    declined 조회(DB)는 온보딩 후보일 때만 타도록 AND 뒤(단축평가)에 둔다 — 일반 유저는 조회 0.
        if (authUser != null
                && isBlankAccountForOnboarding(userId)
                && !isOnboardingDeclined(conversationId)) {
            return ApiResponse.ok(onboardingTurn(conversationId, authUser, question,
                    req.selectedModeCode(), req.selectedCaseId()));
        }

        // (c) 국면 밖 확인 안전망: 여기까지 왔는데 칩/버튼 선택(selectedCaseId·selectedModeCode)이 실려 있으면
        //     이 대화는 더는 진행 중인 인테이크가 아니다(복원했더니 이미 READY/DONE 이거나 만료 — sticky 미활성,
        //     온보딩도 아님). qwen3/FAQ 로 흘러 오답·범용 에러가 나기 전에, 정직하게 안내하고 재시작
        //     화이트리스트("면접 준비해줘")로 유도한다. (활성/복원-PENDING 인테이크였다면 위 sticky 블록에서 이미 처리됨.)
        if (req.conversationId() != null
                && (req.selectedCaseId() != null || req.selectedModeCode() != null)) {
            return ApiResponse.ok(new ChatAskResponse(
                    conversationId,
                    "이 준비는 이미 진행됐거나 대화가 만료됐어요. 이어서 하려면 “면접 준비해줘”라고 말씀해 주세요.",
                    List.of(), List.of("면접 준비해줘"), "③(만료)", null, false, null));
        }

        // 확인 대기(1턴) 소비: 이 턴은 라우팅을 돌리지 않는다. (오분류 안전판 A)
        if (routeConfirmStore.consumePending(conversationId)) {
            if (isAffirmative(question)) {
                return ApiResponse.ok(enterIntake(conversationId, question, userId, "③(확인후)",
                        req.selectedCaseId(), req.selectedModeCode()));
            }
            return ApiResponse.ok(faqPath(conversationId, question, userId, "①(확인후)"));
        }

        // 통합 라우팅 판정.
        UnifiedChatRouter.Decision d = router.decide(question);
        switch (d.target()) {
            case INTAKE_DIRECT -> {
                return ApiResponse.ok(enterIntake(conversationId, question, userId, "③",
                        req.selectedCaseId(), req.selectedModeCode()));
            }
            case INTAKE_CONFIRM -> {
                routeConfirmStore.markPending(conversationId);
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "면접 준비를 도와드릴까요?",
                        List.of(),
                        List.of("시작", "그냥 질문이에요"),
                        "확인반환",
                        null,
                        false,
                        null));
            }
            case FALLBACK -> {
                // 약신호(FAQ도 의도도 불명확) → 에이전트로 보내지 않고 정중한 되묻기로 끊는다.
                return ApiResponse.ok(new ChatAskResponse(
                        conversationId,
                        "질문을 정확히 이해하지 못했어요. 좀 더 구체적으로 말씀해 주시겠어요? "
                                + "(예: \"환불 어떻게 해요\" 같은 이용 문의, 또는 \"네이버 백엔드 면접 준비해줘\" 같은 작업 요청)",
                        List.of(),
                        List.of(),
                        "되묻기",
                        null,
                        false,
                        null));
            }
            case AGENT -> {
                // catch-all: 커뮤니티 글 검색·인사·잡담 → FAQ 게이트 우회하고 에이전트 직행.
                return ApiResponse.ok(agentPath(conversationId, question, userId, "①에이전트"));
            }
            default -> {
                // FAQ Target → faqPath 게이트(즉답/미달 시 에이전트).
                return ApiResponse.ok(faqPath(conversationId, question, userId, "①"));
            }
        }
    }

    /** 확인 1턴 긍정 판정 — 화이트리스트 정확일치만(문장은 어느 항목과도 정확일치 안 해 자동 탈락 = 오탐 0). */
    static boolean isAffirmative(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        return AFFIRMATIVE.contains(norm);
    }

    /**
     * 모드 활성 중 이탈 신호 판정("그만"/"취소"/⏏ 등). 키워드 정확일치 또는 키워드+허용접미사(EXIT_SUFFIXES)일 때만.
     * 부분문자열 contains 를 버려 "중단된 프로젝트"·"종료된 공고"·"환불 취소 절차" 같은 답변의 오탐을 막는다.
     */
    static boolean isExitCommand(String question) {
        if (question == null) {
            return false;
        }
        String norm = question.trim().toLowerCase().replace(" ", "");
        for (String kw : EXIT_COMMANDS) {
            if (norm.equals(kw)) {
                return true;
            }
            if (norm.startsWith(kw) && norm.length() > kw.length()
                    && EXIT_SUFFIXES.getOrDefault(kw, Set.of()).contains(norm.substring(kw.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * ③ 인테이크 입구로 데려다주고(한 턴) sticky 모드를 갱신한다.
     * ready 면 RUN 을 프런트 SSE 가 이어받으므로 sticky 종료, 아니면 모드 유지(다음 턴도 ③ 직행).
     * inOrchestration 은 항상 true — ready 전환 턴도 위젯이 배너를 유지한 채 실행 화면으로 넘어가야 하므로.
     */
    private ChatAskResponse enterIntake(Long conversationId, String question, Long userId, String route,
                                        Long selectedCaseId, String selectedModeCode) {
        IntakeAskResponse r = intakeAskService.ask(userId, question, conversationId, selectedCaseId, selectedModeCode);
        // 지원건 세션 fork 가 일어나면 응답 conversationId 가 새 id 로 바뀐다 — sticky 도 새 id 기준으로 옮긴다.
        Long effectiveId = r.conversationId();
        if (effectiveId != null && !effectiveId.equals(conversationId)) {
            intakeModeStore.exit(conversationId); // 원(잡담) 대화의 sticky 정리
        }
        if (r.ready()) {
            intakeModeStore.exit(effectiveId);
        } else {
            intakeModeStore.enter(effectiveId);
        }
        return new ChatAskResponse(
                effectiveId,
                r.message(),
                List.of(),
                List.of(),
                route,
                new ChatAskResponse.IntakeStep(
                        r.ready(), r.nextAsk(), r.autoPrepRequest(),
                        toCandidates(r.candidates()), toModes(r.modes())),
                true,
                null);
    }

    /** 지원 건 후보 → 칩 렌더용 최소 필드로 축약. */
    private List<ChatAskResponse.CaseCandidate> toCandidates(List<ApplicationCaseResponse> cases) {
        if (cases == null) {
            return List.of();
        }
        return cases.stream()
                .map(c -> new ChatAskResponse.CaseCandidate(
                        c.id(), c.companyName(), c.jobTitle(), c.status()))
                .collect(Collectors.toList());
    }

    /** 면접 모드 선택지 → 칩 렌더용으로 변환. */
    private List<ChatAskResponse.ModeOption> toModes(List<AutoPrepIntakeResponse.ModeOption> modes) {
        if (modes == null) {
            return List.of();
        }
        return modes.stream()
                .map(m -> new ChatAskResponse.ModeOption(m.code(), m.label()))
                .collect(Collectors.toList());
    }

    /**
     * ① 커뮤니티 FAQ/에이전트 경로(기존 동작 보존).
     * FAQ 임베딩 게이트가 top-1 코사인 ≥ 임계를 통과하면 결정적 FAQ 즉답, 미달이면 에이전트(툴 호출).
     */
    private ChatAskResponse faqPath(Long conversationId, String question, Long userId, String route) {
        // FAQ 임베딩 게이트: 질문 원문 임베딩 1회로 top-1 FAQ 코사인 점수를 보고 결정적으로 분기한다.
        // (키워드 isFaqIntent 대체 — LLM 을 FAQ 판정 경로에서 빼서 검색어 재생성 flip 비결정성을 구조적으로 제거.)
        List<FaqHit> faqHits = chatbotService.searchFaqHits(question);
        double faqGate = chatbotProperties.getFaqGateThreshold();
        if (!faqHits.isEmpty() && faqHits.get(0).score() >= faqGate) {
            return faqAnswerFrom(conversationId, question, userId, faqHits, route);
        }
        // 게이트 미달 → 커뮤니티 에이전트(통합 라우터 AGENT catch-all 과 동일 경로).
        return agentPath(conversationId, question, userId, route);
    }

    /**
     * ① 커뮤니티 에이전트 직행(FAQ 게이트 우회). {@link #faqPath} 게이트 미달과 통합 라우터 AGENT(catch-all)가 공유.
     * 글 검색(searchCommunityPosts)·인사/잡담 자연응답은 에이전트가 스스로 판단해 처리한다(시스템 프롬프트).
     * 원문 raw top-1 로 운영 FAQ공백 수집을 보존한다.
     */
    private ChatAskResponse agentPath(Long conversationId, String question, Long userId, String route) {
        searchTrace.clear();
        try {
            ChatbotService.FaqMiss miss = null;
            try {
                miss = chatbotService.analyzeMiss(question);
            } catch (Exception e) {
                // 임베딩/Ollama 이상 → FAQ 공백 수집만 스킵(챗봇 응답엔 무관).
                log.debug("미스 분석 스킵(임베딩/Ollama 이상): {}", e.getMessage());
            }
            if (miss != null) {
                unansweredQuestionService.record(question, miss.topSimilarity(),
                        miss.embeddingJson(), miss.bestFaqId(), userId, conversationId);
            }

            // 에이전트는 String 반환(툴 정상 동작). 메시지는 자유 생성 → 마크다운 잔재 평문화.
            String message = MessageSanitizer.stripMarkdown(agent.chat(conversationId, question));

            // links 접지: 모델 JSON 이 아니라 이번 턴에 툴이 실제로 돌려준 출처(커뮤니티 글 + FAQ 링크)에서만 생성.
            List<SiteLink> links = collectLinks();

            // quickReplies: 보조 기능 → 2차 호출이 실패해도 핵심(message+links)은 정상 반환.
            // 글 제시 여부를 finally clear 전에 읽어 게이트로 넘긴다(이번 턴 검색 툴이 글을 돌려줬을 때만 글 칩 허용).
            boolean postsPresented = !searchTrace.snapshot().isEmpty();
            List<String> quickReplies = suggestQuickReplies(userId, conversationId, question, message, postsPresented);

            // 글이 2개 이상 제시된 턴에서만 묶음 요약 칩 주입(snapshot 은 finally clear 전에 읽는다).
            ChatAskResponse.SummaryChip summaryChip = buildSummaryChip(searchTrace.snapshot());

            // best-effort 적재(AGENT). FAQ 근거 여부 = 이번 턴 searchFaq 링크 존재(finally clear 전에 읽음).
            // 유사도 = 게이트 산출 원문 raw top-1(없으면 null). 전환 없음.
            responseLogService.record(conversationId, userId, question,
                    "AGENT", !searchTrace.faqLinks().isEmpty(),
                    miss != null ? miss.topSimilarity() : null, null, false);

            return new ChatAskResponse(conversationId, message, links, quickReplies, route, null, false, summaryChip);
        } catch (Exception e) {
            log.error("챗봇 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return new ChatAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    List.of(),
                    List.of(),
                    route,
                    null,
                    false,
                    null);
        } finally {
            searchTrace.clear();
        }
    }

    /**
     * 로그인 유저의 가장 최근 대화를 복원한다(이어보기/이어가기).
     * GET /api/chatbot/conversations/recent  (인증 필요 — SecurityConfig permitAll 미포함)
     *
     * <p><b>④ 재진입 재개(F-06):</b> ④턴은 설계상 LLM 메모리 미기록(완료 시 요약 1줄만)이라 진행 중
     * 새로고침 후 messages 가 비어 프론트가 대화를 버리고 새 대화를 발급 — 인메모리 step 이 고아가 됐다.
     * 온보딩 진행 중이면 현재 스텝 재표시를 resume 으로 동봉해 프론트가 같은 대화에 이어붙이게 한다.
     * (재표시 없이 id 만 입양하면 사용자가 "보이지 않는 질문"에 답하게 돼 JOB/SKILLS 오기록이 남 — 필수 짝.)</p>
     */
    @GetMapping("/chatbot/conversations/recent")
    public ApiResponse<ChatHistoryResponse> recentConversation(@AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ApiResponse.ok(null); // 비로그인: 복원 없음(실제론 인증 필터에서 차단)
        }
        Long conversationId = memoryStore.findRecentConversation(authUser.id());
        if (conversationId == null) {
            return ApiResponse.ok(null); // 이전 대화 없음 → 빈 채팅으로 시작
        }
        List<ChatHistoryMessage> messages = memoryStore.getMessages(conversationId).stream()
                .map(this::toHistoryMessage)
                .filter(m -> m != null)
                .collect(Collectors.toList());
        ChatHistoryResponse.ResumePrompt resume = null;
        if (isOnboardingInProgress(conversationId)) {
            try {
                String step = intakeSlotTrace.onboardingStep(conversationId);
                // 기존 재표시 경로 재사용(재시작 "아니요" 복귀와 동일 의미). EXTRACTING 은 추출 상태를
                // 재조회하므로 자리 비운 사이 끝난 추출을 다음 질문으로 바로 이어준다.
                RouteMessage rm = redisplayCurrentStep(conversationId, authUser, step);
                resume = new ChatHistoryResponse.ResumePrompt(
                        rm.route(), rm.message(), rm.quickReplies(), rm.intake());
            } catch (RuntimeException ex) {
                log.warn("온보딩 재개 프롬프트 조립 실패(복원 자체는 진행): {}", ex.getMessage());
            }
        }
        return ApiResponse.ok(new ChatHistoryResponse(conversationId, messages, resume));
    }

    /**
     * 세션 목록(사이드바): 로그인 유저의 인테이크(지원건) 세션 최대 5건(application_case_id 있는 것만, 최근순).
     * 잡담/FAQ 대화는 application_case_id NULL 이라 자연 제외된다.
     * GET /api/chatbot/conversations
     */
    @GetMapping("/chatbot/conversations")
    public ApiResponse<List<ChatSessionSummary>> listConversations(@AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ApiResponse.ok(List.of());
        }
        List<ChatSessionSummary> sessions = memoryStore.listIntakeSessions(authUser.id()).stream()
                .map(r -> new ChatSessionSummary(
                        ((Number) r.get("conversationId")).longValue(),
                        (String) r.get("title"),
                        (String) r.get("mode"),
                        toEpochMillis(r.get("updatedAt"))))
                .collect(Collectors.toList());
        return ApiResponse.ok(sessions);
    }

    /**
     * MyBatis Map 의 DATETIME 값(드라이버/버전별 {@link java.sql.Timestamp} 또는 {@link java.time.LocalDateTime})을
     * epoch millis 로 정규화한다. LocalDateTime 은 저장이 Asia/Seoul 벽시계이므로 같은 zone 으로 해석해
     * 프런트 {@code Date.now()}(UTC epoch)와의 상대시각 차이가 정확하다.
     */
    private static Long toEpochMillis(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.getTime();
        }
        if (v instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * 세션 클릭 시 그 대화의 메시지 로드(이어보기). 본인 대화만 접근 가능.
     * GET /api/chatbot/conversations/{conversationId}/messages
     */
    @GetMapping("/chatbot/conversations/{conversationId}/messages")
    public ApiResponse<ChatHistoryResponse> conversationMessages(@PathVariable Long conversationId,
                                                                 @AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ApiResponse.error("UNAUTHORIZED", "로그인이 필요합니다.");
        }
        Long owner = memoryStore.findOwnerUserId(conversationId);
        if (owner == null || !owner.equals(authUser.id())) {
            return ApiResponse.error("FORBIDDEN", "접근할 수 없는 대화입니다.");
        }
        List<ChatHistoryMessage> messages = memoryStore.getMessages(conversationId).stream()
                .map(this::toHistoryMessage)
                .filter(m -> m != null)
                .collect(Collectors.toList());
        return ApiResponse.ok(new ChatHistoryResponse(conversationId, messages));
    }

    /**
     * LangChain4j 메모리 메시지 → UI 표시용 {role,text}.
     * user/assistant 텍스트만 노출하고 System·툴호출·툴결과 메시지는 버린다(UI 비표시).
     */
    private ChatHistoryMessage toHistoryMessage(dev.langchain4j.data.message.ChatMessage m) {
        if (m instanceof UserMessage u) {
            String t = u.singleText();
            return (t == null || t.isBlank()) ? null : new ChatHistoryMessage("user", t);
        }
        if (m instanceof AiMessage a) {
            String t = a.text();
            if (t == null || t.isBlank()) {
                return null; // 툴 호출만 있는 Ai 메시지(최종 답변 아님) → 스킵
            }
            return new ChatHistoryMessage("bot", MessageSanitizer.stripMarkdown(t));
        }
        return null;
    }

    /**
     * 임베딩 게이트를 통과한 FAQ 매칭으로 즉답을 구성한다(에이전트·모델 우회 = 결정적 FAQ 경로).
     * 트리거만 임베딩 게이트로 바뀌고, 답변/링크 접지 포맷은 기존 FAQ fast-path 로직을 그대로 재사용한다.
     * hits 는 호출부가 게이트 판정에 쓴 것을 재사용 — 재검색(2회 임베딩) 없이 같은 임베딩 결과를 공유한다.
     * 질문과 직접 관련된 최상위 1건의 링크만 노출(연관 FAQ 링크 나열 금지). 이 턴은 대화 메모리에 기록하지 않는다.
     */
    private ChatAskResponse faqAnswerFrom(Long conversationId, String question, Long userId,
                                          List<FaqHit> hits, String route) {
        // 유사도 desc 정렬이므로 linkUrl 있는 첫 hit 1건만.
        List<SiteLink> links = hits.stream()
                .filter(h -> h.linkUrl() != null && !h.linkUrl().isBlank())
                .limit(1)
                .map(h -> new SiteLink(
                        h.linkLabel() != null && !h.linkLabel().isBlank() ? h.linkLabel() : h.question(),
                        h.linkUrl()))
                .collect(Collectors.toList());
        // best-effort 적재(답함=자동 해결). 영속 response_path 는 대시보드 연속성 위해 기존 FAQ_FAST 라벨 유지.
        // 유사도 = 게이트가 본 원문 top-1(같은 임베딩). 전환 없음.
        responseLogService.record(conversationId, userId, question,
                "FAQ_FAST", true, hits.get(0).score(), null, false);
        return new ChatAskResponse(conversationId, hits.get(0).answer(), links, List.of(), route, null, false, null);
    }

    /**
     * 응답 링크 = 이번 턴에 툴이 실제로 돌려준 출처(SearchTrace)에서만 생성.
     */
    private List<SiteLink> collectLinks() {
        List<SiteLink> links = searchTrace.snapshot().stream()
                .filter(h -> h != null && h.url() != null && LINK_WHITELIST.matcher(h.url()).matches())
                .map(h -> new SiteLink(h.title(), h.url()))
                .collect(Collectors.toList());
        searchTrace.faqLinks().stream()
                .filter(l -> l.url() != null && (l.url().startsWith("/") || l.url().startsWith("http")))
                .forEach(links::add);
        return links;
    }

    /**
     * 글(커뮤니티 후기/게시글)을 가리키는 칩을 식별하는 결정적 패턴.
     * qwen3 가 시스템 프롬프트 예시("이 글 요약해줘")를 맥락 무관하게 베끼는 것을 게이트로 차단하기 위함.
     * 이번 턴에 글이 실제로 제시되지 않았으면 이 패턴에 걸리는 칩은 버린다.
     */
    private static final Pattern POST_CHIP = Pattern.compile("글|후기|게시|본문");

    /** "요약" free chip 은 이제 summaryChip 이 소유 → postsPresented 와 무관하게 항상 제거. */
    private static final Pattern SUMMARY_CHIP = Pattern.compile("요약");

    /** 묶음 요약 칩 최대 글 수(top-k). */
    private static final int SUMMARY_TOP_K = 3;

    /**
     * 이번 턴 검색 스냅샷으로 묶음 요약 칩을 만든다.
     * 글이 2개 이상일 때만 앞에서부터 최대 3개 postId 를 담아 칩을 만들고, 1개 이하면 null.
     */
    private ChatAskResponse.SummaryChip buildSummaryChip(List<PostHit> snapshot) {
        if (snapshot == null || snapshot.size() < 2) {
            return null;
        }
        List<Long> postIds = snapshot.stream()
                .limit(SUMMARY_TOP_K)
                .map(PostHit::postId)
                .collect(Collectors.toList());
        String label = "추천 후기 " + postIds.size() + "개 요약";
        return new ChatAskResponse.SummaryChip(label, postIds);
    }

    /**
     * quickReplies 2차 호출. 실패는 보조 기능이므로 삼켜서 빈 리스트로(graceful degradation).
     *
     * <p><b>맥락 주입(질문8 결정):</b> 로그인 유저는 프로필+대화이력을, 비로그인은 대화맥락만 모델에 준다
     * ({@link #buildChipContext}). 케이스/현재단계는 이 경로(커뮤니티)에 바인딩돼 있지 않아 제외한다.
     *
     * <p><b>선별:</b> 모델이 매긴 relevance/importance 로 {@link QuickReplyParser#select 가변 컷}한다.
     * 절대 임계값은 안 쓴다(8B 절대점수 불안정 → 칩 깜빡임).
     *
     * <p><b>글 칩 게이트(보존):</b> 모델은 글 제시 여부를 모른 채 칩을 자유 생성하므로, 이번 턴에 글이
     * 실제로 제시됐을 때({@code postsPresented})만 글 관련 칩을 허용한다. "요약" 칩은 summaryChip 소유라 항상 제거.
     */
    private List<String> suggestQuickReplies(Long userId, Long conversationId,
                                             String question, String answer, boolean postsPresented) {
        try {
            String raw = quickReplyAgent.suggest(buildChipContext(userId, conversationId, question, answer));
            // 게이트 필터를 선별(최대 N) 전에 적용해, 유효 칩으로만 N개를 채운다.
            List<ChipSuggestion> gated = quickReplyParser.parse(raw).stream()
                    .filter(c -> c.text() != null && !SUMMARY_CHIP.matcher(c.text()).find())
                    .filter(c -> postsPresented || !POST_CHIP.matcher(c.text()).find())
                    .collect(Collectors.toList());
            return QuickReplyParser.select(gated, 3);
        } catch (Exception e) {
            log.warn("quickReplies 생성 실패(무시): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 칩 생성 맥락 조립. 비로그인은 대화맥락만, 로그인은 프로필을 더한다(최소 침습 — A 도메인 read 만).
     * 케이스/현재단계는 이 경로에 바인딩이 없어 넣지 않는다.
     */
    private String buildChipContext(Long userId, Long conversationId, String question, String answer) {
        StringBuilder sb = new StringBuilder();
        if (userId != null) {
            UserProfile p = null;
            try {
                p = profileMapper.findByUserId(userId);
            } catch (Exception e) {
                log.debug("칩 맥락 프로필 조회 스킵: {}", e.getMessage());
            }
            if (p != null) {
                sb.append("[프로필]\n");
                appendIfPresent(sb, "희망직무", p.getDesiredJob());
                appendIfPresent(sb, "희망산업", p.getDesiredIndustry());
                appendIfPresent(sb, "보유스킬", p.getSkills());
                appendIfPresent(sb, "자격증", p.getCertificates());
                appendIfPresent(sb, "자기소개", p.getSelfIntro());
                sb.append('\n');
            }
        }
        String history = recentHistoryText(conversationId, 8);
        if (!history.isBlank()) {
            sb.append("[대화맥락]\n").append(history);
        } else {
            sb.append("[대화맥락]\n사용자: ").append(question).append("\n챗봇: ").append(answer);
        }
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    /** 대화 메모리에서 최근 {@code maxLines} 줄(USER/AI 텍스트만)을 오래된→최신으로 만든다. 실패 시 빈 문자열. */
    private String recentHistoryText(Long conversationId, int maxLines) {
        if (conversationId == null) {
            return "";
        }
        try {
            List<String> lines = new ArrayList<>();
            for (ChatMessage m : memoryStore.getMessages(conversationId)) {
                if (m instanceof UserMessage um) {
                    lines.add("사용자: " + um.singleText());
                } else if (m instanceof AiMessage am && am.text() != null && !am.text().isBlank()) {
                    lines.add("챗봇: " + am.text());
                }
            }
            int from = Math.max(0, lines.size() - maxLines);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (Exception e) {
            log.debug("칩 맥락 대화이력 조회 스킵: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 추천했던 후기들을 묶어 핵심만 평문으로 압축 요약한다(summaryChip 클릭 → 이 엔드포인트).
     * POST /api/chatbot/summarize-posts  body: { conversationId?, postIds }
     *
     * <p>postIds 는 dedup 후 최대 3개만 사용한다. 비공개/삭제(getPostContent 가 "찾을 수 없"으로 시작)는 제외하고,
     * 남은 본문을 이어 요약 에이전트에 넘긴다. 응답은 묶음 요약 평문(summaryChip=null, links/quickReplies=[]).
     */
    @PostMapping("/chatbot/summarize-posts")
    public ApiResponse<ChatAskResponse> summarizePosts(@RequestBody ChatSummarizeRequest req) {
        if (req == null || req.postIds() == null || req.postIds().isEmpty()) {
            return ApiResponse.error("BAD_REQUEST", "요약할 글을 선택해 주세요.");
        }

        // dedup(순서 보존) 후 최대 3개.
        List<Long> ids = req.postIds().stream()
                .filter(id -> id != null)
                .distinct()
                .limit(SUMMARY_TOP_K)
                .collect(Collectors.toList());

        // 각 글 본문 수집 — 비공개/삭제("찾을 수 없"으로 시작)는 제외.
        List<String> bodies = ids.stream()
                .map(communityTools::getPostContent)
                .filter(c -> c != null && !c.startsWith("해당 글을 찾을 수 없"))
                .collect(Collectors.toList());

        String message;
        if (bodies.isEmpty()) {
            message = "추천했던 글을 더 이상 볼 수 없어요.";
        } else {
            try {
                String postsBlock = String.join("\n\n---\n\n", bodies);
                message = MessageSanitizer.stripMarkdown(summaryAgent.summarize(postsBlock));
            } catch (Exception e) {
                log.error("후기 묶음 요약 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
                message = "지금은 요약을 만들기 어려워요. 잠시 후 다시 시도해 주세요.";
            }
        }

        return ApiResponse.ok(new ChatAskResponse(
                req.conversationId(), message, List.of(), List.of(), "요약", null, false, null));
    }

    /**
     * 관리자: FAQ 일괄 임베딩
     * POST /api/admin/faq/embed-all
     */
    @PostMapping("/admin/faq/embed-all")
    public ApiResponse<Map<String, Object>> embedAll(
            @RequestParam(defaultValue = "false") boolean forceAll) {
        int count = chatbotService.embedAll(forceAll);
        return ApiResponse.ok(Map.of("embeddedCount", count));
    }
}
