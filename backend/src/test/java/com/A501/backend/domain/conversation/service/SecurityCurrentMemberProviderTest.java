package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

class SecurityCurrentMemberProviderTest {

    private final SecurityCurrentMemberProvider provider = new SecurityCurrentMemberProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증_정보가_없으면_401을_던진다() {
        SecurityContextHolder.clearContext();

        assertUnauthorized();
    }

    @Test
    void 익명_인증은_401을_던진다() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        setAuthentication(anonymous);

        assertUnauthorized();
    }

    @Test
    void Long_principal을_그대로_반환한다() {
        setAuthentication(new UsernamePasswordAuthenticationToken(42L, null, List.of()));

        assertThat(provider.getCurrentMemberId()).isEqualTo(42L);
    }

    @Test
    void 숫자가_아닌_이름은_401을_던진다() {
        setAuthentication(new UsernamePasswordAuthenticationToken("not-a-number", null, List.of()));

        assertUnauthorized();
    }

    @Test
    void 양수가_아닌_principal은_401을_던진다() {
        setAuthentication(new UsernamePasswordAuthenticationToken(0L, null, List.of()));

        assertUnauthorized();
    }

    private void setAuthentication(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(provider::getCurrentMemberId)
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ConversationErrorCode.UNAUTHORIZED));
    }
}
