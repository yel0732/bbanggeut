"""Tests for Realtime Ticket verification and atomic consumption."""

import asyncio
import logging
import time

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.realtime.jwks_cache import JwksKeyNotFoundError
from app.realtime.ticket_verification import (
    LeaseSnapshot,
    RealtimeTicketVerifier,
    TicketConsumeOutcome,
    TicketConsumeStatus,
    TicketVerificationFailureCode,
    TicketVerificationResult,
)

ISSUER = "spring-backend"
AUDIENCE = "ai-realtime"


class FakeJwksCache:
    def __init__(self, keys: dict[str, rsa.RSAPublicKey]) -> None:
        self._keys = keys

    async def get_signing_key(self, kid: str) -> rsa.RSAPublicKey:
        if kid not in self._keys:
            raise JwksKeyNotFoundError(kid)
        return self._keys[kid]


class FakeTicketConsumptionStore:
    def __init__(self, records: dict[str, dict] | None = None) -> None:
        self._records = dict(records or {})
        self._lock = asyncio.Lock()
        self.calls: list[str] = []

    async def consume(self, jti: str) -> TicketConsumeOutcome:
        self.calls.append(jti)
        async with self._lock:
            record = self._records.get(jti)
            if record is None:
                return TicketConsumeOutcome(TicketConsumeStatus.NOT_FOUND, None, None, None)
            if record["state"] != "ISSUED":
                return TicketConsumeOutcome(
                    TicketConsumeStatus.ALREADY_CONSUMED, None, None, None
                )
            record["state"] = "CONSUMED"
            return TicketConsumeOutcome(
                TicketConsumeStatus.CONSUMED,
                record["conversationId"],
                record["realtimeSessionId"],
                record["sessionEpoch"],
            )


class FakeSessionLeaseReader:
    def __init__(self, leases: dict[str, LeaseSnapshot] | None = None) -> None:
        self._leases = dict(leases or {})

    async def get_current(self, conversation_id: str) -> LeaseSnapshot | None:
        return self._leases.get(conversation_id)


def _generate_key_pair():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private_key, private_key.public_key()


def _issue_ticket(
    private_key,
    kid: str | None,
    *,
    conversation_id: str = "conv_1",
    realtime_session_id: str = "rt_1",
    session_epoch: int = 1,
    jti: str | None = "ticket_1",
    scope=("realtime:connect",),
    issuer: str = ISSUER,
    audience: str = AUDIENCE,
    expires_delta_seconds: int = 300,
) -> str:
    now = int(time.time())
    payload = {
        "iss": issuer,
        "aud": audience,
        "conversationId": conversation_id,
        "realtimeSessionId": realtime_session_id,
        "sessionEpoch": session_epoch,
        "scope": list(scope),
        "iat": now,
        "exp": now + expires_delta_seconds,
    }
    if jti is not None:
        payload["jti"] = jti
    headers = {"kid": kid} if kid else {}
    return jwt.encode(payload, private_key, algorithm="RS256", headers=headers)


def _build_verifier(public_key, *, ticket_store=None, lease_reader=None):
    jwks_cache = FakeJwksCache({"kid-1": public_key})
    ticket_store = ticket_store if ticket_store is not None else FakeTicketConsumptionStore()
    lease_reader = lease_reader if lease_reader is not None else FakeSessionLeaseReader()
    logger = logging.getLogger("test.realtime.ticket_verification")
    return RealtimeTicketVerifier(jwks_cache, ticket_store, lease_reader, ISSUER, AUDIENCE, logger)


def _issued_ticket_store() -> FakeTicketConsumptionStore:
    return FakeTicketConsumptionStore(
        {
            "ticket_1": {
                "state": "ISSUED",
                "conversationId": "conv_1",
                "realtimeSessionId": "rt_1",
                "sessionEpoch": 1,
            }
        }
    )


@pytest.mark.asyncio
async def test_valid_ticket_is_verified_and_consumed() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1")
    ticket_store = _issued_ticket_store()
    lease_reader = FakeSessionLeaseReader({"conv_1": LeaseSnapshot("rt_1", 1)})
    verifier = _build_verifier(public_key, ticket_store=ticket_store, lease_reader=lease_reader)

    result = await verifier.verify_and_consume(ticket)

    assert result.is_valid
    assert result.conversation_id == "conv_1"
    assert result.realtime_session_id == "rt_1"
    assert result.session_epoch == 1
    assert result.failure_code is None
    assert ticket_store.calls == ["ticket_1"]


@pytest.mark.asyncio
async def test_wrong_signature_is_rejected() -> None:
    _, public_key = _generate_key_pair()
    other_private_key, _ = _generate_key_pair()
    ticket = _issue_ticket(other_private_key, "kid-1")
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.INVALID_SIGNATURE


