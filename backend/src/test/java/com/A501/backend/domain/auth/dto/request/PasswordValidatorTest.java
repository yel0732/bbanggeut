package com.A501.backend.domain.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "Abcdef1!",           // 최소 조합(영문+숫자+특문) 충족, 8자 하한 경계
            "Aa1\\]\"@x",         // 허용 특수문자 중 이스케이프가 필요한 문자(\, ], ", @) 포함
            "Password1234!@#$",   // 16자 상한 경계
            "abcdefg1!"           // 대소문자 혼합을 강제하지 않으므로 소문자만으로도 통과
    })
    void acceptsValidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab1!",                // 8자 미만
            "Password123456!@#$",  // 16자 초과
            "ABCDEFGH!",           // 숫자 없음
            "Abcdefgh1",           // 특수문자 없음
            "12345678!",           // 영문 없음
            "Abcdef1 !",           // 허용되지 않는 공백 포함
            "Abcdef1가나"           // 허용되지 않는 한글 포함
    })
    void rejectsInvalidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
