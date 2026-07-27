"""Opt-in integration test for main.py's realtime wiring against real Redis.

Verifies that ``create_app()`` assembles a working ``RealtimeTicketVerifier``
in ``app.state`` — real Redis for Ticket/Lease, a mocked JWKS endpoint (via
the dedicated ``realtime_http_transport``, kept separate from the GMS
``transport`` precisely so a GMS mock can never intercept JWKS requests) —
end to end. Skipped by default; mirrors tests/realtime/test_ticket_verification_redis.py's
opt-in gate (jira/plan/DIARY-2-W03.md §7).

The deeper WebRTC mechanics (real aiortc through the full SignalingConnection
state machine) are already covered without Redis by
tests/signaling/test_signaling_webrtc_integration.py — this test's job is
narrowly to prove the main.py assembly wiring itself is correct.
"""

import base64
import json
import os
import time

import httpx
import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.config import AppSettings
from app.main import create_app
from app.realtime.redis_client import create_redis_client

ISSUER = "spring-backend"
AUDIENCE = "ai-realtime"


def _skip_unless_enabled() -> None:
    if os.getenv("RUN_REDIS_INTEGRATION") != "1":
        pytest.skip("RUN_REDIS_INTEGRATION=1 and a local Redis instance are required")


def _b64url_uint(value: int) -> str:
    length = (value.bit_length() + 7) // 8
    return base64.urlsafe_b64encode(value.to_bytes(length, "big")).decode().rstrip("=")


def _jwks_transport(public_key: rsa.RSAPublicKey, kid: str) -> httpx.MockTransport:
    numbers = public_key.public_numbers()
    payload = {
        "keys": [
            {
                "kty": "RSA",
                "use": "sig",
                "alg": "RS256",
                "kid": kid,
                "n": _b64url_uint(numbers.n),
                "e": _b64url_uint(numbers.e),
            }
        ]
    }

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=payload)

    return httpx.MockTransport(handler)


def _issue_ticket(private_key, kid: str, *, conversation_id: str, realtime_session_id: str, session_epoch: int) -> str:
    now = int(time.time())
    payload = {
        "iss": ISSUER,
        "aud": AUDIENCE,
        "scope": ["realtime:connect"],
        "iat": now,
        "exp": now + 300,
        "conversationId": conversation_id,
        "realtimeSessionId": realtime_session_id,
        "sessionEpoch": session_epoch,
        "jti": "ticket_main_wiring_1",
    }
    return jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": kid})


@pytest.mark.redis
@pytest.mark.asyncio
async def test_app_wired_ticket_verifier_succeeds_against_real_redis() -> None:
    _skip_unless_enabled()

    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    kid = "main-wiring-test-kid"
    conversation_id = "conv_main_wiring"
    realtime_session_id = "rt_main_wiring"
    session_epoch = 1
    ticket = _issue_ticket(
        private_key,
        kid,
        conversation_id=conversation_id,
        realtime_session_id=realtime_session_id,
        session_epoch=session_epoch,
    )

    redis_client = create_redis_client(
        host=os.getenv("REALTIME_REDIS_HOST", "127.0.0.1"),
        port=int(os.getenv("REALTIME_REDIS_PORT", "6379")),
    )
    ticket_key = "realtime:ticket:ticket_main_wiring_1"
    lease_key = f"realtime:session:{conversation_id}"
    try:
        await redis_client.set(
            ticket_key,
            json.dumps(
                {
                    "conversationId": conversation_id,
                    "realtimeSessionId": realtime_session_id,
                    "sessionEpoch": session_epoch,
                    "state": "ISSUED",
                }
            ),
            ex=30,
        )
        await redis_client.set(
            lease_key,
            json.dumps(
                {
                    "realtimeSessionId": realtime_session_id,
                    "sessionEpoch": session_epoch,
                    "state": "ACTIVE",
                    "lastHeartbeatAt": int(time.time() * 1000),
                }
            ),
            ex=30,
        )

        app = create_app(
            AppSettings(gms_key="test-key"),
            realtime_http_transport=_jwks_transport(private_key.public_key(), kid),
        )
        async with app.router.lifespan_context(app):
            result = await app.state.ticket_verifier.verify_and_consume(ticket)

        assert result.is_valid
        assert result.conversation_id == conversation_id
        assert result.realtime_session_id == realtime_session_id
        assert result.session_epoch == session_epoch

        # 원자적 소비 확인: 같은 jti로 재사용은 실패해야 한다(REST 재발급 없이는 재시도 불가).
        assert (await redis_client.get(ticket_key)) is not None
        record = json.loads(await redis_client.get(ticket_key))
        assert record["state"] == "CONSUMED"
    finally:
        await redis_client.delete(ticket_key, lease_key)
        await redis_client.aclose()
