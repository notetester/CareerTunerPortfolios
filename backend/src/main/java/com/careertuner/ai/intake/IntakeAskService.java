package com.careertuner.ai.intake;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.autoprep.CaseSlotValidator;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse.ModeOption;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.intake.dto.IntakeAskResponse;
import com.careertuner.applicationcase.dto.ApplicationCaseResponse;
import com.careertuner.applicationcase.service.ApplicationCaseService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

/**
 * 인테이크 한 턴 처리 코어. {@link IntakeController} 가 쓰던 본문을 그대로 옮긴 것으로,
 * 통합 라우터(① 입구)가 ③ 입구로 보낼 때도 같은 로직을 재사용한다(중복 제거).
 *
 * <p><b>지원건 세션 fork(Phase B):</b> 한 턴에 지원 건이 새로 확정되면 새 conversationId 를 발급해
 * "인테이크 진입~confirm" 구간만 복사하고 application_case_id·title 을 바인딩한다.</p>
 *
 * <p><b>슬롯 영속(Phase C/D):</b> 턴 종료 후 확정 슬롯(caseId·mode·originalQuery)을
 * chatbot_intake_slot 에 upsert 하고, 메모리에 슬롯이 없으면(재시작/재방문) DB 에서 복원한다.</p>
 */
@Service
public class IntakeAskService {

    private static final Logger log = LoggerFactory.getLogger(IntakeAskService.class);

    /** 면접 모드 화이트리스트(AutoPrepIntakeService.MODE_OPTIONS 코드 미러 — 버튼 선택 결정적 확정 검증용). */
    private static final Set<String> MODE_CODES =
            Set.of("BASIC", "JOB", "PERSONALITY", "PRESSURE", "RESUME", "COMPANY");

    private final IntakeChatAgent agent;
    private final IntakeSlotTrace trace;
    private final AutoPrepIntakeService autoPrepIntakeService;
    private final MyBatisChatMemoryStore memoryStore;
    private final ChatbotIntakeSlotMapper slotMapper;
    private final ApplicationCaseService applicationCaseService;

    public IntakeAskService(IntakeChatAgent agent,
                            IntakeSlotTrace trace,
                            AutoPrepIntakeService autoPrepIntakeService,
                            MyBatisChatMemoryStore memoryStore,
                            ChatbotIntakeSlotMapper slotMapper,
                            ApplicationCaseService applicationCaseService) {
        this.agent = agent;
        this.trace = trace;
        this.autoPrepIntakeService = autoPrepIntakeService;
        this.memoryStore = memoryStore;
        this.slotMapper = slotMapper;
        this.applicationCaseService = applicationCaseService;
    }

    public IntakeAskResponse ask(Long userId, String message, Long conversationId) {
        return ask(userId, message, conversationId, null, null);
    }

