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
    gms_response_model: str = "gpt-5.4-mini"
    gms_report_model: str = "gpt-5.4-mini"
    gms_child_diary_model: str = "gpt-5.4-mini"
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
        ):
            if not isinstance(text, str) or not text.strip():
                raise ValueError(f"{name} must contain non-whitespace text")
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
            isinstance(self.stt_no_speech_prob_threshold, bool)
            or not isinstance(self.stt_no_speech_prob_threshold, (int, float))
            or not 0 < self.stt_no_speech_prob_threshold <= 1
        ):
            raise ValueError("STT_NO_SPEECH_PROB_THRESHOLD must be within (0, 1]")
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
                "gpt-5.4-mini",
            ),
            gms_report_model=values.get(
                "GMS_REPORT_MODEL",
                "gpt-5.4-mini",
            ),
            gms_child_diary_model=values.get(
                "GMS_CHILD_DIARY_MODEL",
                "gpt-5.4-mini",
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
