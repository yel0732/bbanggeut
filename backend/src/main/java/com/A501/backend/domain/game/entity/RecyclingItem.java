package com.A501.backend.domain.game.entity;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "recycling_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecyclingItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recycling_item_id")
    private Long recyclingItemId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "item_code", nullable = false, unique = true, length = 40)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RecyclingCategory category;

    @Column(name = "guide_message", nullable = false, length = 255)
    private String guideMessage;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder(access = AccessLevel.PRIVATE)
    private RecyclingItem(
            Long gameId,
            String itemCode,
            String itemName,
            String imageUrl,
            RecyclingCategory category,
            String guideMessage,
            boolean active) {
        this.gameId = gameId;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.imageUrl = imageUrl;
        this.category = category;
        this.guideMessage = guideMessage;
        this.active = active;
    }

    public static RecyclingItem create(
            Long gameId,
            String itemCode,
            String itemName,
            String imageUrl,
            RecyclingCategory category,
            String guideMessage,
            boolean active) {
        validate(gameId, itemCode, itemName, imageUrl, category, guideMessage);
        return RecyclingItem.builder()
                .gameId(gameId)
                .itemCode(itemCode)
                .itemName(itemName)
                .imageUrl(imageUrl)
                .category(category)
                .guideMessage(guideMessage)
                .active(active)
                .build();
    }

    private static void validate(
            Long gameId,
            String itemCode,
            String itemName,
            String imageUrl,
            RecyclingCategory category,
            String guideMessage) {
        if (gameId == null || gameId < 1) {
            throw new IllegalArgumentException("게임 ID는 1 이상이어야 합니다.");
        }
        requireText(itemCode, 40, "쓰레기 코드");
        requireText(itemName, 100, "쓰레기 이름");
        requireText(imageUrl, 512, "쓰레기 이미지 URL");
        if (category == null) {
            throw new IllegalArgumentException("분리수거 유형은 필수입니다.");
        }
        requireText(guideMessage, 255, "분리수거 안내 문구");
    }

    private static void requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은 1~" + maxLength + "자여야 합니다.");
        }
    }
}
