package com.A501.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.auth.entity.Parent;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ParentRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    // 운영 코드에서는 AuthService가 설정값(app.storage.default-preset-key)을 넣어준다. 이 테스트는
    // AuthService를 거치지 않고 엔티티를 직접 저장하므로, NOT NULL인 프로필 이미지 키를 직접 채운다.
    private static final String PRESET_KEY = "profile-presets/parent-avatar";

    @Autowired
    private ParentRepository parentRepository;

    @Test
    void savesAndFindsByEmail() {
        parentRepository.save(localParent("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1)));

        assertThat(parentRepository.findByEmail("parent@example.com")).isPresent();
    }

    @Test
    void persistsBirthDate() {
        parentRepository.save(localParent("birthdate@example.com", "hashed-password", LocalDate.of(1990, 5, 17)));
        parentRepository.flush();
        parentRepository.findByEmail("birthdate@example.com").ifPresentOrElse(
                found -> assertThat(found.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 17)),
                () -> { throw new AssertionError("parent not found"); });
    }

    @Test
    void rejectsDuplicateEmail() {
        parentRepository.save(localParent("dup@example.com", "hashed-password", LocalDate.of(1990, 1, 1)));
        parentRepository.flush();

        assertThatThrownBy(() -> {
            parentRepository.save(localParent("dup@example.com", "another-hash", LocalDate.of(1991, 2, 2)));
            parentRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByProviderId() {
        Parent parent = Parent.createGoogle("google@example.com", "google-sub-id", LocalDate.of(1995, 5, 5));
        parent.changeProfileImage(PRESET_KEY);

        parentRepository.save(parent);

        assertThat(parentRepository.findByProviderId("google-sub-id")).isPresent();
    }

    // --- 물리 파기 대상 조회 ---

    @Test
    void findIdsToPurge_excludesActiveAccounts() {
        parentRepository.save(localParent("active@example.com", "hash", LocalDate.of(1990, 1, 1)));
        Parent withdrawn = parentRepository.save(
                localParent("gone@example.com", "hash", LocalDate.of(1990, 1, 1)));
        withdrawn.withdraw(NOW.minusMinutes(1));
        parentRepository.saveAndFlush(withdrawn);

        assertThat(parentRepository.findIdsToPurge(NOW, PageRequest.of(0, 10)))
                .containsExactly(withdrawn.getParentId());
    }

    // 유예 기간을 도입하면 purgeBefore가 과거로 밀린다. 그보다 나중에 탈퇴한 계정은
    // 아직 파기 대상이 아니어야 한다.
    @Test
    void findIdsToPurge_excludesAccountsWithdrawnAfterPurgeBefore() {
        Parent recent = parentRepository.save(
                localParent("recent@example.com", "hash", LocalDate.of(1990, 1, 1)));
        recent.withdraw(NOW.plusMinutes(1));
        parentRepository.saveAndFlush(recent);

        assertThat(parentRepository.findIdsToPurge(NOW, PageRequest.of(0, 10))).isEmpty();
    }

    // 경계값: deleted_at == purgeBefore 는 포함(<=)이어야 한다. 배타로 두면
    // grace-period=0d 에서 방금 탈퇴한 계정이 한 주기를 건너뛴다.
    @Test
    void findIdsToPurge_includesAccountWithdrawnExactlyAtPurgeBefore() {
        Parent boundary = parentRepository.save(
                localParent("boundary@example.com", "hash", LocalDate.of(1990, 1, 1)));
        boundary.withdraw(NOW);
        parentRepository.saveAndFlush(boundary);

        assertThat(parentRepository.findIdsToPurge(NOW, PageRequest.of(0, 10)))
                .containsExactly(boundary.getParentId());
    }

    // 오래 전에 탈퇴한 계정이 먼저 처리되어야 한 계정이 계속 밀리는 일이 없다.
    @Test
    void findIdsToPurge_returnsOldestFirstAndHonoursLimit() {
        Parent older = parentRepository.save(
                localParent("older@example.com", "hash", LocalDate.of(1990, 1, 1)));
        older.withdraw(NOW.minusDays(3));
        Parent newer = parentRepository.save(
                localParent("newer@example.com", "hash", LocalDate.of(1990, 1, 1)));
        newer.withdraw(NOW.minusDays(1));
        parentRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(parentRepository.findIdsToPurge(NOW, PageRequest.of(0, 1)))
                .containsExactly(older.getParentId());
    }

    private Parent localParent(String email, String passwordHash, LocalDate birthDate) {
        Parent parent = Parent.createLocal(email, passwordHash, birthDate);
        parent.changeProfileImage(PRESET_KEY);
        return parent;
    }
}
