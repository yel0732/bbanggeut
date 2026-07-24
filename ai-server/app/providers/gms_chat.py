"""Minimal OpenAI-compatible Chat Completions client for SSAFY GMS."""

from dataclasses import dataclass, field
from typing import Literal

import httpx
from pydantic import BaseModel, ConfigDict, ValidationError

from app.dialogue.question_provider import (
    QuestionProviderError,
    QuestionProviderErrorCode,
)


@dataclass(frozen=True, repr=False)
class GmsChatMessage:
    """One provider message whose content must not appear in repr."""

    role: Literal["developer", "user"]
    content: str

    def __post_init__(self) -> None:
        if self.role not in {"developer", "user"}:
            raise ValueError("unsupported GMS message role")
        if not isinstance(self.content, str) or not self.content.strip():
            raise ValueError("GMS message content must contain text")


class _GmsMessageResponse(BaseModel):
    model_config = ConfigDict(extra="ignore", hide_input_in_errors=True)

    content: str | None


class _GmsChoiceResponse(BaseModel):
    model_config = ConfigDict(extra="ignore", hide_input_in_errors=True)

    message: _GmsMessageResponse


class _GmsChatResponse(BaseModel):
    model_config = ConfigDict(extra="ignore", hide_input_in_errors=True)

    choices: tuple[_GmsChoiceResponse, ...]


@dataclass(repr=False)
class GmsChatClient:
    """Reusable async GMS client that returns only validated text."""

    http_client: httpx.AsyncClient
    api_key: str = field(repr=False)
    model: str

    def __init__(
        self,
        http_client: httpx.AsyncClient,
        *,
        api_key: str,
        model: str,
    ) -> None:
        if not isinstance(api_key, str) or not api_key.strip():
            raise ValueError("api_key must contain text")
        if not isinstance(model, str) or not model.strip():
            raise ValueError("model must contain text")
        self.http_client = http_client
        self.api_key = api_key
        self.model = model

    async def complete(
        self,
        messages: tuple[GmsChatMessage, ...],
    ) -> str:
        """Call GMS once; timeout and retry remain orchestration concerns."""

        if not messages:
            raise ValueError("messages must not be empty")
        try:
            response = await self.http_client.post(
                "/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.model,
                    "messages": [
                        {"role": message.role, "content": message.content}
                        for message in messages
                    ],
                },
            )
        except (httpx.TimeoutException, httpx.NetworkError):
            raise QuestionProviderError(
                QuestionProviderErrorCode.TEMPORARY_UNAVAILABLE,
                retryable=True,
            ) from None

        if response.status_code == 429:
            raise QuestionProviderError(
                QuestionProviderErrorCode.RATE_LIMITED,
                retryable=True,
            )
        if 500 <= response.status_code:
            raise QuestionProviderError(
                QuestionProviderErrorCode.TEMPORARY_UNAVAILABLE,
                retryable=True,
            )
        if response.status_code in {401, 403}:
            raise QuestionProviderError(
                QuestionProviderErrorCode.AUTHENTICATION_FAILED,
                retryable=False,
            )
        if response.status_code >= 400:
            raise QuestionProviderError(
                QuestionProviderErrorCode.INVALID_REQUEST,
                retryable=False,
            )

        try:
            parsed = _GmsChatResponse.model_validate(response.json())
        except (ValueError, ValidationError):
            raise _invalid_response_error() from None
        if not parsed.choices:
            raise _invalid_response_error()
        content = parsed.choices[0].message.content
        if content is None or not content.strip():
            raise _invalid_response_error()
        return content.strip()


def _invalid_response_error() -> QuestionProviderError:
    return QuestionProviderError(
        QuestionProviderErrorCode.UNKNOWN_PROVIDER_ERROR,
        retryable=False,
    )
