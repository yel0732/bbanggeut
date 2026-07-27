"""Rejecting whisper hallucinations before they can become a diary entry.

Not in the original W01 plan — added once GMS was measured and whisper-1 was
confirmed as the only option (jira/plan/DIARY-3-W01.md §1). whisper invents
confident-looking Korean for silence, and the child's confirmed turns are the
input to their diary, so an unguarded transcript can put
"시청해주셔서 감사합니다." into a five-year-old's記録. This is a safety
control, not an accuracy tweak.

Measured behaviour this guard is built from (av/GMS, 2026-07-27):

| input                    | text                       | no_speech_prob |
|--------------------------|----------------------------|---------------:|
| real Korean speech 3.65s | exact                      |           0.00 |
| digital silence 0.4/2/5s | "you"                      |           0.94 |
| 440Hz tone 2s            | "Beep."                    |           0.82 |
| silence + language=ko    | "감사합니다."                |           0.94 |
| silence + Korean prompt  | "...시청해주셔서 감사합니다." |       **0.51** |

Two consequences encoded here:

- ``no_speech_prob`` separates cleanly (0.00 vs 0.82–0.94), so a 0.6 threshold
  has wide margins on both sides.
- The known-artifact phrase list is **never sufficient on its own**. A child
  can genuinely say "감사합니다", and rejecting that would silently delete real
  speech — a worse failure than letting one artifact through. Phrases only
  count when a second signal (short audio, or an elevated no-speech
  probability) already points at silence.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

from app.speech.recognizer import SttResult

# Whisper's stock outputs for silence/noise. Korean entries are the well-known
# YouTube-outro artifacts it falls back on for Korean audio.
_KNOWN_ARTIFACTS = (
    "you",
    "beep.",
    "thank you.",
    "감사합니다.",
    "시청해주셔서 감사합니다.",
    "구독과 좋아요 부탁드립니다.",
    "오늘의 컨텐츠는 여기까지입니다.",
)

# Timestamps beyond the real audio length mean whisper padded its 30s window
# rather than transcribing: a 0.4s file came back with end=2.06s.
_TIMESTAMP_OVERRUN_TOLERANCE = 1.5

# Below this, an artifact phrase is treated as corroborated by duration alone —
# no child utterance worth keeping fits in well under a second.
_ARTIFACT_CORROBORATION_SECONDS = 1.0


class SpeechRejectionReason(StrEnum):
    TOO_SHORT = "tooShort"
    NO_SPEECH = "noSpeech"
    TIMESTAMP_OVERRUN = "timestampOverrun"
    KNOWN_ARTIFACT = "knownArtifact"
    EMPTY_TEXT = "emptyText"


@dataclass(frozen=True)
class HallucinationGuard:
    """Decides whether a transcription represents real child speech."""

    min_audio_seconds: float = 0.5
    no_speech_prob_threshold: float = 0.6

    def should_skip_call(self, audio_seconds: float) -> bool:
        """Whether the audio is too short to be worth sending at all.

        The cheapest layer: a 0.4s clip came back as "you", so this avoids both
        the cost and the artifact.
        """

        return audio_seconds < self.min_audio_seconds

    def evaluate(self, result: SttResult) -> SpeechRejectionReason | None:
        """Return why the result should be discarded, or ``None`` to accept."""

        text = result.text.strip()
        if not text:
            return SpeechRejectionReason.EMPTY_TEXT
        if result.audio_seconds < self.min_audio_seconds:
            return SpeechRejectionReason.TOO_SHORT
        if (
            result.no_speech_prob is not None
            and result.no_speech_prob >= self.no_speech_prob_threshold
        ):
            return SpeechRejectionReason.NO_SPEECH
        if self._timestamps_overrun(result):
            return SpeechRejectionReason.TIMESTAMP_OVERRUN
        if self._is_corroborated_artifact(result, text):
            return SpeechRejectionReason.KNOWN_ARTIFACT
        return None

    def _timestamps_overrun(self, result: SttResult) -> bool:
        if result.reported_end_seconds is None or result.audio_seconds <= 0:
            return False
        limit = result.audio_seconds * _TIMESTAMP_OVERRUN_TOLERANCE
        return result.reported_end_seconds > limit

    def _is_corroborated_artifact(self, result: SttResult, text: str) -> bool:
        if text.casefold() not in _KNOWN_ARTIFACTS:
            return False
        # Corroboration required (see module docstring): the phrase alone never
        # rejects, so a child actually thanking the character is kept.
        if result.audio_seconds < _ARTIFACT_CORROBORATION_SECONDS:
            return True
        return (
            result.no_speech_prob is not None
            and result.no_speech_prob >= self.no_speech_prob_threshold / 2
        )
