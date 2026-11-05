# Launch listing

- **Type**: feat
- **Status**: verified
- **Related**: FR1, TR2, TR3, TR4, TR7, TR9
- **Depends on**: chore-build-setup

## Problem

- As a customer, I want to list the launches open for booking with their details, so that I can decide which one interests me.
- As a customer, I want a clear message when the API is down or returns an error, so that I understand what happened instead of seeing a stack trace.

## Solution

- **cli**: Add the interactive console menu, in English, with input and output injected as streams (ADR-2), and options to list launches and to exit. Later specs add their options to this menu. Show the launch list, formatting the date for display, or the message "No launches open for booking." when no launch passes the filter. Show an error for an unknown menu option and return to the menu. End the client without a stack trace when the user selects the exit option or the input ends. Be the only layer that prints, including the invalid URL error. After showing an API failure, return to the menu.
- **service**: Add the use case that lists launches, keeping only those in the allow-list of `published` and `confirmed` (ADR-12).
- **api**: Add the HTTP client on `java.net.http.HttpClient`, using the configured URL, and the public call to `GET /activities`, reading the JSON with Jackson and translating activities into launches (ADR-3). Turn every failure into one of the three failure types (ADR-10): connection failures and timeouts into API not reachable, a 401 into unauthorized, and any other error response into an API error. A response that is not valid JSON becomes a generic API error, which `cli` shows before returning to the menu like any other API failure.
- **model**: Add the `Launch`, `ApiError` and `FieldError` records, the `ActivityResponse`, `ErrorResponse` and `FieldErrorResponse` DTOs, and the three failure types. Status and date stay as plain text, and other API fields are ignored without error (TR3).
- **Main**: Read `ASTROBOOKINGS_API_URL` once, using `http://localhost:3000` when it is unset or blank (ADR-4). When it is not a valid URL, have `cli` show a readable error without a stack trace and return exit code 1. Otherwise, wire the layers (ADR-7).

## Acceptance criteria

- **AC1**: WHEN the client starts, the system SHALL show a menu with an option to list launches and an option to exit.
- **AC2**: WHEN the user selects the option to list launches, the system SHALL show the name, price, date, duration, location, minimum participants, maximum participants and status of each launch returned by `GET /activities` whose status is `published` or `confirmed`, and SHALL leave every other launch out of the list.
- **AC3**: WHEN the user selects the option to list launches and the API returns no `published` or `confirmed` launch, the system SHALL show exactly "No launches open for booking."
- **AC4**: IF an API response holds a JSON field that is neither listed in the PRD constraints nor part of the API error format, THEN the system SHALL ignore that field without error.
- **AC5**: The system SHALL use the word "launch" and never "activity" in its own texts.
- **AC6**: The system SHALL send every HTTP call to the URL in `ASTROBOOKINGS_API_URL`, or to `http://localhost:3000` when that variable is unset or blank.
- **AC7**: IF the API cannot be reached or the call times out, THEN the system SHALL show `API not reachable at <url>`, with the configured URL, instead of a stack trace, and return to the menu.
- **AC8**: IF the API responds with an error in the format `{message, errors:[{field, message}]}`, THEN the system SHALL show the message verbatim, followed by the field and message of each field error, and return to the menu.
- **AC9**: IF the user enters an unknown menu option, THEN the system SHALL show an error and return to the menu.
- **AC10**: WHEN the user selects the exit option, the system SHALL end the client without a stack trace.
- **AC11**: WHEN the input ends, the system SHALL end the client without a stack trace.
- **AC12**: IF `ASTROBOOKINGS_API_URL` is not a valid URL, THEN the system SHALL show an error without a stack trace and exit with code 1.

## Out of scope

- Registering, logging in, logging out and saved sessions, including the meaning of a 401 (feat-session).
- Booking seats, capacity errors and the 402 payment error (feat-booking-creation).
- Viewing bookings (feat-booking-retrieval).
- Creating or editing launches.
- Showing rocket data or free seats, because the API does not expose them.
