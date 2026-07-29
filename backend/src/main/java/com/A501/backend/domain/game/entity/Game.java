package com.A501.backend.domain.game.entity;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 카탈로그(V3 마이그레이션). 식별자와 이름은 마이그레이션이 주입하는 고정 값이라
 * 애플리케이션에서 생성하지 않고 조회만 한다.
 */
@Getter
@Entity
@Table(name = "game")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game extends BaseTimeEntity {

    @Id
    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "game_code", nullable = false, unique = true, length = 40)
    private String gameCode;

    @Column(name = "game_name", nullable = false, length = 100)
    private String gameName;
}
