package com.A501.backend.domain.conversation.service;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

/**
 * JwtAuthenticationFilter가 SecurityContext에 넣은 부모 ID를 현재 사용자로 제공한다.
 * local 프로필은 LocalCurrentMemberProvider가 mock ID로 대체하므로 !local에만 등록한다.
 */
@Component
@Profile("!local")
public class SecurityCurrentMemberProvider implements CurrentMemberProvider {

    @Override
    public Long getCurrentMemberId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ConversationException(ConversationErrorCode.UNAUTHORIZED);
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
            throw new ConversationException(ConversationErrorCode.UNAUTHORIZED);
        }
    }
}
