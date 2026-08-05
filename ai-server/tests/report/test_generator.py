import pytest

from app.report.generator import DailyReportGenerator, ReportGenerationError
from app.report.schemas import DailyReportSummaryRequest


class FakeProvider:
    model = "gpt-5-mini"

    def __init__(self, responses: list[str]) -> None:
        self.responses = responses
        self.calls = 0

    async def complete(self, messages: tuple) -> str:
        response = self.responses[self.calls]
        self.calls += 1
        return response


@pytest.mark.asyncio
async def test_generates_validated_daily_report() -> None:
    provider = FakeProvider([
        '{"title":"즐거운 하루","dailyStory":"친구와 축구한 이야기를 나누었어요.",'
        '"emotionDetails":[],"interests":[],"notableQuotes":[],'
        '"conversationGuide":{"description":"","questions":[]}}'
    ])
    generator = DailyReportGenerator(provider, 1.0, 2)
    request = DailyReportSummaryRequest.model_validate({
        "reportDate": "2026-07-27",
        "events": ["친구와 축구함"],
        "emotions": ["즐거움"],
        "interests": [],
        "games": [],
    })

    result = await generator.generate(request)

    assert result.title == "즐거운 하루"
    assert result.model_version == "gpt-5-mini"
    assert result.daily_story == "친구와 축구한 이야기를 나누었어요."
    assert result.prompt_version == "daily-report-v3"


def _story(daily_story: str) -> str:
    return (
        '{"title":"오늘","dailyStory":"' + daily_story + '",'
        '"emotionDetails":[],"interests":[],"notableQuotes":[],'
        '"conversationGuide":{"description":"","questions":[]}}'
    )


@pytest.mark.asyncio
async def test_retries_until_the_story_keeps_the_caregiver_voice() -> None:
    """문체 위반은 다시 시키면 고쳐진다. 폴백으로 넘기면 오히려 어긋난다."""

    provider = FakeProvider([
        _story("아이는 놀이터에서 그네를 탔다."),
        _story("아이는 놀이터에서 그네를 탔어요."),
    ])
    generator = DailyReportGenerator(provider, 1.0, 2)
    request = DailyReportSummaryRequest.model_validate({
        "reportDate": "2026-07-27",
        "events": ["놀이터에서 그네 탔다"],
    })

    result = await generator.generate(request)

    assert provider.calls == 2
    assert result.daily_story == "아이는 놀이터에서 그네를 탔어요."


@pytest.mark.asyncio
async def test_rejects_a_story_that_copies_an_assistant_turn() -> None:
    """AI 발화는 맥락 참고용이다. 서술로 옮기면 아이가 하지 않은 말이 실린다."""

    copied = "그네 이야기 해줬구나! 그때 마음은 어땠어?"
    provider = FakeProvider([_story(copied), _story(copied)])
    generator = DailyReportGenerator(provider, 1.0, 2)
    request = DailyReportSummaryRequest.model_validate({
        "reportDate": "2026-07-27",
        "events": ["놀이터에서 그네 탔다"],
        "conversations": [
            {"speaker": "AI", "content": copied},
            {"speaker": "CHILD", "content": "엄청 즐거웠어"},
        ],
    })

    with pytest.raises(ReportGenerationError):
        await generator.generate(request)


@pytest.mark.asyncio
async def test_rejects_non_json_provider_response() -> None:
    generator = DailyReportGenerator(FakeProvider(["not-json"]), 1.0, 2)
    request = DailyReportSummaryRequest.model_validate({
        "reportDate": "2026-07-27",
        "events": ["그림을 그림"],
    })

    with pytest.raises(ReportGenerationError):
        await generator.generate(request)
