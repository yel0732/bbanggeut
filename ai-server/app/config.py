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
