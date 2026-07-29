package com.A501.backend.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class AccountPurgePropertiesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    // 현재 정책(grace-period=0d)에서는 "탈퇴한 계정 전부"가 대상이어야 한다. 기준 시각이
    // now보다 과거로 밀리면 방금 탈퇴한 계정이 영원히 대상에서 빠진다.
    @Test
    void purgeBefore_isNowWhenGracePeriodIsZero() {
        AccountPurgeProperties properties = new AccountPurgeProperties(Duration.ZERO, 50);

        assertThat(properties.purgeBefore(NOW)).isEqualTo(NOW);
    }

    // 유예 기간을 도입하면 조회 조건을 고치지 않고 값만 바꿔도 동작해야 한다.
    @Test
    void purgeBefore_movesBackByGracePeriod() {
        AccountPurgeProperties properties = new AccountPurgeProperties(Duration.ofDays(30), 50);

        assertThat(properties.purgeBefore(NOW)).isEqualTo(NOW.minusDays(30));
    }
}
