"""Tests for startup-only GMS settings."""

import pytest

from app.config import AppSettings


def test_reads_gms_settings_without_exposing_key() -> None:
    settings = AppSettings.from_environment(
        {
            "GMS_KEY": "PRIVATE_GMS_KEY",
            "GMS_ANALYSIS_MODEL": "gpt-5.4-nano",
            "GMS_RESPONSE_MODEL": "gpt-5.4-mini",
            "GMS_REPORT_MODEL": "gpt-5.4-mini",
            "GMS_TIMEOUT_SECONDS": "3.5",
            "GMS_MAX_ATTEMPTS": "2",
        }
    )

    assert settings.gms_timeout_seconds == 3.5
    assert settings.gms_max_attempts == 2
    assert settings.gms_analysis_model == "gpt-5.4-nano"
    assert settings.gms_response_model == "gpt-5.4-mini"
    assert settings.gms_report_model == "gpt-5.4-mini"
    assert "PRIVATE_GMS_KEY" not in repr(settings)


def test_loads_local_dotenv_without_overriding_environment(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path,
) -> None:
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("GMS_RESPONSE_MODEL", "environment-model")
    monkeypatch.delenv("GMS_KEY", raising=False)
    (tmp_path / ".env").write_text(
        "GMS_KEY=dotenv-key\n"
        "GMS_RESPONSE_MODEL=dotenv-model\n",
        encoding="utf-8",
    )

    settings = AppSettings.from_environment()

    assert settings.gms_key == "dotenv-key"
    assert settings.gms_response_model == "environment-model"


def test_supports_legacy_model_for_analysis_only() -> None:
    settings = AppSettings.from_environment(
        {
            "GMS_KEY": "key",
            "GMS_MODEL": "legacy-analysis-model",
        }
    )

    assert settings.gms_analysis_model == "legacy-analysis-model"
    assert settings.gms_response_model == "gpt-5.4-mini"


@pytest.mark.parametrize(
    "environment",
    [
        {},
        {"GMS_KEY": "key", "GMS_TIMEOUT_SECONDS": "0"},
        {"GMS_KEY": "key", "GMS_MAX_ATTEMPTS": "0"},
        {"GMS_KEY": "key", "GMS_BASE_URL": "not-a-url"},
        {"GMS_KEY": "key", "GMS_ANALYSIS_MODEL": " "},
        {"GMS_KEY": "key", "GMS_RESPONSE_MODEL": " "},
        {"GMS_KEY": "key", "GMS_REPORT_MODEL": " "},
    ],
)
def test_rejects_invalid_settings(environment: dict[str, str]) -> None:
    with pytest.raises(ValueError):
        AppSettings.from_environment(environment)
