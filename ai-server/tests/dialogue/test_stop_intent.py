"""아이 종료 의사 감지 규칙 테스트 — 전체 발화 근접 매칭, 정밀도 우선."""

import pytest

from app.dialogue.policy.stop_intent import is_stop_request


@pytest.mark.parametrize(
    "utterance",
    [
        "그만",
        "그만할래",
        "그만할래요",
        "그만하고 싶어",
        "이제 그만",
        "이제 그만할래!",
        "안 할래",
        "안 할래요.",
        "끝",
        "끝낼래~",
        "그만 얘기할래",
        "음 그만할래",
        "어… 이제 그만",
        "대화 그만할래",
        "오늘은 여기까지",
        "질문하지 마",
        "그만 물어봐",
        "말 걸지 마",
        "나 이제 대화 안 할래",
        "이야기하기 싫어",
        "꺼져",
    ],
)
def test_detects_whole_utterance_stop_phrases(utterance: str) -> None:
    assert is_stop_request(utterance) is True


@pytest.mark.parametrize(
    "utterance",
    [
        # 이야기 속에 종료 문구가 포함된 발화 — 절대 오탐하면 안 된다.
        "동생이 그만하라고 해서 속상했어",
        "선생님이 이제 그만 놀자고 했어",
        "그만 웃겨서 배가 아팠어",
        "게임을 끝까지 다 했어",
        "숙제 끝나고 놀았어",
        "게임 이야기에서 친구가 꺼져라고 했어",
        "게임 질문에서 정답을 맞혔어",
        # 피로 표현은 종료 단정이 과하다 — 1차 범위에서 제외.
        "쉬고 싶어",
        "졸려",
        # 일반 답변.
        "블록 놀이 했어",
        "",
        "   ",
    ],
)
def test_does_not_flag_regular_answers(utterance: str) -> None:
    assert is_stop_request(utterance) is False