    /**
     * @param selectedCaseId  지원 건 칩을 직접 고른 경우의 caseId(그 외 null). 소유 검증 후 agent.chat 전에
     *        결정적으로 {@code trace.confirmCase} — qwen3 의 chooseCase 에 의존하지 않고 칩 선택을 확실히 바인딩.
     * @param selectedModeCode 면접 모드 버튼을 직접 고른 경우의 code(그 외 null). case 가 확정된 상태에서만
     *        화이트리스트 검증 후 {@code trace.confirmMode}(case→mode 순서 유지). 텍스트 답변은 둘 다 null → qwen3 폴백.
     */
    public IntakeAskResponse ask(Long userId, String message, Long conversationId,
                                 Long selectedCaseId, String selectedModeCode) {
        trace.clear();
        try {
            // [Phase D] 재시작/재방문 복원: 메모리에 슬롯이 없고 DB 에 있으면 적재(begin 전에).
            restoreSlotIfAbsent(conversationId);

            // 요청 컨텍스트 주입 + 첫 발화면 originalQuery 고정(툴이 userId 를, 컨트롤러가 query 를 읽는다).
            trace.begin(userId, conversationId, message);
            // 첫 인테이크 턴이면 "진입 직전 memory 메시지 수"를 fork 구간 시작점으로 1회 기록(B-0 확정).
            trace.recordEntryOffsetIfAbsent(() -> memoryStore.getMessages(conversationId).size());

            // 칩/버튼(selectedCaseId·selectedModeCode) 선택은 아래 applyExplicitSelections 가 코드로 결정적 바인딩하므로
            // qwen3 없이도 확정 가능하다. 복원 세션은 메모리 윈도우가 무거워(tool-call/result 누적) qwen3 가 tool 루프
            // 상한(IntakeAgentConfig.MAX_TOOL_CALLS=3)을 넘겨 던지는데, 그때 명시 선택 턴까지 범용 에러로 죽는 게 이 버그다.
            // → 명시 선택이 있으면 LLM 실패를 흡수하고 결정적 경로로 계속한다. 텍스트 자유발화(선택 없음)는 이해가 필요해
            //   되살릴 수 없으므로 그대로 재던져 기존 폴백(catch)을 탄다.
            boolean explicitSelection = selectedCaseId != null || selectedModeCode != null;
            String answer = null;
            try {
                answer = MessageSanitizer.stripMarkdown(agent.chat(conversationId, message));
            } catch (RuntimeException agentEx) {
                if (!explicitSelection) {
                    throw agentEx;
                }
                log.warn("인테이크 에이전트 실패 — 명시 선택(caseId={}, mode={})은 결정적으로 진행: {}",
                        selectedCaseId, selectedModeCode, agentEx.getMessage());
            }

            // ★(OptionA) 칩/버튼 선택 결정적 바인딩 — agent.chat "뒤". qwen3 가 같은 턴에 chooseCase(임의 id)/chooseMode 를
            //   호출해 trace 를 잘못 박아도(실측: 칩 클릭 텍스트에 qwen3 가 chooseCase(1)=엉뚱한 건 호출), F 가 마지막에
            //   사용자가 명시 선택한 값으로 덮어써 최종 권위를 가진다(last-write-wins → F 승). qwen3 를 바인딩 루프에서 제거.
            applyExplicitSelections(userId, selectedCaseId, selectedModeCode);

            // ★(A-1) 텍스트 case 답변 결정적 재매칭 — qwen3 의 chooseCase 는 실측상 텍스트를 무시하고 첫 건(보통 id1)을
            //   집는다(메모리 수칙3, "현대차"→카카오페이 거짓 진행). 칩이 아닌 텍스트로 case 를 답한 턴이면 qwen3 가 박은
            //   caseId 를 신뢰하지 않고, 후보 companyName 과 사용자 텍스트를 코드가 매칭해 덮거나(정확히 1건) 비운다(0/2건+
            //   → 다운스트림 CASE-ask 재노출). OptionA 와 같은 원칙: 명확한 선택은 코드가 강제, qwen3 는 이해만.
            reconcileTextCaseAnswer(userId, message, selectedCaseId);

            // 코드가 검증·확정한 슬롯만 그릇에 채운다. (coverLetterText·attachmentFileIds 는 2단계)
            IntakeSlotTrace.IntakeSlots slots = trace.snapshot();
            AutoPrepRequest autoPrepRequest = new AutoPrepRequest(
                    slots.originalQuery(), slots.caseId(), slots.mode(), null, null);

            // ready/nextAsk 판정은 D 의 기존 서비스에 위임(의존그래프·파트선택 재구현 금지).
            AutoPrepIntakeResponse check = autoPrepIntakeService.intake(userId, autoPrepRequest);

            // ★(b3) CASE-ask 강제: AutoPrepPlanner.resolveCase 는 회사 모호 시 "가장 최근 건"을 caseId 로 자동 디폴트한다.
            //   사용자가 chooseCase/칩으로 명시 확정하지 않았는데(slots.caseId()==null) check.plan 이 case 를 자동으로
            //   채운 경우엔 그 판정(MODE/ready)을 신뢰하지 않고 "어느 지원 건?" 되묻기로 되돌린다 — 선택 전엔 case 가정 금지.
            boolean caseAutoDefaulted = slots.caseId() == null
                    && check.plan() != null && check.plan().slots() != null
                    && check.plan().slots().applicationCaseId() != null;

            boolean ready;
            String nextAsk;
            List<ApplicationCaseResponse> candidates;
            List<ModeOption> modes;
            String decidedMessage;
            if (caseAutoDefaulted) {
                ready = false;
                nextAsk = "CASE";
                candidates = safeListCases(userId);
                modes = List.of();
                decidedMessage = "어느 지원 건으로 준비할까요?";
            } else {
                ready = check.ready();
                nextAsk = check.nextAsk();
                candidates = check.candidates();
                modes = check.modes();
                decidedMessage = check.message();
            }

            // ★(일관성) 화면 봇 문장 = 코드결정 메시지 우선, 비었으면(이론상 없음) qwen3 answer 폴백.
            //   봇 문장과 칩/헤더/순서(check)를 한 소스로 묶어 모순·환각을 차단한다.
            String botText = (decidedMessage != null && !decidedMessage.isBlank())
                    ? decidedMessage : answer;

            // ★(C1·통보) 이 턴에 지원 건이 새로/변경 확정됐으면 어느 건인지 코드가 명시 통보한다.
            //   qwen3 최종 문장이 확정 사실을 자주 생략해(실측: "면접 모드는?"만 출력) 오확정을 사용자가
            //   알아챌 방법이 없었다. ready 턴은 describe(plan)에 회사·직무가 이미 들어가므로 중복 제외.
            //   (boundCaseId 는 아래 fork 의 migrateToFork 전이라 아직 이전 턴 기준값이다.)
            if (!ready && slots.caseId() != null && !slots.caseId().equals(trace.boundCaseId())) {
                String confirmedTitle = caseTitle(slots.caseId());
                if (confirmedTitle != null) {
                    botText = "지원 건을 \"" + confirmedTitle + "\" 로 정했어요. "
                            + (botText == null ? "" : botText);
                }
            }

            // ★ 지원건 세션 fork: 이 턴에 지원 건이 새로 확정됐으면 새 conversationId 로 진입~confirm 구간을 옮긴다.
            Long effectiveConversationId =
                    maybeForkOnCaseConfirmed(conversationId, userId, slots.caseId());

            // [Phase C] 슬롯 DB 영속(턴 종료 후). 지원 건 확정 세션만 저장(fork 됐으면 새 id 로).
            persistSlot(effectiveConversationId, userId, slots);

            // [Phase C·status 3단계] ready 도달 = 슬롯 충족·run 을 프런트에 위임한 시점 → status READY 로 승급.
            if (check.ready() && slots.caseId() != null) {
                slotMapper.markStatus(effectiveConversationId, "READY");
            }

            // ★(일관성·②) 라이브=히스토리: fork 결과 대화의 메모리 끝 qwen3 text 를 화면 botText 로 치환.
            reconcileMemoryTail(effectiveConversationId, botText);

            return new IntakeAskResponse(
                    effectiveConversationId, botText, ready, nextAsk, autoPrepRequest,
                    candidates, modes);
        } catch (Exception e) {
            log.error("인테이크 에이전트 응답 실패 (Ollama 장애 추정): {}", e.getMessage(), e);
            return new IntakeAskResponse(
                    conversationId,
                    "지금은 답변을 생성하기 어렵습니다. 잠시 후 다시 시도해 주세요.",
                    false, null, null, List.of(), List.of());
        } finally {
            trace.clear();
        }
    }

