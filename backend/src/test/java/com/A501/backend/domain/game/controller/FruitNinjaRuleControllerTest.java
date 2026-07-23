package com.A501.backend.domain.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.game.entity.FruitScoreConfig;
import com.A501.backend.domain.game.entity.FruitType;
import com.A501.backend.domain.game.entity.MovementGameRuleVersion;
import com.A501.backend.domain.game.repository.FruitScoreConfigRepository;
import com.A501.backend.domain.game.repository.FruitTypeRepository;
import com.A501.backend.domain.game.repository.MovementGameRuleVersionRepository;

@SpringBootTest
@AutoConfigureMockMvc
class FruitNinjaRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovementGameRuleVersionRepository ruleVersionRepository;

    @Autowired
    private FruitTypeRepository fruitTypeRepository;

    @Autowired
    private FruitScoreConfigRepository fruitScoreConfigRepository;

    @BeforeEach
    void setUp() {
        fruitScoreConfigRepository.deleteAll();
        fruitTypeRepository.deleteAll();
        ruleVersionRepository.deleteAll();
    }

    @Test
    void 활성화된_게임_규칙과_과일별_설정을_조회한다() throws Exception {
        MovementGameRuleVersion ruleVersion = ruleVersionRepository.saveAndFlush(
                MovementGameRuleVersion.create(
                        "fruit-v1",
                        120,
                        new BigDecimal("1.5000"),
                        800,
                        5,
                        true));
        FruitType apple = fruitTypeRepository.saveAndFlush(
                FruitType.create("APPLE", "사과", 120));
        FruitType grape = fruitTypeRepository.saveAndFlush(
                FruitType.create("GRAPE", "포도", 80));
        fruitScoreConfigRepository.saveAndFlush(
                FruitScoreConfig.create(ruleVersion, grape, 200));
        fruitScoreConfigRepository.saveAndFlush(
                FruitScoreConfig.create(ruleVersion, apple, 100));

        mockMvc.perform(get("/api/games/fruit-ninja/rules/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesVersion").value("fruit-v1"))
                .andExpect(jsonPath("$.plannedDurationSeconds").value(120))
                .andExpect(jsonPath("$.sliceSpeedThreshold").value(1.5))
                .andExpect(jsonPath("$.fruitSpawnIntervalMs").value(800))
                .andExpect(jsonPath("$.maxVisibleFruit").value(5))
                .andExpect(jsonPath("$.fruits.length()").value(2))
                .andExpect(jsonPath("$.fruits[0].fruitCode").value("APPLE"))
                .andExpect(jsonPath("$.fruits[0].name").value("사과"))
                .andExpect(jsonPath("$.fruits[0].sizePx").value(120))
                .andExpect(jsonPath("$.fruits[0].score").value(100))
                .andExpect(jsonPath("$.fruits[1].fruitCode").value("GRAPE"))
                .andExpect(jsonPath("$.fruits[1].score").value(200));
    }

    @Test
    void 활성화된_규칙이_없으면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/games/fruit-ninja/rules/active"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("ACTIVE_FRUIT_NINJA_RULE_NOT_FOUND"));
    }

    @Test
    void 활성화된_규칙이_여러_개면_409를_반환한다() throws Exception {
        ruleVersionRepository.saveAndFlush(MovementGameRuleVersion.create(
                "fruit-v1",
                120,
                new BigDecimal("1.5000"),
                800,
                5,
                true));
        ruleVersionRepository.saveAndFlush(MovementGameRuleVersion.create(
                "fruit-v2",
                120,
                new BigDecimal("1.7000"),
                700,
                5,
                true));

        mockMvc.perform(get("/api/games/fruit-ninja/rules/active"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("MULTIPLE_ACTIVE_FRUIT_NINJA_RULES"));
    }

    @Test
    void 활성화된_규칙에_과일이_없으면_409를_반환한다() throws Exception {
        ruleVersionRepository.saveAndFlush(MovementGameRuleVersion.create(
                "fruit-v1",
                120,
                new BigDecimal("1.5000"),
                800,
                5,
                true));

        mockMvc.perform(get("/api/games/fruit-ninja/rules/active"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMPTY_FRUIT_NINJA_RULE"));
    }
}
