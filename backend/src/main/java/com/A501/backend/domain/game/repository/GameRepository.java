package com.A501.backend.domain.game.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllByGameIdIn(Collection<Long> gameIds);
}
