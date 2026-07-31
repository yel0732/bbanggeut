package com.A501.backend.domain.game.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecyclingCategory {

    CAN("캔", "/assets/recycling/bins/can.png"),
    PLASTIC("플라스틱", "/assets/recycling/bins/plastic.png"),
    PAPER("종이", "/assets/recycling/bins/paper.png"),
    GLASS("유리", "/assets/recycling/bins/glass.png");

    private final String categoryName;
    private final String binImageUrl;
}
