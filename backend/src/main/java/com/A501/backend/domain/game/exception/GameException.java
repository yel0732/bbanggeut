package com.A501.backend.domain.game.exception;

import com.A501.backend.common.exception.BusinessException;

public class GameException extends BusinessException {

    public GameException(GameErrorCode errorCode) {
        super(errorCode);
    }
}
