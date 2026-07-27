import pytest

from app.report.generator import DailyReportGenerator, ReportGenerationError
from app.report.schemas import DailyReportSummaryRequest


class FakeProvider:
    model = "gpt-5.4-mini"

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
        '{"title":"즐거운 하루","body":"친구와 축구한 이야기를 나누었어요."}'
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
    assert result.model_version == "gpt-5.4-mini"
    assert result.prompt_version == "daily-report-v1"


@pytest.mark.asyncio
async def test_rejects_non_json_provider_response() -> None:
    generator = DailyReportGenerator(FakeProvider(["not-json"]), 1.0, 2)
    request = DailyReportSummaryRequest.model_validate({
        "reportDate": "2026-07-27",
        "events": ["그림을 그림"],
    })

    with pytest.raises(ReportGenerationError):
        await generator.generate(request)
