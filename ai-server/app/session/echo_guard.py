"""Barge-in 직후 첫 아이 턴이 중단된 AI 발화의 에코인지 판정한다.

브라우저 echoCancellation이 약해지는 환경(외장·블루투스 스피커, 높은 볼륨)에서는
AI 자신의 음성이 barge-in으로 확정되고, 응답이 중단되며, 그 오디오 조각이 '아이
턴'으로 전사된다. 그 전사가 대화에 들어가면 AI의 말이 아이의 대답이 되어 슬롯과
일기 원문이 오염된다.

판정은 텍스트만 사용한다 — 원본 오디오를 보관하지 않는다는 규칙을 지킨다.
에코 조각은 AI 발화의 **앞부분**이다: barge-in은 출력 시작 약 400ms 뒤에
확정되고, 확정 즉시 응답이 중단되어 소리가 사라지므로, 잡히는 것은 pre-roll을
포함한 문장 서두 조각이다. 그래서 중단된 문장의 prefix 창에 대해서만 in-order
겹침을 재고, 아이가 질문 꼬리 단어를 되받는 정상 대답("재미있었어")이 문장
전체와 겹치는 것은 에코로 치지 않는다.
"""

from __future__ import annotations

import re
from difflib import SequenceMatcher

# 이보다 짧은 전사는 판정 근거가 부족하다 — 진짜 발화로 취급하고 통과시킨다.
_MIN_CHILD_CHARS = 4
# 에코 전사가 문장 첫 음절을 놓치거나 조사를 다르게 듣는 오차 허용분.
_PREFIX_WINDOW_MARGIN_CHARS = 4
# 아이 전사의 이 비율 이상이 prefix 창 안에 순서대로 존재하면 에코로 판정한다.
_ECHO_OVERLAP_THRESHOLD = 0.85

_NON_WORD = re.compile(r"[^0-9A-Za-z가-힣]+")


def _normalize(text: str) -> str:
    return _NON_WORD.sub("", text).lower()


def is_probable_echo(child_text: str, interrupted_text: str) -> bool:
    """중단 직후 턴의 전사가 중단된 발화의 서두와 사실상 같은지 판정한다."""

    child = _normalize(child_text)
    source = _normalize(interrupted_text)
    if len(child) < _MIN_CHILD_CHARS or not source:
        return False
    window = source[: len(child) + _PREFIX_WINDOW_MARGIN_CHARS]
    matcher = SequenceMatcher(None, child, window, autojunk=False)
    matched = sum(block.size for block in matcher.get_matching_blocks())
    return matched / len(child) >= _ECHO_OVERLAP_THRESHOLD
