package com.A501.backend.domain.child.dto.response;

import com.A501.backend.domain.child.entity.Child;

// profileImageUrl이 있으면 그대로 이미지 주소로 쓰고(웹캠 업로드 = presigned GET),
// null이면 profileImageKey로 클라이언트 번들의 프리셋 이미지를 찾는다.
public record ChildResponse(Long childId, String name, String profileImageKey, String profileImageUrl) {

    public static ChildResponse from(Child child, String profileImageUrl) {
        return new ChildResponse(child.getChildId(), child.getName(), child.getProfileImageKey(), profileImageUrl);
    }
}
