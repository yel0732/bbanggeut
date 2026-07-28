package com.A501.backend.domain.child.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

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
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;
import com.A501.backend.domain.auth.security.JwtProperties;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.child.dto.response.ChildResponse;
import com.A501.backend.domain.child.exception.ChildErrorCode;
import com.A501.backend.domain.child.service.ChildService;

@WebMvcTest(ChildController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtProvider.class})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class ChildControllerTest {

    private static final String PRESET_KEY = "profile-presets/preset-1";
    private static final String OTHER_PRESET_KEY = "profile-presets/preset-2";
    private static final String PRESET_URL = "https://cdn.example.com/" + PRESET_KEY;
    private static final String OTHER_PRESET_URL = "https://cdn.example.com/" + OTHER_PRESET_KEY;
    private static final String UPLOAD_URL = "https://upload.example.com/signed";

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

    @Test
    void create_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/children")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"chorong\",\"imageKey\":\"profile-presets/preset-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returnsCreatedWithChildResponse() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.createChild(1L, "chorong", PRESET_KEY))
                .thenReturn(new ChildResponse(10L, "chorong", PRESET_URL));

        mockMvc.perform(post("/api/children")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"chorong\",\"imageKey\":\"profile-presets/preset-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"childId\":10,\"name\":\"chorong\",\"profileImageUrl\":\"" + PRESET_URL + "\"}"));
    }

    @Test
    void create_returnsBadRequestForBlankName() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(post("/api/children")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"imageKey\":\"profile-presets/preset-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returnsForbiddenWhenPinNotSet() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_NOT_SET))
                .when(childService).createChild(1L, "chorong", PRESET_KEY);

        mockMvc.perform(post("/api/children")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"chorong\",\"imageKey\":\"profile-presets/preset-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returnsForbiddenWhenChildLimitExceeded() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(ChildErrorCode.CHILD_LIMIT_EXCEEDED))
                .when(childService).createChild(1L, "chorong", PRESET_KEY);

        mockMvc.perform(post("/api/children")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"chorong\",\"imageKey\":\"profile-presets/preset-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returnsConflictWhenNameAlreadyExists() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS))
                .when(childService).createChild(1L, "chorong", PRESET_KEY);

        mockMvc.perform(post("/api/children")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"chorong\",\"imageKey\":\"profile-presets/preset-1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void list_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/children")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_returnsChildrenForAuthenticatedParent() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.listChildren(1L))
                .thenReturn(List.of(new ChildResponse(10L, "chorong", PRESET_URL)));

        mockMvc.perform(get("/api/children").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"childId\":10,\"name\":\"chorong\",\"profileImageUrl\":\"" + PRESET_URL + "\"}]"));
    }

    @Test
    void list_returnsEmptyArrayWhenNoChildren() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.listChildren(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/children").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void update_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(patch("/api/children/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"banjjak\",\"imageKey\":\"profile-presets/preset-2\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_returnsOkWithUpdatedChildResponse() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.updateChild(1L, 10L, "banjjak", OTHER_PRESET_KEY))
                .thenReturn(new ChildResponse(10L, "banjjak", OTHER_PRESET_URL));

        mockMvc.perform(patch("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"banjjak\",\"imageKey\":\"profile-presets/preset-2\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"childId\":10,\"name\":\"banjjak\",\"profileImageUrl\":\"" + OTHER_PRESET_URL + "\"}"));
    }

    @Test
    void update_returnsBadRequestForBlankName() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(patch("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"imageKey\":\"profile-presets/preset-2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returnsNotFoundWhenChildNotOwnedOrMissing() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(ChildErrorCode.CHILD_NOT_FOUND))
                .when(childService).updateChild(1L, 10L, "banjjak", OTHER_PRESET_KEY);

        mockMvc.perform(patch("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"banjjak\",\"imageKey\":\"profile-presets/preset-2\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returnsConflictWhenNewNameAlreadyExists() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS))
                .when(childService).updateChild(1L, 10L, "banjjak", OTHER_PRESET_KEY);

        mockMvc.perform(patch("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"banjjak\",\"imageKey\":\"profile-presets/preset-2\"}"))
                .andExpect(status().isConflict());
    }

    // SecurityConfig가 anyRequest().permitAll() 기반 화이트리스트라, 신규 경로를 명시하지 않으면
    // 인증 없이 열린다. 아이 실물 사진 업로드 경로라 이 검증을 반드시 둔다.
    @Test
    void uploadImage_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(multipart("/api/children/10/image").file(webcamFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImage_returnsUpdatedChildResponse() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.uploadChildImage(eq(1L), eq(10L), any()))
                .thenReturn(new ChildResponse(10L, "chorong", UPLOAD_URL));

        mockMvc.perform(multipart("/api/children/10/image")
                        .file(webcamFile())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"childId\":10,\"name\":\"chorong\",\"profileImageUrl\":\"" + UPLOAD_URL + "\"}"));
    }

    @Test
    void uploadImage_returnsNotFoundWhenChildNotOwnedOrMissing() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.uploadChildImage(eq(1L), eq(10L), any()))
                .thenThrow(new BusinessException(ChildErrorCode.CHILD_NOT_FOUND));

        mockMvc.perform(multipart("/api/children/10/image")
                        .file(webcamFile())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadImage_returnsBadRequestForRejectedImage() throws Exception {
        String accessToken = generateAccessToken();
        when(childService.uploadChildImage(eq(1L), eq(10L), any()))
                .thenThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE));

        mockMvc.perform(multipart("/api/children/10/image")
                        .file(webcamFile())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returnsUnauthorizedWithoutBearerToken() throws Exception {
        mockMvc.perform(delete("/api/children/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_returnsNoContentOnSuccess() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(delete("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returnsBadRequestForNonFourDigitPin() throws Exception {
        String accessToken = generateAccessToken();

        mockMvc.perform(delete("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"12a4\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returnsNotFoundWhenChildNotOwnedOrMissing() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(ChildErrorCode.CHILD_NOT_FOUND))
                .when(childService).deleteChild(1L, 10L, "1234");

        mockMvc.perform(delete("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returnsUnauthorizedWhenPinMismatch() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_MISMATCH))
                .when(childService).deleteChild(1L, 10L, "0000");

        mockMvc.perform(delete("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_returnsLockedWhenPinLockedOut() throws Exception {
        String accessToken = generateAccessToken();
        doThrow(new BusinessException(PinErrorCode.PIN_LOCKED))
                .when(childService).deleteChild(1L, 10L, "0000");

        mockMvc.perform(delete("/api/children/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isLocked());
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
