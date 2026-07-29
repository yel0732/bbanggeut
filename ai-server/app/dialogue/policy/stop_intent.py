"""아이의 명시적 종료 의사 감지 (CHILD_REQUESTED_STOP 신호원).

전체 발화 근접 매칭만 사용한다 — 부분 문자열 매칭은 "동생이 그만하라고 했어"처럼
종료 문구가 이야기 안에 포함된 발화를 오탐해 대화를 끊어버리므로 금지한다.
오탐(멀쩡한 대화 중단)의 비용이 미탐(최대 질문 수까지 계속 질문)보다 크다는
비대칭에 따라 정밀도를 우선한다. LLM은 개입하지 않는다.
"""

import re

# 발화 전체가 아래 문구 중 하나로 정규화될 때만 종료 의사로 판정한다.
# "쉬고 싶어", "졸려" 같은 피로 표현은 종료 단정이 과해 제외한다.
_STOP_PHRASES: frozenset[str] = frozenset(
    {
        "그만",
        "그만해",
        "그만할래",
        "그만할래요",
        "그만할게",
        "그만할게요",
        "그만하자",
        "그만하고싶어",
        "그만하고싶어요",
        "그만말할래",
        "그만얘기할래",
        "그만이야기할래",
        "이제그만",
        "이제그만할래",
        "이제그만할래요",
        "안할래",
        "안할래요",
        "이제안할래",
        "이제안할래요",
        "끝",
        "끝낼래",
        "끝낼래요",
        "끝내자",
    }
)

# 흔한 말머리 감탄사 — 한 개까지 제거하고 재판정한다.
_LEADING_FILLERS: tuple[str, ...] = ("음", "어", "아", "흠")

_STRIP_PATTERN = re.compile(r"[\s.,!?~…\"'‥·]+")


def is_stop_request(child_answer: str) -> bool:
    """발화 전체가 종료 문구일 때만 True를 반환한다."""

    normalized = _STRIP_PATTERN.sub("", child_answer)
    if not normalized:
        return False
    if normalized in _STOP_PHRASES:
        return True
    for filler in _LEADING_FILLERS:
        if normalized.startswith(filler) and normalized[len(filler):] in _STOP_PHRASES:
            return True
    return False
