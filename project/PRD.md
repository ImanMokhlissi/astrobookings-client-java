# AstroBookings Client: Product Requirements Document

## Overview

**Vision**: Customers of AstroBookings, a fictional space travel company, can browse rocket launches, book seats and check their bookings from a terminal.

**In scope**:

- Listing the launches open for booking, with the details the API provides.
- Registering a user and reusing the session across runs.
- Logging in, in case the API's broken login gets fixed.
- Logging out.
- Booking seats on launches in status `published` or `confirmed`.
- Viewing the user's bookings and their `paymentStatus`.

**Out of scope**:

- Creating or editing launches.
- Cancelling bookings, because the API does not allow it.
- Real payments. The `paymentStatus` value only comes from the API.
- Showing rocket data, because the API has no such field.
- Showing free seats before booking, because the API does not expose them.

## Functional requirements

| ID  | Description                                                                                      | Status      |
|-----|--------------------------------------------------------------------------------------------------|-------------|
| FR1 | The user can list published or confirmed launches with name, price, date, duration, location, participant limits and status. | done        |
| FR2 | Session management: the user can register (username, email, password, accepting the terms) or log in, and a login 401 is explained clearly with registration offered. | done        |
| FR3 | The user can start the client and continue with the saved session without registering again.     | done        |
| FR4 | Session management: the user can log out, which deletes `~/.astrobookings/session.json`.         | done        |
| FR5 | The user whose session the API rejects with a 401 is asked to register again.                    | done        |
| FR6 | The user can book a chosen number of seats on a published or confirmed launch.                   | done        |
| FR7 | The user who requests more seats than are free sees the available and requested numbers.         | done        |
| FR8 | The user can list their bookings and see the `paymentStatus` of each one.                        | done        |
| FR9 | The user can view the details of a single booking, including its `paymentStatus`.                | done        |

## Technical requirements

| ID  | Description                                                                                       | Status      |
|-----|---------------------------------------------------------------------------------------------------|-------------|
| TR1 | Console application in Java 21, built with Maven through the Maven Wrapper, with no heavy frameworks. | done        |
| TR2 | HTTP calls use `java.net.http.HttpClient` against the API URL configured with the environment variable `ASTROBOOKINGS_API_URL`, with `http://localhost:3000` as the default. | done        |
| TR3 | JSON is handled with Jackson, and API fields not listed in the constraints are ignored.           | done        |
| TR4 | The client says "launches" to the user and maps them internally to the API's "activities". Exception: API error messages are shown verbatim, so they may say 'activity' (see ADR-3). | done        |
| TR5 | The token is stored outside the repository in `~/.astrobookings/session.json` and read on startup. | done        |
| TR6 | Requests to protected endpoints (all /bookings calls) send `Authorization: Bearer <token>`.       | done        |
| TR7 | API errors in the format `{message, errors:[{field, message}]}` are shown to the user in a readable form. | done        |
| TR8 | Tests are written with JUnit 5 and run with `./mvnw test`.                                        | done        |
| TR9 | If the API is not running, the client shows `API not reachable at <url>`, with the configured URL, instead of a stack trace. | done        |

## Assumptions & constraints

**Assumptions**:

- The session file needs no special protection, because this is a training exercise that is not for production.

**Constraints**:

- The client depends on the activity-bookings-express API, which must be reachable at the configured URL (`ASTROBOOKINGS_API_URL`), by default `http://localhost:3000`.
- The console interface is in English.
- `GET /activities` is public and provides id, name, slug, price, date, duration, location, minParticipants, maxParticipants and status.
- The launch list shows only `published` and `confirmed` launches.
- `POST /users` takes {username, email, password, terms:true} and returns {user, accessToken}. The client uses the `username` and `email` fields of `user`. The token expires after 24 h.
- A repeated email responds 400 "Email is already registered" with `errors[0].field` = "email", and the client then asks for another email.
- `POST /login` {email, password} would return {user, accessToken}, like registration, but today it always responds 401 "Invalid email or password". The working session token therefore comes from registration.
- `GET /bookings`, `GET /bookings/:id` and `POST /bookings` {activityId, participants} require the token.
- A booking from `GET /bookings` and `GET /bookings/:id` has id, activityId, userId, participants, createdAt, paymentId, paymentStatus (pending | paid | refunded) and activity {name, slug, price, date, duration, location, status}.
- `POST /bookings` responds 201 with the booking without the `activity` object, and with paymentStatus "paid".
- POST /bookings responds 402 'Payment could not be processed' when the mock payment gateway rejects the charge (it fails when price × participants is a multiple of 1000). The client shows this error clearly and no booking is created.
- The API has no rocket data, so the exercise statement's request to show the rocket cannot be met.
- Free seats are not exposed, so the client cannot show them or check them in advance. The API enforces capacity with a 400 and "Capacity exceeded. Available: N, Requested: M".
- The API accepts bookings on draft launches, so the client itself must limit booking to `published` and `confirmed` launches.
- The API offers no way to cancel bookings.
- This is the final exercise of the "Programación Inteligente" course and is not for production.

**Known limitations**:

- Bookings are tied to the userId in the token, and `GET /bookings` returns only the authenticated user's bookings. A user who registers again is a different user and cannot see their previous bookings.
- In activity-bookings-express the services are created when the routes are imported, before loadAllData() fills the repositories. The email index and the user id counter start empty, so POST /login always fails and, after a restart, ids are reused and repeated emails may not be detected.
