package com.A501.backend.domain.game.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.game.entity.CardRound;
import com.A501.backend.domain.game.entity.GameSession;
import com.A501.backend.domain.game.metric.CardResultStatus;
import com.A501.backend.domain.game.metric.CognitiveGrade;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CardRoundRepositoryTest {

    @Autowired
    private CardRoundRepository cardRoundRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Test
    void 게임_세션의_카드_결과를_제시_순서대로_조회한다() {
        GameSession gameSession = saveGameSession();
        cardRoundRepository.save(completedRound(gameSession.getGameSessionId(), 2));
        cardRoundRepository.save(completedRound(gameSession.getGameSessionId(), 1));
        cardRoundRepository.saveAndFlush(CardRound.create(
                gameSession.getGameSessionId(),
                3,
                CardResultStatus.INCOMPLETE,
                0,
                0,
                null,
                null));

        var rounds = cardRoundRepository.findAllByGameSessionIdOrderByRoundNo(
                gameSession.getGameSessionId());

        assertThat(rounds).extracting(CardRound::getRoundNo).containsExactly(1, 2, 3);
        assertThat(rounds.get(2).getStatus()).isEqualTo(CardResultStatus.INCOMPLETE);
    }

    @Test
    void 같은_게임_세션에_같은_카드_제시_순서를_중복_저장할_수_없다() {
        GameSession gameSession = saveGameSession();
        cardRoundRepository.saveAndFlush(completedRound(gameSession.getGameSessionId(), 1));

        assertThatThrownBy(() -> cardRoundRepository.saveAndFlush(
                completedRound(gameSession.getGameSessionId(), 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private GameSession saveGameSession() {
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        return gameSessionRepository.saveAndFlush(GameSession.create(
                1L,
                2L,
                UUID.randomUUID(),
                null,
                selectedAt,
                selectedAt.plusSeconds(3),
                selectedAt.plusMinutes(2).plusSeconds(3),
                120,
                0));
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
