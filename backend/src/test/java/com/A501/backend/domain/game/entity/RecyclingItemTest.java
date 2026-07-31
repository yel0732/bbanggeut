package com.A501.backend.domain.game.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecyclingItemTest {

    @Test
    void createsEverySupportedCategory() {
        for (RecyclingCategory category : RecyclingCategory.values()) {
            RecyclingItem item = RecyclingItem.create(
                    9L,
                    category.name() + "_ITEM",
                    category.getCategoryName() + " 쓰레기",
                    "/assets/recycling/items/item.png",
                    category,
                    "알맞은 통에 넣으면 돼!",
                    true);

            assertThat(item.getCategory()).isEqualTo(category);
            assertThat(item.isActive()).isTrue();
        }
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatThrownBy(() -> RecyclingItem.create(
                9L,
                " ",
                "음료수 캔",
                "/assets/recycling/items/beverage-can.png",
                RecyclingCategory.CAN,
                "캔 통에 넣으면 돼!",
                true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RecyclingItem.create(
                9L,
                "BEVERAGE_CAN",
                "음료수 캔",
                "/assets/recycling/items/beverage-can.png",
                null,
                "캔 통에 넣으면 돼!",
                true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
