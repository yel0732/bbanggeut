"""Environment-backed application settings assembled at startup."""

import math
import os
from collections.abc import Mapping
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

from dotenv import load_dotenv


@dataclass(frozen=True)
class AppSettings:
    """Validated settings without exposing the GMS credential."""

    gms_key: str = field(repr=False)
    gms_base_url: str = (
        "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
    )
    gms_analysis_model: str = "gpt-5.4-nano"
    gms_response_model: str = "gpt-5-mini"
    gms_report_model: str = "gpt-5-mini"
    gms_child_diary_model: str = "gpt-5-mini"
    gms_child_diary_timeout_seconds: float = 90.0
    gms_timeout_seconds: float = 10.0
    gms_max_attempts: int = 2
    realtime_redis_host: str = "127.0.0.1"
    realtime_redis_port: int = 6379
    realtime_jwks_url: str = (
        "http://127.0.0.1:8080/internal/v1/realtime/jwks"
    )
    realtime_ticket_issuer: str = "spring-backend"
    realtime_ticket_audience: str = "ai-realtime"
    realtime_heartbeat_interval_seconds: float = 5.0
    realtime_lease_ttl_seconds: float = 15.0
    # 빈 tuple이면 PeerConnection이 STUN 조회 없이 host 후보만 사용한다(로컬 MVP 기본).
    # NAT 통과가 필요한 배포에서만 REALTIME_STUN_URLS로 주입한다(INFRA Epic).
    realtime_stun_urls: tuple[str, ...] = ()
    # STT(DEC-04: whisper-1 배치). 기본값 근거는 jira/plan/DIARY-3-W01.md §1 실측.
    stt_model: str = "whisper-1"
    stt_language: str = "ko"
    stt_timeout_seconds: float = 10.0
    stt_max_utterance_seconds: float = 30.0  # whisper 처리 창과 동일, 초과 시 턴 강제 종료
    stt_min_utterance_seconds: float = 0.5  # 미만이면 호출하지 않음(0.4초 → "you" 환각)
    stt_no_speech_prob_threshold: float = 0.6  # 실제 발화 0.00 vs 환각 0.82~0.94
    # 신뢰도 되묻기(FUNCTIONAL_SPEC §22 STT_LOW_CONFIDENCE).
    # 1차 보정은 합성 음성 20건 기준이었다(정확 17건 0.642~0.786, 오인식 3건
    # 0.627~0.668 → 0.67). 그러나 실제 마이크 음성은 분포가 통째로 낮아,
    # 하네스 실측에서 **정확히 인식된 발화가 0.52~0.67**로 나와 정상 대화가
    # 매 턴 되묻기에 걸렸다(재발화해도 같은 문장이 0.67로 통과 — 되묻기가 정확도를
    # 높이지 못했다). 실제 음성은 이렇게 낮은 신뢰도에서도 전사가 정확하므로
    # 관측 하한(0.52)보다 더 아래로 내려 헛된 되묻기를 없앤다.
    # 환각(무음·톤)은 이 게이트가 아니라 HallucinationGuard가 no_speech_prob
    # 0.82~0.94로 이미 차단하므로, 여기서는 심하게 깨진 전사만 남기면 된다.
    stt_low_confidence_threshold: float = 0.35  # 미만이면 확인 질문(0이면 비활성)
    stt_low_confidence_reask_limit: int = 1  # 연속 되묻기 상한(도달 시 낮아도 진행)
    # Turn Detection(W02). 기본값 근거는 jira/plan/DIARY-3-W02.md §5.
    stt_vad_aggressiveness: int = 2  # 3은 저음량 아동 발화를 놓칠 위험(§11-D)
    # webrtcvad 앞단 에너지 하한(s16 RMS). webrtcvad는 절대 문턱이 없어 진폭 400
    # 화이트노이즈도 speech로 판정(실측)하고, 브라우저 AGC가 무발화 구간의 소음을
    # 증폭해 턴 종료를 영구히 막을 수 있다. 0이면 비활성 — 실환경 보정 후 기본값을
    # 정한다(하네스 테스트에서 250 부근 권장, 참고: 진폭 400 균일잡음 RMS≈231).
    stt_vad_min_frame_rms: int = 0
    stt_min_speech_ms: int = 250  # 미만 burst(기침·클릭)는 발화 시작으로 확정하지 않음
    # 종료 판정 침묵. 발화가 끝나고 AI가 말하기까지의 체감 지연에서 이 값이 가장 큰
    # 비중을 차지한다(나머지: STT 호출 + 슬롯·관련성 LLM 병렬 + TTS TTFB 실측 1.89s).
    # 3.0s는 아이가 문장 중간에 오래 쉬는 경우까지 감안한 상단값이었지만, 매 턴
    # 3초의 정적이 대화를 끊기게 만든다. 실환경에서 되묻기·끊김이 늘면 올린다.
    stt_trailing_silence_seconds: float = 1.8
    stt_preroll_ms: int = 300  # 발화 확정 전 보존할 선행 오디오
    stt_no_speech_timeout_seconds: float = 10.0  # 무응답 신호(정책 연결은 W03)
    # Streaming TTS(W04). 기본값 근거는 jira/plan/DIARY-3-W04.md §4.
    tts_model: str = "gpt-4o-mini-tts"  # GMS의 유일한 TTS 모델
    tts_voice: str = "alloy"  # §11-B: 캐릭터 목소리는 제품 결정 — 임시 기본값
    tts_timeout_seconds: float = 10.0  # 첫 바이트까지(실측 TTFB 1.89s의 5배 여유)
    # Conversation Event 발행(W05). 기본값 근거는 jira/plan/DIARY-3-W05.md §4.
    stream_publish_max_attempts: int = 3
    stream_publish_retry_seconds: float = 0.5
    stream_outbox_size: int = 256

    def __post_init__(self) -> None:
        if not isinstance(self.gms_key, str) or not self.gms_key.strip():
            raise ValueError("GMS_KEY must contain non-whitespace text")
        parsed_url = urlparse(self.gms_base_url)
        if (
            parsed_url.scheme not in {"http", "https"}
            or not parsed_url.netloc
        ):
            raise ValueError("GMS_BASE_URL must be an absolute HTTP URL")
        for name, model in (
            ("GMS_ANALYSIS_MODEL", self.gms_analysis_model),
            ("GMS_RESPONSE_MODEL", self.gms_response_model),
            ("GMS_REPORT_MODEL", self.gms_report_model),
            ("GMS_CHILD_DIARY_MODEL", self.gms_child_diary_model),
        ):
            if not isinstance(model, str) or not model.strip():
                raise ValueError(f"{name} must contain non-whitespace text")
        if (
            isinstance(self.gms_child_diary_timeout_seconds, bool)
            or not isinstance(
                self.gms_child_diary_timeout_seconds, (int, float)
            )
            or not math.isfinite(self.gms_child_diary_timeout_seconds)
            or self.gms_child_diary_timeout_seconds <= 0
        ):
            raise ValueError(
                "GMS_CHILD_DIARY_TIMEOUT_SECONDS must be a positive finite number"
            )
        if (
            isinstance(self.gms_timeout_seconds, bool)
            or not isinstance(self.gms_timeout_seconds, (int, float))
            or not math.isfinite(self.gms_timeout_seconds)
            or self.gms_timeout_seconds <= 0
        ):
            raise ValueError(
                "GMS_TIMEOUT_SECONDS must be a positive finite number"
            )
        if (
            isinstance(self.gms_max_attempts, bool)
            or not isinstance(self.gms_max_attempts, int)
            or self.gms_max_attempts < 1
        ):
            raise ValueError("GMS_MAX_ATTEMPTS must be a positive integer")
        if (
            not isinstance(self.realtime_redis_host, str)
            or not self.realtime_redis_host.strip()
        ):
            raise ValueError(
                "REALTIME_REDIS_HOST must contain non-whitespace text"
            )
        if (
            isinstance(self.realtime_redis_port, bool)
            or not isinstance(self.realtime_redis_port, int)
            or not (0 < self.realtime_redis_port < 65536)
        ):
            raise ValueError(
                "REALTIME_REDIS_PORT must be a valid TCP port number"
            )
        parsed_jwks_url = urlparse(self.realtime_jwks_url)
        if (
            parsed_jwks_url.scheme not in {"http", "https"}
            or not parsed_jwks_url.netloc
        ):
            raise ValueError("REALTIME_JWKS_URL must be an absolute HTTP URL")
        for name, value in (
            ("REALTIME_TICKET_ISSUER", self.realtime_ticket_issuer),
            ("REALTIME_TICKET_AUDIENCE", self.realtime_ticket_audience),
        ):
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{name} must contain non-whitespace text")
        for name, value in (
            (
                "REALTIME_HEARTBEAT_INTERVAL_SECONDS",
                self.realtime_heartbeat_interval_seconds,
            ),
            ("REALTIME_LEASE_TTL_SECONDS", self.realtime_lease_ttl_seconds),
        ):
            if (
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
                or value <= 0
            ):
                raise ValueError(f"{name} must be a positive finite number")
        for name, text in (
            ("STT_MODEL", self.stt_model),
            ("STT_LANGUAGE", self.stt_language),
            ("TTS_MODEL", self.tts_model),
            ("TTS_VOICE", self.tts_voice),
        ):
            if not isinstance(text, str) or not text.strip():
                raise ValueError(f"{name} must contain non-whitespace text")
        if (
            isinstance(self.tts_timeout_seconds, bool)
            or not isinstance(self.tts_timeout_seconds, (int, float))
            or not math.isfinite(self.tts_timeout_seconds)
            or self.tts_timeout_seconds <= 0
        ):
            raise ValueError("TTS_TIMEOUT_SECONDS must be a positive finite number")
        if (
            isinstance(self.stream_publish_max_attempts, bool)
            or not isinstance(self.stream_publish_max_attempts, int)
            or self.stream_publish_max_attempts < 1
        ):
            raise ValueError("STREAM_PUBLISH_MAX_ATTEMPTS must be a positive integer")
        if (
            isinstance(self.stream_outbox_size, bool)
            or not isinstance(self.stream_outbox_size, int)
            or self.stream_outbox_size < 1
        ):
            raise ValueError("STREAM_OUTBOX_SIZE must be a positive integer")
        if (
            isinstance(self.stream_publish_retry_seconds, bool)
            or not isinstance(self.stream_publish_retry_seconds, (int, float))
            or not math.isfinite(self.stream_publish_retry_seconds)
            or self.stream_publish_retry_seconds <= 0
        ):
            raise ValueError("STREAM_PUBLISH_RETRY_SECONDS must be a positive finite number")
        for name, value in (
            ("STT_TIMEOUT_SECONDS", self.stt_timeout_seconds),
            ("STT_MAX_UTTERANCE_SECONDS", self.stt_max_utterance_seconds),
            ("STT_MIN_UTTERANCE_SECONDS", self.stt_min_utterance_seconds),
        ):
            if (
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
                or value <= 0
            ):
                raise ValueError(f"{name} must be a positive finite number")
        if self.stt_min_utterance_seconds >= self.stt_max_utterance_seconds:
            raise ValueError(
                "STT_MIN_UTTERANCE_SECONDS must be below STT_MAX_UTTERANCE_SECONDS"
            )
        if (
            isinstance(self.stt_vad_aggressiveness, bool)
            or not isinstance(self.stt_vad_aggressiveness, int)
            or self.stt_vad_aggressiveness not in range(4)
        ):
            raise ValueError("STT_VAD_AGGRESSIVENESS must be an integer from 0 to 3")
        if (
            isinstance(self.stt_vad_min_frame_rms, bool)
            or not isinstance(self.stt_vad_min_frame_rms, int)
            or self.stt_vad_min_frame_rms < 0
        ):
            raise ValueError("STT_VAD_MIN_FRAME_RMS must be a non-negative integer")
        for name, value in (
            ("STT_MIN_SPEECH_MS", self.stt_min_speech_ms),
            ("STT_PREROLL_MS", self.stt_preroll_ms),
        ):
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"{name} must be a non-negative integer")
        if self.stt_min_speech_ms == 0:
            raise ValueError("STT_MIN_SPEECH_MS must be positive")
        for name, value in (
            ("STT_TRAILING_SILENCE_SECONDS", self.stt_trailing_silence_seconds),
            ("STT_NO_SPEECH_TIMEOUT_SECONDS", self.stt_no_speech_timeout_seconds),
        ):
            if (
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
                or value <= 0
            ):
                raise ValueError(f"{name} must be a positive finite number")
        if (
            isinstance(self.stt_no_speech_prob_threshold, bool)
            or not isinstance(self.stt_no_speech_prob_threshold, (int, float))
            or not 0 < self.stt_no_speech_prob_threshold <= 1
        ):
            raise ValueError("STT_NO_SPEECH_PROB_THRESHOLD must be within (0, 1]")
        if (
            isinstance(self.stt_low_confidence_threshold, bool)
            or not isinstance(self.stt_low_confidence_threshold, (int, float))
            or not math.isfinite(self.stt_low_confidence_threshold)
            or not 0 <= self.stt_low_confidence_threshold < 1
        ):
            raise ValueError("STT_LOW_CONFIDENCE_THRESHOLD must be within [0, 1)")
        if (
            isinstance(self.stt_low_confidence_reask_limit, bool)
            or not isinstance(self.stt_low_confidence_reask_limit, int)
            or self.stt_low_confidence_reask_limit < 0
        ):
            raise ValueError(
                "STT_LOW_CONFIDENCE_REASK_LIMIT must be a non-negative integer"
            )
        if not isinstance(self.realtime_stun_urls, tuple):
            raise ValueError("REALTIME_STUN_URLS must be a tuple of URLs")
        for url in self.realtime_stun_urls:
            if not isinstance(url, str) or not url.startswith(
                ("stun:", "stuns:", "turn:", "turns:")
            ):
                raise ValueError(
                    "REALTIME_STUN_URLS entries must start with "
                    "stun:, stuns:, turn: or turns:"
                )

    @classmethod
    def from_environment(
        cls,
        environment: Mapping[str, str] | None = None,
    ) -> "AppSettings":
        """Read environment variables only when application startup asks."""

        if environment is None:
            load_dotenv(Path.cwd() / ".env", override=False)
            values = os.environ
        else:
            values = environment
        return cls(
            gms_key=values.get("GMS_KEY", ""),
            gms_base_url=values.get(
                "GMS_BASE_URL",
                "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
            ).rstrip("/"),
            gms_analysis_model=values.get(
                "GMS_ANALYSIS_MODEL",
                values.get("GMS_MODEL", "gpt-5.4-nano"),
            ),
            gms_response_model=values.get(
                "GMS_RESPONSE_MODEL",
                "gpt-5-mini",
            ),
            gms_report_model=values.get(
                "GMS_REPORT_MODEL",
                "gpt-5-mini",
            ),
            gms_child_diary_model=values.get(
                "GMS_CHILD_DIARY_MODEL",
                "gpt-5-mini",
            ),
            gms_child_diary_timeout_seconds=_parse_float(
                values.get("GMS_CHILD_DIARY_TIMEOUT_SECONDS", "90"),
                "GMS_CHILD_DIARY_TIMEOUT_SECONDS",
            ),
            gms_timeout_seconds=_parse_float(
                values.get("GMS_TIMEOUT_SECONDS", "10"),
                "GMS_TIMEOUT_SECONDS",
            ),
            gms_max_attempts=_parse_int(
                values.get("GMS_MAX_ATTEMPTS", "2"),
                "GMS_MAX_ATTEMPTS",
            ),
            realtime_redis_host=values.get(
                "REALTIME_REDIS_HOST", "127.0.0.1"
            ),
            realtime_redis_port=_parse_int(
                values.get("REALTIME_REDIS_PORT", "6379"),
                "REALTIME_REDIS_PORT",
            ),
            realtime_jwks_url=values.get(
                "REALTIME_JWKS_URL",
                "http://127.0.0.1:8080/internal/v1/realtime/jwks",
            ),
            realtime_ticket_issuer=values.get(
                "REALTIME_TICKET_ISSUER", "spring-backend"
            ),
            realtime_ticket_audience=values.get(
                "REALTIME_TICKET_AUDIENCE", "ai-realtime"
            ),
            realtime_heartbeat_interval_seconds=_parse_float(
                values.get("REALTIME_HEARTBEAT_INTERVAL_SECONDS", "5"),
                "REALTIME_HEARTBEAT_INTERVAL_SECONDS",
            ),
            realtime_lease_ttl_seconds=_parse_float(
                values.get("REALTIME_LEASE_TTL_SECONDS", "15"),
                "REALTIME_LEASE_TTL_SECONDS",
            ),
            realtime_stun_urls=_parse_url_list(
                values.get("REALTIME_STUN_URLS", "")
            ),
            stt_model=values.get("STT_MODEL", "whisper-1"),
            stt_language=values.get("STT_LANGUAGE", "ko"),
            stt_timeout_seconds=_parse_float(
                values.get("STT_TIMEOUT_SECONDS", "10"),
                "STT_TIMEOUT_SECONDS",
            ),
            stt_max_utterance_seconds=_parse_float(
                values.get("STT_MAX_UTTERANCE_SECONDS", "30"),
                "STT_MAX_UTTERANCE_SECONDS",
            ),
            stt_min_utterance_seconds=_parse_float(
                values.get("STT_MIN_UTTERANCE_SECONDS", "0.5"),
                "STT_MIN_UTTERANCE_SECONDS",
            ),
            stt_no_speech_prob_threshold=_parse_float(
                values.get("STT_NO_SPEECH_PROB_THRESHOLD", "0.6"),
                "STT_NO_SPEECH_PROB_THRESHOLD",
            ),
            stt_low_confidence_threshold=_parse_float(
                values.get("STT_LOW_CONFIDENCE_THRESHOLD", "0.35"),
                "STT_LOW_CONFIDENCE_THRESHOLD",
            ),
            stt_low_confidence_reask_limit=_parse_int(
                values.get("STT_LOW_CONFIDENCE_REASK_LIMIT", "1"),
                "STT_LOW_CONFIDENCE_REASK_LIMIT",
            ),
            stt_vad_aggressiveness=_parse_int(
                values.get("STT_VAD_AGGRESSIVENESS", "2"),
                "STT_VAD_AGGRESSIVENESS",
            ),
            stt_vad_min_frame_rms=_parse_int(
                values.get("STT_VAD_MIN_FRAME_RMS", "0"),
                "STT_VAD_MIN_FRAME_RMS",
            ),
            stt_min_speech_ms=_parse_int(
                values.get("STT_MIN_SPEECH_MS", "250"),
                "STT_MIN_SPEECH_MS",
            ),
            stt_trailing_silence_seconds=_parse_float(
                values.get("STT_TRAILING_SILENCE_SECONDS", "1.8"),
                "STT_TRAILING_SILENCE_SECONDS",
            ),
            stt_preroll_ms=_parse_int(
                values.get("STT_PREROLL_MS", "300"),
                "STT_PREROLL_MS",
            ),
            stt_no_speech_timeout_seconds=_parse_float(
                values.get("STT_NO_SPEECH_TIMEOUT_SECONDS", "10"),
                "STT_NO_SPEECH_TIMEOUT_SECONDS",
            ),
            tts_model=values.get("TTS_MODEL", "gpt-4o-mini-tts"),
            tts_voice=values.get("TTS_VOICE", "alloy"),
            tts_timeout_seconds=_parse_float(
                values.get("TTS_TIMEOUT_SECONDS", "10"),
                "TTS_TIMEOUT_SECONDS",
            ),
            stream_publish_max_attempts=_parse_int(
                values.get("STREAM_PUBLISH_MAX_ATTEMPTS", "3"),
                "STREAM_PUBLISH_MAX_ATTEMPTS",
            ),
            stream_publish_retry_seconds=_parse_float(
                values.get("STREAM_PUBLISH_RETRY_SECONDS", "0.5"),
                "STREAM_PUBLISH_RETRY_SECONDS",
            ),
            stream_outbox_size=_parse_int(
                values.get("STREAM_OUTBOX_SIZE", "256"),
                "STREAM_OUTBOX_SIZE",
            ),
        )


def _parse_float(value: str, name: str) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        raise ValueError(f"{name} must be a number") from None


def _parse_int(value: str, name: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        raise ValueError(f"{name} must be an integer") from None


def _parse_url_list(value: str) -> tuple[str, ...]:
    return tuple(part.strip() for part in value.split(",") if part.strip())
