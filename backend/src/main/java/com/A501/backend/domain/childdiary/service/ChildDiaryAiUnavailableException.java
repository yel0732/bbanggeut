package com.A501.backend.domain.childdiary.service;

public class ChildDiaryAiUnavailableException extends RuntimeException {
    public ChildDiaryAiUnavailableException(String message) { super(message); }
    public ChildDiaryAiUnavailableException(String message, Throwable cause) { super(message, cause); }
}
