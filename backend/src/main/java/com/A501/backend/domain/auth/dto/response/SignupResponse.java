package com.A501.backend.domain.auth.dto.response;

import com.A501.backend.domain.auth.entity.Parent;

public record SignupResponse(
        Long parentId,
        String email
) {

    public static SignupResponse of(Parent parent) {
        return new SignupResponse(parent.getParentId(), parent.getEmail());
    }
}
