package com.careertuner.notification.domain;

/**
 * 알림을 노출할 클라이언트 플랫폼.
 *
 * <p>기존 알림처럼 값이 없거나 {@link #ALL}이면 모든 플랫폼에서 노출한다.
 */
public enum NotificationDestinationPlatform {
    ALL,
    MOBILE,
    DESKTOP;

    public static NotificationDestinationPlatform resolve(
            NotificationDestinationPlatform destinationPlatform) {
        return destinationPlatform == null ? ALL : destinationPlatform;
    }
}
