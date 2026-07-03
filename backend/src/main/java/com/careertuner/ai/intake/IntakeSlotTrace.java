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
        /** fork 구간 시작 인덱스 = 인테이크 진입 직전 memory 메시지 수(첫 턴 1회 기록). */
        volatile Integer entryOffset;
        /** 이 대화가 바인딩된 지원 건 id(fork 후 설정). 같은 건 재확정 시 재-fork 방지(케이스 전환만 격리). */
        volatile Long boundCaseId;
        /** (b)(d) 깡통 온보딩 단계(JOB/SKILLS/AWAIT_POSTING/EXTRACTING/AWAIT_COMPANY/AWAIT_JOBTITLE/CASE_READY). null=미진입. */
        volatile String onboardingStep;
        /** (b) 온보딩에서 받은 직무·기술 — 유저 답 *그대로*(가공 0). 배열 변환은 (e) save 단계의 코드가. */
        volatile String onboardingJob;
        volatile String onboardingSkills;
        /** (d) 온보딩에서 생성한 지원 건 id(공고 추출 폴링·보정 update 대상). 미생성이면 null. */
        volatile Long onboardingCaseId;
        /** (d) EXTRACTING 진입 시각(ms) — 상한 초과 시 AWAIT_POSTING 리셋 게이트(F-13)용. 비대기면 null. */
        volatile Long onboardingExtractingSince;
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

    /**
     * 첫 인테이크 턴이면 "진입 직전 memory 메시지 수"를 fork 구간 시작점으로 1회 기록한다.
     * 이미 기록됐으면(=첫 턴 아님) supplier 를 호출하지 않는다(불필요한 memory 조회 회피).
     */
    public void recordEntryOffsetIfAbsent(java.util.function.IntSupplier offsetSupplier) {
        SlotState state = currentState();
        if (state.entryOffset == null) {
            state.entryOffset = offsetSupplier.getAsInt();
        }
    }

    /** fork 구간 시작 인덱스(진입 직전 메시지 수). 미기록이면 null. */
    public Integer entryOffset() {
        return currentState().entryOffset;
    }

    /** 이 대화가 바인딩된 지원 건 id(fork 전이면 null). 같은 건 재확정 시 재-fork 방지에 쓴다. */
    public Long boundCaseId() {
        return currentState().boundCaseId;
    }

    /**
     * fork 후처리: 누적 슬롯을 원 대화→새 대화로 이전하고, 바인딩 건과 새 진입 offset 을 갱신한다.
     * 다음 케이스로 전환하면 그 구간만 다시 fork 되도록 entryOffset 을 새 대화 길이로 재무장한다.
     */
    public void migrateToFork(Long oldConversationId, Long newConversationId,
                              Long boundCaseId, int newEntryOffset) {
        SlotState state = slotsByConversation.remove(oldConversationId);
        if (state == null) {
            state = new SlotState();
        }
        state.boundCaseId = boundCaseId;
        state.entryOffset = newEntryOffset;
        if (newConversationId != null) {
            slotsByConversation.put(newConversationId, state);
        }
    }

    /** 이 대화의 누적 슬롯이 메모리에 있는지(없으면 DB 복원 후보 — 재시작/재방문). */
    public boolean hasSlot(Long conversationId) {
        return conversationId != null && slotsByConversation.containsKey(conversationId);
    }

    /**
     * DB 에서 읽은 슬롯을 메모리로 복원(재시작/재방문). entryOffset·boundCaseId 는 파생값이라 인자/규칙으로 채운다.
     * boundCaseId = caseId(이미 그 건에 바인딩된 세션이므로 같은 건 재확정은 재-fork 안 함).
     * entryOffset 은 호출부가 현재 memory 길이로 넘긴다(다음 케이스 전환은 현재 지점부터 fork).
     */
    public void restore(Long conversationId, Long caseId, String mode, String originalQuery, int entryOffset) {
        if (conversationId == null) {
            return;
        }
        SlotState state = new SlotState();
        state.caseId = caseId;
        state.mode = mode;
        state.originalQuery = originalQuery;
        state.boundCaseId = caseId;
        state.entryOffset = entryOffset;
        slotsByConversation.put(conversationId, state);
    }

    // ──────── (b) 깡통 온보딩 수집 (대화 단위, ThreadLocal 무관·인메모리·미영속·LLM 40창 미사용) ────────

    /** 온보딩 단계(JOB/SKILLS/COLLECTED). 미진입이면 null. */
    public String onboardingStep(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        SlotState state = slotsByConversation.get(conversationId);
        return state == null ? null : state.onboardingStep;
    }

    /** 온보딩 단계 전이. */
    public void setOnboardingStep(Long conversationId, String step) {
        if (conversationId == null) {
            return;
        }
        slotsByConversation.computeIfAbsent(conversationId, key -> new SlotState()).onboardingStep = step;
    }

    /** 받은 직무를 유저 답 *그대로* 누적(가공·해석 없음). */
    public void recordOnboardingJob(Long conversationId, String job) {
        if (conversationId == null) {
            return;
        }
        slotsByConversation.computeIfAbsent(conversationId, key -> new SlotState()).onboardingJob = job;
    }

    /** 받은 기술을 유저 답 *그대로* 누적(배열 변환은 (e) save 에서). */
    public void recordOnboardingSkills(Long conversationId, String skills) {
        if (conversationId == null) {
            return;
        }
        slotsByConversation.computeIfAbsent(conversationId, key -> new SlotState()).onboardingSkills = skills;
    }

    /** (d) 온보딩에서 생성한 지원 건 id. 미생성이면 null. */
    public Long onboardingCaseId(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        SlotState state = slotsByConversation.get(conversationId);
        return state == null ? null : state.onboardingCaseId;
    }

    /** (d) 공고로 생성한 지원 건 id 기록(추출 폴링·보정 update 대상). */
    public void setOnboardingCaseId(Long conversationId, Long caseId) {
        if (conversationId == null) {
            return;
        }
        slotsByConversation.computeIfAbsent(conversationId, key -> new SlotState()).onboardingCaseId = caseId;
    }

    /** (d) EXTRACTING 진입 시각(ms). 대기 중이 아니면 null — F-13 상한 게이트용. */
    public Long onboardingExtractingSince(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        SlotState state = slotsByConversation.get(conversationId);
        return state == null ? null : state.onboardingExtractingSince;
    }

    /** (d) EXTRACTING 진입/이탈 기록(진입=현재시각, 이탈=null) — F-13 상한 게이트용. */
    public void setOnboardingExtractingSince(Long conversationId, Long sinceMillis) {
        if (conversationId == null) {
            return;
        }
        slotsByConversation.computeIfAbsent(conversationId, key -> new SlotState()).onboardingExtractingSince = sinceMillis;
    }

    /**
     * 온보딩 인메모리 상태 정리(프로세스 내 즉시 정리용 — "그만" 탈출 시).
     * step/job/skills/caseId 만 비운다(영속 거부 권위는 DB onboarding_declined_at).
     * 일반 intake 슬롯(caseId/mode/originalQuery)은 건드리지 않는다.
     */
    public void clearOnboarding(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        SlotState state = slotsByConversation.get(conversationId);
        if (state != null) {
            state.onboardingStep = null;
            state.onboardingJob = null;
            state.onboardingSkills = null;
            state.onboardingCaseId = null;
            state.onboardingExtractingSince = null;
        }
    }

    /** 온보딩 수집 스냅샷(검증·(e) 변환용). */
    public OnboardingCollected onboarding(Long conversationId) {
        SlotState state = conversationId == null ? null : slotsByConversation.get(conversationId);
        return state == null
                ? new OnboardingCollected(null, null, null)
                : new OnboardingCollected(state.onboardingStep, state.onboardingJob, state.onboardingSkills);
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

    /** (b) 온보딩 수집 스냅샷(직무·기술은 유저 답 원문 그대로). */
    public record OnboardingCollected(String step, String job, String skills) {}
}
