package com.A501.backend.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountPurgePropertiesBindingTest {

    @Autowired
    private AccountPurgeProperties properties;

    @Test
    void bindsDefaultsFromApplicationYml() {
        assertThat(properties.gracePeriod()).isEqualTo(Duration.ZERO);
        assertThat(properties.batchSize()).isEqualTo(50);
    }
}