    /**
     * 칩(caseId)·버튼(modeCode) 선택을 코드가 결정적으로 trace 에 확정한다(qwen3 미경유, agent.chat 전).
     * case 는 소유 검증, mode 는 case 확정된 상태 + 화이트리스트일 때만(case→mode 순서·검증 유지).
     */
    private void applyExplicitSelections(Long userId, Long selectedCaseId, String selectedModeCode) {
        if (selectedCaseId != null) {
            List<ApplicationCaseResponse> owned = safeListCases(userId);
            if (owned.stream().anyMatch(c -> selectedCaseId.equals(c.id()))) {
                trace.recordFetchedCases(owned);   // chooseCase 화이트리스트·세션 title 백업용
                trace.confirmCase(selectedCaseId);
            }
        }
        if (selectedModeCode != null && trace.snapshot().caseId() != null) {
            String code = selectedModeCode.trim().toUpperCase(Locale.ROOT);
            if (MODE_CODES.contains(code)) {
                trace.confirmMode(code);
            }
        }
    }

    /**
     * 텍스트로 case 를 답한 턴에서 qwen3 의 chooseCase 결과를 코드 매칭으로 교정한다(A-1 회귀 차단).
     * <ul>
     *   <li>칩 선택(selectedCaseId!=null)이면 이미 결정적으로 바인딩됐으니 건드리지 않는다.</li>
     *   <li>qwen3 가 이 턴에 caseId 를 안 박았으면(침묵) 기존 b3 가드(caseAutoDefaulted)가 처리 — 스킵.</li>
     *   <li>바인딩(fork)된 대화에서 qwen3 가 <b>같은</b> 건을 재확정한 턴(예: MODE 답변에 헛 chooseCase)은
     *       전환이 아니다 — 스킵(기존 보호 유지).</li>
     *   <li>바인딩된 대화에서 qwen3 가 <b>다른</b> 건을 박은 턴 = 케이스 전환 시도 — 여기도 재매칭을 태운다.
     *       (C1 잔여 구멍: 종전엔 bound 무조건 스킵이라 "토스로 바꿔줘"→chooseCase(2) 오확정이 무검증 통과 —
     *       9000231·9000234 실측.)</li>
     * </ul>
     * 위를 통과하면 qwen3 가 박은 id 를 버리고, 사용자 텍스트와 후보 companyName 을 매칭한다.
     * 정확히 1건이면 그 id 로 덮어 확정. 0건/2건 이상이면 — 신규 대화는 caseId 를 비워 CASE-ask 재노출,
     * 바인딩된 대화는 근거 불명 전환을 취소하고 기존 건으로 되돌린다(세션 퇴행 방지).
     */
    private void reconcileTextCaseAnswer(Long userId, String message, Long selectedCaseId) {
        Long claimed = trace.snapshot().caseId();
        Long bound = trace.boundCaseId();
        if (selectedCaseId != null || (claimed == null && bound == null)) {
            return;  // 칩 확정 턴 / 신규 대화에서 qwen3 침묵(b3 가드가 CASE-ask 처리)
        }
        boolean switchClaimed = claimed != null && !claimed.equals(bound);
        // 바인딩 대화에서 qwen3 가 전환을 안 박은 턴(추측 거부 후 포기·침묵 — 실측 chooseCase(4) 거부→포기)도
        // 발화가 다른 건을 유일 지목하면 전환으로 본다. 매칭 0/2건+ 인 무고 턴(MODE 답변 등)은 아래서 no-op.
        List<ApplicationCaseResponse> owned = safeListCases(userId);
        List<ApplicationCaseResponse> matched = matchCasesByText(message, owned);
        if (matched.size() == 1) {
            trace.recordFetchedCases(owned);            // chooseCase 화이트리스트·세션 title 백업용
            trace.confirmCase(matched.get(0).id());     // qwen3 의 (대개 틀린) id 를 코드 매칭 결과로 덮는다.
        } else if (switchClaimed) {
            trace.confirmCase(bound);                   // 신규(null)=CASE-ask 재노출 / 바인딩=근거불명 전환 취소.
        }
        // matched!=1 && !switchClaimed(claimed==bound 무고 턴): 건드리지 않는다 — 기존 확정 유지.
    }

