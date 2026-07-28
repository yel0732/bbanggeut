package com.A501.backend.domain.child.entity;

import java.time.LocalDateTime;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "child")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_id")
    private Long childId;

    // Parent 엔티티를 직접 참조하지 않고 ID만 보관한다 — child 도메인이 auth 도메인의
    // 엔티티를 참조하지 않는다는 규칙을 지키기 위함이며, 소유권 검증은 ID 비교로 충분하다.
    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    // 같은 부모 아래 삭제되지 않은 자녀끼리만 이름이 유일해야 한다. 이 조건은 일반
    // UNIQUE(parent_id, name) 제약으로 표현할 수 없고(소프트 삭제된 이름까지 묶어버림) DB별
    // 함수 기반 인덱스가 필요해 여기서는 강제하지 않는다 — ChildService의 애플리케이션
    // 레이어 중복 체크가 실질적인 방어선이다.
    @Column(nullable = false, length = 15)
    private String name;

    // 프리셋과 업로드(웹캠) 사진을 S3 키 하나로 표현하고, 공개 여부는 키 접두사가 결정한다
    // (common/storage). 기본 프리셋 키는 ChildService가 설정값에서 채워 넣는다.
    @Column(name = "profile_image_key", nullable = false, length = 512)
    private String profileImageKey;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Child(Long parentId, String name, String profileImageKey) {
        this.parentId = parentId;
        this.name = name;
        this.profileImageKey = profileImageKey;
    }

    public static Child create(Long parentId, String name, String profileImageKey) {
        return new Child(parentId, name, profileImageKey);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeProfileImage(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(Long parentId) {
        return this.parentId.equals(parentId);
    }
}
