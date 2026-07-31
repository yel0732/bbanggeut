"""Prompt construction using only validated structured report facts."""

from app.providers.gms_chat import GmsChatMessage
from app.report.schemas import DailyReportSummaryRequest


PROMPT_VERSION = "daily-report-v2"

_DEVELOPER_PROMPT = """
당신은 보호자가 읽는 아동 일일 활동 리포트 작성자입니다.
입력 JSON의 대화와 구조화된 사실만 사용하세요. 추측, 진단, 성격·능력 평가,
비교, 새로운 사람·장소·사건·감정을 추가하지 마세요.
아이의 대화 맥락과 감정의 이유가 드러나도록 따뜻하고 구체적인 한국어로 작성하세요.
notableQuotes는 speaker가 CHILD인 content와 완전히 동일한 문장만 사용하세요.
질문은 아이가 편하게 답할 수 있는 열린 질문으로 최대 3개 작성하세요.
게임 점수에 가치 판단을 붙이지 마세요.
반드시 다음 JSON 객체만 출력하세요.
{"title":"...","dailyStory":"...","emotionDetails":[{"emotion":"...","reason":"..."}],
"interests":["..."],"notableQuotes":["..."],
"conversationGuide":{"description":"...","questions":["..."]}}
제목은 40자 이하, dailyStory는 1200자 이하로 작성하세요.
""".strip()


def build_report_messages(
    request: DailyReportSummaryRequest,
) -> tuple[GmsChatMessage, ...]:
    if not request.has_activity():
        raise ValueError("report generation requires at least one activity")
    return (
        GmsChatMessage(role="developer", content=_DEVELOPER_PROMPT),
        GmsChatMessage(
            role="user",
            content=request.model_dump_json(by_alias=True),
        ),
    )
