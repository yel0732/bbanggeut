"""Internal REST endpoint for one conversation-scoped child diary."""

from fastapi import APIRouter, HTTPException, Request, status

from app.child_diary.generator import (
    ChildDiaryGenerationError,
    ChildDiaryGenerator,
)
from app.child_diary.schemas import (
    ChildDiarySummaryRequest,
    ChildDiarySummaryResponse,
)


router = APIRouter(
    prefix="/internal/v1/child-diaries",
    tags=["Internal Child Diaries"],
)


@router.post(
    "/summary",
    response_model=ChildDiarySummaryResponse,
    response_model_by_alias=True,
)
async def generate_child_diary_summary(
    payload: ChildDiarySummaryRequest,
    request: Request,
) -> ChildDiarySummaryResponse:
    generator: ChildDiaryGenerator = request.app.state.child_diary_generator
    try:
        return await generator.generate(payload)
    except ChildDiaryGenerationError:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"error": "CHILD_DIARY_GENERATION_UNAVAILABLE"},
        ) from None
