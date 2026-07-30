package com.A501.backend.domain.report.service;

import java.util.List;
import java.sql.Time;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.port.ChildReportQueryPort;
import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.model.ReportDeliverySchedule;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
@Transactional(readOnly = true)
public class SourceChildReportQueryAdapter implements ChildReportQueryPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ReportDeliverySchedule> findActiveDeliverySchedules() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select distinct
                       coalesce(preference.notification_time, :defaultTime),
                       coalesce(preference.time_zone, :defaultTimezone)
                from child child_profile
                left join report_notification_preference preference
                  on preference.parent_id = child_profile.parent_id
                where child_profile.deleted_at is null
                order by 2, 1
                """)
                .setParameter("defaultTime", Time.valueOf(
                        ReportNotificationPreference.DEFAULT_TIME))
                .setParameter("defaultTimezone",
                        ReportNotificationPreference.DEFAULT_TIME_ZONE)
                .getResultList();
        return rows.stream()
                .map(row -> new ReportDeliverySchedule(
                        toLocalTime(row[0]),
                        (String) row[1]))
                .toList();
    }

    private java.time.LocalTime toLocalTime(Object value) {
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        if (value instanceof java.time.LocalTime localTime) {
            return localTime;
        }
        return java.time.LocalTime.parse(value.toString());
    }

    @Override
    public Slice<Long> findActiveChildIds(
            ReportDeliverySchedule schedule,
            Pageable pageable) {
        int requestedSize = pageable.getPageSize();
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery("""
                select child_profile.child_id
                from child child_profile
                left join report_notification_preference preference
                  on preference.parent_id = child_profile.parent_id
                where child_profile.deleted_at is null
                  and coalesce(preference.notification_time, :defaultTime) = :deliveryTime
                  and coalesce(preference.time_zone, :defaultTimezone) = :timezone
                order by child_profile.child_id
                """)
                .setParameter("defaultTime", Time.valueOf(
                        ReportNotificationPreference.DEFAULT_TIME))
                .setParameter("defaultTimezone",
                        ReportNotificationPreference.DEFAULT_TIME_ZONE)
                .setParameter("deliveryTime",
                        Time.valueOf(schedule.deliveryTime()))
                .setParameter("timezone", schedule.timezone())
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
