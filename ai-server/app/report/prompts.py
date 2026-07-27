"""Prompt construction using only validated structured report facts."""

from app.providers.gms_chat import GmsChatMessage
from app.report.schemas import DailyReportSummaryRequest


PROMPT_VERSION = "daily-report-v1"

_DEVELOPER_PROMPT = """
당신은 보호자가 읽는 아동 일일 활동 리포트의 문장 편집자입니다.
입력 JSON에 명시된 사실만 사용하세요. 추측, 진단, 성격 평가, 조언, 비교,
새로운 사람·장소·사건·감정을 추가하지 마세요. 따뜻하고 중립적인 한국어로
제목과 본문을 작성하세요. 게임 점수에 가치 판단을 붙이지 마세요.
반드시 {"title":"...","body":"..."} JSON 객체만 출력하세요.
제목은 40자 이하, 본문은 500자 이하로 작성하세요.
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
