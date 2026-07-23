package com.A501.backend.domain.game.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FruitScoreConfigId implements Serializable {

    @Column(name = "rule_version_id")
    private Long ruleVersionId;

    @Column(name = "fruit_type_id")
    private Long fruitTypeId;
}
