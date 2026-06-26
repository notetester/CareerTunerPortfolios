package com.careertuner.ai.intake;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.chat.MessageSanitizer;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.ai.intake.dto.IntakeAskResponse;

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

    private final IntakeChatAgent agent;
    private final IntakeSlotTrace trace;
    private final AutoPrepIntakeService autoPrepIntakeService;
    private final MyBatisChatMemoryStore memoryStore;
    private final ChatbotIntakeSlotMapper slotMapper;

    public IntakeAskService(IntakeChatAgent agent,
                            IntakeSlotTrace trace,
                            AutoPrepIntakeService autoPrepIntakeService,
                            MyBatisChatMemoryStore memoryStore,
                            ChatbotIntakeSlotMapper slotMapper) {
        this.agent = agent;
        this.trace = trace;
        this.autoPrepIntakeService = autoPrepIntakeService;
        this.memoryStore = memoryStore;
        this.slotMapper = slotMapper;
    }

    public IntakeAskResponse ask(Long userId, String message, Long conversationId) {
        trace.clear();
        try {
            // [Phase D] 재시작/재방문 복원: 메모리에 슬롯이 없고 DB 에 있으면 적재(begin 전에).
            restoreSlotIfAbsent(conversationId);

            // 요청 컨텍스트 주입 + 첫 발화면 originalQuery 고정(툴이 userId 를, 컨트롤러가 query 를 읽는다).
            trace.begin(userId, conversationId, message);
            // 첫 인테이크 턴이면 "진입 직전 memory 메시지 수"를 fork 구간 시작점으로 1회 기록(B-0 확정).
            trace.recordEntryOffsetIfAbsent(() -> memoryStore.getMessages(conversationId).size());

            String answer = MessageSanitizer.stripMarkdown(agent.chat(conversationId, message));

            // 코드가 검증·확정한 슬롯만 그릇에 채운다. (coverLetterText·attachmentFileIds 는 2단계)
            IntakeSlotTrace.IntakeSlots slots = trace.snapshot();
            AutoPrepRequest autoPrepRequest = new AutoPrepRequest(
                    slots.originalQuery(), slots.caseId(), slots.mode(), null, null);

            // ready/nextAsk 판정은 D 의 기존 서비스에 위임(의존그래프·파트선택 재구현 금지).
            AutoPrepIntakeResponse check = autoPrepIntakeService.intake(userId, autoPrepRequest);

            // ★ 지원건 세션 fork: 이 턴에 지원 건이 새로 확정됐으면 새 conversationId 로 진입~confirm 구간을 옮긴다.
            Long effectiveConversationId =
                    maybeForkOnCaseConfirmed(conversationId, userId, slots.caseId());

            // [Phase C] 슬롯 DB 영속(턴 종료 후). 지원 건 확정 세션만 저장(fork 됐으면 새 id 로).
            persistSlot(effectiveConversationId, userId, slots);

            // [Phase C·status 3단계] ready 도달 = 슬롯 충족·run 을 프런트에 위임한 시점 → 더는 sticky-인테이크 아님.
            // status 를 READY 로 승급해 다음 턴부터 isPersistedIntakeSession=false(라우터 정상 판정·이탈 가능).
            // persistSlot 직후라 행이 존재하고, fork 됐으면 effectiveConversationId 가 새 id 라 정확히 그 슬롯이 찍힌다.
            // (ready 면 caseId·mode 충족이 보장되므로 persistSlot 도 항상 행을 만든 상태.)
            if (check.ready() && slots.caseId() != null) {
                slotMapper.markStatus(effectiveConversationId, "READY");
            }

            return new IntakeAskResponse(
                    effectiveConversationId, answer, check.ready(), check.nextAsk(), autoPrepRequest,
                    check.candidates(), check.modes());
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
