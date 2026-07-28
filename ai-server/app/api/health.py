"""Process liveness and dependency readiness endpoints."""

import asyncio

from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse
from redis.exceptions import RedisError

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/live")
async def live() -> dict[str, str]:
    """Report that the API process can serve requests."""

    return {"status": "up"}


@router.get("/ready")
async def ready(request: Request) -> JSONResponse:
    """Report readiness only when the required Redis connection works."""

    try:
        async with asyncio.timeout(1.0):
            redis_available = await request.app.state.redis_client.ping()
    except (RedisError, OSError, asyncio.TimeoutError):
        redis_available = False

    if not redis_available:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"status": "not_ready", "checks": {"redis": "down"}},
        )

    return JSONResponse(
        status_code=status.HTTP_200_OK,
        content={"status": "ready", "checks": {"redis": "up"}},
    )
