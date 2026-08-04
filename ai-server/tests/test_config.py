"""Tests for startup-only GMS settings."""

import pytest

from app.config import AppSettings

 
def test_reads_gms_settings_without_exposing_key() -> None:
    settings = AppSettings.from_environment(
        {
            "GMS_KEY": "PRIVATE_GMS_KEY",
            "GMS_ANALYSIS_MODEL": "gpt-5.4-nano",
            "GMS_RESPONSE_MODEL": "gpt-5-mini",
            "GMS_REPORT_MODEL": "gpt-5-mini",
            "GMS_CHILD_DIARY_MODEL": "gpt-5-mini",
            "GMS_CHILD_DIARY_TIMEOUT_SECONDS": "75",
            "GMS_TIMEOUT_SECONDS": "3.5",
            "GMS_MAX_ATTEMPTS": "2",
        }
    )

    assert settings.gms_timeout_seconds == 3.5
    assert settings.gms_max_attempts == 2
    assert settings.gms_analysis_model == "gpt-5.4-nano"
    assert settings.gms_response_model == "gpt-5-mini"
    assert settings.gms_report_model == "gpt-5-mini"
    assert settings.gms_child_diary_model == "gpt-5-mini"
    assert settings.gms_child_diary_timeout_seconds == 75.0
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


def test_reads_realtime_settings_with_defaults() -> None:
    settings = AppSettings.from_environment({"GMS_KEY": "key"})

    assert settings.realtime_redis_host == "127.0.0.1"
    assert settings.realtime_redis_port == 6379
    assert settings.realtime_jwks_url == (
        "http://127.0.0.1:8080/internal/v1/realtime/jwks"
    )
    assert settings.realtime_ticket_issuer == "spring-backend"
    assert settings.realtime_ticket_audience == "ai-realtime"
    assert settings.realtime_heartbeat_interval_seconds == 5.0
    assert settings.realtime_lease_ttl_seconds == 15.0
    # 기본은 STUN 미사용 — 로컬 MVP에는 host 후보로 충분하고, 외부 STUN 의존은
    # 도달 불가 네트워크에서 ICE gathering을 ~5초씩 지연시킨다.
    assert settings.realtime_stun_urls == ()
    assert settings.stt_model == "whisper-1"
    assert settings.stt_language == "ko"
    assert settings.stt_timeout_seconds == 10.0
    assert settings.stt_max_utterance_seconds == 30.0
    assert settings.stt_min_utterance_seconds == 0.5
    assert settings.stt_no_speech_prob_threshold == 0.6
    assert settings.stt_low_confidence_threshold == 0.35
    assert settings.stt_low_confidence_reask_limit == 1
    assert settings.stt_vad_aggressiveness == 2
    assert settings.stt_vad_min_frame_rms == 250
    assert settings.stt_min_speech_ms == 250
    assert settings.stt_trailing_silence_seconds == 1.0
    assert settings.stt_preroll_ms == 300
    assert settings.stt_no_speech_timeout_seconds == 10.0
    assert settings.tts_model == "gpt-4o-mini-tts"
    assert settings.tts_voice == "shimmer"
    assert settings.tts_voice_shift_semitones == 2.0
    assert settings.tts_timeout_seconds == 10.0
    assert settings.stream_publish_max_attempts == 3
    assert settings.stream_publish_retry_seconds == 0.5
    assert settings.stream_outbox_size == 256


