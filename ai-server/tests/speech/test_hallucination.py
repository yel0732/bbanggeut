"""Hallucination guard tests, built from the measured GMS probe values.

The fixtures below are the real numbers observed against whisper-1 via GMS on
2026-07-27 (jira/plan/DIARY-3-W01.md §1), so these tests fail if the thresholds
drift away from the behaviour they were chosen for.
"""

import pytest

from app.speech.hallucination import HallucinationGuard, SpeechRejectionReason
from app.speech.recognizer import SttResult


def _measured(
    text: str,
    *,
    audio_seconds: float,
    no_speech_prob: float,
    avg_logprob: float,
    reported_end_seconds: float | None = None,
) -> SttResult:
    return SttResult(
        text=text,
        audio_seconds=audio_seconds,
        no_speech_prob=no_speech_prob,
        avg_logprob=avg_logprob,
        reported_end_seconds=(
            audio_seconds if reported_end_seconds is None else reported_end_seconds
        ),
    )


# --- measured probe outputs -------------------------------------------------

REAL_SPEECH = _measured(
    "오늘 과일 자르기 게임을 했는데 정말 재미있었어.",
    audio_seconds=3.65,
    no_speech_prob=0.00,
    avg_logprob=-0.299,
)
SILENCE_2S = _measured(
    "you", audio_seconds=2.0, no_speech_prob=0.94, avg_logprob=-0.515,
    reported_end_seconds=2.06,
)
TONE_2S = _measured(
    "Beep.", audio_seconds=2.0, no_speech_prob=0.82, avg_logprob=-0.941,
)
SILENCE_KO = _measured(
    "감사합니다.", audio_seconds=2.0, no_speech_prob=0.94, avg_logprob=-0.515,
)
# The probe returned two segments (end=3.0 and end=5.0) for a 2.0s file, so the
# transcript is both joined and its last timestamp is 2.5x the real duration.
SILENCE_WITH_PROMPT = _measured(
    "오늘의 컨텐츠는 여기까지입니다. 시청해주셔서 감사합니다.",
    audio_seconds=2.0,
    no_speech_prob=0.51,
    avg_logprob=-0.982,
    reported_end_seconds=5.0,
)


class TestSkipBeforeCalling:
    def test_short_audio_is_never_sent_to_the_provider(self) -> None:
        """A 0.4s clip came back as "you" — cheapest layer is not calling."""

        assert HallucinationGuard().should_skip_call(0.4) is True

    def test_normal_utterance_is_sent(self) -> None:
        assert HallucinationGuard().should_skip_call(3.65) is False


class TestMeasuredCases:
    def test_real_speech_is_accepted(self) -> None:
        assert HallucinationGuard().evaluate(REAL_SPEECH) is None

    @pytest.mark.parametrize(
        ("result", "reason"),
        [
            (SILENCE_2S, SpeechRejectionReason.NO_SPEECH),
            (TONE_2S, SpeechRejectionReason.NO_SPEECH),
            (SILENCE_KO, SpeechRejectionReason.NO_SPEECH),
            (SILENCE_WITH_PROMPT, SpeechRejectionReason.TIMESTAMP_OVERRUN),
        ],
    )
    def test_measured_hallucinations_are_rejected(
        self, result: SttResult, reason: SpeechRejectionReason
    ) -> None:
        assert HallucinationGuard().evaluate(result) is reason

    def test_prompt_case_is_caught_despite_the_lowered_no_speech_prob(self) -> None:
        """The Korean prompt dropped no_speech_prob 0.94 -> 0.51, under the
        threshold. A second signal has to catch it, which is why the guard has
        more than one layer (and why the adapter sends no prompt at all)."""

        assert SILENCE_WITH_PROMPT.no_speech_prob < HallucinationGuard().no_speech_prob_threshold
        assert HallucinationGuard().evaluate(SILENCE_WITH_PROMPT) is not None


class TestArtifactPhrasesNeedCorroboration:
    def test_a_child_really_saying_thank_you_is_kept(self) -> None:
        """The most important test here: the phrase list must not delete real speech."""

        genuine = _measured(
            "감사합니다.", audio_seconds=1.8, no_speech_prob=0.02, avg_logprob=-0.31
        )

        assert HallucinationGuard().evaluate(genuine) is None

    def test_artifact_phrase_with_high_no_speech_prob_is_rejected(self) -> None:
        artifact = _measured(
            "감사합니다.", audio_seconds=1.8, no_speech_prob=0.45, avg_logprob=-0.9
        )

        assert (
            HallucinationGuard().evaluate(artifact)
            is SpeechRejectionReason.KNOWN_ARTIFACT
        )

    def test_artifact_phrase_in_a_very_short_clip_is_rejected(self) -> None:
        artifact = _measured(
            "you", audio_seconds=0.7, no_speech_prob=0.1, avg_logprob=-0.5
        )

        assert (
            HallucinationGuard().evaluate(artifact)
            is SpeechRejectionReason.KNOWN_ARTIFACT
        )

    def test_ordinary_speech_containing_an_artifact_word_is_kept(self) -> None:
        """Matching is on the whole utterance, not substrings."""

        genuine = _measured(
            "친구한테 감사합니다. 하고 말했어.",
            audio_seconds=2.5,
            no_speech_prob=0.01,
            avg_logprob=-0.35,
        )

        assert HallucinationGuard().evaluate(genuine) is None


class TestOtherRejections:
    def test_empty_transcript_is_rejected(self) -> None:
        empty = _measured("   ", audio_seconds=2.0, no_speech_prob=0.1, avg_logprob=-0.3)

        assert HallucinationGuard().evaluate(empty) is SpeechRejectionReason.EMPTY_TEXT

    def test_short_audio_reaching_evaluate_is_rejected(self) -> None:
        short = _measured("응", audio_seconds=0.2, no_speech_prob=0.1, avg_logprob=-0.3)

        assert HallucinationGuard().evaluate(short) is SpeechRejectionReason.TOO_SHORT

    def test_missing_provider_signals_do_not_reject_by_themselves(self) -> None:
        """A provider that reports no probabilities must not fail every turn."""

        sparse = SttResult(text="오늘 재미있었어.", audio_seconds=2.0)

        assert HallucinationGuard().evaluate(sparse) is None

    def test_thresholds_are_configurable(self) -> None:
        strict = HallucinationGuard(min_audio_seconds=5.0, no_speech_prob_threshold=0.001)

        # Both knobs bite: real 3.65s speech now falls under the length floor...
        assert strict.should_skip_call(3.65) is True
        assert strict.evaluate(REAL_SPEECH) is SpeechRejectionReason.TOO_SHORT
        # ...and a longer clip is judged against the tightened no-speech bar.
        assert HallucinationGuard(no_speech_prob_threshold=0.001).evaluate(
            _measured("응 맞아.", audio_seconds=2.0, no_speech_prob=0.01, avg_logprob=-0.3)
        ) is SpeechRejectionReason.NO_SPEECH
