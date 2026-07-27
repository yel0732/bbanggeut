package com.A501.backend.domain.auth.entity;

import java.time.Duration;
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

    // 부모도 자녀와 동일하게 기본 제공 이미지 중 선택하는 프로필 사진을 설정할 수 있다(CHILD와 동일 방식).
    @Column(name = "profile_image_num", nullable = false)
    private int profileImageNum = 1;

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

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean hasPin() {
        return pinHash != null;
    }

    public boolean isPinLocked(LocalDateTime now) {
        return pinLockedUntil != null && pinLockedUntil.isAfter(now);
    }

    // now는 호출부(PinService)가 Asia/Seoul 기준으로 계산해 넘긴다 — 엔티티가 타임존을
    // 알 필요 없이, PIN 잠금 시간 비교와 동일한 기준으로 pinSetAt을 남기기 위함.
    public void setPin(String pinHash, LocalDateTime now) {
        this.pinHash = pinHash;
        this.pinSetAt = now;
        resetPinFailures();
    }

    // 잠금이 만료된 뒤 첫 검증 시도 전에 호출한다. 리셋하지 않으면 실패 횟수가 이미 임계치라
    // 한 번만 틀려도 즉시 재잠금된다.
    public void resetPinFailuresIfLockExpired(LocalDateTime now) {
        if (pinLockedUntil != null && !pinLockedUntil.isAfter(now)) {
            resetPinFailures();
        }
    }

    public void registerPinFailure(LocalDateTime now, int maxFailedAttempts, Duration lockDuration) {
        this.pinFailedAttempts++;
        if (this.pinFailedAttempts >= maxFailedAttempts) {
            this.pinLockedUntil = now.plus(lockDuration);
        }
    }

    public void resetPinFailures() {
        this.pinFailedAttempts = 0;
        this.pinLockedUntil = null;
    }

    public void changeProfileImage(int profileImageNum) {
        this.profileImageNum = profileImageNum;
    }
}
