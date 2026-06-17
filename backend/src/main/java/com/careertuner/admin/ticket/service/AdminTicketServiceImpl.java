package com.careertuner.admin.ticket.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.ticket.dto.AdminTicketDetailResponse;
import com.careertuner.admin.ticket.dto.AdminTicketListResponse;
import com.careertuner.admin.ticket.dto.AdminTicketReplyRequest;
import com.careertuner.admin.ticket.dto.AdminTicketUpdateRequest;
import com.careertuner.admin.ticket.mapper.AdminTicketMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTicketServiceImpl implements AdminTicketService {

    private final AdminTicketMapper ticketMapper;
    private final NotificationMapper notificationMapper;

    @Override
    public List<AdminTicketListResponse> getTickets(AuthUser authUser, String status) {
        requireAdmin(authUser);
        String dbStatus = toDbStatus(status);
        return ticketMapper.findAll(dbStatus);
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
        String dbStatus = request.status() != null ? request.status().toUpperCase() : null;
        String dbPriority = request.priority() != null ? request.priority().toUpperCase() : null;
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
        boolean internal = request.internal() != null && request.internal();
        ticketMapper.insertMessage(id, "ADMIN", authUser.id(), request.content(), internal);
        if (!internal) {
            ticketMapper.updateStatus(id, "ANSWERED");
            // 답변 등록 시 문의 작성자에게 알림(내부 메모는 제외).
            Long ownerId = ticketMapper.findUserIdById(id);
            if (ownerId != null) {
                notificationMapper.insert(Notification.builder()
                        .userId(ownerId)
                        .actorId(authUser.id())
                        .type("SUPPORT_TICKET_ANSWERED")
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

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
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

    private String toCategoryLabel(String category) {
        if (category == null) return "기타";
        return switch (category.toUpperCase()) {
            case "PAYMENT"        -> "결제";
            case "AI_FEATURE"     -> "AI기능";
            case "ACCOUNT"        -> "계정";
            case "TECHNICAL"      -> "기술문제";
            default               -> "기타";
        };
    }
}
