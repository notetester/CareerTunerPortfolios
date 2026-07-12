package com.careertuner.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class SmsProviderRouterTest {

    private final MockSmsProvider mockProvider = new MockSmsProvider();
    private final AligoSmsProvider aligoProvider = mock(AligoSmsProvider.class);

    @Test
    void explicitMockIsTheOnlyMockSuccessPath() {
        SmsProperties properties = new SmsProperties();
        properties.setProvider("mock");

        assertThat(router(properties).resolve()).isSameAs(mockProvider);
    }

    @Test
    void configuredAligoUsesTheRealProvider() {
        SmsProperties properties = new SmsProperties();
        properties.setProvider("aligo");
        properties.getAligo().setApiKey("key");
        properties.getAligo().setUserId("user");
        properties.getAligo().setSender("0212345678");

        assertThat(router(properties).resolve()).isSameAs(aligoProvider);
    }

    @Test
    void incompleteAligoDoesNotPretendThatMockWasSent() {
        SmsProperties properties = new SmsProperties();
        properties.setProvider("aligo");

        SmsProviderRouter router = router(properties);
        assertThatThrownBy(router::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_ALIGO");
    }

    @Test
    void unsupportedOrUnknownProviderFailsInsteadOfFallingBackToMock() {
        for (String provider : new String[] {"twilio", "naver-sens", "unknown"}) {
            SmsProperties properties = new SmsProperties();
            properties.setProvider(provider);
            assertThatThrownBy(() -> router(properties).validateConfiguration())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(provider);
        }
    }

    private SmsProviderRouter router(SmsProperties properties) {
        return new SmsProviderRouter(properties, mockProvider, aligoProvider);
    }
}
