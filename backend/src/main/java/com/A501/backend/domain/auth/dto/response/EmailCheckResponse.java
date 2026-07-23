package com.A501.backend.domain.auth.dto.response;

public record EmailCheckResponse(boolean available) {

    public static EmailCheckResponse of(boolean available) {
        return new EmailCheckResponse(available);
    }
}
