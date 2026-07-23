package com.A501.backend.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.config.SecurityConfig;
import com.A501.backend.domain.auth.service.AuthService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void signup_returnsBadRequestWhenEmailHasNoTld() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example\",\"password\":\"Abcdef1!\",\"birthDate\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_returnsBadRequestForMalformedJsonBody() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Abc\\d123!\",\"birthDate\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_returnsBadRequestForMalformedDateFormat() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Abcdef1!\",\"birthDate\":\"2000-1-1\"}"))
                .andExpect(status().isBadRequest());
    }
}
