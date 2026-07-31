package com.A501.backend.domain.game.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.game.repository.CardRoundRepository;
import com.A501.backend.domain.game.repository.FruitNinjaFruitResultRepository;
import com.A501.backend.domain.game.repository.FruitNinjaResultRepository;
import com.A501.backend.domain.game.repository.GameSessionRepository;
import com.A501.backend.domain.game.repository.RecyclingGameResultRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamePurgeService {

    private final GameSessionRepository gameSessionRepository;
    private final CardRoundRepository cardRoundRepository;
    private final FruitNinjaFruitResultRepository fruitNinjaFruitResultRepository;
    private final FruitNinjaResultRepository fruitNinjaResultRepository;
    private final RecyclingGameResultRepository recyclingGameResultRepository;

    @Transactional
    public void purgeByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        List<UUID> gameSessionIds = gameSessionRepository.findIdsByChildIds(childIds);
        if (gameSessionIds.isEmpty()) {
            return;
        }

        int cardRoundCount = cardRoundRepository.deleteByGameSessionIds(gameSessionIds);
        int fruitDetailCount =
                fruitNinjaFruitResultRepository.deleteByGameSessionIds(gameSessionIds);
        int fruitResultCount = fruitNinjaResultRepository.deleteByGameSessionIds(gameSessionIds);
        int recyclingResultCount =
                recyclingGameResultRepository.deleteByGameSessionIds(gameSessionIds);
        int gameSessionCount = gameSessionRepository.deleteByChildIds(childIds);

        log.info(
                "게임 데이터 hard delete 완료: gameSessions={}, cardRounds={}, "
                        + "fruitResults={}, fruitDetails={}, recyclingResults={}",
                gameSessionCount,
                cardRoundCount,
                fruitResultCount,
                fruitDetailCount,
                recyclingResultCount);
    }
}
