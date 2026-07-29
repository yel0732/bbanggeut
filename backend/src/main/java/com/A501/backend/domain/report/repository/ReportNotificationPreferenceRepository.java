package com.A501.backend.domain.report.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.ReportNotificationPreference;

public interface ReportNotificationPreferenceRepository
        extends JpaRepository<ReportNotificationPreference, Long> {

    Optional<ReportNotificationPreference> findByParentId(Long parentId);

    void deleteByParentId(Long parentId);
}
