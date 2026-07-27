package com.A501.backend.domain.child.dto.response;

public record ChildNameCheckResponse(boolean available) {

    public static ChildNameCheckResponse of(boolean available) {
        return new ChildNameCheckResponse(available);
    }
}
