package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.model.ReportDeliverySchedule;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "report.recovery.enabled=false"
})
class SourceChildReportQueryAdapterTest {

    @Autowired
    private SourceChildReportQueryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportNotificationPreferenceRepository preferenceRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                create table if not exists child (
                    child_id bigint primary key,
                    parent_id bigint not null,
                    deleted_at timestamp null
                )
                """);
        jdbcTemplate.update("delete from child");
        preferenceRepository.deleteAll();
    }

    @Test
    void returnsAllActiveChildrenIncludingChildrenWithoutActivity() {
        saveChild(22L, null);
        saveChild(11L, null);
        saveChild(33L, "2026-07-23 10:00:00");

        var children = adapter.findActiveChildIds(
                new ReportDeliverySchedule(
                        java.time.LocalTime.of(20, 0), "Asia/Seoul"),
                PageRequest.of(0, 10));

        assertThat(children.getContent()).containsExactly(11L, 22L);
        assertThat(children.hasNext()).isFalse();
    }

    @Test
    void storesWallClockTimeAsMinuteWithoutJdbcTimezoneConversion() {
        ReportNotificationPreference seoul =
                ReportNotificationPreference.defaults(101L);
        seoul.update(true, java.time.LocalTime.of(16, 0), "Asia/Seoul");
        ReportNotificationPreference newYork =
                ReportNotificationPreference.defaults(102L);
        newYork.update(true, java.time.LocalTime.of(16, 0), "America/New_York");
        preferenceRepository.saveAndFlush(seoul);
        preferenceRepository.saveAndFlush(newYork);
        saveChild(1010L, 101L, null);
        saveChild(1020L, 102L, null);

        assertThat(jdbcTemplate.queryForList(
                "select notification_minute from report_notification_preference order by parent_id",
                Integer.class))
                .containsExactly(960, 960);
        assertThat(adapter.findActiveDeliverySchedules())
                .contains(
                        new ReportDeliverySchedule(
                                java.time.LocalTime.of(16, 0), "Asia/Seoul"),
                        new ReportDeliverySchedule(
                                java.time.LocalTime.of(16, 0), "America/New_York"));
        assertThat(adapter.findActiveChildIds(
                new ReportDeliverySchedule(
                        java.time.LocalTime.of(16, 0), "Asia/Seoul"),
                PageRequest.of(0, 10)).getContent())
                .containsExactly(1010L);
        assertThat(adapter.findActiveChildIds(
                new ReportDeliverySchedule(
                        java.time.LocalTime.of(16, 0), "America/New_York"),
                PageRequest.of(0, 10)).getContent())
                .containsExactly(1020L);
    }

    private void saveChild(Long childId, String deletedAt) {
        saveChild(childId, 1L, deletedAt);
    }

    private void saveChild(Long childId, Long parentId, String deletedAt) {
        jdbcTemplate.update(
                "insert into child (child_id, parent_id, name, profile_image_key, "
                        + "deleted_at, created_at, updated_at) values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
                childId,
                parentId,
                "child-" + childId,
                "profile-presets/child-avatar",
                deletedAt);
    }
}
