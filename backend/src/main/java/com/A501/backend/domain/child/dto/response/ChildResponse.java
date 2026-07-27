package com.A501.backend.domain.child.dto.response;

import com.A501.backend.domain.child.entity.Child;

public record ChildResponse(Long childId, String name, int profileImageNum) {

    public static ChildResponse from(Child child) {
        return new ChildResponse(child.getChildId(), child.getName(), child.getProfileImageNum());
    }
}
