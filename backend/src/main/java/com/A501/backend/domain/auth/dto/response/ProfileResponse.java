package com.A501.backend.domain.auth.dto.response;

import com.A501.backend.domain.auth.entity.Parent;

// 로그인 이후 부모 프로필 화면을 그리는 데 필요한 정보.
//
// pinSet을 포함하는 이유: 이게 없으면 클라이언트가 PIN 설정 여부를 알기 위해 PIN 설정 API를
// 실제로 호출해보고 403(PIN_ALREADY_SET)이 오는지로 추측해야 한다.
public record ProfileResponse(
        Long parentId,
        String email,
        String profileImageKey,
        String profileImageUrl,
        boolean pinSet
) {

    public static ProfileResponse from(Parent parent, String profileImageUrl) {
        return new ProfileResponse(
                parent.getParentId(),
                parent.getEmail(),
                parent.getProfileImageKey(),
                profileImageUrl,
                parent.hasPin());
    }
}
