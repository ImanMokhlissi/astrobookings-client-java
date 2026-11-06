# Web interface

- **Type**: feat
- **Status**: planned
- **Related**: FR10, TR1
- **Depends on**: feat-booking-retrieval

## Problem

- As a customer, I want to list launches, register, log in and log out from a browser, so that I can use AstroBookings without a terminal menu.
- As a customer, I want to book seats and check my bookings and their `paymentStatus` from a browser, so that I have the same features as in the console.
- As a customer, I want the web interface to act with my saved token only for me, so that another machine or website cannot use my session.

## Solution

- **web**: Adds the sixth package, on the JDK HTTP server with its default executor, and no frameworks (ADR-14). Generates HTML pages on the server, with one CSS file from `src/main/resources` and no mandatory JavaScript, for the launches, registration, login, booking, my bookings and booking detail (ADR-15). Every action that changes something is a POST form, every GET changes nothing, and links and forms use ids (ADR-14). A successful POST answers with a 303 redirect to a relative path, and a failed POST shows the form again with the error, the other typed values escaped and the password empty (ADR-15). Presents every failure, with no stack trace reaching the browser (ADR-14), and tells the user when the session file could not be read (ADR-17). Escapes all user and API text, including inside attributes, and leaves API messages otherwise verbatim. Listens only on 127.0.0.1 and answers a POST from a foreign `Origin` with 403 before any `service` call (ADR-17).
- **service**: Receives `LaunchDateFormatter` and the whole-number seats check from `cli`, so both interfaces share them (ADR-14). Looks up a launch id from a form in a fresh list of bookable launches, so a stale or forged form cannot bypass the allow-list (ADR-12, ADR-14). The session lifecycle and the meaning of a 401 stay as they are (ADR-10, ADR-11).
- **cli**: Uses the moved formatter and seats check, and keeps the 0 that cancels as console behaviour (ADR-14). The console's behaviour does not change. `StartupErrorView` shows the web startup errors and the address of the running server on the terminal (ADR-16).
- **Main**: Reads the `--web` argument and `ASTROBOOKINGS_WEB_PORT` once, and starts one interface or the other. A blank or unset port means 8080, and a valid port is 1 to 65535. After starting the server it does not call `System.exit` and does not read standard input (ADR-16).
- **tests**: Handler unit tests, and end-to-end tests tagged `@Tag("e2e")` that start the server on port 0 with a temporary session file and make real HTTP requests against the real API. Behaviour the real API cannot reach, such as a successful login, is covered by unit tests with the fake server, which also covers the 402 (ADR-18).

## Acceptance criteria

