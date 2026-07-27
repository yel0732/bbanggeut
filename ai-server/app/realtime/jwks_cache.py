"""Async, self-healing cache for the Spring-issued RSA public keys (JWKS).

PyJWT's built-in ``PyJWKClient`` fetches JWKS with blocking I/O, which would
stall the event loop. This module fetches with ``httpx.AsyncClient`` instead,
matching the async style already used for GMS calls.
"""

from __future__ import annotations

import base64
from collections.abc import Mapping

import httpx
from cryptography.hazmat.primitives.asymmetric import rsa


class JwksFetchError(Exception):
    """The Spring JWKS endpoint could not be reached or parsed."""


class JwksKeyNotFoundError(Exception):
    """No key matching the requested ``kid`` exists, even after a refresh."""

    def __init__(self, kid: str) -> None:
        super().__init__("jwks key not found")
        self.kid = kid


class RealtimeJwksCache:
    """Fetches and caches RSA public keys by ``kid`` from Spring's JWKS endpoint."""

    def __init__(self, http_client: httpx.AsyncClient, jwks_url: str) -> None:
        self._http_client = http_client
        self._jwks_url = jwks_url
        self._keys_by_kid: dict[str, rsa.RSAPublicKey] = {}

    async def get_signing_key(self, kid: str) -> rsa.RSAPublicKey:
        """Return the public key for ``kid``, refreshing the cache on a miss."""

        if kid in self._keys_by_kid:
            return self._keys_by_kid[kid]
        await self._refresh()
        if kid not in self._keys_by_kid:
            raise JwksKeyNotFoundError(kid)
        return self._keys_by_kid[kid]

    async def _refresh(self) -> None:
        try:
            response = await self._http_client.get(self._jwks_url)
            response.raise_for_status()
            payload = response.json()
        except httpx.HTTPError as error:
            raise JwksFetchError("failed to fetch JWKS") from error

        keys: dict[str, rsa.RSAPublicKey] = {}
        for entry in payload.get("keys", []):
            kid = entry.get("kid")
            if not kid:
                continue
            keys[kid] = _to_rsa_public_key(entry)
        self._keys_by_kid = keys


def _to_rsa_public_key(jwk: Mapping[str, str]) -> rsa.RSAPublicKey:
    n = _decode_unsigned_int(jwk["n"])
    e = _decode_unsigned_int(jwk["e"])
    return rsa.RSAPublicNumbers(e, n).public_key()


def _decode_unsigned_int(value: str) -> int:
    padded = value + "=" * (-len(value) % 4)
    return int.from_bytes(base64.urlsafe_b64decode(padded), byteorder="big")
