package com.A501.backend.domain.game.dto.response;

import com.A501.backend.domain.game.entity.RecyclingItem;

public record RecyclingItemResponse(
        String itemCode,
        String itemName,
        String categoryCode,
        String imageUrl,
        String guideMessage
) {

    public static RecyclingItemResponse from(RecyclingItem item) {
        return new RecyclingItemResponse(
                item.getItemCode(),
                item.getItemName(),
                item.getCategory().name(),
                item.getImageUrl(),
                item.getGuideMessage());
    }
}
