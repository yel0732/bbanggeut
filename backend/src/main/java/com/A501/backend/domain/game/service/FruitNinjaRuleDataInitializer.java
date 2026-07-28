package com.A501.backend.domain.game.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "game.fruit-ninja.seed",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FruitNinjaRuleDataInitializer implements ApplicationRunner {

    private final FruitNinjaRuleBootstrapService bootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.initialize();
        log.info("Fruit Ninja rule data initialized: {}", FruitNinjaRuleBootstrapService.RULES_VERSION);
    }
}
