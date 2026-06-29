package com.careertuner.admin.ticket.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.ticket.ai.TicketDraftAiClient;
import com.careertuner.admin.ticket.dto.AdminTicketDetailResponse;
import com.careertuner.admin.ticket.dto.AdminTicketDraftResponse;
import com.careertuner.admin.ticket.dto.AdminTicketListResponse;
import com.careertuner.admin.ticket.dto.AdminTicketMessageResponse;
import com.careertuner.admin.ticket.dto.AdminTicketReplyRequest;
import com.careertuner.admin.ticket.dto.AdminTicketSummaryResponse;
import com.careertuner.admin.ticket.dto.AdminTicketUpdateRequest;
import com.careertuner.admin.ticket.mapper.AdminTicketMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTicketServiceImpl implements AdminTicketService {

    private final AdminTicketMapper ticketMapper;
    private final NotificationService notificationService;
    private final TicketDraftAiClient draftAiClient;

    /** support_ticket.status 허용값(DB 저장 기준). */
    private static final Set<String> ALLOWED_STATUS = Set.of("RECEIVED", "IN_PROGRESS", "ANSWERED", "CLOSED");
    /** support_ticket.priority 허용값(DB 저장 기준). */
    private static final Set<String> ALLOWED_PRIORITY = Set.of("NORMAL", "HIGH", "URGENT");
    /** support_ticket.category 저장 표준(CreateTicketRequest @Pattern 이 강제하는 한글 라벨). */
    private static final Set<String> CATEGORY_LABELS = Set.of("계정", "결제", "AI기능", "기술문제", "기타");

    @Override
    public List<AdminTicketListResponse> getTickets(AuthUser authUser, String status) {
        requireAdmin(authUser);
        String dbStatus = toDbStatus(status);
        List<AdminTicketListResponse> tickets = ticketMapper.findAll(dbStatus);
        // 목록도 상세(getTicketDetail)와 동일하게 status·category 를 표시값으로 변환한다.
        // status: DB enum → 프런트 값. category: toCategoryLabel 이 라벨이면 그대로·enum 이면 라벨로(라벨 인식).
        tickets.forEach(t -> {
            t.setStatus(toFrontStatus(t.getStatus()));
            t.setCategory(toCategoryLabel(t.getCategory()));
        });
        return tickets;
    }

    @Override
    public AdminTicketDetailResponse getTicketDetail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminTicketListResponse ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        String memo = ticketMapper.findInternalMemo(id);
        return AdminTicketDetailResponse.builder()
                .id(ticket.getId())
                .category(toCategoryLabel(ticket.getCategory()))
                .subject(ticket.getSubject())
                .memberName(ticket.getMemberName())
                .createdAt(ticket.getCreatedAt())
                .status(toFrontStatus(ticket.getStatus()))
                .priority(ticket.isPriority())
                .plan(ticket.getPlan())
                .joinedAt(ticket.getJoinedAt())
                .memo(memo != null ? memo : "")
                .msgs(ticketMapper.findMessages(id))
                .build();
    }

    @Override
    @Transactional
    public AdminTicketDetailResponse updateTicket(AuthUser authUser, Long id, AdminTicketUpdateRequest request) {
        requireAdmin(authUser);
        AdminTicketListResponse existing = ticketMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        // null 은 부분 업데이트(해당 항목 미변경)이므로 검증 스킵.
        String dbStatus = toDbStatus(request.status());
        if (dbStatus != null && !ALLOWED_STATUS.contains(dbStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 상태값입니다.");
        }
        String dbPriority = request.priority() != null ? request.priority().toUpperCase() : null;
        if (dbPriority != null && !ALLOWED_PRIORITY.contains(dbPriority)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 우선순위값입니다.");
        }
        ticketMapper.updateTicket(id, dbStatus, dbPriority);
        return getTicketDetail(authUser, id);
    }

    @Override
    @Transactional
    public AdminTicketDetailResponse reply(AuthUser authUser, Long id, AdminTicketReplyRequest request) {
        requireAdmin(authUser);
        AdminTicketListResponse existing = ticketMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "답변 내용을 입력해 주세요.");
        }
        boolean internal = request.internal() != null && request.internal();
        ticketMapper.insertMessage(id, "ADMIN", authUser.id(), request.content(), internal);
        if (!internal) {
            ticketMapper.updateStatus(id, "ANSWERED");
            // 답변 등록 시 문의 작성자에게 알림(내부 메모는 제외).
            Long ownerId = ticketMapper.findUserIdById(id);
            if (ownerId != null) {
                notificationService.notify(Notification.builder()
                        .userId(ownerId)
                        .actorId(authUser.id())
                        .type("TICKET_ANSWERED")
                        .targetType("SUPPORT_TICKET")
                        .targetId(id)
                        .title("문의에 답변이 등록되었습니다")
                        .message(existing.getSubject())
                        .link("/support/contact")
                        .build());
            }
        }
        return getTicketDetail(authUser, id);
    }

    @Override
    public AdminTicketDraftResponse generateDraft(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminTicketDetailResponse detail = getTicketDetail(authUser, id);

        StringBuilder context = new StringBuilder();
        context.append("문의 분류: ").append(detail.getCategory()).append('\n');
        context.append("문의 제목: ").append(detail.getSubject()).append('\n');
        context.append("대화 내역:\n");
        for (AdminTicketMessageResponse msg : detail.getMsgs()) {
            if (msg.isInternal()) {
                continue; // 내부 메모는 초안 생성 컨텍스트에서 제외
            }
            String speaker = "admin".equals(msg.getWho()) ? "상담사" : "고객";
            context.append("- [").append(speaker).append("] ").append(msg.getText()).append('\n');
        }

        String draft = draftAiClient.generateDraft(context.toString());
        return new AdminTicketDraftResponse(draft);
    }

    @Override
    public AdminTicketSummaryResponse generateMemberSummary(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminTicketListResponse ticket = ticketMapper.findById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        Long userId = ticketMapper.findUserIdById(id);
        List<AdminTicketListResponse> history = userId != null
                ? ticketMapper.findByUserId(userId)
                : List.of();

        StringBuilder context = new StringBuilder();
        context.append("[회원 정보]\n");
        context.append("이름: ").append(ticket.getMemberName()).append('\n');
        context.append("구독 등급: ").append(ticket.getPlan()).append('\n');
        context.append("가입일: ").append(ticket.getJoinedAt()).append('\n');
        context.append("총 문의 건수: ").append(history.size()).append("건\n");

        context.append("\n[과거 문의 이력]\n");
        if (history.isEmpty()) {
            context.append("- (이력 없음 — 이번이 첫 문의)\n");
        } else {
            for (AdminTicketListResponse h : history) {
                context.append("- [").append(h.getCategory()).append("] ")
                       .append(h.getSubject())
                       .append(" (상태: ").append(h.getStatus())
                       .append(", ").append(h.getCreatedAt()).append(")\n");
            }
        }

        context.append("\n[이번 문의]\n");
        context.append("분류: ").append(ticket.getCategory()).append('\n');
        context.append("제목: ").append(ticket.getSubject()).append('\n');

        String summary = draftAiClient.summarizeMember(context.toString());
        return new AdminTicketSummaryResponse(summary);
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }

    private String toDbStatus(String frontStatus) {
        if (frontStatus == null || frontStatus.isBlank()) return null;
        return switch (frontStatus.toLowerCase()) {
            case "pending"  -> "RECEIVED";
            case "progress" -> "IN_PROGRESS";
            case "answered" -> "ANSWERED";
            default         -> frontStatus.toUpperCase();
        };
    }

    private String toFrontStatus(String dbStatus) {
        if (dbStatus == null) return "pending";
        return switch (dbStatus) {
            case "RECEIVED"    -> "pending";
            case "IN_PROGRESS" -> "progress";
            case "ANSWERED", "CLOSED" -> "answered";
            default -> "pending";
        };
    }

    /**
     * 저장 표준은 한글 라벨(CreateTicketRequest @Pattern)이라 라벨이면 그대로 통과시킨다.
     * 검증을 우회해 들어온 enum 값(PAYMENT/AI_FEATURE/…)만 라벨로 변환하고, 그 외 미상값은 "기타".
     * (이전 버전은 enum→라벨만 알아 정상 라벨을 "기타"로 떨궜다 — 방향 자체를 라벨 인식으로 수정.)
     */
    private String toCategoryLabel(String category) {
        if (category == null) return "기타";
        if (CATEGORY_LABELS.contains(category)) return category;   // 이미 라벨(저장 표준)
        return switch (category.toUpperCase()) {
            case "PAYMENT"        -> "결제";
            case "AI_FEATURE"     -> "AI기능";
            case "ACCOUNT"        -> "계정";
            case "TECHNICAL"      -> "기술문제";
            default               -> "기타";
        };
    }
}
