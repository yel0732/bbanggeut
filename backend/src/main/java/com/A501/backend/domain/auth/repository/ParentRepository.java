package com.A501.backend.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.auth.entity.Parent;

public interface ParentRepository extends JpaRepository<Parent, Long> {

    Optional<Parent> findByEmail(String email);

    Optional<Parent> findByProviderId(String providerId);
}
