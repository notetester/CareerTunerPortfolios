package com.careertuner.notification.push;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.notification.domain.PushSubscription;
import com.careertuner.notification.dto.PushSubscribeRequest;
import com.careertuner.notification.mapper.PushSubscriptionMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PushServiceImpl implements PushService {

    private final PushSubscriptionMapper pushSubscriptionMapper;

    @Override
    public void subscribe(Long userId, PushSubscribeRequest request, String userAgent) {
        pushSubscriptionMapper.upsert(PushSubscription.builder()
                .userId(userId)
                .kind(request.kind().trim().toUpperCase(Locale.ROOT))
                .token(request.token())
                .p256dh(request.p256dh())
                .auth(request.auth())
                .userAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())))
                .build());
    }

    @Override
    public void unsubscribe(Long userId, String token) {
        if (token != null && !token.isBlank()) {
            pushSubscriptionMapper.deleteByToken(userId, token);
        }
    }
}
