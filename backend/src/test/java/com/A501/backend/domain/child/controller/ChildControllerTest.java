package com.A501.backend.domain.child.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import com.A501.backend.domain.child.service.ChildService;

@WebMvcTest(ChildController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtProvider.class})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class ChildControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ChildService childService;

    @Test
    void checkName_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/children/name/check").param("name", "chorong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkName_returnsAvailableTrue() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.isNameAvailable(1L, "chorong")).thenReturn(true);

        mockMvc.perform(get("/api/children/name/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("name", "chorong"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"available\":true}"));
    }

    @Test
    void checkName_returnsAvailableFalse() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.isNameAvailable(1L, "chorong")).thenReturn(false);

        mockMvc.perform(get("/api/children/name/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("name", "chorong"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"available\":false}"));
    }

    @Test
    void checkName_returnsBadRequestForBlankName() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(get("/api/children/name/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("name", ""))
                .andExpect(status().isBadRequest());
    }

    private String generateAccessToken() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        return jwtProvider.generateAccessToken(parent);
    }
}
