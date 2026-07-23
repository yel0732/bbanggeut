package com.A501.backend.domain.auth.dto.request;

public final class EmailPatterns {

    public static final String WITH_TLD = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$";

    private EmailPatterns() {
    }
}