- **AC1**: WHEN the user opens the launches page, the system SHALL show the name, price, date, duration, location, minimum participants, maximum participants and status of each launch returned by `GET /activities` whose status is `published` or `confirmed`, leave out every other launch, use the word "launch" and never "activity" in its own texts, and show "No launches open for booking." when no launch qualifies.
- **AC2**: WHEN the user submits the registration form with a username, an email, a password and the terms accepted, the system SHALL send them to `POST /users` with `terms` set to true. WHEN registration or login succeeds, the system SHALL save the access token, username and email, and never the password, to the session file, answer with a 303 redirect to a relative path, and show who is logged in on the page it redirects to.
- **AC3**: IF the registration form is submitted without accepting the terms, or `POST /users` responds with a field error on `email` such as "Email is already registered", THEN the system SHALL show the form again without a redirect, with the error, the typed username and email and an empty password, and start no session. When the terms were not accepted, the system SHALL send no request to the API.
- **AC4**: The login page SHALL state that the API's login is currently broken and rejects every attempt. IF `POST /login` responds with a 401, THEN the system SHALL show the form again without a redirect and with an empty password, explain that the API did not accept the email or password and that its login is broken today, and offer a link to the registration page.
- **AC5**: WHEN the user submits the logout action with a POST, the system SHALL delete the session file, end the session and answer with a 303 redirect, without showing an error if the file does not exist. IF the API responds with a 401 to `GET /bookings`, `GET /bookings/:id` or `POST /bookings`, THEN the system SHALL delete the session file, end the session and ask the user to register again, with a link to the registration page.
- **AC6**: WHEN the user with a session opens the booking page, the system SHALL offer only the launches in status `published` or `confirmed` from a fresh `GET /activities`, with a field for the number of seats, or show "No launches open for booking." when there are none. WHEN the user submits an offered launch and a valid number of seats, the system SHALL send `POST /bookings` with that launch's id as `activityId`, the seats as `participants` and an `Authorization: Bearer <token>` header, and, on a 201, answer with a 303 redirect to a relative path whose page shows the booking id, the launch name, the number of seats and the `paymentStatus`.
- **AC7**: IF the booking form is submitted with no session, with seats that are not a whole number of at least 1 (such as 0, 1.5 or text), or with a launch id that is not in a fresh list of `published` and `confirmed` launches, THEN the system SHALL show an error without a redirect and send no `POST /bookings`.
- **AC8**: IF `POST /bookings` responds 400 with "Capacity exceeded. Available: N, Requested: M", THEN the system SHALL show the booking form again with that message unchanged apart from HTML escaping, so that the user sees the available and requested numbers. IF it responds 402 "Payment could not be processed", THEN the system SHALL show the booking form again with that message and state that no booking was created.
- **AC9**: WHEN the user with a session opens the my bookings page, the system SHALL request `GET /bookings` with the `Authorization: Bearer <token>` header and show each booking's id, launch name, number of seats and `paymentStatus` as the API sends it, with a link to its detail, or a message that the user has no bookings. WHEN the user opens a booking's detail, the system SHALL request `GET /bookings/:id` with that header and show its id, number of seats, creation date, `paymentId`, `paymentStatus` as the API sends it, and the launch name, price, date, duration, location and status. IF the user opens either page with no session, THEN the system SHALL tell the user to register or log in, with no request to `/bookings`.
- **AC10**: IF the API cannot be reached or a call times out on any page or action, THEN the system SHALL show `API not reachable at <url>`, with the configured URL, in the response instead of a stack trace. IF the API responds with any other error in the format `{message, errors:[{field, message}]}`, THEN the system SHALL show the message verbatim apart from HTML escaping, followed by the field and message of each field error, with no stack trace.
- **AC11**: WHEN the client starts with `--web`, the system SHALL serve the web interface only on 127.0.0.1, on the port in `ASTROBOOKINGS_WEB_PORT` or on 8080 when that variable is unset or blank, so that a request to another address of the machine is not served. WHEN the client starts without `--web`, the system SHALL start the console menu. IF the port is invalid or busy, THEN the system SHALL show a readable error without a stack trace and exit with code 1.
- **AC12**: The system SHALL HTML-escape all text coming from the user or the API in every page, including inside HTML attributes, so that markup in that text appears as plain text. IF a POST has an `Origin` header that is neither `http://127.0.0.1:<port>` nor `http://localhost:<port>`, with the port actually bound, THEN the system SHALL answer 403 with no request to the API and no change to the session, and it SHALL accept a POST with no `Origin` header.

## Out of scope

- Multiple users or a login per browser: the interface serves a single local user with `~/.astrobookings/session.json` (ADR-17).
- Noticing session file changes made by a console running at the same time, until the server restarts (ADR-17).
- Checking the `Host` header or protecting GET responses against DNS rebinding (ADR-17, `project/BACKLOG.md`).
- Fixing the broken `POST /login` in the API.
- Numbered selection and the 0 that cancels, which are console behaviour (ADR-13, ADR-14).
- Showing free seats or checking them before booking, and predicting the payment gateway's rule.
- Cancelling bookings, real payments, creating or editing launches, and showing rocket data.
- Mandatory JavaScript, a front-end build and any web framework (TR1, ADR-15).
