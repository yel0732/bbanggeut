package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;

@SpringBootTest
@Transactional
class AuthPurgeServiceIntegrationTest {

    private static final String PRESET_KEY = "profile-presets/parent-avatar";

    @Autowired
    private AuthPurgeService authPurgeService;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void hardDeleteRefreshTokens_removesRevokedAndActiveTokensAlike() {
        Parent parent = saveParent("tokens@example.com");
        RefreshToken revoked = saveToken(parent, "hash-revoked");
        revoked.revoke();
        refreshTokenRepository.saveAndFlush(revoked);
        saveToken(parent, "hash-active");

        authPurgeService.hardDeleteRefreshTokens(parent.getParentId());

        assertThat(refreshTokenRepository.findByTokenHash("hash-revoked")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("hash-active")).isEmpty();
    }

    @Test
    void hardDeleteRefreshTokens_leavesOtherParentsTokensAlone() {
        Parent target = saveParent("target@example.com");
        Parent other = saveParent("other@example.com");
        saveToken(target, "hash-target");
        saveToken(other, "hash-other");

        authPurgeService.hardDeleteRefreshTokens(target.getParentId());

        assertThat(refreshTokenRepository.findByTokenHash("hash-target")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("hash-other")).isPresent();
    }

    @Test
    void hardDeleteParent_removesTheRow() {
        Parent parent = saveParent("gone@example.com");

        authPurgeService.hardDeleteParent(parent.getParentId());

        assertThat(parentRepository.findById(parent.getParentId())).isEmpty();
    }

    // 이전 주기에서 여기까지 성공한 계정이 다시 들어오는 상황이 정상적으로 발생한다.
    // 파기는 몇 번을 호출해도 성공해야 한다.
    @Test
    void hardDelete_isIdempotent() {
        Parent parent = saveParent("twice@example.com");
        saveToken(parent, "hash-twice");
        Long parentId = parent.getParentId();

        authPurgeService.hardDeleteRefreshTokens(parentId);
        authPurgeService.hardDeleteParent(parentId);
        authPurgeService.hardDeleteRefreshTokens(parentId);
        authPurgeService.hardDeleteParent(parentId);

        assertThat(parentRepository.findById(parentId)).isEmpty();
    }

    @Test
    void hardDelete_ignoresNullParentId() {
        authPurgeService.hardDeleteRefreshTokens(null);
        authPurgeService.hardDeleteParent(null);
    }

    // refresh_token은 parent를 @ManyToOne으로 참조하는 유일한 도메인 간 관계다. 순서를 틀리면
    // 조용히 고아가 남는 대신 예외로 드러난다 — 다른 도메인(child·report·conversation·game)에는
    // FK도 연관 매핑도 없어 이런 보호가 없다.
    //
    // 예외 타입은 시점에 따라 갈린다. 영속성 컨텍스트에 RefreshToken이 올라와 있으면 Hibernate가
    // TransientPropertyValueException으로 먼저 잡고(InvalidDataAccessApiUsageException),
    // 그렇지 않으면 DELETE가 DB에 닿아 FK 제약이 DataIntegrityViolationException을 던진다.
    // 둘 다 DataAccessException이므로 "시끄럽게 실패한다"는 사실만 고정한다.
    @Test
    void deletingParentBeforeItsRefreshTokensFailsLoudly() {
        Parent parent = saveParent("fk@example.com");
        saveToken(parent, "hash-fk");

        assertThatThrownBy(() -> {
            authPurgeService.hardDeleteParent(parent.getParentId());
            parentRepository.flush();
        }).isInstanceOf(DataAccessException.class);
    }

    private Parent saveParent(String email) {
        Parent parent = Parent.createLocal(email, "hashed-password", LocalDate.of(1990, 1, 1));
        parent.changeProfileImage(PRESET_KEY);
        return parentRepository.saveAndFlush(parent);
    }

    private RefreshToken saveToken(Parent parent, String tokenHash) {
        return refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(parent, tokenHash, LocalDateTime.now().plusDays(1), false));
    }
}
