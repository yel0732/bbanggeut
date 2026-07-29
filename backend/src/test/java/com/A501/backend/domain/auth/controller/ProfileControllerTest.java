package com.A501.backend.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.common.storage.ImageErrorCode;
import com.A501.backend.config.CorsProperties;
import com.A501.backend.config.SecurityConfig;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;
import com.A501.backend.domain.auth.security.JwtProperties;
import com.A501.backend.domain.auth.dto.response.ProfileImageResponse;
import com.A501.backend.domain.auth.dto.response.ProfileResponse;
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

    private static final String PRESET_KEY = "profile-presets/child-avatar-rabbit";
    private static final String UPLOAD_KEY = "profile-uploads/1/parent/abc";
    private static final String UPLOAD_URL = "https://upload.example.com/signed";

    @Test
    void getProfile_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_returnsProfileWithImageKeyAndPinState() throws Exception {
        String accessToken = generateAccessToken();
        when(profileService.getProfile(1L))
                .thenReturn(new ProfileResponse(1L, "parent@example.com", PRESET_KEY, null, true));

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"parentId\":1,\"email\":\"parent@example.com\","
                        + "\"profileImageKey\":\"" + PRESET_KEY + "\",\"profileImageUrl\":null,\"pinSet\":true}"));
    }

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
        when(profileService.updateProfileImage(1L, PRESET_KEY))
                .thenReturn(ProfileImageResponse.of(PRESET_KEY, null));

        mockMvc.perform(patch("/api/profile/image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageKey\":\"" + PRESET_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"profileImageKey\":\"" + PRESET_KEY + "\",\"profileImageUrl\":null}"));

        verify(profileService).updateProfileImage(1L, PRESET_KEY);
    }

    @Test
    void uploadProfileImage_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(multipart("/api/profile/image/upload").file(webcamFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadProfileImage_returnsResolvedUrlWithValidBearerToken() throws Exception {
        String accessToken = generateAccessToken();
        when(profileService.uploadProfileImage(eq(1L), any()))
                .thenReturn(ProfileImageResponse.of(UPLOAD_KEY, UPLOAD_URL));

        mockMvc.perform(multipart("/api/profile/image/upload")
                        .file(webcamFile())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"profileImageUrl\":\"" + UPLOAD_URL + "\"}"));
    }

    // 형식·용량 위반은 서비스가 BusinessException으로 알리고 공통 핸들러가 400으로 변환한다.
    @Test
    void uploadProfileImage_returnsBadRequestForRejectedImage() throws Exception {
        String accessToken = generateAccessToken();
        when(profileService.uploadProfileImage(eq(1L), any()))
                .thenThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE));

        mockMvc.perform(multipart("/api/profile/image/upload")
                        .file(webcamFile())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
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

    // 실제 아동 사진을 픽스처로 쓰지 않는다(AGENTS.md 민감정보 규칙). 컨트롤러 계층 검증에는
    // 바이트 내용이 필요 없고, 형식 검증은 ProfileImageServiceTest가 담당한다.
    private MockMultipartFile webcamFile() {
        return new MockMultipartFile("file", "webcam.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private String generateAccessToken() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        return jwtProvider.generateAccessToken(parent);
    }
}
