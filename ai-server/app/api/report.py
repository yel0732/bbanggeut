"""Internal REST route for daily report narrative generation."""

from fastapi import APIRouter, HTTPException, Request, status

from app.report.generator import DailyReportGenerator, ReportGenerationError
from app.report.schemas import DailyReportSummaryRequest, DailyReportSummaryResponse


router = APIRouter(prefix="/internal/v1/reports", tags=["Internal Reports"])


@router.post(
    "/daily-summary",
    response_model=DailyReportSummaryResponse,
    response_model_by_alias=True,
)
async def generate_daily_summary(
    payload: DailyReportSummaryRequest,
    request: Request,
) -> DailyReportSummaryResponse:
    generator: DailyReportGenerator = request.app.state.report_generator
    try:
        return await generator.generate(payload)
    except (ReportGenerationError, ValueError):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"error": "REPORT_GENERATION_UNAVAILABLE"},
        ) from None
