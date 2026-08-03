import pytest
from pydantic import ValidationError

from app.child_diary.schemas import (
    DIARY_TITLE,
    ChildDiarySummaryRequest,
    ChildDiarySummaryResponse,
)


def test_accepts_one_conversation_scoped_diary_request() -> None:
    request = ChildDiarySummaryRequest.model_validate({
        "conversationId": "conversation-1",
        "diaryDate": "2026-07-27",
        "topics": [{
            "topicType": "DAILY",
            "slots": {
                "event": "친구와 공놀이를 했다",
                "people": ["친구"],
                "emotion": "즐거웠다",
            },
        }],
    })

    assert request.conversation_id == "conversation-1"
    assert request.topics[0].slots.people == ("친구",)


def test_rejects_request_without_validated_facts() -> None:
    with pytest.raises(ValidationError):
        ChildDiarySummaryRequest.model_validate({
            "conversationId": "conversation-1",
            "diaryDate": "2026-07-27",
            "topics": [{"topicType": "DAILY", "slots": {}}],
        })


def test_rejects_blank_conversation_id() -> None:
    with pytest.raises(ValidationError):
        ChildDiarySummaryRequest.model_validate({
            "conversationId": "   ",
            "diaryDate": "2026-07-27",
            "topics": [{
                "topicType": "DAILY",
                "slots": {"event": "그림을 그렸다"},
            }],
        })


def test_accepts_game_and_daily_topics_together() -> None:
    request = ChildDiarySummaryRequest.model_validate({
        "conversationId": "conversation-1",
        "diaryDate": "2026-07-27",
        "topics": [
            {
                "topicType": "GAME",
                "slots": {"event": "과일 자르기 게임을 했다"},
            },
            {
                "topicType": "DAILY",
                "slots": {"event": "친구와 놀이터에서 놀았다"},
            },
        ],
    })

    assert [topic.topic_type for topic in request.topics] == ["GAME", "DAILY"]


def test_rejects_duplicate_topic_types() -> None:
    with pytest.raises(ValidationError):
        ChildDiarySummaryRequest.model_validate({
            "conversationId": "conversation-1",
            "diaryDate": "2026-07-27",
            "topics": [
                {"topicType": "DAILY", "slots": {"event": "그림을 그렸다"}},
                {"topicType": "DAILY", "slots": {"emotion": "즐거웠다"}},
            ],
        })


def test_response_requires_fixed_title_and_three_to_four_short_lines() -> None:
    response = ChildDiarySummaryResponse.model_validate({
        "conversationId": "conversation-1",
        "diaryDate": "2026-07-27",
        "title": DIARY_TITLE,
        "lines": ["오늘은 그림을 그렸다.", "색칠해서 즐거웠다.", "그림이 기억에 남았다."],
        "modelVersion": "gpt-5.4-mini",
        "promptVersion": "child-diary-v1",
    })

    assert len(response.lines) == 3


@pytest.mark.parametrize(
    "title,lines",
    [
        ("다른 제목", ["첫째 줄이다.", "둘째 줄이다.", "셋째 줄이다."]),
        (DIARY_TITLE, ["첫째 줄이다.", "둘째 줄이다."]),
        (DIARY_TITLE, ["가" * 61, "둘째 줄이다.", "셋째 줄이다."]),
    ],
)
def test_rejects_invalid_ui_constraints(title: str, lines: list[str]) -> None:
    with pytest.raises(ValidationError):
        ChildDiarySummaryResponse.model_validate({
            "conversationId": "conversation-1",
            "diaryDate": "2026-07-27",
            "title": title,
            "lines": lines,
            "modelVersion": "gpt-5.4-mini",
            "promptVersion": "child-diary-v1",
        })
