package com.A501.backend.domain.game.dto.response;

import com.A501.backend.domain.game.entity.RecyclingCategory;

public record RecyclingCategoryResponse(
        String categoryCode,
        String categoryName,
        String binImageUrl
) {

    public static RecyclingCategoryResponse from(RecyclingCategory category) {
        return new RecyclingCategoryResponse(
                category.name(),
                category.getCategoryName(),
                category.getBinImageUrl());
    }
}
