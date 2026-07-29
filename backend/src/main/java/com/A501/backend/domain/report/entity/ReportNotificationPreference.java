package com.A501.backend.domain.report.entity;

import java.time.LocalTime;
import java.time.ZoneId;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "report_notification_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_notification_preference_parent",
                columnNames = "parent_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportNotificationPreference extends BaseTimeEntity {

    public static final LocalTime DEFAULT_TIME = LocalTime.of(20, 0);
    public static final String DEFAULT_TIME_ZONE = "Asia/Seoul";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Long id;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "notification_time", nullable = false)
    private LocalTime notificationTime;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    private ReportNotificationPreference(Long parentId) {
        this.parentId = parentId;
        this.enabled = true;
        this.notificationTime = DEFAULT_TIME;
        this.timeZone = DEFAULT_TIME_ZONE;
    }

    public static ReportNotificationPreference defaults(Long parentId) {
        if (parentId == null || parentId < 1) {
            throw new IllegalArgumentException("Parent ID is required.");
        }
        return new ReportNotificationPreference(parentId);
    }

    public void update(boolean enabled, LocalTime notificationTime, String timeZone) {
        if (notificationTime == null || timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("Notification time and time zone are required.");
        }
        ZoneId.of(timeZone);
        this.enabled = enabled;
        this.notificationTime = notificationTime.withSecond(0).withNano(0);
        this.timeZone = timeZone;
    }
}
