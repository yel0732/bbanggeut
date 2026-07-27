"""Public ``rtc.error.code`` values for Signaling-layer failures.

``TicketVerificationFailureCode`` (app/realtime/ticket_verification.py) is an
internal verification result — never sent to the client directly. This module
maps it (and the Signaling protocol's own failure cases) onto the public codes
recorded in docs/diary/FUNCTIONAL_SPEC.md §22 / API_CONTRACT.md §9
(jira/plan/DIARY-2-W03.md §11-C).
"""

from __future__ import annotations

from app.realtime.ticket_verification import TicketVerificationFailureCode

SIGNALING_MESSAGE_INVALID = "SIGNALING_MESSAGE_INVALID"
WEBRTC_CONNECTION_FAILED = "WEBRTC_CONNECTION_FAILED"

_TICKET_FAILURE_CODES: dict[TicketVerificationFailureCode, str] = {
    TicketVerificationFailureCode.MALFORMED_TICKET: "REALTIME_TICKET_MALFORMED",
    TicketVerificationFailureCode.UNKNOWN_KEY: "REALTIME_TICKET_UNKNOWN_KEY",
    TicketVerificationFailureCode.INVALID_SIGNATURE: "REALTIME_TICKET_INVALID_SIGNATURE",
    TicketVerificationFailureCode.EXPIRED: "REALTIME_TICKET_EXPIRED",
    TicketVerificationFailureCode.WRONG_ISSUER: "REALTIME_TICKET_WRONG_ISSUER",
    TicketVerificationFailureCode.WRONG_AUDIENCE: "REALTIME_TICKET_WRONG_AUDIENCE",
    TicketVerificationFailureCode.MISSING_SCOPE: "REALTIME_TICKET_MISSING_SCOPE",
    TicketVerificationFailureCode.NOT_FOUND: "REALTIME_TICKET_NOT_FOUND",
    TicketVerificationFailureCode.ALREADY_CONSUMED: "REALTIME_TICKET_USED",
    TicketVerificationFailureCode.LEASE_MISMATCH: "REALTIME_SESSION_LEASE_MISMATCH",
}


def map_ticket_failure_code(code: TicketVerificationFailureCode) -> str:
    """Map an internal verification failure to its public ``rtc.error.code``."""

    return _TICKET_FAILURE_CODES[code]
