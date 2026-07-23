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

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.game.entity.GameSession;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class GameSessionRepositoryTest {

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Test
    void 정상_완료된_게임_세션을_저장하고_조회한다() {
        UUID clientPlayId = UUID.randomUUID();
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        LocalDateTime playStartedAt = selectedAt.plusSeconds(10);
        LocalDateTime completedAt = playStartedAt.plusSeconds(90);
        GameSession session = createSession(clientPlayId, "EASY", selectedAt, playStartedAt, completedAt);

        GameSession saved = gameSessionRepository.saveAndFlush(session);

        GameSession found = gameSessionRepository.findById(saved.getGameSessionId()).orElseThrow();
        assertThat(found.getChildId()).isEqualTo(15L);
        assertThat(found.getGameId()).isEqualTo(7L);
        assertThat(found.getClientPlayId()).isEqualTo(clientPlayId);
        assertThat(found.getDifficultyCode()).isEqualTo("EASY");
        assertThat(found.getSelectedAt()).isEqualTo(selectedAt);
        assertThat(found.getPlayStartedAt()).isEqualTo(playStartedAt);
        assertThat(found.getCompletedAt()).isEqualTo(completedAt);
        assertThat(found.getPlayDurationSeconds()).isEqualTo(90);
        assertThat(found.getScore()).isEqualTo(1200);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 난이도_코드가_없어도_저장된다() {
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        GameSession session = createSession(
                UUID.randomUUID(), null, selectedAt, selectedAt.plusSeconds(10), selectedAt.plusSeconds(100));

        GameSession saved = gameSessionRepository.saveAndFlush(session);

        assertThat(saved.getDifficultyCode()).isNull();
    }

    @Test
    void 같은_client_play_id는_중복_저장할_수_없다() {
        UUID clientPlayId = UUID.randomUUID();
        LocalDateTime selectedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        gameSessionRepository.saveAndFlush(createSession(
                clientPlayId, "EASY", selectedAt, selectedAt.plusSeconds(10), selectedAt.plusSeconds(100)));

        GameSession duplicate = createSession(
                clientPlayId, "EASY", selectedAt, selectedAt.plusSeconds(10), selectedAt.plusSeconds(100));

        assertThatThrownBy(() -> gameSessionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private GameSession createSession(
            UUID clientPlayId,
            String difficultyCode,
            LocalDateTime selectedAt,
            LocalDateTime playStartedAt,
            LocalDateTime completedAt) {
        return GameSession.create(
                15L,
                7L,
                clientPlayId,
                difficultyCode,
                selectedAt,
                playStartedAt,
                completedAt,
                90,
                1200);
    }
}
