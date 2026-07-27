"""Realtime Ticket signature verification and atomic one-time consumption.

Mirrors the async Protocol + safe-error-code style established for the
dialogue question generation Provider (see app/dialogue/question/provider.py):
external SDK/Redis errors never leak raw messages into results or logs, and
results are immutable dataclasses whose fields are mutually consistent.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol

import jwt

from app.realtime.jwks_cache import JwksFetchError, JwksKeyNotFoundError, RealtimeJwksCache
from app.realtime.session_epoch_registry import SessionEpochRegistry

_TICKET_KEY_PREFIX = "realtime:ticket:"
_SESSION_KEY_PREFIX = "realtime:session:"
_REQUIRED_SCOPE = "realtime:connect"

_CONSUME_SCRIPT = """
local raw = redis.call('GET', KEYS[1])
if not raw then return 'NOT_FOUND' end
local record = cjson.decode(raw)
if record.state ~= 'ISSUED' then return 'ALREADY_CONSUMED' end
record.state = 'CONSUMED'
redis.call('SET', KEYS[1], cjson.encode(record), 'KEEPTTL')
return cjson.encode(record)
"""


class TicketPeekStatus(StrEnum):
    """Read-only observation of a ticket's current state (no side effect)."""

    ISSUED = "issued"
    ALREADY_CONSUMED = "alreadyConsumed"
    NOT_FOUND = "notFound"


class TicketConsumeStatus(StrEnum):
    CONSUMED = "consumed"
    ALREADY_CONSUMED = "alreadyConsumed"
    NOT_FOUND = "notFound"


@dataclass(frozen=True)
class TicketConsumeOutcome:
    status: TicketConsumeStatus
    conversation_id: str | None
    realtime_session_id: str | None
    session_epoch: int | None

    def __post_init__(self) -> None:
        ticket_fields = (
            self.conversation_id,
            self.realtime_session_id,
            self.session_epoch,
        )
        if self.status == TicketConsumeStatus.CONSUMED:
            if any(field is None for field in ticket_fields):
                raise ValueError("consumed outcome requires all ticket fields")
        elif any(field is not None for field in ticket_fields):
            raise ValueError("non-consumed outcome must not carry ticket fields")


class TicketConsumptionStore(Protocol):
    async def peek(self, jti: str) -> TicketPeekStatus: ...
    async def consume(self, jti: str) -> TicketConsumeOutcome: ...


@dataclass(frozen=True)
class LeaseSnapshot:
    realtime_session_id: str
    session_epoch: int


class SessionLeaseReader(Protocol):
    async def get_current(self, conversation_id: str) -> LeaseSnapshot | None: ...


class RedisTicketConsumptionStore:
    """Atomically transitions ``realtime:ticket:{jti}`` from ISSUED to CONSUMED."""

    def __init__(self, redis_client) -> None:
        self._redis = redis_client
        self._consume_script = redis_client.register_script(_CONSUME_SCRIPT)

    async def peek(self, jti: str) -> TicketPeekStatus:
        raw = await self._redis.get(f"{_TICKET_KEY_PREFIX}{jti}")
        if raw is None:
            return TicketPeekStatus.NOT_FOUND
        record = json.loads(raw)
        return (
            TicketPeekStatus.ISSUED
            if record.get("state") == "ISSUED"
            else TicketPeekStatus.ALREADY_CONSUMED
        )

    async def consume(self, jti: str) -> TicketConsumeOutcome:
        key = f"{_TICKET_KEY_PREFIX}{jti}"
        result = await self._consume_script(keys=[key])
        if result == "NOT_FOUND":
            return TicketConsumeOutcome(TicketConsumeStatus.NOT_FOUND, None, None, None)
        if result == "ALREADY_CONSUMED":
            return TicketConsumeOutcome(TicketConsumeStatus.ALREADY_CONSUMED, None, None, None)
        record = json.loads(result)
        return TicketConsumeOutcome(
            TicketConsumeStatus.CONSUMED,
            record["conversationId"],
            record["realtimeSessionId"],
            record["sessionEpoch"],
        )


