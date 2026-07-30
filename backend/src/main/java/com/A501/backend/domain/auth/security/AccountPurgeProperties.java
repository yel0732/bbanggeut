package com.A501.backend.domain.auth.security;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 탈퇴 계정 hard delete 정책.
 *
 * <p>gracePeriod는 현재 0(즉시 파기)이지만 처음부터 설정값으로 둔다. 나중에 유예를 도입할 때
 * 조회 조건을 바꾸지 않고 값만 바꾸면 되도록 하기 위함이다. 유예를 두면 그 기간 동안 이메일
 * unique 제약이 살아 있어 같은 이메일 재가입이 막힌다는 부작용이 있다.
 */
@ConfigurationProperties(prefix = "account.purge")
public record AccountPurgeProperties(Duration gracePeriod, int batchSize) {

    /**
     * 이 시각 이전에 탈퇴한 계정이 파기 대상이다. gracePeriod가 0이면 사실상
     * "deleted_at IS NOT NULL"과 같아진다.
     *
     * <p>기준 시각을 인자로 받는 이유는 테스트가 시계를 고정할 수 있게 하기 위함이다.
     */
    public LocalDateTime purgeBefore(LocalDateTime now) {
        return now.minus(gracePeriod);
    }
}
