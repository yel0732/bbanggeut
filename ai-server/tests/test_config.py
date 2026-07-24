"""Tests for startup-only GMS settings."""

import pytest

from app.config import AppSettings


def test_reads_gms_settings_without_exposing_key() -> None:
    settings = AppSettings.from_environment(
        {
            "GMS_KEY": "PRIVATE_GMS_KEY",
            "GMS_MODEL": "gpt-5.4-nano",
            "GMS_TIMEOUT_SECONDS": "3.5",
            "GMS_MAX_ATTEMPTS": "2",
        }
    )

    assert settings.gms_timeout_seconds == 3.5
    assert settings.gms_max_attempts == 2
    assert "PRIVATE_GMS_KEY" not in repr(settings)


@pytest.mark.parametrize(
    "environment",
    [
        {},
        {"GMS_KEY": "key", "GMS_TIMEOUT_SECONDS": "0"},
        {"GMS_KEY": "key", "GMS_MAX_ATTEMPTS": "0"},
        {"GMS_KEY": "key", "GMS_BASE_URL": "not-a-url"},
    ],
)
def test_rejects_invalid_settings(environment: dict[str, str]) -> None:
    with pytest.raises(ValueError):
        AppSettings.from_environment(environment)
