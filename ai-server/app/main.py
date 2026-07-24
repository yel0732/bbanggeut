"""FastAPI application factory and provider lifecycle assembly."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.dialogue import router as dialogue_router
from app.config import AppSettings
from app.dialogue.pipeline import DialogueTurnPipeline
from app.dialogue.question_fallback import (
    QuestionGenerationExecutionPolicy,
)
from app.dialogue.target_selector import QuestionTarget
from app.dialogue.termination_policy import DialogueTerminationPolicy
from app.providers.gms_chat import GmsChatClient


def create_app(
    settings: AppSettings | None = None,
    *,
    transport: httpx.AsyncBaseTransport | None = None,
) -> FastAPI:
    """Create an application without reading secrets at module import time."""

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        resolved_settings = (
            AppSettings.from_environment()
            if settings is None
            else settings
        )
        http_client = httpx.AsyncClient(
            base_url=resolved_settings.gms_base_url,
            timeout=None,
            transport=transport,
        )
        gms_client = GmsChatClient(
            http_client,
            api_key=resolved_settings.gms_key,
            model=resolved_settings.gms_model,
        )
        app.state.dialogue_pipeline = DialogueTurnPipeline(
            client=gms_client,
            execution_policy=QuestionGenerationExecutionPolicy(
                timeout_seconds=resolved_settings.gms_timeout_seconds,
                max_attempts=resolved_settings.gms_max_attempts,
            ),
            termination_policy=DialogueTerminationPolicy(
                required_targets=frozenset(QuestionTarget)
            ),
            logger=logging.getLogger("app.dialogue.pipeline"),
        )
        try:
            yield
        finally:
            await http_client.aclose()

    app = FastAPI(
        title="A501 AI Server",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.include_router(dialogue_router)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        request: Request,
        error: RequestValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content={"error": "DIALOGUE_REQUEST_INVALID"},
        )

    return app
