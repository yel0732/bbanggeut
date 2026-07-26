"""Generate a short direct reply before returning to a diary question."""

import asyncio
import logging
from dataclasses import dataclass
from typing import Protocol

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.dialogue.question.fallback import QuestionGenerationExecutionPolicy
from app.providers.gms_chat import GmsChatMessage


CHILD_QUESTION_REPLY_SYSTEM_PROMPT = """\
너는 유아와 대화하는 친절한 AI 캐릭터다.
childQuestion에 짧고 자연스럽게 직접 답한다.
반드시 "나는"으로 시작하고, 질문하거나 일기 이야기로 돌아가려 하지 않는다.
AI에게 실제 경험, 가족, 몸 또는 현실의 위치가 있다고 꾸미지 않는다.
설명, 인사말, Markdown, 따옴표, 줄바꿈 없이 40자 이내의 평서문이나 감탄문 하나만 반환한다.
예시:
- "너는 어떤 영화 좋아해?" -> "나는 재미있는 모험 영화를 좋아!"
- "너는 어떤 공룡 좋아해?" -> "나는 티라노사우루스가 멋진 것 같아!"
"""


class ChildQuestionReplyRequest(BaseModel):
    """One private child question supplied to the reply generator."""

    model_config = ConfigDict(
        extra="forbid",
        hide_input_in_errors=True,
        populate_by_name=True,
        frozen=True,
    )

    child_question: str = Field(alias="childQuestion", repr=False)

    @field_validator("child_question")
    @classmethod
    def normalize_question(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("child question must contain text")
        return normalized


@dataclass(frozen=True)
class ChildQuestionReplyLogContext:
    """Non-sensitive identifiers used for bounded failure logging."""

    conversation_id: str
    request_id: str


class ChildQuestionReplyClient(Protocol):
    """Minimum completion capability used by the reply generator."""

    async def complete(
        self,
        messages: tuple[GmsChatMessage, ...],
    ) -> str:
        """Return one short direct reply."""


def parse_child_question_reply(raw_output: str) -> str:
    """Validate a short answer without retaining rejected model text."""

    normalized = raw_output.strip()
    if (
        not normalized
        or len(normalized) > 40
        or not normalized.startswith("나는")
        or "?" in normalized
        or "\n" in normalized
        or "\r" in normalized
        or normalized.startswith(('"', "'"))
        or normalized.endswith(('"', "'"))
    ):
        raise ValueError("child question reply rejected")
    return normalized


async def generate_child_question_reply_with_retry(
    request: ChildQuestionReplyRequest,
    client: ChildQuestionReplyClient,
    policy: QuestionGenerationExecutionPolicy,
    log_context: ChildQuestionReplyLogContext,
    logger: logging.Logger,
) -> str | None:
    """Generate a direct reply, returning None after bounded failures."""

    messages = (
        GmsChatMessage(
            role="developer",
            content=CHILD_QUESTION_REPLY_SYSTEM_PROMPT,
        ),
        GmsChatMessage(
            role="user",
            content=request.model_dump_json(by_alias=True),
        ),
    )
    for attempt in range(1, policy.max_attempts + 1):
        try:
            async with asyncio.timeout(policy.timeout_seconds):
                raw_output = await client.complete(messages)
            return parse_child_question_reply(raw_output)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.warning(
                "child question reply generation attempt failed",
                extra={
                    "conversation_id": log_context.conversation_id,
                    "request_id": log_context.request_id,
                    "attempt": attempt,
                    "max_attempts": policy.max_attempts,
                },
            )
    return None
