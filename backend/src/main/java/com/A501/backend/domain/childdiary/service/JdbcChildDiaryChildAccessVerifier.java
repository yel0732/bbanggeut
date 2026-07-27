package com.A501.backend.domain.childdiary.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.exception.ChildDiaryErrorCode;
import com.A501.backend.domain.childdiary.port.ChildDiaryChildAccessVerifier;

import lombok.RequiredArgsConstructor;

@Component
@Profile("!local")
@RequiredArgsConstructor
public class JdbcChildDiaryChildAccessVerifier implements ChildDiaryChildAccessVerifier {
    private final JdbcTemplate jdbcTemplate;
    @Override
    public void verifyOwnership(Long parentId, Long childId) {
        List<Long> owners = jdbcTemplate.query(
                "select parent_id from child where child_id = ? and deleted_at is null",
                (rs, row) -> rs.getLong("parent_id"), childId);
        if (owners.size() != 1 || !parentId.equals(owners.getFirst())) {
            throw new BusinessException(ChildDiaryErrorCode.CHILD_NOT_FOUND);
        }
    }
}
