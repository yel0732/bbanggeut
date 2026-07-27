"""Tests for the internal TicketVerificationFailureCode -> public rtc.error.code mapping."""

import pytest

from app.realtime.ticket_verification import TicketVerificationFailureCode
from app.signaling.error_mapping import (
    SIGNALING_MESSAGE_INVALID,
    WEBRTC_CONNECTION_FAILED,
    map_ticket_failure_code,
)


@pytest.mark.parametrize(
    ("code", "expected"),
    [
        (TicketVerificationFailureCode.MALFORMED_TICKET, "REALTIME_TICKET_MALFORMED"),
        (TicketVerificationFailureCode.UNKNOWN_KEY, "REALTIME_TICKET_UNKNOWN_KEY"),
        (TicketVerificationFailureCode.INVALID_SIGNATURE, "REALTIME_TICKET_INVALID_SIGNATURE"),
        (TicketVerificationFailureCode.EXPIRED, "REALTIME_TICKET_EXPIRED"),
        (TicketVerificationFailureCode.WRONG_ISSUER, "REALTIME_TICKET_WRONG_ISSUER"),
        (TicketVerificationFailureCode.WRONG_AUDIENCE, "REALTIME_TICKET_WRONG_AUDIENCE"),
        (TicketVerificationFailureCode.MISSING_SCOPE, "REALTIME_TICKET_MISSING_SCOPE"),
        (TicketVerificationFailureCode.NOT_FOUND, "REALTIME_TICKET_NOT_FOUND"),
        (TicketVerificationFailureCode.ALREADY_CONSUMED, "REALTIME_TICKET_USED"),
        (TicketVerificationFailureCode.LEASE_MISMATCH, "REALTIME_SESSION_LEASE_MISMATCH"),
    ],
)
def test_maps_each_failure_code_to_its_public_code(
    code: TicketVerificationFailureCode, expected: str
) -> None:
    assert map_ticket_failure_code(code) == expected


def test_every_failure_code_has_a_mapping() -> None:
    for code in TicketVerificationFailureCode:
        assert isinstance(map_ticket_failure_code(code), str)


def test_signaling_protocol_codes_are_distinct_from_ticket_codes() -> None:
    ticket_codes = {map_ticket_failure_code(code) for code in TicketVerificationFailureCode}
    assert SIGNALING_MESSAGE_INVALID not in ticket_codes
    assert WEBRTC_CONNECTION_FAILED not in ticket_codes
