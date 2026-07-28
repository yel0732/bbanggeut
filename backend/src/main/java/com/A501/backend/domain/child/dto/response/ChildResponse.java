package com.A501.backend.domain.child.dto.response;

import com.A501.backend.domain.child.entity.Child;

public record ChildResponse(Long childId, String name, String profileImageUrl) {

    // 저장된 키가 아니라 해석된 URL을 담는다 — 업로드 사진의 키가 노출되면 추측·재사용 여지가
    // 생기므로 클라이언트에는 접근 가능한 URL만 내려준다. 해석은 ChildService가 수행해 넘긴다.
    public static ChildResponse from(Child child, String profileImageUrl) {
        return new ChildResponse(child.getChildId(), child.getName(), profileImageUrl);
    }
}
