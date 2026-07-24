package com.A501.backend.domain.auth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final AuthService authService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanupExpiredRefreshTokens() {
        authService.cleanupExpiredRefreshTokens();
    }
}
