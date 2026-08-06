"""Prometheus 스크레이프 엔드포인트.

process·platform 기본 컬렉터만 노출한다(prometheus_client의 기본 레지스트리).
대화 파이프라인 도메인 메트릭(barge-in, STT/TTS 지연)은 별도 Task에서 이
레지스트리에 등록한다. host 네트워크의 8000 포트에 열리지만 UFW가 외부를
막으므로 노출 수준은 기존 /health/*와 같다.
"""

from fastapi import APIRouter, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

router = APIRouter(tags=["metrics"])


@router.get("/metrics")
def metrics() -> Response:
    """Prometheus 텍스트 포맷으로 현재 레지스트리를 직렬화한다."""

    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