class RedisSessionLeaseReader:
    """Reads ``realtime:session:{conversationId}`` without modifying it."""

    def __init__(self, redis_client) -> None:
        self._redis = redis_client

    async def get_current(self, conversation_id: str) -> LeaseSnapshot | None:
        raw = await self._redis.get(f"{_SESSION_KEY_PREFIX}{conversation_id}")
        if raw is None:
            return None
        record = json.loads(raw)
        return LeaseSnapshot(record["realtimeSessionId"], record["sessionEpoch"])


class TicketVerificationFailureCode(StrEnum):
    MALFORMED_TICKET = "malformedTicket"
    UNKNOWN_KEY = "unknownKey"
    INVALID_SIGNATURE = "invalidSignature"
    EXPIRED = "expired"
    WRONG_ISSUER = "wrongIssuer"
    WRONG_AUDIENCE = "wrongAudience"
    MISSING_SCOPE = "missingScope"
    NOT_FOUND = "notFound"
    ALREADY_CONSUMED = "alreadyConsumed"
    LEASE_MISMATCH = "leaseMismatch"


@dataclass(frozen=True)
class TicketVerificationResult:
    conversation_id: str | None
    realtime_session_id: str | None
    session_epoch: int | None
    failure_code: TicketVerificationFailureCode | None

    def __post_init__(self) -> None:
        ticket_fields = (
            self.conversation_id,
            self.realtime_session_id,
            self.session_epoch,
        )
        if self.failure_code is None:
            if any(field is None for field in ticket_fields):
                raise ValueError("successful result requires all ticket fields")
        elif any(field is not None for field in ticket_fields):
            raise ValueError("failed result must not carry ticket fields")

    @property
    def is_valid(self) -> bool:
        return self.failure_code is None

    @staticmethod
    def success(
        conversation_id: str, realtime_session_id: str, session_epoch: int
    ) -> "TicketVerificationResult":
        return TicketVerificationResult(
            conversation_id, realtime_session_id, session_epoch, None
        )

    @staticmethod
    def failure(code: TicketVerificationFailureCode) -> "TicketVerificationResult":
        return TicketVerificationResult(None, None, None, code)


