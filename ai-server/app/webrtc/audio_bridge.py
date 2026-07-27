"""Per-session inbound Audio Frame consumer and STT fan-out.

Originally (DIARY-2-W04) this also echoed inbound audio back on a test track to
prove the WebRTC audio pipe worked. DIARY-3-W04 replaced that outbound path with
the real AI voice (``app/speech/speaker.py``), so this class now only consumes
the child's inbound audio and fans each frame out to the STT ``AudioSink``.
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from typing import Protocol

from aiortc.mediastreams import MediaStreamError, MediaStreamTrack
from av import AudioFrame


class AudioSink(Protocol):
    """Consumer of one session's inbound Frames (the STT pipeline in practice)."""

    def on_frame(self, frame: AudioFrame) -> None:
        """Must not block: this runs inside the media receive loop."""
        ...

    async def aclose(self) -> None:
        """Release the sink's own tasks and buffers. Idempotent."""
        ...


class SessionAudioBridge:
    """Owns one session's inbound Audio Consumer Task.

    One instance per Signaling connection — never shared across sessions
    (AI_CONVENTIONS.md §8 session isolation), mirroring the "one instance per
    connection" rule already established for ``SessionLeaseHeartbeat``.
    """

    def __init__(
        self,
        logger: logging.Logger,
        sink: AudioSink | None = None,
    ) -> None:
        self._logger = logger
        self._consume_task: asyncio.Task | None = None
        self._sink = sink

    @property
    def is_running(self) -> bool:
        return self._consume_task is not None

    @property
    def has_sink(self) -> bool:
        return self._sink is not None

    def attach_sink(self, sink: AudioSink) -> None:
        """Attach the STT sink after the fact.

        Required by the real event order: aiortc raises ``track`` during
        ``setRemoteDescription()``, but the ``conversation-events`` DataChannel
        only arrives once SCTP finishes — and the sink needs that channel to
        publish ``stt.final``. Frames received before this point are dropped,
        which is harmless because the client only starts speaking after
        ``client.ready`` (REALTIME_EVENT_CONTRACT.md §6.1).
        """

        if self._sink is not None:
            raise RuntimeError("audio sink already attached")
        self._sink = sink

    def start(self, inbound_track: MediaStreamTrack) -> None:
        if self._consume_task is not None:
            raise RuntimeError("audio bridge already started")
        self._consume_task = asyncio.create_task(self._consume(inbound_track))

    async def _consume(self, inbound_track: MediaStreamTrack) -> None:
        try:
            while True:
                frame = await inbound_track.recv()
                self._feed_sink(frame)
        except asyncio.CancelledError:
            raise
        except MediaStreamError:
            self._logger.info("inbound audio track ended")

    def _feed_sink(self, frame: AudioFrame) -> None:
        if self._sink is None:
            return
        try:
            self._sink.on_frame(frame)
        except Exception:  # noqa: BLE001 - a broken sink must not kill the media loop
            # Losing STT is recoverable; losing the receive loop drops the call.
            self._logger.exception("audio sink rejected a frame")

    async def stop(self) -> None:
        """Stop the consumer and the sink. Idempotent."""

        task = self._consume_task
        self._consume_task = None
        if task is not None:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
        sink, self._sink = self._sink, None
        if sink is not None:
            await sink.aclose()
