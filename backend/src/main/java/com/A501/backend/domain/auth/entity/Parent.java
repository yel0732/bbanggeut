package com.A501.backend.domain.auth.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "parent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Parent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", unique = true)
    private String providerId;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "pin_failed_attempts", nullable = false)
    private int pinFailedAttempts = 0;

    @Column(name = "pin_locked_until")
    private LocalDateTime pinLockedUntil;

    @Column(name = "pin_set_at")
    private LocalDateTime pinSetAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Parent(String email, String passwordHash, LocalDate birthDate, AuthProvider provider, String providerId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.birthDate = birthDate;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static Parent createLocal(String email, String passwordHash, LocalDate birthDate) {
        return new Parent(email, passwordHash, birthDate, AuthProvider.LOCAL, null);
    }

    public static Parent createGoogle(String email, String providerId, LocalDate birthDate) {
        return new Parent(email, null, birthDate, AuthProvider.GOOGLE, providerId);
    }

    public void linkGoogleAccount(String providerId) {
        this.providerId = providerId;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