@pytest.mark.asyncio
async def test_expired_ticket_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1", expires_delta_seconds=-10)
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.EXPIRED


@pytest.mark.asyncio
async def test_wrong_issuer_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1", issuer="someone-else")
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.WRONG_ISSUER


@pytest.mark.asyncio
async def test_wrong_audience_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1", audience="someone-else")
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.WRONG_AUDIENCE


@pytest.mark.asyncio
async def test_missing_scope_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1", scope=())
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.MISSING_SCOPE


@pytest.mark.asyncio
async def test_unknown_kid_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-unknown")
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.UNKNOWN_KEY


@pytest.mark.asyncio
async def test_missing_kid_header_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, None)
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.MALFORMED_TICKET


@pytest.mark.asyncio
async def test_missing_jti_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1", jti=None)
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.MALFORMED_TICKET


@pytest.mark.asyncio
async def test_malformed_ticket_string_is_rejected() -> None:
    _, public_key = _generate_key_pair()
    verifier = _build_verifier(public_key)

    result = await verifier.verify_and_consume("not-a-jwt")

    assert result.failure_code == TicketVerificationFailureCode.MALFORMED_TICKET


@pytest.mark.asyncio
async def test_already_consumed_ticket_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1")
    ticket_store = FakeTicketConsumptionStore(
        {
            "ticket_1": {
                "state": "CONSUMED",
                "conversationId": "conv_1",
                "realtimeSessionId": "rt_1",
                "sessionEpoch": 1,
            }
        }
    )
    verifier = _build_verifier(public_key, ticket_store=ticket_store)

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.ALREADY_CONSUMED


@pytest.mark.asyncio
async def test_ticket_missing_from_redis_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1")
    verifier = _build_verifier(public_key, ticket_store=FakeTicketConsumptionStore())

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.NOT_FOUND


@pytest.mark.asyncio
async def test_missing_lease_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1")
    verifier = _build_verifier(
        public_key, ticket_store=_issued_ticket_store(), lease_reader=FakeSessionLeaseReader()
    )

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.LEASE_MISMATCH


@pytest.mark.asyncio
async def test_lease_epoch_mismatch_is_rejected() -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1")
    lease_reader = FakeSessionLeaseReader({"conv_1": LeaseSnapshot("rt_1", 2)})
    verifier = _build_verifier(
        public_key, ticket_store=_issued_ticket_store(), lease_reader=lease_reader
    )

    result = await verifier.verify_and_consume(ticket)

    assert result.failure_code == TicketVerificationFailureCode.LEASE_MISMATCH


@pytest.mark.asyncio
async def test_two_concurrent_consumptions_only_one_succeeds() -> None:
    ticket_store = _issued_ticket_store()

    results = await asyncio.gather(
        ticket_store.consume("ticket_1"),
        ticket_store.consume("ticket_1"),
    )

    statuses = sorted(result.status for result in results)
    assert statuses == sorted(
        [TicketConsumeStatus.CONSUMED, TicketConsumeStatus.ALREADY_CONSUMED]
    )


def test_consumed_outcome_requires_ticket_fields() -> None:
    with pytest.raises(ValueError):
        TicketConsumeOutcome(TicketConsumeStatus.CONSUMED, None, None, None)


def test_non_consumed_outcome_rejects_ticket_fields() -> None:
    with pytest.raises(ValueError):
        TicketConsumeOutcome(TicketConsumeStatus.NOT_FOUND, "conv_1", "rt_1", 1)


def test_verification_result_success_and_failure_factories() -> None:
    success = TicketVerificationResult.success("conv_1", "rt_1", 1)
    assert success.is_valid

    failure = TicketVerificationResult.failure(TicketVerificationFailureCode.EXPIRED)
    assert not failure.is_valid


def test_verification_result_rejects_inconsistent_fields() -> None:
    with pytest.raises(ValueError):
        TicketVerificationResult(None, None, None, None)
    with pytest.raises(ValueError):
        TicketVerificationResult(
            "conv_1", "rt_1", 1, TicketVerificationFailureCode.EXPIRED
        )


@pytest.mark.asyncio
async def test_logs_only_safe_metadata(caplog: pytest.LogCaptureFixture) -> None:
    private_key, public_key = _generate_key_pair()
    ticket = _issue_ticket(private_key, "kid-1", expires_delta_seconds=-10)
    verifier = _build_verifier(public_key)

    with caplog.at_level(
        logging.WARNING, logger="test.realtime.ticket_verification"
    ):
        await verifier.verify_and_consume(ticket)

    rendered = " ".join(repr(vars(record)) for record in caplog.records)
    assert ticket not in rendered
    assert "conv_1" not in rendered
    assert any(
        record.failure_code == TicketVerificationFailureCode.EXPIRED.value
        for record in caplog.records
    )
