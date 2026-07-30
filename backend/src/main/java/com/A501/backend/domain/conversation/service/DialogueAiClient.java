package com.A501.backend.domain.conversation.service;

import com.A501.backend.domain.conversation.dto.internal.DialogueAiRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueAiResponse;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningRequest;
import com.A501.backend.domain.conversation.dto.internal.DialogueOpeningResponse;

public interface DialogueAiClient {

    DialogueOpeningResponse openConversation(DialogueOpeningRequest request);

    DialogueAiResponse processTurn(DialogueAiRequest request);
}
