package com.A501.backend.domain.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.FruitScoreConfigId;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;

public interface FruitScoreConfigRepository
        extends JpaRepository<FruitScoreConfig, FruitScoreConfigId> {

    @Query("""
            select config
            from FruitScoreConfig config
            join fetch config.fruitType fruit
            where config.ruleVersion = :ruleVersion
            order by fruit.fruitCode
            """)
    List<FruitScoreConfig> findAllByRuleVersion(
            @Param("ruleVersion") MovementGameRuleVersion ruleVersion);
}
