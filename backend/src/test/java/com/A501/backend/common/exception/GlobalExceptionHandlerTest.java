package com.A501.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.A501.backend.common.storage.ImageErrorCode;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // 서블릿 한도를 넘긴 업로드는 컨트롤러에 닿지 않아 앱 레벨 검증을 타지 못한다. 매핑이 없으면
    // 500이 나가므로, 앱 검증과 동일한 400 IMAGE_TOO_LARGE로 응답하는지 고정한다.
    @Test
    void handlesServletMultipartLimitAsImageTooLarge() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(10L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE.getCode());
    }
}