def test_reads_realtime_settings_from_environment() -> None:
    settings = AppSettings.from_environment(
        {
            "GMS_KEY": "key",
            "REALTIME_REDIS_HOST": "redis.internal",
            "REALTIME_REDIS_PORT": "16379",
            "REALTIME_JWKS_URL": "http://spring.internal:8080/internal/v1/realtime/jwks",
            "REALTIME_TICKET_ISSUER": "custom-issuer",
            "REALTIME_TICKET_AUDIENCE": "custom-audience",
            "REALTIME_HEARTBEAT_INTERVAL_SECONDS": "7.5",
            "REALTIME_LEASE_TTL_SECONDS": "20",
            "REALTIME_STUN_URLS": "stun:stun.internal:3478, turns:turn.internal:5349",
            "STT_MODEL": "whisper-large",
            "STT_LANGUAGE": "en",
            "STT_TIMEOUT_SECONDS": "6",
            "STT_MAX_UTTERANCE_SECONDS": "20",
            "STT_MIN_UTTERANCE_SECONDS": "1",
            "STT_NO_SPEECH_PROB_THRESHOLD": "0.75",
            "STT_LOW_CONFIDENCE_THRESHOLD": "0.5",
            "STT_LOW_CONFIDENCE_REASK_LIMIT": "2",
            "STT_VAD_AGGRESSIVENESS": "3",
            "STT_VAD_MIN_FRAME_RMS": "250",
            "STT_MIN_SPEECH_MS": "300",
            "STT_TRAILING_SILENCE_SECONDS": "2.5",
            "STT_PREROLL_MS": "200",
            "STT_NO_SPEECH_TIMEOUT_SECONDS": "8",
            "TTS_MODEL": "tts-x",
            "TTS_VOICE": "verse",
            "TTS_VOICE_SHIFT_SEMITONES": "1.5",
            "TTS_TIMEOUT_SECONDS": "7",
            "STREAM_PUBLISH_MAX_ATTEMPTS": "5",
            "STREAM_PUBLISH_RETRY_SECONDS": "1.5",
            "STREAM_OUTBOX_SIZE": "64",
        }
    )

    assert settings.realtime_redis_host == "redis.internal"
    assert settings.realtime_redis_port == 16379
    assert settings.realtime_jwks_url == (
        "http://spring.internal:8080/internal/v1/realtime/jwks"
    )
    assert settings.realtime_ticket_issuer == "custom-issuer"
    assert settings.realtime_ticket_audience == "custom-audience"
    assert settings.realtime_heartbeat_interval_seconds == 7.5
    assert settings.realtime_lease_ttl_seconds == 20.0
    assert settings.realtime_stun_urls == (
        "stun:stun.internal:3478",
        "turns:turn.internal:5349",
    )
    assert settings.stt_model == "whisper-large"
    assert settings.stt_language == "en"
    assert settings.stt_timeout_seconds == 6.0
    assert settings.stt_max_utterance_seconds == 20.0
    assert settings.stt_min_utterance_seconds == 1.0
    assert settings.stt_no_speech_prob_threshold == 0.75
    assert settings.stt_low_confidence_threshold == 0.5
    assert settings.stt_low_confidence_reask_limit == 2
    assert settings.stt_vad_aggressiveness == 3
    assert settings.stt_vad_min_frame_rms == 250
    assert settings.stt_min_speech_ms == 300
    assert settings.stt_trailing_silence_seconds == 2.5
    assert settings.stt_preroll_ms == 200
    assert settings.stt_no_speech_timeout_seconds == 8.0
    assert settings.tts_model == "tts-x"
    assert settings.tts_voice == "verse"
    assert settings.tts_voice_shift_semitones == 1.5
    assert settings.tts_timeout_seconds == 7.0
    assert settings.stream_publish_max_attempts == 5
    assert settings.stream_publish_retry_seconds == 1.5
    assert settings.stream_outbox_size == 64


def test_supports_legacy_model_for_analysis_only() -> None:
    settings = AppSettings.from_environment(
        {
            "GMS_KEY": "key",
            "GMS_MODEL": "legacy-analysis-model",
        }
    )

    assert settings.gms_analysis_model == "legacy-analysis-model"
    assert settings.gms_response_model == "gpt-5-mini"


