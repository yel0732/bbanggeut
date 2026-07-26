package com.A501.backend.domain.conversation.service;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;

public interface DialogueAiClient {

    DialogueAiResponse processTurn(DialogueAiRequest request);
}