class RealtimeTicketVerifier:
    """Runs the ``session.authenticate`` verification order from
    REALTIME_EVENT_CONTRACT.md §5.1: signature -> iss/aud/scope/exp ->
    Redis ticket state -> Session Lease/Epoch -> atomic consumption.
    """

    def __init__(
        self,
        jwks_cache: RealtimeJwksCache,
        ticket_store: TicketConsumptionStore,
        lease_reader: SessionLeaseReader,
        epoch_registry: SessionEpochRegistry,
        issuer: str,
        audience: str,
        logger: logging.Logger,
    ) -> None:
        self._jwks_cache = jwks_cache
        self._ticket_store = ticket_store
        self._lease_reader = lease_reader
        self._epoch_registry = epoch_registry
        self._issuer = issuer
        self._audience = audience
        self._logger = logger

    async def verify_and_consume(self, ticket: str) -> TicketVerificationResult:
        try:
            header = jwt.get_unverified_header(ticket)
        except jwt.InvalidTokenError:
            return self._reject(TicketVerificationFailureCode.MALFORMED_TICKET)

        kid = header.get("kid")
        if not kid:
            return self._reject(TicketVerificationFailureCode.MALFORMED_TICKET)

        try:
            public_key = await self._jwks_cache.get_signing_key(kid)
        except (JwksKeyNotFoundError, JwksFetchError):
            return self._reject(TicketVerificationFailureCode.UNKNOWN_KEY, kid=kid)

        try:
            claims = jwt.decode(
                ticket,
                key=public_key,
                algorithms=["RS256"],
                issuer=self._issuer,
                audience=self._audience,
            )
        except jwt.ExpiredSignatureError:
            return self._reject(TicketVerificationFailureCode.EXPIRED, kid=kid)
        except jwt.InvalidIssuerError:
            return self._reject(TicketVerificationFailureCode.WRONG_ISSUER, kid=kid)
        except jwt.InvalidAudienceError:
            return self._reject(TicketVerificationFailureCode.WRONG_AUDIENCE, kid=kid)
        except jwt.InvalidSignatureError:
            return self._reject(TicketVerificationFailureCode.INVALID_SIGNATURE, kid=kid)
        except jwt.InvalidTokenError:
            return self._reject(TicketVerificationFailureCode.MALFORMED_TICKET, kid=kid)

        scope = claims.get("scope") or []
        if _REQUIRED_SCOPE not in scope:
            return self._reject(TicketVerificationFailureCode.MISSING_SCOPE, kid=kid)

        jti = claims.get("jti")
        conversation_id = claims.get("conversationId")
        realtime_session_id = claims.get("realtimeSessionId")
        session_epoch = claims.get("sessionEpoch")
        if (
            not jti
            or not conversation_id
            or not realtime_session_id
            or session_epoch is None
        ):
            return self._reject(TicketVerificationFailureCode.MALFORMED_TICKET, kid=kid)

        # Redis Ticket 상태 검증 (읽기 전용 — REALTIME_EVENT_CONTRACT.md §5.1 순서상
        # 원자적 소비보다 먼저 온다). 서명된 claim을 신뢰 소스로 사용해 Lease를 조회한다.
        peek_status = await self._ticket_store.peek(jti)
        if peek_status is TicketPeekStatus.NOT_FOUND:
            return self._reject(
                TicketVerificationFailureCode.NOT_FOUND, kid=kid, conversation_id=conversation_id
            )
        if peek_status is TicketPeekStatus.ALREADY_CONSUMED:
            return self._reject(
                TicketVerificationFailureCode.ALREADY_CONSUMED,
                kid=kid,
                conversation_id=conversation_id,
            )

        # Session Lease·Epoch 검증. 아직 Ticket을 소비하지 않았으므로 검증에 실패해도
        # Ticket은 ISSUED 상태로 남아 재시도(예: 새 Lease로 재발급 후)가 가능하다.
        lease = await self._lease_reader.get_current(conversation_id)
        if (
            lease is None
            or lease.realtime_session_id != realtime_session_id
            or lease.session_epoch != session_epoch
        ):
            return self._reject(
                TicketVerificationFailureCode.LEASE_MISMATCH,
                kid=kid,
                conversation_id=conversation_id,
            )

        # Ticket 원자적 소비 (마지막 단계). peek 이후 경쟁 상태로 다른 요청이 먼저
        # 소비했을 가능성을 여기서 다시 확인한다.
        outcome = await self._ticket_store.consume(jti)
        if outcome.status is TicketConsumeStatus.NOT_FOUND:
            return self._reject(
                TicketVerificationFailureCode.NOT_FOUND, kid=kid, conversation_id=conversation_id
            )
        if outcome.status is TicketConsumeStatus.ALREADY_CONSUMED:
            return self._reject(
                TicketVerificationFailureCode.ALREADY_CONSUMED,
                kid=kid,
                conversation_id=conversation_id,
            )

        # 방어적 교차검증: 소비된 Redis 레코드가 서명된 claim과 반드시 같아야 한다.
        # 정상 발급 경로에서는 항상 같지만(Spring이 같은 지역 변수로 둘 다 기록),
        # 신뢰 경계를 claim에 두고 Redis 레코드는 그 claim과 일치할 때만 신뢰한다.
        if (
            outcome.conversation_id != conversation_id
            or outcome.realtime_session_id != realtime_session_id
            or outcome.session_epoch != session_epoch
        ):
            return self._reject(
                TicketVerificationFailureCode.MALFORMED_TICKET,
                kid=kid,
                conversation_id=conversation_id,
            )

        self._epoch_registry.register(conversation_id, session_epoch)
        self._logger.info(
            "realtime ticket consumed",
            extra={
                "conversation_id": conversation_id,
                "realtime_session_id": realtime_session_id,
                "session_epoch": session_epoch,
            },
        )
        return TicketVerificationResult.success(
            conversation_id, realtime_session_id, session_epoch
        )

    def _reject(
        self,
        code: TicketVerificationFailureCode,
        *,
        kid: str | None = None,
        conversation_id: str | None = None,
    ) -> TicketVerificationResult:
        self._logger.warning(
            "realtime ticket rejected",
            extra={
                "failure_code": code.value,
                "kid": kid,
                "conversation_id": conversation_id,
            },
        )
        return TicketVerificationResult.failure(code)
