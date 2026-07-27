"""Per-session inbound Audio Frame consumer + Echo outbound Track.

W04 scope: prove the Audio Track pipe actually works end to end (child mic
Track -> FastAPI -> Echo Track back) — real STT/dialogue processing is out of
scope (jira/plan/DIARY-2-W04.md §3 principle 6). DIARY-3 replaces or extends
the consumer loop to feed real STT instead of echoing.
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from fractions import Fraction

from aiortc.mediastreams import MediaStreamError, MediaStreamTrack
from av import AudioFrame

_DEFAULT_MAX_QUEUE_SIZE = 50  # ~1s at 20ms/frame — backpressure bound (§3-2)


class EchoAudioTrack(MediaStreamTrack):
    """Re-emits Frames pulled from a SessionAudioBridge's queue.

    Each Frame is re-timestamped onto this track's own outbound timeline —
    the original inbound pts sequence is not reused, since this track starts
    its own independent RTP timeline once added via ``addTrack()``.
    """

    kind = "audio"

    def __init__(self, queue: "asyncio.Queue[AudioFrame]") -> None:
        super().__init__()
        self._queue = queue
        self._next_pts = 0

    async def recv(self) -> AudioFrame:
        if self.readyState != "live":
            raise MediaStreamError("track is not live")
        frame = await self._queue.get()
        frame.pts = self._next_pts
        frame.time_base = Fraction(1, frame.sample_rate)
        self._next_pts += frame.samples
        return frame


class SessionAudioBridge:
    """Owns one session's inbound Audio Consumer Task and Echo Queue.

    One instance per Signaling connection — never shared across sessions
    (AI_CONVENTIONS.md §8 session isolation), mirroring the "one instance per
    connection" rule already established for ``SessionLeaseHeartbeat``.
    """

    def __init__(
        self,
        logger: logging.Logger,
        max_queue_size: int = _DEFAULT_MAX_QUEUE_SIZE,
    ) -> None:
        self._logger = logger
        self._queue: asyncio.Queue[AudioFrame] = asyncio.Queue(maxsize=max_queue_size)
        self._consume_task: asyncio.Task | None = None

    @property
    def is_running(self) -> bool:
        return self._consume_task is not None

    def start(self, inbound_track: MediaStreamTrack) -> None:
        if self._consume_task is not None:
            raise RuntimeError("audio bridge already started")
        self._consume_task = asyncio.create_task(self._consume(inbound_track))

    def create_outbound_track(self) -> EchoAudioTrack:
        return EchoAudioTrack(self._queue)

    async def _consume(self, inbound_track: MediaStreamTrack) -> None:
        try:
            while True:
                frame = await inbound_track.recv()
                if self._queue.full():
                    self._queue.get_nowait()  # drop-oldest backpressure(§3-2)
                self._queue.put_nowait(frame)
        except asyncio.CancelledError:
            raise
        except MediaStreamError:
            self._logger.info("inbound audio track ended")

    async def stop(self) -> None:
        task = self._consume_task
        if task is None:
            return
        self._consume_task = None
        task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await task
