import asyncio

import pytest

from app.child_diary.generator import (
    ChildDiaryGenerationError,
    ChildDiaryGenerator,
)
from app.child_diary.schemas import ChildDiarySummaryRequest


class FakeProvider:
    model = "gpt-5-mini"

    def __init__(self, responses: list[str]) -> None:
        self.responses = responses
        self.calls = 0

    async def complete(self, messages: tuple) -> str:
        response = self.responses[self.calls]
        self.calls += 1
        return response


class BlockingProvider:
    model = "gpt-5-mini"

    def __init__(self) -> None:
        self.calls = 0
        self.never_finishes = asyncio.Event()

    async def complete(self, messages: tuple) -> str:
        self.calls += 1
        await self.never_finishes.wait()
        raise AssertionError("unreachable")


def _request() -> ChildDiarySummaryRequest:
    return ChildDiarySummaryRequest.model_validate({
        "conversationId": "conversation-1",
        "diaryDate": "2026-07-27",
        "topics": [{
            "topicType": "DAILY",
            "slots": {
                "event": "블록으로 탑을 만들었다",
                "emotion": "뿌듯했다",
                "memorableMoment": "탑을 완성한 순간",
            },
        }],
    })


@pytest.mark.asyncio
async def test_generates_fixed_title_and_validated_lines() -> None:
    generator = ChildDiaryGenerator(
        FakeProvider([
            '{"gameLines":[],"dailyLines":['
            '"오늘은 블록으로 탑을 만들었다.",'
            '"탑을 완성해서 뿌듯했다.",'
            '"완성한 순간이 가장 기억에 남았다."]}'
        ]),
        timeout_seconds=1.0,
        max_attempts=2,
    )

    result = await generator.generate(_request())

    assert result.title == "오늘의 일기"
    assert len(result.lines) == 3
    assert result.model_version == "gpt-5-mini"
    assert result.prompt_version == "child-diary-v7"


@pytest.mark.asyncio
async def test_rejects_malformed_or_overlong_model_output_without_retry() -> None:
    provider = FakeProvider([
        '{"gameLines":[],"dailyLines":["가나다", "라마바", "'
        + "가" * 61 + '"]}'
    ])
    generator = ChildDiaryGenerator(provider, 1.0, 2)

    with pytest.raises(ChildDiaryGenerationError):
        await generator.generate(_request())

    assert provider.calls == 1


@pytest.mark.asyncio
async def test_retries_bounded_number_of_times_on_timeout() -> None:
    provider = BlockingProvider()
    generator = ChildDiaryGenerator(provider, 0.001, 2)

    with pytest.raises(ChildDiaryGenerationError):
        await generator.generate(_request())

    assert provider.calls == 2


@pytest.mark.asyncio
async def test_rejects_combined_topic_without_a_line_per_topic() -> None:
    request = ChildDiarySummaryRequest.model_validate({
        "conversationId": "conversation-1",
        "diaryDate": "2026-07-27",
        "topics": [
            {"topicType": "GAME", "slots": {"event": "게임을 했다"}},
            {"topicType": "DAILY", "slots": {"event": "친구와 놀았다"}},
        ],
    })
    # 두 토픽인데 DAILY 줄이 하나도 없으면(토픽당 최소 1줄 위반) 거부된다.
    provider = FakeProvider([
        '{"gameLines":["게임 첫째 줄.","게임 둘째 줄.","게임 셋째 줄."],'
        '"dailyLines":[]}'
    ])

    with pytest.raises(ChildDiaryGenerationError):
        await ChildDiaryGenerator(provider, 1.0, 2).generate(request)

    assert provider.calls == 1
