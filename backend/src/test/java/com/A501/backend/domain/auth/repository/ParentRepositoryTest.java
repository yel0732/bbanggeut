package com.A501.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.domain.auth.entity.Parent;

@DataJpaTest
class ParentRepositoryTest {

    @Autowired
    private ParentRepository parentRepository;

    @Test
    void savesAndFindsByEmail() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));

        parentRepository.save(parent);

        assertThat(parentRepository.findByEmail("parent@example.com")).isPresent();
    }

    @Test
    void rejectsDuplicateEmail() {
        parentRepository.save(Parent.createLocal("dup@example.com", "hashed-password", LocalDate.of(1990, 1, 1)));
        parentRepository.flush();

        assertThatThrownBy(() -> {
            parentRepository.save(Parent.createLocal("dup@example.com", "another-hash", LocalDate.of(1991, 2, 2)));
            parentRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByProviderId() {
        Parent parent = Parent.createGoogle("google@example.com", "google-sub-id", LocalDate.of(1995, 5, 5));

        parentRepository.save(parent);

        assertThat(parentRepository.findByProviderId("google-sub-id")).isPresent();
    }
}
