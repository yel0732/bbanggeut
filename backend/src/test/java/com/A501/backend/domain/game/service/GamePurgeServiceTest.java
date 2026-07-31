package com.A501.backend.domain.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.entity.RecyclingGameResult;
import com.A501.backend.domain.game.entity.WordMatchResult;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;
import com.A501.backend.domain.game.metric.FruitResultCount;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.RecyclingGameResultRepository;
import com.A501.backend.domain.game.repository.WordMatchResultRepository;

@DataJpaTest
@Import({JpaAuditingConfig.class, GamePurgeService.class})
class GamePurgeServiceTest {

    @Autowired GamePurgeService gamePurgeService;
    @Autowired GameSessionRepository gameSessionRepository;
    @Autowired CardRoundRepository cardRoundRepository;
    @Autowired FruitNinjaResultRepository fruitNinjaResultRepository;
    @Autowired FruitNinjaFruitResultRepository fruitNinjaFruitResultRepository;
    @Autowired RecyclingGameResultRepository recyclingGameResultRepository;
    @Autowired WordMatchResultRepository wordMatchResultRepository;

    @Test
    void purgesOnlyTargetChildrenGameDataAndKeepsGlobalReferenceData() {
        GameSession targetDobble = saveSession(15L, 7L);
        cardRoundRepository.saveAndFlush(CardRound.create(
                targetDobble.getGameSessionId(),
                1,
                CardResultStatus.COMPLETED,
                1,
                0,
                CognitiveGrade.PERFECT,
                1000L));

        GameSession targetFruit = saveSession(15L, 8L);
        saveFruitResult(targetFruit);

        GameSession otherChildFruit = saveSession(16L, 8L);
        saveFruitResult(otherChildFruit);

        GameSession targetRecycling = saveSession(15L, 9L);
        recyclingGameResultRepository.saveAndFlush(RecyclingGameResult.create(
                targetRecycling.getGameSessionId(), "RECYCLING_V1", 10, 2));
        GameSession targetWordMatch = saveSession(15L, 10L);
        wordMatchResultRepository.saveAndFlush(WordMatchResult.create(
                targetWordMatch.getGameSessionId(), "WORD_MATCH_V1", 1, 0, 0, 0));

        gamePurgeService.purgeByChildIds(List.of(15L));

        assertThat(gameSessionRepository.findById(targetDobble.getGameSessionId())).isEmpty();
        assertThat(gameSessionRepository.findById(targetFruit.getGameSessionId())).isEmpty();
        assertThat(cardRoundRepository.count()).isZero();
        assertThat(fruitNinjaResultRepository.findById(targetFruit.getGameSessionId())).isEmpty();
        assertThat(fruitNinjaFruitResultRepository
                .findAllByGameSessionIdOrderByFruitCode(targetFruit.getGameSessionId()))
                .isEmpty();
        assertThat(recyclingGameResultRepository
                .findById(targetRecycling.getGameSessionId()))
                .isEmpty();
        assertThat(wordMatchResultRepository
                .findById(targetWordMatch.getGameSessionId()))
                .isEmpty();

        assertThat(gameSessionRepository.findById(otherChildFruit.getGameSessionId())).isPresent();
        assertThat(fruitNinjaResultRepository.findById(otherChildFruit.getGameSessionId()))
                .isPresent();
        assertThat(fruitNinjaFruitResultRepository
                .findAllByGameSessionIdOrderByFruitCode(otherChildFruit.getGameSessionId()))
                .hasSize(1);
    }

    @Test
    void isIdempotentForEmptyMissingAndAlreadyPurgedTargets() {
        gamePurgeService.purgeByChildIds(List.of());
        gamePurgeService.purgeByChildIds(List.of(999L));

        saveSession(15L, 7L);
        gamePurgeService.purgeByChildIds(List.of(15L));
        gamePurgeService.purgeByChildIds(List.of(15L));

        assertThat(gameSessionRepository.count()).isZero();
    }

    private GameSession saveSession(Long childId, Long gameId) {
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 29, 9, 0);
        return gameSessionRepository.saveAndFlush(GameSession.create(
                childId,
                gameId,
                UUID.randomUUID(),
                null,
                selectedAt,
                selectedAt.plusSeconds(3),
                selectedAt.plusMinutes(2).plusSeconds(3),
                120,
                1000));
    }

    private void saveFruitResult(GameSession session) {
        fruitNinjaResultRepository.saveAndFlush(FruitNinjaResult.create(
                session.getGameSessionId(), "fruit-v1", 2, 1));
        fruitNinjaFruitResultRepository.saveAndFlush(FruitNinjaFruitResult.create(
                session.getGameSessionId(),
                new FruitResultCount("APPLE", 2, 1)));
    }
}
