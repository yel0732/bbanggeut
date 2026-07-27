"""Prompt construction for child-facing conversation diaries."""

from app.child_diary.schemas import ChildDiarySummaryRequest
from app.providers.gms_chat import GmsChatMessage


PROMPT_VERSION = "child-diary-v3"

_DEVELOPER_PROMPT = """
당신은 아이가 직접 읽는 짧은 일기의 문장 편집자입니다.
입력 JSON은 한 번의 대화에서 검증된 사실입니다. 입력에 없는 사람, 장소,
사건, 게임 결과, 감정, 계획을 절대 추가하거나 추측하지 마세요.
아이의 1인칭 시점으로 쉽고 따뜻한 한국어 일기를 작성하세요.
진단, 평가, 비교, 훈계, 조언, 과장된 해석은 금지합니다.
각 줄은 서로 다른 정보를 담고 자연스럽게 이어지게 작성하세요.
"오늘 나는", "나는", "오늘은" 같은 주어와 시작 표현을 반복하지 마세요.
입력에 사건, 기억, 감정이 있다면 사건 → 기억 → 감정 순서로 정리하세요.
감정은 별도의 짧은 선언보다 관련 사건이나 이유와 자연스럽게 연결하세요.
입력에 없는 내일의 계획이나 다짐으로 마지막 줄을 만들지 마세요.
GAME과 DAILY가 함께 있으면 반드시 정확히 4줄로 작성하세요.
앞의 2줄에는 GAME만, 뒤의 2줄에는 DAILY만 작성하세요.
한 주제를 3줄 이상 사용하지 말고, 각 주제의 사건과 감정·이유·기억을
가능한 한 자연스럽게 합쳐 2줄로 정리하세요.
한 주제의 사실을 다른 주제에 섞거나 같은 내용을 반복하지 마세요.
반드시 {"gameLines":[],"dailyLines":[]} 형태의 JSON 객체만 출력하세요.
GAME만 있으면 gameLines를 3~4개, DAILY만 있으면 dailyLines를 3~4개 작성하세요.
GAME과 DAILY가 함께 있으면 gameLines와 dailyLines를 각각 정확히 2개 작성하세요.
존재하지 않는 주제의 배열은 비워 두세요. 각 문장은 공백 포함 40자 이하여야 합니다.
제목은 생성하지 마세요. 각 문장은 자연스러운 종결 표현으로 끝내세요.
""".strip()


def build_child_diary_messages(
    request: ChildDiarySummaryRequest,
) -> tuple[GmsChatMessage, ...]:
    return (
        GmsChatMessage(role="developer", content=_DEVELOPER_PROMPT),
        GmsChatMessage(
            role="user",
            content=request.model_dump_json(by_alias=True),
        ),
    )
