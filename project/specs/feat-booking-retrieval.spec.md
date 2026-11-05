# Booking retrieval

- **Type**: feat
- **Status**: draft
- **Related**: FR8, FR9
- **Depends on**: chore-build-setup, feat-launch-listing, feat-session

## Problem

- As a customer, I want to list my bookings with the `paymentStatus` of each one, so that I can check what I have booked and whether it is paid.
- As a customer, I want to view the details of a single booking, so that I can see its launch, seats and payment data in full.

## Solution

- **cli**: Adds two menu options, one to list the user's bookings and one to view a single booking by the id shown in that list. Presents the bookings using the client's own "launch" wording, and reuses the existing display of API errors, the API-down error and the rejected session flow.
- **service**: Adds the two use cases. Each reads the token from the saved session and passes it to `api`, and reports when there is no session. A 401 on either call means a rejected session, handled as `feat-session` defines (ADR-10, ADR-11).
- **api**: Adds the calls to `GET /bookings` and `GET /bookings/:id` with the `Authorization: Bearer <token>` header, and maps the responses to Booking and LaunchSummary. Failures map to the three shared failure types (ADR-10).
- **model**: Adds the Booking and LaunchSummary records and the BookingResponse and BookingActivityResponse DTOs, if they do not exist yet.

## Acceptance criteria

- **AC1**: WHEN the user with a saved session selects the option to list their bookings, the system SHALL show each booking the API returns with its id, launch name, number of seats and `paymentStatus`, the last one as the API sends it.
- **AC2**: IF the API returns no bookings for the user, THEN the system SHALL show a message saying that the user has no bookings.
- **AC3**: WHEN the user with a saved session selects the option to view a booking and enters its id, the system SHALL show its id, number of seats, creation date, `paymentId`, `paymentStatus` as the API sends it, and the launch name, price, date, duration, location and status.
- **AC4**: The system SHALL send the saved token in an `Authorization: Bearer <token>` header on every request to `GET /bookings` and `GET /bookings/:id`.
- **AC5**: IF the user selects either option with no saved session, THEN the system SHALL tell the user to register or log in first, without calling the API.
- **AC6**: IF the user enters a blank booking id, THEN the system SHALL ask for the id again without calling the API.
- **AC7**: IF the API responds 401 to `GET /bookings` or `GET /bookings/:id`, THEN the system SHALL delete the session file and ask the user to register again.
- **AC8**: IF the API responds to either request with an error other than 401, such as for a booking id that does not exist, THEN the system SHALL show the API's error in a readable form and return to the menu.
- **AC9**: IF the API is not reachable when the user selects either option, THEN the system SHALL show `API not reachable at <url>`, with the configured URL, instead of a stack trace.

## Out of scope

- Creating bookings, the capacity error and the 402 payment error (`feat-booking-creation`).
- Registering, logging in, loading the saved session and defining the rejected session flow (`feat-session`).
- The menu itself, the display of API errors and the API-down error (`feat-launch-listing`).
- Cancelling bookings, because the API does not allow it.
- Changing a booking's `paymentStatus`, which only comes from the API.
- Showing bookings made under an earlier registration, because the API ties bookings to the userId in the token (PRD known limitations).
