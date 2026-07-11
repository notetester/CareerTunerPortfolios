package com.careertuner.notification.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private Long id;
    private Long userId;
    private Long actorId;
    private String type;
    private String targetType;
    private Long targetId;
    /** 발신자 관계(stranger/friend/company/operator). 관계 기반 알림에만 채워진다. */
    private String senderRelation;
    /** 수신 플랫폼. 기존 null 알림은 ALL로 해석한다. */
    private NotificationDestinationPlatform destinationPlatform;
    private String title;
    private String message;
    private String link;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    // JOIN으로 가져오는 actor 정보
    private String actorName;
    private String actorAvatarUrl;

    public NotificationDestinationPlatform resolvedDestinationPlatform() {
        return NotificationDestinationPlatform.resolve(destinationPlatform);
    }
}
