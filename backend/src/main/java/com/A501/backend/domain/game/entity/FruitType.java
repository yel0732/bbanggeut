package com.A501.backend.domain.game.entity;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "fruit_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FruitType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fruit_type_id")
    private Long fruitTypeId;

    @Column(name = "fruit_code", nullable = false, unique = true, length = 40)
    private String fruitCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "default_size_px", nullable = false)
    private int defaultSizePx;

    @Builder(access = AccessLevel.PRIVATE)
    private FruitType(
            String fruitCode,
            String name,
            int defaultSizePx) {
        this.fruitCode = fruitCode;
        this.name = name;
        this.defaultSizePx = defaultSizePx;
    }

    public static FruitType create(
            String fruitCode,
            String name,
            int defaultSizePx) {
        if (fruitCode == null || fruitCode.isBlank() || fruitCode.length() > 40) {
            throw new IllegalArgumentException("과일 코드는 1~40자여야 합니다.");
        }
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("과일 이름은 1~100자여야 합니다.");
        }
        if (defaultSizePx < 1) {
            throw new IllegalArgumentException("과일 기본 크기는 1px 이상이어야 합니다.");
        }

        return FruitType.builder()
                .fruitCode(fruitCode)
                .name(name)
                .defaultSizePx(defaultSizePx)
                .build();
    }
}
