package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "report.recovery.enabled=false"
})
class SourceChildReportQueryAdapterTest {

    @Autowired
    private SourceChildReportQueryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    }

    @Test
    void returnsAllActiveChildrenIncludingChildrenWithoutActivity() {
        saveChild(22L, null);
        saveChild(11L, null);
        saveChild(33L, "2026-07-23 10:00:00");

        var children = adapter.findActiveChildIds(PageRequest.of(0, 10));

        assertThat(children.getContent()).containsExactly(11L, 22L);
        assertThat(children.hasNext()).isFalse();
    }

    private void saveChild(Long childId, String deletedAt) {
        jdbcTemplate.update(
                "insert into child (child_id, parent_id, name, profile_image_num, "
                        + "deleted_at, created_at, updated_at) values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
                childId,
                1L,
                "child-" + childId,
                1,
                deletedAt);
    }
}
