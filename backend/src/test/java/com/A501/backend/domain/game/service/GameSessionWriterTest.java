package com.A501.backend.domain.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;
import com.A501.backend.domain.game.entity.FruitNinjaResult;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;
import com.A501.backend.domain.game.metric.FruitResultCount;
import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;

@DataJpaTest
@Import({JpaAuditingConfig.class, GameSessionWriter.class})
class GameSessionWriterTest {

    @Autowired GameSessionWriter gameSessionWriter;
    @Autowired GameSessionRepository gameSessionRepository;
    @Autowired CardRoundRepository cardRoundRepository;
    @Autowired FruitNinjaResultRepository fruitNinjaResultRepository;
    @Autowired FruitNinjaFruitResultRepository fruitNinjaFruitResultRepository;

    @Test
    void rollsBackSessionWhenCardRoundSaveFails() {
        GameSession gameSession = createGameSession();
        CardRound first = completedRound(gameSession.getGameSessionId(), 1);
        CardRound duplicate = completedRound(gameSession.getGameSessionId(), 1);

        assertThatThrownBy(() -> gameSessionWriter.saveDobbleResult(
                gameSession,
                List.of(first, duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(gameSessionRepository.findById(gameSession.getGameSessionId())).isEmpty();
        assertThat(cardRoundRepository.count()).isZero();
    }

    @Test
    void rollsBackAllFruitDataWhenFruitDetailSaveFails() {
        GameSession gameSession = createGameSession();
        FruitNinjaResult summary = FruitNinjaResult.create(
                gameSession.getGameSessionId(), "fruit-v1", 4, 2);
        FruitNinjaFruitResult first = FruitNinjaFruitResult.create(
                gameSession.getGameSessionId(),
                new FruitResultCount("APPLE", 2, 1));
        FruitNinjaFruitResult duplicate = FruitNinjaFruitResult.create(
                gameSession.getGameSessionId(),
                new FruitResultCount("APPLE", 2, 1));

        assertThatThrownBy(() -> gameSessionWriter.saveFruitNinjaResult(
                gameSession,
                summary,
                List.of(first, duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(gameSessionRepository.findById(gameSession.getGameSessionId())).isEmpty();
        assertThat(fruitNinjaResultRepository.findById(gameSession.getGameSessionId())).isEmpty();
        assertThat(fruitNinjaFruitResultRepository.count()).isZero();
    }

    private GameSession createGameSession() {
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        return GameSession.create(
                15L,
                7L,
                UUID.randomUUID(),
                null,
                selectedAt,
                selectedAt.plusSeconds(3),
                selectedAt.plusMinutes(2).plusSeconds(3),
                120,
                1000);
    }

    private CardRound completedRound(UUID gameSessionId, int roundNo) {
        return CardRound.create(
                gameSessionId,
                roundNo,
                CardResultStatus.COMPLETED,
                1,
                0,
                CognitiveGrade.PERFECT,
                1000L);
    }
}
