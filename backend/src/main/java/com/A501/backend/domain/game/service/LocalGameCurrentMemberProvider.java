package com.A501.backend.domain.game.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalGameCurrentMemberProvider implements GameCurrentMemberProvider {

    private final Long defaultMemberId;

    public LocalGameCurrentMemberProvider(
            @Value("${app.local.mock-principal-id:1}") Long defaultMemberId) {
        this.defaultMemberId = defaultMemberId;
    }

    @Override
    public Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return defaultMemberId;
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ignored) {
            return defaultMemberId;
        }
    }
}
