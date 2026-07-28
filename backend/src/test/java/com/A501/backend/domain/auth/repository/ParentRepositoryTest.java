package com.A501.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.auth.entity.Parent;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ParentRepositoryTest {

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

    private Parent localParent(String email, String passwordHash, LocalDate birthDate) {
        Parent parent = Parent.createLocal(email, passwordHash, birthDate);
        parent.changeProfileImage(PRESET_KEY);
        return parent;
    }
}
