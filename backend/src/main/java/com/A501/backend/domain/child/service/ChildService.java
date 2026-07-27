package com.A501.backend.domain.child.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.service.PinService;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.exception.ChildErrorCode;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.child.security.ChildProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChildRepository childRepository;
    private final ChildProperties childProperties;
    private final PinService pinService;

    public boolean isNameAvailable(Long parentId, String name) {
        return !childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name);
    }

    public List<Child> listChildren(Long parentId) {
        return childRepository.findAllByParentIdAndDeletedAtIsNull(parentId);
    }

    @Transactional
    public Child createChild(Long parentId, String name, int profileImageNum) {
        if (!pinService.hasPin(parentId)) {
            throw new BusinessException(PinErrorCode.PIN_NOT_SET);
        }
        if (childRepository.countByParentIdAndDeletedAtIsNull(parentId) >= childProperties.maxProfileCount()) {
            throw new BusinessException(ChildErrorCode.CHILD_LIMIT_EXCEEDED);
        }
        if (childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name)) {
            throw new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS);
        }
        return childRepository.save(Child.create(parentId, name, profileImageNum));
    }

    @Transactional
    public Child updateChild(Long parentId, Long childId, String name, int profileImageNum) {
        Child child = findOwnedChild(parentId, childId);
        if (!child.getName().equals(name) && childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name)) {
            throw new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS);
        }
        child.rename(name);
        child.changeProfileImage(profileImageNum);
        return child;
    }

    // noRollbackFor 필요: 이 메서드가 먼저 트랜잭션을 열기 때문에 pinService.verifyPin(...)
    // 호출은 새 트랜잭션이 아니라 이 트랜잭션에 합류한다. 이 메서드에 noRollbackFor가 없으면
    // verifyPin이 늘려놓은 PIN 실패 카운트까지 이 메서드의 기본 롤백 규칙에 밀려 통째로
    // 롤백된다 — PinService 안에서 겪은 self-invocation 문제와 같은 종류의 버그다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void deleteChild(Long parentId, Long childId, String pin) {
        // 소유권 검증(404)을 PIN 검증보다 먼저 한다 — 어차피 404로 끝날 요청 때문에
        // PIN 실패 카운터를 소모하지 않기 위함이다. PIN 검증은 대상 자녀와 무관하게 호출자
        // 본인 계정만 보므로, 순서를 바꿔도 자녀 존재 여부가 새어나가지는 않는다.
        Child child = findOwnedChild(parentId, childId);
        pinService.verifyPin(parentId, pin);
        child.softDelete(LocalDateTime.now(KST));
    }

    // childId 경로 변수를 받는 모든 엔드포인트가 공유하는 소유권 검증. 존재하지 않거나 다른
    // 부모 소유면 둘 다 404로 통일한다 — 403을 쓰면 "리소스는 있는데 네 것이 아니다"가 드러나
    // 리소스 존재 자체가 노출된다.
    private Child findOwnedChild(Long parentId, Long childId) {
        Child child = childRepository.findByChildIdAndDeletedAtIsNull(childId)
                .orElseThrow(() -> new BusinessException(ChildErrorCode.CHILD_NOT_FOUND));
        if (!child.isOwnedBy(parentId)) {
            throw new BusinessException(ChildErrorCode.CHILD_NOT_FOUND);
        }
        return child;
    }
}