@pytest.mark.parametrize(
    "environment",
    [
        {},
        {"GMS_KEY": "key", "GMS_TIMEOUT_SECONDS": "0"},
        {"GMS_KEY": "key", "GMS_MAX_ATTEMPTS": "0"},
        {"GMS_KEY": "key", "GMS_BASE_URL": "not-a-url"},
        {"GMS_KEY": "key", "GMS_ANALYSIS_MODEL": " "},
        {"GMS_KEY": "key", "GMS_RESPONSE_MODEL": " "},
        {"GMS_KEY": "key", "REALTIME_REDIS_HOST": " "},
        {"GMS_KEY": "key", "REALTIME_REDIS_PORT": "0"},
        {"GMS_KEY": "key", "REALTIME_REDIS_PORT": "70000"},
        {"GMS_KEY": "key", "REALTIME_JWKS_URL": "not-a-url"},
        {"GMS_KEY": "key", "REALTIME_TICKET_ISSUER": " "},
        {"GMS_KEY": "key", "REALTIME_TICKET_AUDIENCE": " "},
        {"GMS_KEY": "key", "REALTIME_HEARTBEAT_INTERVAL_SECONDS": "0"},
        {"GMS_KEY": "key", "REALTIME_LEASE_TTL_SECONDS": "-1"},
        {"GMS_KEY": "key", "GMS_REPORT_MODEL": " "},
        {"GMS_KEY": "key", "GMS_CHILD_DIARY_MODEL": " "},
        {"GMS_KEY": "key", "REALTIME_STUN_URLS": "not-a-stun-url"},
        {"GMS_KEY": "key", "REALTIME_STUN_URLS": "http://stun.internal:3478"},
        {"GMS_KEY": "key", "STT_MODEL": " "},
        {"GMS_KEY": "key", "STT_LANGUAGE": " "},
        {"GMS_KEY": "key", "STT_TIMEOUT_SECONDS": "0"},
        {"GMS_KEY": "key", "STT_MIN_UTTERANCE_SECONDS": "-1"},
        {"GMS_KEY": "key", "STT_NO_SPEECH_PROB_THRESHOLD": "0"},
        {"GMS_KEY": "key", "STT_NO_SPEECH_PROB_THRESHOLD": "1.5"},
        # 1 이상이면 모든 발화를 되묻는다 — 대화가 성립하지 않는다(0은 비활성으로 허용)
        {"GMS_KEY": "key", "STT_LOW_CONFIDENCE_THRESHOLD": "1"},
        {"GMS_KEY": "key", "STT_LOW_CONFIDENCE_THRESHOLD": "-0.1"},
        {"GMS_KEY": "key", "STT_LOW_CONFIDENCE_REASK_LIMIT": "-1"},
        # 최소가 최대보다 크면 어떤 발화도 인식되지 않는다
        {"GMS_KEY": "key", "STT_MIN_UTTERANCE_SECONDS": "40"},
        {"GMS_KEY": "key", "STT_VAD_AGGRESSIVENESS": "4"},
        {"GMS_KEY": "key", "STT_VAD_AGGRESSIVENESS": "-1"},
        {"GMS_KEY": "key", "STT_VAD_MIN_FRAME_RMS": "-1"},
        {"GMS_KEY": "key", "STT_MIN_SPEECH_MS": "0"},
        {"GMS_KEY": "key", "STT_TRAILING_SILENCE_SECONDS": "0"},
        {"GMS_KEY": "key", "STT_PREROLL_MS": "-1"},
        {"GMS_KEY": "key", "STT_NO_SPEECH_TIMEOUT_SECONDS": "0"},
        {"GMS_KEY": "key", "TTS_MODEL": " "},
        {"GMS_KEY": "key", "TTS_VOICE": " "},
        {"GMS_KEY": "key", "TTS_VOICE_SHIFT_SEMITONES": "-0.1"},
        {"GMS_KEY": "key", "TTS_VOICE_SHIFT_SEMITONES": "4.1"},
        {"GMS_KEY": "key", "TTS_TIMEOUT_SECONDS": "0"},
        {"GMS_KEY": "key", "STREAM_PUBLISH_MAX_ATTEMPTS": "0"},
        {"GMS_KEY": "key", "STREAM_OUTBOX_SIZE": "0"},
        {"GMS_KEY": "key", "STREAM_PUBLISH_RETRY_SECONDS": "0"},
    ],
)
def test_rejects_invalid_settings(environment: dict[str, str]) -> None:
    with pytest.raises(ValueError):
        AppSettings.from_environment(environment)
