package com.A501.backend.domain.report.sse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 접속 중인 부모별 SSE 연결을 보관한다. 한 부모가 여러 탭을 열 수 있으므로
 * parentId 하나에 여러 ManagedEmitter를 둔다. 다른 부모의 연결에는 절대
 * 접근할 수 없도록 parentId를 키로 격리한다.
 */
@Component
public class ReportNotificationEmitterRegistry {

    private final Map<Long, Set<ManagedEmitter>> emittersByParent =
            new ConcurrentHashMap<>();

    public void add(ManagedEmitter managed) {
        emittersByParent
                .computeIfAbsent(
                        managed.parentId(),
                        key -> ConcurrentHashMap.newKeySet())
                .add(managed);
    }

    public void remove(ManagedEmitter managed) {
        emittersByParent.computeIfPresent(
                managed.parentId(),
                (key, emitters) -> {
                    emitters.remove(managed);
                    return emitters.isEmpty() ? null : emitters;
                });
    }

    public Collection<ManagedEmitter> findByParent(Long parentId) {
        Set<ManagedEmitter> emitters = emittersByParent.get(parentId);
        return emitters == null ? List.of() : List.copyOf(emitters);
    }

    public Collection<ManagedEmitter> findAll() {
        return emittersByParent.values().stream()
                .flatMap(Set::stream)
                .toList();
    }
}
