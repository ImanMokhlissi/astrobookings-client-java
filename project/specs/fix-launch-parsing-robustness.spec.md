# Launch parsing robustness

- **Type**: fix
- **Status**: draft
- **Related**: FR1, TR2, TR9
- **Depends on**: feat-launch-listing

## Problem

- As a customer, I want a value of `ASTROBOOKINGS_API_URL` with a query or fragment to be rejected at startup, so that I can correct it instead of getting a generic error when the call misses `/activities`.
- As a customer, I want a launch with a fractional duration to be listed, so that one legitimate value does not make the whole list fail.
- As a customer, I want a response that stalls after its headers to count as a timeout, so that the client does not risk waiting forever and I get back to the menu.

## Solution

- **cli**: Show a duration without rounding, and with no decimals when it is a whole number. The invalid URL error stays as it is and also covers the new rejected values.
- **api**: Read a fractional `duration` instead of failing the list, leaving the handling of `minParticipants` and `maxParticipants` as it is. Make a response that stalls after its headers end as API not reachable (ADR-10), whatever the cause of the stall turns out to be. Replace the existing `ApiClientTest` case that expects a fractional duration to fail with the generic error.
- **model**: Let `duration` hold a fractional value in `ActivityResponse` and `Launch`.
- **Main**: Treat a URL with a query or fragment as invalid (ADR-4), including an empty one.

## Acceptance criteria

- **AC1**: IF `ASTROBOOKINGS_API_URL` has a query, even an empty one, THEN the system SHALL show the invalid URL error without a stack trace and exit with code 1.
- **AC2**: IF `ASTROBOOKINGS_API_URL` has a fragment, even an empty one, THEN the system SHALL show the invalid URL error without a stack trace and exit with code 1.
- **AC3**: WHEN the user selects the option to list launches and `ASTROBOOKINGS_API_URL` has no query and no fragment, with or without a path or a trailing slash, the system SHALL send the call to `/activities` under that URL.
- **AC4**: WHEN the user selects the option to list launches and a `published` or `confirmed` launch has a fractional duration, the system SHALL list that launch and show its duration as the API sends it, with no rounding, such as `Duration: 1.5`.
- **AC5**: WHEN the system shows a launch whose duration is a whole number, the system SHALL show it with no decimals, such as `Duration: 90`.
- **AC6**: IF any launch returned by `GET /activities` has a fractional duration, whatever its status, THEN the system SHALL still show every other `published` or `confirmed` launch.
- **AC7**: IF a launch returned by `GET /activities` has a `duration` that is missing, null or not numeric, THEN the system SHALL show the generic API error and return to the menu.
- **AC8**: IF the API sends the response headers and then stops sending the body before it is complete, whether it sent part of the body or none, THEN the system SHALL stop waiting no later than one request timeout after the last data it received.
- **AC9**: IF the system stops waiting for a response that stalled after its headers, THEN the system SHALL show `API not reachable at <url>`, with the configured URL, instead of a generic error or a stack trace.
- **AC10**: WHEN the system has shown `API not reachable at <url>` for a response that stalled after its headers, the system SHALL return to the menu.
- **AC11**: IF the body pauses after the headers but the whole response arrives within one request timeout of the start of the call, THEN the system SHALL list the launches as usual.

## Out of scope

- Fractional `minParticipants` and `maxParticipants`. The same setting makes them fail the whole list with the generic error, and this spec leaves that unchanged.
- The duration in booking data (`feat-booking-creation`, `feat-booking-retrieval`), which has no code yet.
- Listing the other launches when a launch is malformed in any way other than a fractional duration.
- Any required outcome for a body that keeps delivering data slowly without stalling.
- Changing the length of the request timeout.
- Rejecting any other form of URL, such as one with a path or with user information.
- Showing a unit or any other format for the duration.
