package com.A501.backend.domain.auth.dto.response;

// 응답에는 저장된 키 대신 해석된 URL만 담는다. 업로드 사진의 키가 노출되면 다른 사용자가 키를
// 추측·재사용할 여지가 생기므로, 클라이언트에는 접근 가능한 URL만 내려준다.
public record ProfileImageResponse(String profileImageUrl) {

    public static ProfileImageResponse of(String profileImageUrl) {
        return new ProfileImageResponse(profileImageUrl);
    }
}
