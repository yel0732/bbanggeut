package com.A501.backend.domain.game.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.game.entity.RecyclingCategory;
import com.A501.backend.domain.game.entity.RecyclingItem;
import com.A501.backend.domain.game.entity.RecyclingRuleVersion;
import com.A501.backend.domain.game.repository.RecyclingItemRepository;
import com.A501.backend.domain.game.repository.RecyclingRuleVersionRepository;

@SpringBootTest
@AutoConfigureMockMvc
class RecyclingRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecyclingItemRepository recyclingItemRepository;

    @Autowired
    private RecyclingRuleVersionRepository ruleVersionRepository;

    @BeforeEach
    void setUp() {
        recyclingItemRepository.deleteAll();
        ruleVersionRepository.deleteAll();
    }

    @Test
    void returnsActiveRuleAndOnlyActiveItems() throws Exception {
        saveRule("RECYCLING_V1", true);
        recyclingItemRepository.saveAndFlush(createItem(
                "BEVERAGE_CAN", "음료수 캔", RecyclingCategory.CAN, true));
        recyclingItemRepository.saveAndFlush(createItem(
                "OLD_CAN", "비활성 캔", RecyclingCategory.CAN, false));

        mockMvc.perform(get("/api/games/recycling/rules/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesVersion").value("RECYCLING_V1"))
                .andExpect(jsonPath("$.plannedDurationSeconds").value(60))
                .andExpect(jsonPath("$.trashSpawnIntervalMs").value(2000))
                .andExpect(jsonPath("$.trashMovementDurationMs").value(10000))
                .andExpect(jsonPath("$.maxVisibleTrash").value(5))
                .andExpect(jsonPath("$.scorePerCorrect").value(100))
                .andExpect(jsonPath("$.wrongGuideDurationMs").value(2500))
                .andExpect(jsonPath("$.categories.length()").value(4))
                .andExpect(jsonPath("$.categories[0].categoryCode").value("CAN"))
                .andExpect(jsonPath("$.categories[1].categoryCode").value("PLASTIC"))
                .andExpect(jsonPath("$.categories[2].categoryCode").value("PAPER"))
                .andExpect(jsonPath("$.categories[3].categoryCode").value("GLASS"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].itemCode").value("BEVERAGE_CAN"))
                .andExpect(jsonPath("$.items[0].itemName").value("음료수 캔"))
                .andExpect(jsonPath("$.items[0].categoryCode").value("CAN"))
                .andExpect(jsonPath("$.items[0].imageUrl")
                        .value("/assets/recycling/items/beverage-can.png"));
    }

    @Test
    void returnsNotFoundWhenActiveRuleDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/games/recycling/rules/active"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("ACTIVE_RECYCLING_RULE_NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenMultipleRulesAreActive() throws Exception {
        saveRule("RECYCLING_V1", true);
        saveRule("RECYCLING_V2", true);

        mockMvc.perform(get("/api/games/recycling/rules/active"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("MULTIPLE_ACTIVE_RECYCLING_RULES"));
    }

    @Test
    void returnsConflictWhenActiveCatalogIsEmpty() throws Exception {
        saveRule("RECYCLING_V1", true);

        mockMvc.perform(get("/api/games/recycling/rules/active"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMPTY_RECYCLING_RULE"));
    }

    @Test
    void rejectsDuplicatedItemCode() {
        recyclingItemRepository.saveAndFlush(createItem(
                "BEVERAGE_CAN", "음료수 캔", RecyclingCategory.CAN, true));

        assertThatThrownBy(() -> recyclingItemRepository.saveAndFlush(createItem(
                "BEVERAGE_CAN", "다른 캔", RecyclingCategory.CAN, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void saveRule(String versionCode, boolean active) {
        ruleVersionRepository.saveAndFlush(RecyclingRuleVersion.create(
                versionCode,
                60,
                2000,
                10000,
                5,
                100,
                2500,
                active));
    }

    private RecyclingItem createItem(
            String itemCode,
            String itemName,
            RecyclingCategory category,
            boolean active) {
        return RecyclingItem.create(
                9L,
                itemCode,
                itemName,
                "/assets/recycling/items/" + itemCode.toLowerCase().replace('_', '-') + ".png",
                category,
                "기억해 두자! " + itemName + "은 알맞은 통에 넣으면 돼!",
                active);
    }
}
