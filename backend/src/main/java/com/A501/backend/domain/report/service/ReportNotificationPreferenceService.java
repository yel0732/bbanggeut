package com.A501.backend.domain.report.service;

import java.time.DateTimeException;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.dto.response.ReportNotificationPreferenceResponse;
import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportNotificationPreferenceService {

    private final ReportNotificationPreferenceRepository preferenceRepository;

    @Transactional
    public ReportNotificationPreferenceResponse getOrCreate(Long parentId) {
        return ReportNotificationPreferenceResponse.from(
                preferenceRepository.findByParentId(parentId)
                        .orElseGet(() -> preferenceRepository.save(
                                ReportNotificationPreference.defaults(parentId))));
    }

    @Transactional
    public ReportNotificationPreferenceResponse update(
            Long parentId,
            boolean enabled,
            LocalTime time,
            String timezone
    ) {
        ReportNotificationPreference preference = preferenceRepository
                .findByParentId(parentId)
                .orElseGet(() -> ReportNotificationPreference.defaults(parentId));
        try {
            preference.update(enabled, time, timezone.trim());
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ReportApiErrorCode.INVALID_NOTIFICATION_PREFERENCE);
        }
        ReportNotificationPreference saved = preferenceRepository.save(preference);
        return ReportNotificationPreferenceResponse.from(saved);
    }
}
