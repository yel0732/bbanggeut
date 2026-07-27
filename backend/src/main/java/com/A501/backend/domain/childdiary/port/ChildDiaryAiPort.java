package com.A501.backend.domain.childdiary.port;

import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiResponse;

public interface ChildDiaryAiPort {
    ChildDiaryAiResponse generate(ChildDiaryAiRequest request);
}
