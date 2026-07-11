package com.careertuner.notification.push;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.mapper.PushSubscriptionMapper;
import com.careertuner.notification.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * 알림 1건을 사용자의 등록 기기로 푸시한다(best-effort).
 * 푸시 비활성/카테고리 off/기기 없음/발송 실패 시 조용히 건너뛰어 알림 생성 흐름을 끊지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final NotificationPreferenceService preferenceService;
    private final PushSender pushSender;

    public void dispatch(Notification notification) {
        if (notification == null || notification.getUserId() == null) {
            return;
        }
        // 데스크톱 앱은 API polling으로 수신한다. WEB/FCM/APNs로 재방송하지 않는다.
        NotificationDestinationPlatform destination = notification.resolvedDestinationPlatform();
        if (destination == NotificationDestinationPlatform.DESKTOP) {
            return;
        }
        try {
            var pref = preferenceService.get(notification.getUserId());
            if (!pref.pushEnabled()) {
                return;
            }
            String type = notification.getType();
            if (!pref.ruleEnabled(type)) {
                return;
            }
            // 발신자 관계(모르는 사람/친구/기업/운영자)별 수신 설정 — 관계 미상은 통과.
            if (!pref.senderEnabled(type, notification.getSenderRelation())) {
                return;
            }
            String category = NotificationCategories.of(type);
            if (Boolean.FALSE.equals(pref.categories().get(category))) {
                return;
            }
            // 방해금지 시간대(KST)면 푸시는 보내지 않는다. in-app 알림은 이미 저장돼 있어 사용자가 나중에 확인할 수 있다.
            if (isWithinQuietHours(pref.quietHoursStart(), pref.quietHoursEnd())) {
                return;
            }
            // 모바일 소리/진동 설정을 Android 알림 채널로 변환(이벤트별로 다르게 울릴 수 있다).
            String androidChannelId = PushMessage.channelFor(
                    pref.channelEnabled(type, "mobileSound"),
                    pref.channelEnabled(type, "mobileVibration"));
            PushMessage message = new PushMessage(
                    notification.getTitle(), notification.getMessage(), notification.getLink(), androidChannelId);
            for (var subscription : pushSubscriptionMapper.findByUserId(notification.getUserId())) {
                // MOBILE 핸드오프는 네이티브 기기만 수신한다. 데스크톱 브라우저의 WEB push로
                // 같은 알림을 다시 보내면 플랫폼 필터의 의미가 없어진다.
                if (destination == NotificationDestinationPlatform.MOBILE
                        && "WEB".equalsIgnoreCase(subscription.getKind())) {
                    continue;
                }
                String channel = pushChannel(subscription.getKind());
                if (!pref.channelEnabled(type, channel)) {
                    continue;
                }
                pushSender.send(subscription, message);
            }
        } catch (RuntimeException ex) {
            // 푸시는 보조 채널 — 실패해도 in-app 알림에는 영향 주지 않는다.
        }
    }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static String pushChannel(String kind) {
        return "WEB".equalsIgnoreCase(kind) ? "webPush" : "mobilePush";
    }

    /** 지금(KST)이 사용자의 방해금지 시간대 안인지 판정. */
    static boolean isWithinQuietHours(String start, String end) {
        return isWithinQuietHours(start, end, LocalTime.now(KST));
    }

    /**
     * 방해금지 구간 판정. 시작 포함·끝 제외([start, end)). 자정을 넘기는 구간(start &gt; end)도 처리한다.
     * 미설정(빈값)·형식 오류·시작==끝은 "방해금지 없음"(false)으로 본다 — 안전하게 발송을 허용한다.
     */
    static boolean isWithinQuietHours(String start, String end, LocalTime now) {
        if (start == null || start.isBlank() || end == null || end.isBlank()) {
            return false;
        }
        LocalTime s;
        LocalTime e;
        try {
            s = LocalTime.parse(start.trim());
            e = LocalTime.parse(end.trim());
        } catch (DateTimeParseException ex) {
            return false;
        }
        if (s.equals(e)) {
            return false;
        }
        if (s.isBefore(e)) {
            // 같은 날 구간 [s, e)
            return !now.isBefore(s) && now.isBefore(e);
        }
        // 자정을 넘기는 구간 (예: 22:00~07:00) → [s, 24:00) ∪ [00:00, e)
        return !now.isBefore(s) || now.isBefore(e);
    }
}
