package com.A501.backend.domain.report.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.port.ChildReportQueryPort;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
@Transactional(readOnly = true)
public class SourceChildReportQueryAdapter implements ChildReportQueryPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Slice<Long> findActiveChildIds(Pageable pageable) {
        int requestedSize = pageable.getPageSize();
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery("""
                select child_id
                from child
                where deleted_at is null
                order by child_id
                """)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(requestedSize + 1)
                .getResultList();
        boolean hasNext = rows.size() > requestedSize;
        List<Long> childIds = rows.stream()
                .limit(requestedSize)
                .map(Number::longValue)
                .toList();
        return new SliceImpl<>(childIds, pageable, hasNext);
    }
}
