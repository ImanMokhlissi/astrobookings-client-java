# Session management

- **Type**: feat
- **Status**: draft
- **Related**: FR2, FR3, FR4, FR5, TR5
- **Depends on**: chore-build-setup, feat-launch-listing

## Problem

- As a new customer, I want to register or log in from the console, so that I get a session for booking seats.
- As a returning customer, I want the client to reuse my saved session, so that I do not have to register on every run.
- As a customer, I want to log out, and to be told when my session is no longer valid, so that I know when I need to register again.

## Solution

- **cli**: Adds the register, log in and log out options to the menu, and shows who is logged in. Registration prompts for username, email and password and asks the user to accept the terms. Passwords are read with the console's password prompt when there is a console, and from the injected input otherwise (ADR-2). It shows the session failures: a rejected login with an offer to register, a repeated email with a prompt for another one, an unreadable session file, and a rejected session with a request to register again.
- **service**: Adds the session lifecycle: register, log in, log out and load the saved session at startup without calling the API (ADR-11). It decides what a 401 means: bad credentials on login, and a rejected session on any authenticated call, in which case it deletes the session file (ADR-10). It provides that rejected-session handling for the authenticated calls added by feat-booking-creation and feat-booking-retrieval.
- **api**: Adds `POST /users` and `POST /login`, and maps a 401 to the unauthorized failure (ADR-10).
- **model**: Adds the User and Session records and the RegisterRequest, LoginRequest and AuthResponse DTOs, plus any ADR-10 failure type that feat-launch-listing did not need.
- **session**: Reads, writes and deletes the session file. `Main` passes in the path `~/.astrobookings/session.json`, so tests use a temporary file (ADR-7).

## Acceptance criteria

- **AC1**: WHEN the user enters a username, email and password and accepts the terms, the system SHALL send them to `POST /users` with `terms` set to true.
- **AC2**: IF the user does not accept the terms, THEN the system SHALL not send the registration to the API and SHALL not start a session.
- **AC3**: IF the registration response has a field error on `email`, such as "Email is already registered", THEN the system SHALL show the error, ask for another email and resend the registration with it.
- **AC4**: WHEN registration or login succeeds, the system SHALL save the access token, username and email, and never the password, to `~/.astrobookings/session.json`, creating the folder if needed, and SHALL show who is logged in.
- **AC5**: IF `POST /login` responds with a 401, THEN the system SHALL explain that the email or password was not accepted and SHALL offer to register.
- **AC6**: WHEN the client starts with a readable session file, the system SHALL continue with that session and show who is logged in, without asking the user to register and without making an authenticated API call.
- **AC7**: IF the session file exists but cannot be read, THEN the system SHALL tell the user and continue with no session.
- **AC8**: WHEN the user logs out, the system SHALL delete the session file and end the session, without showing an error if the file does not exist.
- **AC9**: IF the API rejects an authenticated call with a 401, THEN the system SHALL delete the session file and ask the user to register again.

## Out of scope

- Booking seats and listing or viewing bookings, including the authenticated calls and the `Authorization` header (feat-booking-creation, feat-booking-retrieval).
- The menu itself, the display of API errors and the API-down message, which this spec reuses for registration and login (feat-launch-listing).
- Calculating the 24 h token expiry or validating the token at startup (ADR-11).
- Protecting the session file, which needs no special protection (PRD assumptions).
- Fixing the broken `POST /login` in the API.
