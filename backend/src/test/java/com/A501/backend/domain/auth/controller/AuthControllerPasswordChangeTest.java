package com.A501.backend.domain.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.config.CorsProperties;
import com.A501.backend.config.SecurityConfig;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.security.CookieProperties;
import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;
import com.A501.backend.domain.auth.security.JwtProperties;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.service.AuthService;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtProvider.class})
@EnableConfigurationProperties({CookieProperties.class, JwtProperties.class, CorsProperties.class})
class AuthControllerPasswordChangeTest {

    private static final String REQUEST_BODY =
            "{\"currentPassword\":\"Abcdef1!\",\"newPassword\":\"Newpass1!\",\"newPasswordConfirm\":\"Newpass1!\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthService authService;

    @Test
    void changePassword_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returnsOkWithValidBearerToken() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_returnsBadRequestWhenNewPasswordFailsFormatValidation() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Abcdef1!\",\"newPassword\":\"short1!\","
                                + "\"newPasswordConfirm\":\"short1!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returnsUnauthorizedWhenCurrentPasswordMismatch() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH))
                .when(authService).changePassword(1L, "Abcdef1!", "Newpass1!", "Newpass1!", null);

        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returnsBadRequestWhenConfirmationMismatch() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(AuthErrorCode.NEW_PASSWORD_CONFIRMATION_MISMATCH))
                .when(authService).changePassword(1L, "Abcdef1!", "Newpass1!", "Newpass1!", null);

        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returnsForbiddenWhenPasswordNotSet() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(AuthErrorCode.PASSWORD_NOT_SET))
                .when(authService).changePassword(1L, "Abcdef1!", "Newpass1!", "Newpass1!", null);

        mockMvc.perform(patch("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isForbidden());
    }

    private String generateAccessToken() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        return jwtProvider.generateAccessToken(parent);
    }
}
