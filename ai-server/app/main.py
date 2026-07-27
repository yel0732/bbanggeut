"""FastAPI application factory and provider lifecycle assembly."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.dialogue import router as dialogue_router
from app.api.child_diary import router as child_diary_router
from app.api.report import router as report_router
from app.config import AppSettings
from app.child_diary.generator import ChildDiaryGenerator
from app.dialogue.pipeline import DialogueTurnPipeline
from app.dialogue.question.fallback import (
    QuestionGenerationExecutionPolicy,
)
from app.dialogue.policy.target import QuestionTarget
from app.dialogue.policy.termination import DialogueTerminationPolicy
from app.providers.gms_chat import GmsChatClient
from app.realtime.jwks_cache import RealtimeJwksCache
from app.realtime.redis_client import create_redis_client
from app.realtime.session_epoch_registry import SessionEpochRegistry
from app.realtime.session_lease_heartbeat import SessionLeaseHeartbeat
from app.realtime.ticket_verification import (
    RealtimeTicketVerifier,
    RedisSessionLeaseReader,
    RedisTicketConsumptionStore,
)
from app.report.generator import DailyReportGenerator
from app.session.realtime_connection_registry import RealtimeConnectionRegistry
from app.signaling.error_mapping import map_ticket_failure_code
from app.signaling.router import router as signaling_router
from app.webrtc.peer_connection import AiortcPeerConnectionFactory


def create_app(
    settings: AppSettings | None = None,
    *,
    transport: httpx.AsyncBaseTransport | None = None,
    realtime_http_transport: httpx.AsyncBaseTransport | None = None,
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
        analysis_client = GmsChatClient(
            http_client,
            api_key=resolved_settings.gms_key,
            model=resolved_settings.gms_analysis_model,
        )
        response_client = GmsChatClient(
            http_client,
            api_key=resolved_settings.gms_key,
            model=resolved_settings.gms_response_model,
        )
        report_client = GmsChatClient(
            http_client,
            api_key=resolved_settings.gms_key,
            model=resolved_settings.gms_report_model,
        )
        child_diary_client = GmsChatClient(
            http_client,
            api_key=resolved_settings.gms_key,
            model=resolved_settings.gms_child_diary_model,
        )
        app.state.dialogue_pipeline = DialogueTurnPipeline(
            analysis_client=analysis_client,
            response_client=response_client,
            execution_policy=QuestionGenerationExecutionPolicy(
                timeout_seconds=resolved_settings.gms_timeout_seconds,
                max_attempts=resolved_settings.gms_max_attempts,
            ),
            termination_policy=DialogueTerminationPolicy(
                required_targets=frozenset(QuestionTarget)
            ),
            logger=logging.getLogger("app.dialogue.pipeline"),
        )
        app.state.report_generator = DailyReportGenerator(
            provider=report_client,
            timeout_seconds=resolved_settings.gms_timeout_seconds,
            max_attempts=resolved_settings.gms_max_attempts,
        )
        app.state.child_diary_generator = ChildDiaryGenerator(
            provider=child_diary_client,
            timeout_seconds=resolved_settings.gms_timeout_seconds,
            max_attempts=resolved_settings.gms_max_attempts,
        )

        redis_client = create_redis_client(
            resolved_settings.realtime_redis_host, resolved_settings.realtime_redis_port
        )
        # 별도 httpx.AsyncClient: GMS용 http_client는 gms_base_url·GMS 전용 transport를
        # 갖고 있어 절대 URL 요청(JWKS)에는 base_url이 영향을 주지 않지만, transport
        # override까지 공유하면 테스트에서 GMS Mock이 JWKS 요청까지 가로채게 된다.
        realtime_http_client = httpx.AsyncClient(timeout=None, transport=realtime_http_transport)
        jwks_cache = RealtimeJwksCache(realtime_http_client, resolved_settings.realtime_jwks_url)
        # 같은 인스턴스를 ticket_verifier(쓰기: 소비 성공 시 register())와 signaling
        # 연결(읽기: DataChannel Envelope의 sessionEpoch 검사)이 함께 공유해야 한다.
        session_epoch_registry = SessionEpochRegistry()
        app.state.session_epoch_registry = session_epoch_registry
        app.state.ticket_verifier = RealtimeTicketVerifier(
            jwks_cache,
            RedisTicketConsumptionStore(redis_client),
            RedisSessionLeaseReader(redis_client),
            session_epoch_registry,
            resolved_settings.realtime_ticket_issuer,
            resolved_settings.realtime_ticket_audience,
            logging.getLogger("app.realtime.ticket_verification"),
        )
        app.state.peer_connection_factory = AiortcPeerConnectionFactory()
        app.state.realtime_connection_registry = RealtimeConnectionRegistry()
        app.state.realtime_heartbeat_factory = lambda: SessionLeaseHeartbeat(
            redis_client,
            resolved_settings.realtime_heartbeat_interval_seconds,
            resolved_settings.realtime_lease_ttl_seconds,
            logging.getLogger("app.realtime.session_lease_heartbeat"),
        )
        app.state.map_ticket_failure_code = map_ticket_failure_code

        try:
            yield
        finally:
            await redis_client.aclose()
            await realtime_http_client.aclose()
            await http_client.aclose()

    app = FastAPI(
        title="A501 AI Server",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.include_router(dialogue_router)
    app.include_router(child_diary_router)
    app.include_router(report_router)
    app.include_router(signaling_router)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        request: Request,
        error: RequestValidationError,
    ) -> JSONResponse:
        error_code = "DIALOGUE_REQUEST_INVALID"
        if request.url.path.startswith("/internal/v1/child-diaries"):
            error_code = "CHILD_DIARY_REQUEST_INVALID"
        elif request.url.path.startswith("/internal/v1/reports"):
            error_code = "REPORT_REQUEST_INVALID"
        return JSONResponse(
            status_code=422,
            content={"error": error_code},
        )

    return app
