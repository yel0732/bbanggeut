"""FastAPI application factory and provider lifecycle assembly."""

import asyncio
import contextlib
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
from app.dialogue.pipeline import DialogueTurnPipeline, fixed_assistant_messages
from app.dialogue.question.fallback import (
    QuestionGenerationExecutionPolicy,
)
from app.dialogue.policy.target import QuestionTarget
from app.dialogue.policy.termination import DialogueTerminationPolicy
from app.providers.gms_chat import GmsChatClient
from app.realtime.context_snapshot import (
    RedisContextSnapshotReader,
    default_context_snapshot,
)
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
from app.session.realtime_dialogue import create_realtime_dialogue_session
from app.signaling.error_mapping import map_ticket_failure_code
from app.signaling.router import router as signaling_router
from app.speech.hallucination import HallucinationGuard
from app.speech.stt_pipeline import SessionSttPipeline
from app.events.stream import ConversationStreamProducer
from app.speech.synthesis import SpeechSynthesisService
from app.speech.tts_cache import CachedSpeechSynthesizer
from app.speech.tts_client import GmsSpeechSynthesizer
from app.speech.turn_detector import TurnDetectionConfig
from app.speech.vad import WebrtcVoiceActivityDetector
from app.speech.whisper_client import WhisperSpeechRecognizer
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
        app.state.peer_connection_factory = AiortcPeerConnectionFactory(
            resolved_settings.realtime_stun_urls
        )
        app.state.realtime_connection_registry = RealtimeConnectionRegistry()
        app.state.realtime_heartbeat_factory = lambda: SessionLeaseHeartbeat(
            redis_client,
            resolved_settings.realtime_heartbeat_interval_seconds,
            resolved_settings.realtime_lease_ttl_seconds,
            logging.getLogger("app.realtime.session_lease_heartbeat"),
        )
        app.state.map_ticket_failure_code = map_ticket_failure_code

        # STT: one recognizer shared (stateless HTTP), one pipeline per
        # connection (owns that session's converter/buffer/turn state, §8).
        speech_recognizer = WhisperSpeechRecognizer(
            http_client,
            api_key=resolved_settings.gms_key,
            model=resolved_settings.stt_model,
            language=resolved_settings.stt_language,
            timeout_seconds=resolved_settings.stt_timeout_seconds,
        )
        hallucination_guard = HallucinationGuard(
            min_audio_seconds=resolved_settings.stt_min_utterance_seconds,
            no_speech_prob_threshold=resolved_settings.stt_no_speech_prob_threshold,
        )
        turn_detection_config = TurnDetectionConfig(
            min_speech_ms=resolved_settings.stt_min_speech_ms,
            trailing_silence_seconds=resolved_settings.stt_trailing_silence_seconds,
            no_speech_timeout_seconds=resolved_settings.stt_no_speech_timeout_seconds,
            preroll_ms=resolved_settings.stt_preroll_ms,
        )

        def stt_pipeline_factory(
            publisher,
            *,
            turn_sequencer,
            on_final,
            on_no_speech,
            is_output_active=None,
        ):
            return SessionSttPipeline(
                speech_recognizer,
                publisher,
                logging.getLogger("app.speech.stt_pipeline"),
                guard=hallucination_guard,
                max_utterance_seconds=resolved_settings.stt_max_utterance_seconds,
                # VAD 자체는 세션별 인스턴스(webrtcvad가 내부 상태를 가짐, §8).
                vad=WebrtcVoiceActivityDetector(
                    resolved_settings.stt_vad_aggressiveness,
                    min_frame_rms=resolved_settings.stt_vad_min_frame_rms,
                ),
                turn_detection=turn_detection_config,
                turn_sequencer=turn_sequencer,
                on_final=on_final,
                on_no_speech=on_no_speech,
                is_output_active=is_output_active,
            )

        context_snapshot_reader = RedisContextSnapshotReader(
            redis_client, logging.getLogger("app.realtime.context_snapshot")
        )
        # TTS synthesizer는 상태 비저장(HTTP)이라 연결 간 공유. 합성 서비스는
        # speaker·publisher·registry가 연결별이라 세션마다 새로 만든다(§8).
        speech_synthesizer = CachedSpeechSynthesizer(
            GmsSpeechSynthesizer(
                http_client,
                api_key=resolved_settings.gms_key,
                model=resolved_settings.tts_model,
                voice=resolved_settings.tts_voice,
                first_byte_timeout_seconds=resolved_settings.tts_timeout_seconds,
            ),
            logging.getLogger("app.speech.tts_cache"),
        )
        # 매번 같은 문장(무응답 복구·되묻기·주제 전환)은 한 번만 합성한다 —
        # TTFB 실측 1.89초를 그 경로에서 없앤다. 기동을 막지 않도록 배경에서
        # 채운다(11회 합성 = 20초 이상). 아직 안 채워진 문장은 평소처럼 합성되고
        # 그 결과가 캐시에 남으므로, 이 태스크가 실패해도 기능은 온전하다.
        speech_synthesizer.register(fixed_assistant_messages())
        prewarm_task = asyncio.create_task(speech_synthesizer.prewarm())

        def synthesis_service_factory(speaker, publisher, response_registry, on_completed):
            return SpeechSynthesisService(
                speech_synthesizer,
                speaker,
                publisher,
                response_registry.is_cancelled,
                logging.getLogger("app.speech.synthesis"),
                on_completed=on_completed,
            )

        def stream_producer_factory(publisher):
            return ConversationStreamProducer(
                redis_client,
                conversation_id=publisher.conversation_id,
                realtime_session_id=publisher.realtime_session_id,
                session_epoch=publisher.session_epoch,
                logger=logging.getLogger("app.events.stream"),
                max_attempts=resolved_settings.stream_publish_max_attempts,
                retry_backoff_seconds=resolved_settings.stream_publish_retry_seconds,
                outbox_size=resolved_settings.stream_outbox_size,
            )

        async def realtime_session_factory(publisher, conversation_id, speaker):
            return await create_realtime_dialogue_session(
                publisher=publisher,
                conversation_id=conversation_id,
                read_snapshot=context_snapshot_reader.read,
                default_snapshot=default_context_snapshot,
                dialogue_pipeline=app.state.dialogue_pipeline,
                termination_policy=DialogueTerminationPolicy(
                    required_targets=frozenset(QuestionTarget)
                ),
                is_epoch_current=lambda: session_epoch_registry.is_current(
                    conversation_id, publisher.session_epoch
                ),
                stt_pipeline_factory=stt_pipeline_factory,
                logger=logging.getLogger("app.session.realtime_dialogue"),
                speaker=speaker,
                synthesis_service_factory=synthesis_service_factory,
                stream_producer_factory=stream_producer_factory,
                low_confidence_threshold=(
                    resolved_settings.stt_low_confidence_threshold
                ),
                low_confidence_reask_limit=(
                    resolved_settings.stt_low_confidence_reask_limit
                ),
            )

        app.state.realtime_session_factory = realtime_session_factory

        try:
            yield
        finally:
            # 사전 합성은 HTTP client보다 먼저 정리한다 — 닫힌 client로 요청하면
            # 종료 경로에서 예외가 난다.
            prewarm_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await prewarm_task
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
