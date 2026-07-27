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
    gms_timeout_seconds: float = 10.0
    gms_max_attempts: int = 2

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
            gms_timeout_seconds=_parse_float(
                values.get("GMS_TIMEOUT_SECONDS", "10"),
                "GMS_TIMEOUT_SECONDS",
            ),
            gms_max_attempts=_parse_int(
                values.get("GMS_MAX_ATTEMPTS", "2"),
                "GMS_MAX_ATTEMPTS",
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