    /**
     * 사용자 텍스트와 후보 companyName 매칭. 2단계:
     * <ol>
     *   <li><b>정확일치 우선</b>: 정규화 후 companyName 이 텍스트와 정확히 같은 후보가 딱 1건이면 그것만 반환.
     *       불량 데이터(회사명에 공고 본문이 통째로 든 후보)가 부분일치로 끼어들어도 정확명은 확정시킨다.</li>
     *   <li><b>양방향 부분일치</b>: 정확일치가 0/2건+ 일 때만. 짧거나 모호한 입력은 여러 건이 잡혀
     *       호출부에서 CASE-ask 로 되돌아간다(거짓 진행 차단).</li>
     * </ol>
     */
    private List<ApplicationCaseResponse> matchCasesByText(String message, List<ApplicationCaseResponse> cases) {
        String m = normalizeForMatch(message);
        if (m.isBlank() || cases == null) {
            return List.of();
        }
        List<ApplicationCaseResponse> exact = new ArrayList<>();
        List<ApplicationCaseResponse> partial = new ArrayList<>();
        for (ApplicationCaseResponse c : cases) {
            String company = normalizeForMatch(c.companyName());
            if (company.isBlank()) {
                continue;
            }
            if (company.equals(m)) {
                exact.add(c);
            }
            if (m.contains(company) || company.contains(m)) {
                partial.add(c);
            }
        }
        return exact.size() == 1 ? exact : partial;
    }

