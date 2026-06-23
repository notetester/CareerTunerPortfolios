package com.careertuner.notification.event;

import com.careertuner.notification.domain.Notification;

/**
 * "이 알림을 푸시 발송해야 한다"는 이벤트.
 *
 * 알림 insert 트랜잭션이 커밋된 뒤, 트랜잭션 밖에서 비동기로
 * PushDispatcher.dispatch(notification) 를 호출하기 위해 사용한다.
 *
 * dispatch()는 Notification 전체(userId·type·title·message·link)를 인자로 받으므로,
 * insert로 id까지 채워진 Notification 객체를 그대로 실어 보낸다.
 */
public record NotificationPushEvent(Notification notification) {}
