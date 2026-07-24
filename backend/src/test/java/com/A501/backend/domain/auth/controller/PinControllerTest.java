package com.A501.backend.domain.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;
import com.A501.backend.domain.auth.security.JwtProperties;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.service.PinService;

@WebMvcTest(PinController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtProvider.class})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class PinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private PinService pinService;

    @Test
    void setPin_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/profile/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setPin_returnsOkWithValidBearerTokenAndPin() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(post("/api/profile/pin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void setPin_returnsBadRequestForNonFourDigitPin() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(post("/api/profile/pin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"12a4\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setPin_returnsForbiddenWhenPinAlreadySet() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_ALREADY_SET))
                .when(pinService).setPin(1L, "1234");

        mockMvc.perform(post("/api/profile/pin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyPin_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/profile/pin/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyPin_returnsOkOnMatch() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(post("/api/profile/pin/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void verifyPin_returnsForbiddenWhenPinNotSet() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_NOT_SET))
                .when(pinService).verifyPin(1L, "1234");

        mockMvc.perform(post("/api/profile/pin/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyPin_returnsUnauthorizedOnMismatch() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_MISMATCH))
                .when(pinService).verifyPin(1L, "0000");

        mockMvc.perform(post("/api/profile/pin/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyPin_returnsLockedWhenLockoutTriggered() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_LOCKED))
                .when(pinService).verifyPin(1L, "0000");

        mockMvc.perform(post("/api/profile/pin/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isLocked());
    }

    private String generateAccessToken() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        return jwtProvider.generateAccessToken(parent);
    }
}
