package com.A501.backend.domain.auth.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.A501.backend.config.CorsProperties;
import com.A501.backend.config.SecurityConfig;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;
import com.A501.backend.domain.auth.security.JwtProperties;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.service.ProfileService;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtProvider.class})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ProfileService profileService;

    private static final String PRESET_KEY = "profile-presets/preset-3";
    private static final String PRESET_URL = "https://cdn.example.com/" + PRESET_KEY;

    @Test
    void updateProfileImage_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(patch("/api/profile/image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKey\":\"" + PRESET_KEY + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileImage_returnsResolvedUrlWithValidBearerToken() throws Exception {
        String accessToken = generateAccessToken();
        when(profileService.updateProfileImage(1L, PRESET_KEY)).thenReturn(PRESET_URL);

        mockMvc.perform(patch("/api/profile/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKey\":\"" + PRESET_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"profileImageUrl\":\"" + PRESET_URL + "\"}"));

        verify(profileService).updateProfileImage(1L, PRESET_KEY);
    }

    @Test
    void updateProfileImage_returnsBadRequestForBlankImageKey() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(patch("/api/profile/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private String generateAccessToken() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        return jwtProvider.generateAccessToken(parent);
    }
}
