package com.careertuner.ai.intake;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.dto.ApplicationCaseResponse;

/**
 * 인테이크 대화의 "슬롯 접지" 저장소. 커뮤니티 챗봇의 {@code SearchTrace} 와 같은 역할이지만,
 * 인테이크 슬롯은 여러 턴에 걸쳐 누적되므로 두 가지 보존 범위를 구분한다.
 *
 * <ul>
 *   <li><b>요청 스레드 컨텍스트</b>(userId·conversationId): 컨트롤러가 매 요청 주입하고 finally 에서 제거한다.
 *       에이전트가 동기 요청 스레드에서 도는 동안만 유효 — {@code SearchTrace} 의 ThreadLocal 패턴과 동일.
 *       (툴은 전역 정적이 아니라 여기서 userId 를 읽는다 — 동시성 안전.)</li>
 *   <li><b>대화 단위 누적 슬롯</b>(caseId·mode·originalQuery·후보 목록): conversationId 로 키링한 맵에 보존.
 *       이전 턴에 확정한 슬롯이 다음 턴까지 유지되어야 ready 판정이 성립한다. 메모리 윈도우(40) 밀림이나
 *       요청 ThreadLocal 정리와 무관하게 안정적으로 유지된다(결정사항 #2).</li>
 * </ul>
 *
 * <p>핵심: LLM 출력을 그대로 믿지 않고, 검증을 통과한 툴 호출 결과만 여기에 기록한다.
 * 컨트롤러는 {@link #snapshot()} 의 확정값만 AutoPrepRequest 로 넘긴다.</p>
 *
 * <p>1단계는 스키마 변경 없이 인메모리 보존(프로세스 수명 동안 유지). 세션 영속화는 D·C 합의 후 별도 단계.</p>
 */
@Component
public class IntakeSlotTrace {

    /** 요청 스레드 컨텍스트 — 동기 요청 스레드에서 툴이 현재 사용자·대화를 알기 위함. */
    private final ThreadLocal<Long> userIdTL = new ThreadLocal<>();
    private final ThreadLocal<Long> conversationIdTL = new ThreadLocal<>();

    /** 대화 단위 누적 슬롯. 멀티턴 보존 — 요청 ThreadLocal·윈도우 밀림과 무관. */
    private final Map<Long, SlotState> slotsByConversation = new ConcurrentHashMap<>();

    private static final class SlotState {
        volatile Long caseId;
        volatile String mode;
        volatile String originalQuery;
        volatile List<ApplicationCaseResponse> fetchedCases = List.of();
    }

    private SlotState currentState() {
        Long conversationId = conversationIdTL.get();
        if (conversationId == null) {
            // 요청 컨텍스트가 없으면(이론상 미발생) 저장하지 않는 임시 상태 반환.
            return new SlotState();
        }
        return slotsByConversation.computeIfAbsent(conversationId, key -> new SlotState());
    }

    /** 컨트롤러가 요청 시작 시 호출: 사용자·대화 컨텍스트 주입 + 첫 발화면 originalQuery 고정. */
    public void begin(Long userId, Long conversationId, String message) {
        userIdTL.set(userId);
        conversationIdTL.set(conversationId);
        SlotState state = currentState();
        if (state.originalQuery == null && message != null && !message.isBlank()) {
            state.originalQuery = message;
        }
    }

    public Long userId() {
        return userIdTL.get();
    }

    /** listCases 가 돌려준 후보 목록을 기록(= chooseCase 검증 화이트리스트). */
    public void recordFetchedCases(List<ApplicationCaseResponse> cases) {
        if (cases != null) {
            currentState().fetchedCases = List.copyOf(cases);
        }
    }

    public List<ApplicationCaseResponse> fetchedCases() {
        return currentState().fetchedCases;
    }

    /** chooseCase 가 코드 검증을 통과한 뒤에만 호출. */
    public void confirmCase(Long caseId) {
        currentState().caseId = caseId;
    }

    /** chooseMode 가 코드 검증을 통과한 뒤에만 호출. */
    public void confirmMode(String mode) {
        currentState().mode = mode;
    }

    /** 컨트롤러가 AutoPrepRequest 조립에 쓰는 확정 슬롯 스냅샷. */
    public IntakeSlots snapshot() {
        SlotState state = currentState();
        return new IntakeSlots(state.caseId, state.mode, state.originalQuery);
    }

    /** 요청 ThreadLocal 만 정리. 대화 단위 누적 슬롯은 다음 턴을 위해 보존한다. */
    public void clear() {
        userIdTL.remove();
        conversationIdTL.remove();
    }

    /** 코드가 검증·확정한 슬롯 스냅샷(요청 범위). */
    public record IntakeSlots(Long caseId, String mode, String originalQuery) {}
}
