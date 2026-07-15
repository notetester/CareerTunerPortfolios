package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class AutoPrepCancellationTest {

    @Test
    void cancelCancelsRegisteredFuturesAndRejectsLaterWork() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        Future<?> future = mock(Future.class);
        cancellation.register(future);

        assertThat(cancellation.cancel()).isTrue();

        verify(future).cancel(true);
        assertThatThrownBy(cancellation::checkActive)
                .isInstanceOf(AutoPrepCancelledException.class);
    }

    @Test
    void userScopedRegistryDoesNotCancelAnotherUsersSameRunId() {
        AutoPrepRunCancellationRegistry registry = new AutoPrepRunCancellationRegistry();
        AutoPrepCancellation first = new AutoPrepCancellation();
        AutoPrepCancellation second = new AutoPrepCancellation();
        registry.register(1L, "same_run", first);
        registry.register(2L, "same_run", second);

        assertThat(registry.cancel(1L, "same_run")).isTrue();
        assertThat(first.isCancelled()).isTrue();
        assertThat(second.isCancelled()).isFalse();
    }

    @Test
    void completedRunCannotBeCancelledByLateEmitterCallback() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        cancellation.finish();

        assertThat(cancellation.cancel()).isFalse();
        assertThat(cancellation.isCancelled()).isFalse();
    }

    @Test
    void cancelArrivingBeforeStreamRegistrationCancelsLateToken() {
        AutoPrepRunCancellationRegistry registry = new AutoPrepRunCancellationRegistry();

        assertThat(registry.cancel(1L, "early_run")).isTrue();
        assertThat(registry.earlyCancelCount()).isEqualTo(1);

        AutoPrepCancellation late = new AutoPrepCancellation();
        registry.register(1L, "early_run", late);

        assertThat(late.isCancelled()).isTrue();
        assertThat(registry.earlyCancelCount()).isEqualTo(1);

        AutoPrepCancellation duplicateStream = new AutoPrepCancellation();
        registry.register(1L, "early_run", duplicateStream);
        assertThat(duplicateStream.isCancelled()).isTrue();
    }
}
