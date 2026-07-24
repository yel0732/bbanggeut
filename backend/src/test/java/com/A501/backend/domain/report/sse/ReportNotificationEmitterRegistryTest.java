package com.A501.backend.domain.report.sse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ReportNotificationEmitterRegistryTest {

    private final ReportNotificationEmitterRegistry registry =
            new ReportNotificationEmitterRegistry();

    private ManagedEmitter managed(Long parentId) {
        return new ManagedEmitter(parentId, new SseEmitter());
    }

    @Test
    void keepsMultipleConnectionsForSameParent() {
        ManagedEmitter first = managed(1L);
        ManagedEmitter second = managed(1L);

        registry.add(first);
        registry.add(second);

        assertThat(registry.findByParent(1L))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void isolatesConnectionsByParent() {
        ManagedEmitter parentOne = managed(1L);
        ManagedEmitter parentTwo = managed(2L);

        registry.add(parentOne);
        registry.add(parentTwo);

        assertThat(registry.findByParent(1L)).containsExactly(parentOne);
        assertThat(registry.findByParent(2L)).containsExactly(parentTwo);
    }

    @Test
    void returnsEmptyForUnknownParent() {
        assertThat(registry.findByParent(999L)).isEmpty();
    }

    @Test
    void removesConnectionAndCleansEmptyParentEntry() {
        ManagedEmitter only = managed(1L);
        registry.add(only);

        registry.remove(only);

        assertThat(registry.findByParent(1L)).isEmpty();
        assertThat(registry.findAll()).isEmpty();
    }

    @Test
    void findAllAggregatesAcrossParents() {
        ManagedEmitter parentOne = managed(1L);
        ManagedEmitter parentTwo = managed(2L);
        registry.add(parentOne);
        registry.add(parentTwo);

        assertThat(registry.findAll())
                .containsExactlyInAnyOrder(parentOne, parentTwo);
    }
}
