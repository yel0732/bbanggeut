"""Dialogue manager package."""

from app.dialogue.parser import (
    SlotParseError,
    SlotParseErrorCode,
    SlotValidationIssue,
    parse_diary_slots,
)
from app.dialogue.question_prompts import (
    QUESTION_TARGET_GUIDANCE,
    DiaryQuestionGenerationRequest,
    QuestionGenerationPrompt,
    build_question_generation_prompt,
)
from app.dialogue.prompts import (
    SlotExtractionPrompt,
    build_slot_extraction_prompt,
    build_slot_repair_instruction,
    diary_slots_json_schema,
)
from app.dialogue.schemas import (
    DiaryAnswerAnalysisRequest,
    DiarySlots,
    GameContext,
)
from app.dialogue.slot_merger import merge_diary_slots
from app.dialogue.target_selector import (
    QUESTION_TARGET_PRIORITY,
    QuestionTarget,
    select_next_question_target,
)

__all__ = [
    "QUESTION_TARGET_GUIDANCE",
    "QUESTION_TARGET_PRIORITY",
    "DiaryAnswerAnalysisRequest",
    "DiaryQuestionGenerationRequest",
    "DiarySlots",
    "GameContext",
    "QuestionGenerationPrompt",
    "QuestionTarget",
    "SlotExtractionPrompt",
    "SlotParseError",
    "SlotParseErrorCode",
    "SlotValidationIssue",
    "build_question_generation_prompt",
    "build_slot_extraction_prompt",
    "build_slot_repair_instruction",
    "diary_slots_json_schema",
    "merge_diary_slots",
    "parse_diary_slots",
    "select_next_question_target",
]

