package com.A501.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class RefreshTokenRepositoryTest {

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void savesAndFindsByTokenHash() {
        Parent parent = parentRepository.save(localParent("token-owner@example.com"));

        RefreshToken token = RefreshToken.issue(parent, "hash-1", LocalDateTime.now().plusDays(1), false);
        refreshTokenRepository.save(token);

        assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isPresent();
    }

    @Test
    void rejectsDuplicateTokenHash() {
        Parent parent = parentRepository.save(localParent("token-owner-2@example.com"));
        refreshTokenRepository.save(RefreshToken.issue(parent, "dup-hash", LocalDateTime.now().plusDays(1), false));
        refreshTokenRepository.flush();

        assertThatThrownBy(() -> {
            refreshTokenRepository.save(RefreshToken.issue(parent, "dup-hash", LocalDateTime.now().plusDays(60), true));
            refreshTokenRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsAllNonRevokedTokensForParent() {
        Parent parent = parentRepository.save(localParent("multi-token@example.com"));
        RefreshToken active = RefreshToken.issue(parent, "active-hash", LocalDateTime.now().plusDays(1), false);
        RefreshToken revoked = RefreshToken.issue(parent, "revoked-hash", LocalDateTime.now().plusDays(1), false);
        revoked.revoke();
        refreshTokenRepository.save(active);
        refreshTokenRepository.save(revoked);

        assertThat(refreshTokenRepository.findAllByParentAndRevokedFalse(parent))
                .extracting(RefreshToken::getTokenHash)
                .containsExactly("active-hash");
    }

    @Test
    void deletesOnlyTokensExpiredBeforeCutoff() {
        Parent parent = parentRepository.save(localParent("cleanup@example.com"));
        RefreshToken longExpired = RefreshToken.issue(
                parent, "long-expired-hash", LocalDateTime.now().minusDays(40), false);
        RefreshToken recentlyExpired = RefreshToken.issue(
                parent, "recently-expired-hash", LocalDateTime.now().minusDays(5), false);
        RefreshToken stillValid = RefreshToken.issue(
                parent, "still-valid-hash", LocalDateTime.now().plusDays(1), false);
        refreshTokenRepository.save(longExpired);
        refreshTokenRepository.save(recentlyExpired);
        refreshTokenRepository.save(stillValid);

        long deletedCount = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(30));

        assertThat(deletedCount).isEqualTo(1);
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder("recently-expired-hash", "still-valid-hash");
    }

    // 운영 코드에서는 AuthService가 설정값(app.storage.default-preset-key)을 넣어준다. 이 테스트는
    // AuthService를 거치지 않고 엔티티를 직접 저장하므로, NOT NULL인 프로필 이미지 키를 직접 채운다.
    private Parent localParent(String email) {
        Parent parent = Parent.createLocal(email, "hashed-password", LocalDate.of(1990, 1, 1));
        parent.changeProfileImage("profile-presets/preset-1");
        return parent;
    }
}
