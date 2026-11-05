# Booking retrieval

- **Type**: feat
- **Status**: draft
- **Related**: FR8, FR9, TR6
- **Depends on**: chore-build-setup, feat-launch-listing, feat-session, feat-booking-creation

## Problem

- As a customer, I want to list my bookings with the `paymentStatus` of each one, so that I can check what I have booked and whether it is paid.
- As a customer, I want to view the details of a single booking, so that I can see its launch, seats and payment data in full.

## Solution

- **cli**: Adds two menu options, one to list the user's bookings and one to view a single booking. Both get a fresh list from `service` and show it numbered from 1. The list option then returns to the menu. The view option asks for the number of a booking in that list, telling the user at the prompt that 0 cancels. It checks the entry against the list and passes the id of the chosen booking to `service`, so the user never types an id. An invalid entry makes it ask again, and 0 cancels the selection and returns to the menu, neither of them calling the API. When the list is empty, the view option asks for nothing. Presents the bookings using the client's own "launch" wording, and reuses the existing display of API errors, the API-down error and the rejected session flow.
- **service**: Adds the two use cases, listing the bookings and getting one booking by its id. Each reads the token from the saved session and passes it to `api`, and reports when there is no session. A 401 on either call means a rejected session, handled as `feat-session` defines (ADR-10, ADR-11).
- **api**: Adds the calls to `GET /bookings` and `GET /bookings/:id` with the `Authorization: Bearer <token>` header, and maps the responses to Booking and LaunchSummary. Failures map to the three shared failure types (ADR-10).
- **model**: Reuses the Booking and LaunchSummary records and the BookingResponse and BookingActivityResponse DTOs from `feat-booking-creation`, and adds no new ones.

## Acceptance criteria

- **AC1**: WHEN the user with a saved session selects the option to list their bookings or the option to view a booking, the system SHALL request `GET /bookings` and show each booking the API returns, numbered from 1, with its id, launch name, number of seats and `paymentStatus`, the last one as the API sends it.
- **AC2**: IF the API returns no bookings for the user, THEN the system SHALL show a message saying that the user has no bookings and return to the menu without asking for a booking number.
- **AC3**: WHEN the user who selected the option to view a booking enters the number of a booking in the displayed list, the system SHALL request `GET /bookings/:id` with that booking's id and show its id, number of seats, creation date, `paymentId`, `paymentStatus` as the API sends it, and the launch name, price, date, duration, location and status.
- **AC4**: The system SHALL send the saved token in an `Authorization: Bearer <token>` header on every request to `GET /bookings` and `GET /bookings/:id`.
- **AC5**: IF the user selects either option with no saved session, THEN the system SHALL tell the user to register or log in first, without calling the API.
- **AC6**: IF the user enters something that is not 0 and not a number in the displayed list, THEN the system SHALL ask for the number again without calling the API.
- **AC7**: IF the API responds 401 to `GET /bookings` or `GET /bookings/:id`, THEN the system SHALL delete the session file and ask the user to register again.
- **AC8**: IF the API responds to either request with an error other than 401, such as when the chosen booking no longer exists, THEN the system SHALL show the API's error in a readable form and return to the menu.
- **AC9**: IF the API is not reachable when the system sends `GET /bookings` or `GET /bookings/:id`, THEN the system SHALL show `API not reachable at <url>`, with the configured URL, instead of a stack trace.
- **AC10**: WHEN the user enters 0 at the booking number prompt, the system SHALL cancel the selection and return to the menu without calling `GET /bookings/:id`.

## Out of scope

- Creating bookings, the capacity error and the 402 payment error (`feat-booking-creation`).
- Registering, logging in, loading the saved session and defining the rejected session flow (`feat-session`).
- The menu itself, the display of API errors and the API-down error (`feat-launch-listing`).
- Choosing a booking by typing its id.
- Cancelling bookings, because the API does not allow it.
- Changing a booking's `paymentStatus`, which only comes from the API.
- Showing bookings made under an earlier registration, because the API ties bookings to the userId in the token (PRD known limitations).
