"""Internal REST route for stateless text dialogue turns."""

from fastapi import APIRouter, HTTPException, Request, status

from app.dialogue.pipeline import (
    DialoguePipelineError,
    DialogueTurnPipeline,
    DialogueTurnRequest,
    DialogueTurnResponse,
)


router = APIRouter(prefix="/internal/v1/dialogue", tags=["Internal Dialogue"])


@router.post(
    "/turns",
    response_model=DialogueTurnResponse,
    response_model_by_alias=True,
)
async def process_dialogue_turn(
    payload: DialogueTurnRequest,
    request: Request,
) -> DialogueTurnResponse:
    """Process one complete child text turn for the Spring backend."""

    pipeline: DialogueTurnPipeline = request.app.state.dialogue_pipeline
    try:
        return await pipeline.process(payload)
    except DialoguePipelineError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"error": error.code.value},
        ) from None
