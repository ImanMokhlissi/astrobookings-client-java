# Booking creation

- **Type**: feat
- **Status**: verified
- **Related**: FR6, FR7, TR6
- **Depends on**: chore-build-setup, feat-launch-listing, feat-session

## Problem

- As a customer, I want to book a chosen number of seats on a published or confirmed launch, so that I secure my place on it.
- As a customer, I want to see the available and requested numbers when I ask for more seats than are free, so that I can decide how many to request instead.
- As a customer, I want to be told clearly when my payment is rejected, so that I know no booking was created.

## Solution

- **cli**: Add a booking option to the menu. It shows the launches offered by `service` as a list numbered from 1 and asks for a launch's number and a number of seats, telling the user at each prompt that 0 cancels. When `service` offers no launches, it shows "No launches open for booking." and returns to the menu without asking for anything. The user never types a launch id: `cli` resolves the number to the launch in the displayed list. On a launch entry that is not 0 and not a number in that list, it asks for the launch number again. On a seats entry that is not 0 and not a whole number of at least 1, it asks for the seats again. On 0 at either prompt, it cancels and returns to the menu. None of these entries sends a request to the API. It shows the confirmation using the launch the user chose, because the created booking has no launch data. It shows the capacity and payment errors using the API's message as it is, and states that no booking was created after a 402.
- **service**: Add the booking use case. It gets a fresh list of launches, applies the `published` and `confirmed` allow-list before each booking, reads the token from `session` and passes it to `api`. A 401 on this call goes through the rejected-session handling from feat-session.
- **api**: Add the `POST /bookings` call with the `Authorization: Bearer <token>` header. Translate the launch id to `activityId` and map the 201 response to a Booking. Capacity errors and the 402 map to an API error that carries the status and message, with no parsing of the message.
- **model**: Add the Booking and LaunchSummary records and the CreateBookingRequest, BookingResponse and BookingActivityResponse DTOs defined in the ADD. This spec owns them, and feat-booking-retrieval reuses the Booking and LaunchSummary records and the BookingResponse and BookingActivityResponse DTOs.

## Acceptance criteria

- **AC1**: WHEN the user with a saved session selects the booking option, the system SHALL fetch the launches from the API and offer only those in status `published` or `confirmed`, as a numbered list.
- **AC2**: IF the booking use case receives a launch whose status is not `published` or `confirmed`, THEN the system SHALL refuse the booking without sending a request to the API.
- **AC3**: WHEN the user chooses an offered launch by its number in the displayed list and enters a valid number of seats, the system SHALL send `POST /bookings` with that launch's id as `activityId` and the seats as `participants`.
- **AC4**: WHEN the system sends `POST /bookings`, the system SHALL include the saved token in an `Authorization: Bearer <token>` header.
- **AC5**: WHEN the API responds 201 to `POST /bookings`, the system SHALL show a confirmation with the booking id, the chosen launch's name, the number of seats and the `paymentStatus`.
- **AC6**: IF the launch entry is not 0 and not a number in the displayed list, THEN the system SHALL ask for the launch number again and send no request to the API.
- **AC7**: IF the API responds 400 with "Capacity exceeded. Available: N, Requested: M", THEN the system SHALL show that message unchanged, so that the user sees the available and requested numbers.
- **AC8**: IF the API responds 402 "Payment could not be processed" to `POST /bookings`, THEN the system SHALL show that message and state that no booking was created.
- **AC9**: IF the user has no saved session when selecting the booking option, THEN the system SHALL tell the user to register or log in and send no booking request to the API.
- **AC10**: IF the seats entry is not 0 and not a whole number of at least 1, THEN the system SHALL ask for the seats again and send no request to the API.
- **AC11**: WHEN the user enters 0 at the launch number prompt or the seats prompt, the system SHALL cancel and return to the menu without sending a booking request to the API.
- **AC12**: WHEN the user with a saved session selects the booking option and there are no `published` or `confirmed` launches, the system SHALL show "No launches open for booking." and return to the menu.

## Out of scope

- The menu, the launch list and the generic display of API errors and of an unreachable API (feat-launch-listing).
- Registering, logging in, saving the session and handling a session rejected with a 401 (feat-session).
- Listing bookings and viewing a single booking (feat-booking-retrieval).
- Showing free seats or checking them before booking, because the API does not expose them.
- Predicting the payment gateway's rule or warning about it before booking.
- Cancelling bookings and real payments.
