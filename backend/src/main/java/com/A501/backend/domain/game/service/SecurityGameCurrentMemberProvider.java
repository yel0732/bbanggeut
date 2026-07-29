package com.A501.backend.domain.game.service;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.game.exception.GameErrorCode;
import com.A501.backend.domain.game.exception.GameException;

@Component
@Profile("!local")
public class SecurityGameCurrentMemberProvider implements GameCurrentMemberProvider {

    @Override
    public Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new GameException(GameErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long memberId && memberId > 0) {
            return memberId;
        }
        try {
            Long memberId = Long.valueOf(authentication.getName());
            if (memberId < 1) {
                throw new NumberFormatException();
            }
            return memberId;
        } catch (NumberFormatException exception) {
            throw new GameException(GameErrorCode.UNAUTHORIZED);
        }
    }
}
