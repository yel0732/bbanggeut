"""Prometheus 스크레이프 엔드포인트 테스트."""

import httpx
import pytest

from app.config import AppSettings
from app.main import create_app


@pytest.mark.asyncio
async def test_metrics_endpoint_serves_prometheus_text() -> None:
    app = create_app(AppSettings(gms_key="test-key"))

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/metrics")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    # prometheus_client 기본 레지스트리의 플랫폼 컬렉터 — 어떤 OS에서든 존재한다.
    assert "python_info" in response.text
