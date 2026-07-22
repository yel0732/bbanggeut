package com.A501.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void savesAndFindsByTokenHash() {
        Parent parent = parentRepository.save(
                Parent.createLocal("token-owner@example.com", "hashed-password", LocalDate.of(1990, 1, 1)));

        RefreshToken token = RefreshToken.issue(parent, "hash-1", LocalDateTime.now().plusDays(1), false);
        refreshTokenRepository.save(token);

        assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isPresent();
    }

    @Test
    void rejectsDuplicateTokenHash() {
        Parent parent = parentRepository.save(
                Parent.createLocal("token-owner-2@example.com", "hashed-password", LocalDate.of(1990, 1, 1)));
        refreshTokenRepository.save(RefreshToken.issue(parent, "dup-hash", LocalDateTime.now().plusDays(1), false));
        refreshTokenRepository.flush();

        assertThatThrownBy(() -> {
            refreshTokenRepository.save(RefreshToken.issue(parent, "dup-hash", LocalDateTime.now().plusDays(60), true));
            refreshTokenRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsAllNonRevokedTokensForParent() {
        Parent parent = parentRepository.save(
                Parent.createLocal("multi-token@example.com", "hashed-password", LocalDate.of(1990, 1, 1)));
        RefreshToken active = RefreshToken.issue(parent, "active-hash", LocalDateTime.now().plusDays(1), false);
        RefreshToken revoked = RefreshToken.issue(parent, "revoked-hash", LocalDateTime.now().plusDays(1), false);
        revoked.revoke();
        refreshTokenRepository.save(active);
        refreshTokenRepository.save(revoked);

        assertThat(refreshTokenRepository.findAllByParentAndRevokedFalse(parent))
                .extracting(RefreshToken::getTokenHash)
                .containsExactly("active-hash");
    }
}
