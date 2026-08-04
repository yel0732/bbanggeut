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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParentRole role = ParentRole.PARENT;

    // 부모도 자녀와 동일하게 프로필 사진을 갖는다. 프리셋과 업로드(웹캠) 사진을 S3 키 하나로 표현하고,
    // 공개 여부는 키 접두사가 결정한다(common/storage).
    //
    // 기본값을 엔티티에 상수로 두지 않는다 — 그러면 설정(app.storage.default-preset-key)과 값이
    // 두 군데로 갈라져 한쪽만 바꿔도 조용히 어긋난다. 자녀와 동일하게 생성 시점에 서비스가
    // 설정값을 넣어준다(AuthService). 넣지 않고 저장하면 NOT NULL 위반으로 즉시 드러난다.
    @Column(name = "profile_image_key", nullable = false, length = 512)
    private String profileImageKey;

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

    // now는 호출부(AuthService)가 Asia/Seoul 기준으로 계산해 넘긴다 — 엔티티가 타임존을 알 필요 없이
    // 다른 소프트 삭제 시각과 동일한 기준으로 deleted_at을 남기기 위함.
    public void withdraw(LocalDateTime now) {
        this.deletedAt = now;
    }

    // birth_date는 NOT NULL이라 비울 수 없어 고정값으로 덮는다.
    private static final LocalDate ANONYMIZED_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    /**
     * 탈퇴 즉시 식별정보와 자격증명을 제거한다.
     *
     * <p>두 가지를 동시에 해결한다.
     *
     * <p><b>1. 재가입 차단 해소.</b> email·providerId의 unique 제약은 소프트 삭제된 row에도
     * 적용된다. 파기 스케줄러가 row를 지울 때까지 기다리면 그 사이 같은 이메일·구글 계정으로
     * 재가입할 수 없고, 경로마다 다른 에러가 나간다(로컬은 EMAIL_ALREADY_EXISTS, googleAuth는
     * DB 제약 충돌로 DATA_CONFLICT). 스케줄러를 켜지 않으면 영구 차단된다.
     *
     * <p><b>2. 개인정보·자격증명 즉시 파기.</b> 스케줄러가 꺼져 있으면 row가 무기한 남는데,
     * 익명화 덕에 재가입은 잘 되므로 아무도 이상함을 느끼지 못한다. 그동안 생년월일과
     * 비밀번호·PIN 해시가 계속 보존되는 것을 막는다.
     *
     * <p>파기 경로가 쓰는 값은 건드리지 않는다 — parentId(삭제 키), deletedAt(대상 선정 기준),
     * profileImageKey(잔여 S3 객체 정리)는 그대로 둔다.
     *
     * <p>원래 값은 복구할 수 없다 — 탈퇴 취소 기능을 만들 수 없다는 뜻이다(복구 미제공 방침).
     */
    public void anonymize() {
        this.email = "withdrawn-" + parentId + "@deleted.invalid";
        this.providerId = null;
        this.passwordHash = null;
        this.pinHash = null;
        this.pinSetAt = null;
        this.birthDate = ANONYMIZED_BIRTH_DATE;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isAdmin() {
        return role == ParentRole.ADMIN;
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

    public void changeProfileImage(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }
}
