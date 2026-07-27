package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.A501.backend.common.exception.BusinessException;

class JdbcReportChildOwnershipAdapterTest {

    @Test
    void verifiesAuthoritativeOwnerAndResolvesNotificationRecipient() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq(15L)))
                .thenReturn(List.of(3L));
        JdbcReportChildOwnershipAdapter adapter =
                new JdbcReportChildOwnershipAdapter(jdbcTemplate);

        adapter.verifyOwnership(3L, 15L);

        assertThat(adapter.findParentId(15L)).isEqualTo(3L);
    }

    @Test
    void hidesMissingOrAnotherParentsChildAsNotFound() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq(15L)))
                .thenReturn(List.of(3L));
        JdbcReportChildOwnershipAdapter adapter =
                new JdbcReportChildOwnershipAdapter(jdbcTemplate);

        assertThatThrownBy(() -> adapter.verifyOwnership(4L, 15L))
                .isInstanceOf(BusinessException.class);
    }
}