    /** 공백·법인 표기((주)/㈜/주식회사) 제거 + 소문자화 — "현대자동차 (주)" 와 "현대자동차" 를 같게 본다. */
    private static String normalizeForMatch(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", "")
                .replace("(주)", "")
                .replace("㈜", "")
                .replace("주식회사", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 통합 라우터(컨트롤러)가 "이 대화가 (재시작/재방문이라도) 복원할 지원건 세션인지" 판정하는 데 쓴다.
     *
     * <p><b>status 게이트(방향 A):</b> 행 존재만 보던 것을 <b>status==PENDING(인테이크 진행 중)</b>일 때만
     * true 로 좁힌다. READY(슬롯 충족·run 을 프런트에 위임한 시점)/DONE 은 더는 sticky-인테이크가 아니므로
     * false 를 돌려 라우터가 정상 판정(FAQ·이탈 등)하게 둔다. — 완료 후 ③ 삼킴/이탈 불가 버그의 근본 차단.</p>
     */
    public boolean isPersistedIntakeSession(Long conversationId) {
        if (conversationId == null) {
            return false;
        }
        Map<String, Object> row = slotMapper.findByConversation(conversationId);
        return row != null && "PENDING".equals(row.get("status"));
    }

    /**
     * 이탈("그만") 판정용: 슬롯 행이 있고 아직 닫히지 않았으면(PENDING/READY) true. DONE/행없음이면 false.
     *
     * <p>{@link #isPersistedIntakeSession}(PENDING 만)과 달리 READY 도 포함한다 — 복원 대상은 아니지만
     * "그만" 이탈 시엔 친절한 일반 모드 복귀 응답으로 받아 라우터 FALLBACK(버그2)을 막아야 하기 때문.</p>
     */
    public boolean hasOpenIntakeSlot(Long conversationId) {
        if (conversationId == null) {
            return false;
        }
        Map<String, Object> row = slotMapper.findByConversation(conversationId);
        return row != null && !"DONE".equals(row.get("status"));
    }

    /**
     * 이탈("그만") 시 슬롯을 DONE 으로 닫는다 → 재복원(PENDING)·sticky 차단. 행 없으면 no-op.
     *
     * <p>마이그레이션 주석의 "DONE 예약·미사용"은 <b>run 완료</b>(백엔드가 못 보는 이벤트)를 가리킨다.
     * 사용자의 명시적 "그만"은 백엔드가 직접 관측하는 이탈이므로 터미널 상태 DONE 로 닫는 게 맞다.</p>
     */
    public void closeIntakeSession(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        slotMapper.markStatus(conversationId, "DONE");
    }

    /** 메모리에 슬롯이 없고 DB 에 있으면 복원(재시작/재방문). entryOffset 은 현재 memory 길이로 재무장. */
    private void restoreSlotIfAbsent(Long conversationId) {
        if (conversationId == null || trace.hasSlot(conversationId)) {
            return;
        }
        Map<String, Object> row = slotMapper.findByConversation(conversationId);
        if (row == null) {
            return;
        }
        // status 게이트: PENDING 만 복원한다(isPersistedIntakeSession 과 정합). READY/DONE 슬롯은
        // 되살리지 않아 완료·이탈 세션이 인테이크 턴에서 옛 case/mode 로 stale 부활하는 것을 막는다.
        if (!"PENDING".equals(row.get("status"))) {
            return;
        }
        Long caseId = toLong(row.get("applicationCaseId"));
        String mode = (String) row.get("mode");
        String originalQuery = (String) row.get("originalQuery");
        int entryOffset = memoryStore.getMessages(conversationId).size();
        trace.restore(conversationId, caseId, mode, originalQuery, entryOffset);
    }

    /**
     * 지원 건이 확정된 세션의 슬롯만 영속(잡담/미확정은 저장 안 함 → 세션 목록·복원 대상에서 자연 제외).
     * fetched_cases 는 listCases 재조회로 대체 가능한 파생 캐시라 영속하지 않는다(null).
     */
    private void persistSlot(Long conversationId, Long userId, IntakeSlotTrace.IntakeSlots slots) {
        if (conversationId == null || slots.caseId() == null) {
            return;
        }
        slotMapper.upsert(conversationId, userId, slots.caseId(), slots.mode(), slots.originalQuery(), null);
    }

    /**
     * 메모리 윈도우의 "마지막 메시지가 text 있는 (tool-call 아닌) AiMessage" 일 때만 그 text 를 화면 botText 로
     * 교체한다(라이브=히스토리). tool-call AiMessage·ToolExecutionResultMessage·UserMessage 는 보존하고,
     * 마지막이 text AiMessage 가 아니거나 메모리가 비면 skip(NPE/구조 훼손 방지). 개수 불변이라 윈도우/다음 턴 무영향.
     */
    private void reconcileMemoryTail(Long conversationId, String botText) {
        if (conversationId == null || botText == null || botText.isBlank()) {
            return;
        }
        List<ChatMessage> msgs = memoryStore.getMessages(conversationId);
        if (msgs.isEmpty()) {
            return;
        }
        int last = msgs.size() - 1;
        ChatMessage tail = msgs.get(last);
        if (tail instanceof AiMessage ai
                && !ai.hasToolExecutionRequests()
                && ai.text() != null && !ai.text().isBlank()) {
            List<ChatMessage> updated = new ArrayList<>(msgs);
            updated.set(last, AiMessage.from(botText));
            memoryStore.updateMessages(conversationId, updated);
        }
    }

    /** CASE-ask 오버라이드·칩 검증용 후보 목록(0-tool 이라 trace.fetchedCases 가 빌 수 있어 직접 조회). 실패/없음이면 빈 리스트. */
    private List<ApplicationCaseResponse> safeListCases(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            List<ApplicationCaseResponse> cases = applicationCaseService.list(userId, null, false);
            if (cases == null) {
                return List.of();
            }
            // (C3) 회사·직무 둘 다 미확인인 placeholder 건은 후보 칩·텍스트 재매칭 대상에서 제외한다 —
            // ③에선 어차피 선택 불가(①′ 게이트 차단)라 추출실패 잔재가 노이즈·오선택 유도만 만든다.
            return cases.stream()
                    .filter(c -> !(CaseSlotValidator.isUnresolved(c.companyName())
                            && CaseSlotValidator.isUnresolved(c.jobTitle())))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("지원 건 후보 조회 실패(빈 목록으로 진행): {}", ex.getMessage());
            return List.of();
        }
    }

    private static Long toLong(Object value) {
        return (value instanceof Number n) ? n.longValue() : null;
    }

    /**
     * 지원 건이 새로 확정된 턴이면 fork 한다: 새 conversationId 를 발급해 "진입~confirm" 구간만 복사하고
     * application_case_id·title 을 바인딩한 뒤, 누적 슬롯을 새 대화로 이전한다.
     *
     * <ul>
     *   <li>caseId 미확정이거나 <b>이미 그 건에 바인딩된 세션</b>이면 fork 안 함
     *       (무한 바인딩 방지 + 케이스 전환 시에만 또 fork 되어 세션 격리 — 리스크6).</li>
     *   <li>원본 대화 행은 건드리지 않는다(파괴적 수정 회피 — 결정1). 잡담 방은 application_case_id NULL 로 남아
     *       세션 목록(Phase E)에 안 뜨므로 중복으로 안 보인다.</li>
     * </ul>
     *
     * @return fork 했으면 새 conversationId, 아니면 원본 conversationId.
     */
    private Long maybeForkOnCaseConfirmed(Long conversationId, Long userId, Long caseId) {
        if (caseId == null || caseId.equals(trace.boundCaseId())) {
            return conversationId;
        }
        // ★(A′) boundCaseId 인메모리 증발 보강: 재시작·READY세션·대화단절로 인메모리 가드가 비어도, fork 시
        //   bindCase 가 이미 chatbot_conversation_memory.application_case_id 에 영속해 둔 값을 권위로 읽어
        //   "이미 이 건에 바인딩된 대화"면 재fork 하지 않는다(매 턴 새 대화 분기 루프 차단). #1 과 같은 패턴
        //   (DB 권위·인메모리 보조). boundCaseId 가 메모리에 있으면 위에서 끝나 DB 조회는 타지 않는다.
        if (trace.boundCaseId() == null
                && caseId.equals(memoryStore.findApplicationCaseId(conversationId))) {
            return conversationId;
        }
        // 진입 offset 이후 메시지 = 이 지원 건 인테이크 구간(진입 전 잡담/커뮤니티 메시지는 제외).
        List<ChatMessage> all = memoryStore.getMessages(conversationId);
        Integer offset = trace.entryOffset();
        int from = (offset == null || offset < 0 || offset > all.size()) ? 0 : offset;
        List<ChatMessage> segment = new ArrayList<>(all.subList(from, all.size()));

        Long newConversationId = memoryStore.createConversation(userId);
        memoryStore.updateMessages(newConversationId, segment);             // 구간 복사(원본 보존)
        memoryStore.bindCase(newConversationId, caseId, caseTitle(caseId)); // 지원건 세션 표식(case_id+title)

        // 슬롯을 새 대화로 이전 + 바인딩 건 기록 + 다음 케이스 전환 대비 entryOffset 재무장(새 대화 길이로).
        trace.migrateToFork(conversationId, newConversationId, caseId, segment.size());
        return newConversationId;
    }

    /** 세션 제목 "{회사} {직무}". 후보 캐시(chooseCase 검증 화이트리스트)에서 확정 건을 찾아 만든다(없으면 null → 후속 백필). */
    private String caseTitle(Long caseId) {
        return trace.fetchedCases().stream()
                .filter(c -> caseId.equals(c.id()))
                .findFirst()
                .map(c -> ((c.companyName() == null ? "" : c.companyName()) + " "
                        + (c.jobTitle() == null ? "" : c.jobTitle())).trim())
                .filter(t -> !t.isEmpty())
                .orElse(null);
    }
}
