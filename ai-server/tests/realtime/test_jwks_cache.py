"""Tests for the async JWKS cache."""

import base64

import httpx
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.realtime.jwks_cache import (
    JwksFetchError,
    JwksKeyNotFoundError,
    RealtimeJwksCache,
)

JWKS_URL = "http://spring.internal:8080/internal/v1/realtime/jwks"


def _encode_unsigned(value: int) -> str:
    length = (value.bit_length() + 7) // 8 or 1
    return base64.urlsafe_b64encode(value.to_bytes(length, "big")).rstrip(b"=").decode()


def _generate_public_numbers() -> rsa.RSAPublicNumbers:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private_key.public_key().public_numbers()


def _jwk_body(kid: str, numbers: rsa.RSAPublicNumbers) -> dict:
    return {
        "kty": "RSA",
        "use": "sig",
        "alg": "RS256",
        "kid": kid,
        "n": _encode_unsigned(numbers.n),
        "e": _encode_unsigned(numbers.e),
    }


def _client(handler) -> httpx.AsyncClient:
    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


@pytest.mark.asyncio
async def test_fetches_and_caches_key_by_kid() -> None:
    numbers = _generate_public_numbers()
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        return httpx.Response(200, json={"keys": [_jwk_body("kid-1", numbers)]})

    cache = RealtimeJwksCache(_client(handler), JWKS_URL)

    first = await cache.get_signing_key("kid-1")
    second = await cache.get_signing_key("kid-1")

    assert first.public_numbers().n == numbers.n
    assert first.public_numbers().e == numbers.e
    assert second is first
    assert call_count == 1


@pytest.mark.asyncio
async def test_refetches_on_unknown_kid_then_finds_rotated_key() -> None:
    numbers = _generate_public_numbers()
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        # kid changes on the second fetch, simulating a Spring key rotation.
        kid = "kid-old" if call_count == 1 else "kid-new"
        return httpx.Response(200, json={"keys": [_jwk_body(kid, numbers)]})

    cache = RealtimeJwksCache(_client(handler), JWKS_URL)

    await cache.get_signing_key("kid-old")
    key = await cache.get_signing_key("kid-new")

    assert key.public_numbers().n == numbers.n
    assert call_count == 2


@pytest.mark.asyncio
async def test_raises_not_found_when_kid_missing_after_refresh() -> None:
    numbers = _generate_public_numbers()

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"keys": [_jwk_body("kid-1", numbers)]})

    cache = RealtimeJwksCache(_client(handler), JWKS_URL)

    with pytest.raises(JwksKeyNotFoundError) as excinfo:
        await cache.get_signing_key("kid-unknown")
    assert excinfo.value.kid == "kid-unknown"


@pytest.mark.asyncio
async def test_wraps_http_failure_as_fetch_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503)

    cache = RealtimeJwksCache(_client(handler), JWKS_URL)

    with pytest.raises(JwksFetchError):
        await cache.get_signing_key("kid-1")


@pytest.mark.asyncio
async def test_ignores_entries_without_kid() -> None:
    numbers = _generate_public_numbers()

    def handler(request: httpx.Request) -> httpx.Response:
        entry_without_kid = _jwk_body("kid-1", numbers)
        entry_without_kid["kid"] = ""
        return httpx.Response(200, json={"keys": [entry_without_kid]})

    cache = RealtimeJwksCache(_client(handler), JWKS_URL)

    with pytest.raises(JwksKeyNotFoundError):
        await cache.get_signing_key("kid-1")
