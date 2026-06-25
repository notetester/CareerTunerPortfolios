package com.careertuner.ai.intake;

import java.util.ArrayList;
import java.util.List;

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
 * <p><b>범위:</b> 한 턴만 처리한다 — 슬롯 접지·ready 판정 위임 등 ③ 내부 멀티턴 동작은 변경하지 않는다.
 * conversationId 발급은 호출부가 책임진다(여기는 non-null 가정).</p>
 *
 * <p><b>지원건 세션 fork(Phase B):</b> 한 턴에 지원 건이 새로 확정되면(슬롯 caseId) 새 conversationId 를
 * 발급해 "인테이크 진입~confirm" 구간만 복사하고 application_case_id·title 을 바인딩한다. 응답의
 * conversationId 가 새 id 로 바뀌어 내려가고, 프론트는 그 값을 그대로 다음 턴에 회신한다(자동 채택).</p>
 */
@Service
public class IntakeAskService {

    private static final Logger log = LoggerFactory.getLogger(IntakeAskService.class);

    private final IntakeChatAgent agent;
    private final IntakeSlotTrace trace;
    private final AutoPrepIntakeService autoPrepIntakeService;
    private final MyBatisChatMemoryStore memoryStore;

    public IntakeAskService(IntakeChatAgent agent,
                            IntakeSlotTrace trace,
                            AutoPrepIntakeService autoPrepIntakeService,
                            MyBatisChatMemoryStore memoryStore) {
        this.agent = agent;
        this.trace = trace;
        this.autoPrepIntakeService = autoPrepIntakeService;
        this.memoryStore = memoryStore;
    }

    public IntakeAskResponse ask(Long userId, String message, Long conversationId) {
        trace.clear();
        try {
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
            // candidates·modes 는 D 가 nextAsk 에 맞춰 결정적으로 채워주는 칩 후보 — 버리지 않고 그대로 전달한다.
            AutoPrepIntakeResponse check = autoPrepIntakeService.intake(userId, autoPrepRequest);

            // ★ 지원건 세션 fork: 이 턴에 지원 건이 새로 확정됐으면 새 conversationId 로 진입~confirm 구간을 옮긴다.
            //   (D 호출 성공 후에 수행 — 실패 턴은 orphan 대화를 만들지 않는다.)
            Long effectiveConversationId =
                    maybeForkOnCaseConfirmed(conversationId, userId, slots.caseId());

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
     * 지원 건이 새로 확정된 턴이면 fork 한다: 새 conversationId 를 발급해 "진입~confirm" 구간만 복사하고
     * application_case_id·title 을 바인딩한 뒤, 누적 슬롯을 새 대화로 이전한다.
     *
     * <ul>
     *   <li>caseId 미확정이거나 <b>이미 그 건에 바인딩된 세션</b>이면 fork 안 함
     *       (무한 바인딩 방지 + 케이스 전환 시에만 또 fork 되어 세션 격리 — 리스크6).</li>
     *   <li>원본 대화 행은 건드리지 않는다(파괴적 수정 회피 — 결정1). 잡담 방은 application_case_id NULL 로 남아
     *       세션 목록(Phase E)에 안 뜨므로 중복으로 안 보인다.</li>
     *   <li>슬롯 DB 영속(chatbot_intake_slot)·세션 목록은 별도 Phase — 여기선 fork 만(슬롯은 인메모리 이전).</li>
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
