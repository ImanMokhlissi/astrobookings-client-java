# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0] - 2026-11-06

### Added

- Customers can list their bookings from the menu, in a numbered list with the id, launch name, number of seats and payment status of each one.
- Customers can view a single booking by choosing its number from that list, and see its seats, creation date, payment data and launch details.
- Customers can enter 0 at the booking number prompt to cancel, and are asked again when the number is not valid.
- Customers with no session are told to register or log in before checking their bookings, and are told when they have no bookings.

## [0.3.0] - 2026-11-05

### Added

- Customers can book seats from the menu by choosing a published or confirmed launch from a numbered list and entering a number of seats, and see a confirmation with the booking id, launch name, seats and payment status.
- Customers can enter 0 at either booking prompt to cancel, and are asked again when a launch number or a number of seats is not valid.
- Customers who ask for more seats than are free see the available and requested numbers.
- Customers are told when their payment is rejected and that no booking was created.
- Customers with no session are told to register or log in before booking, and are told when no launches are open for booking.

## [0.2.0] - 2026-11-05

### Added

- Customers can register from the console with a username, email and password after accepting the terms, and are asked for another email if theirs is already registered.
- Customers can log in, and are told when the email or password was not accepted and offered to register instead.
- The session is saved in `~/.astrobookings/session.json`, without the password, and reused on the next run, and the menu shows who is logged in.
- Customers can log out, which deletes the saved session.
- Customers are told when the saved session cannot be read, and the client continues with no session.
- When the API rejects a session, the saved session is deleted and customers are asked to register again.

## [0.1.0] - 2026-11-05

### Added

- The client can be compiled, run and tested through the Maven Wrapper on Java 21.
- `./mvnw package` produces a self-contained executable jar that runs with `java -jar`.
- `./mvnw test` runs without the API, and `./mvnw test -Pe2e` runs only the end-to-end tests.
- An interactive console menu lets customers list the launches open for booking, with their name, price, date, duration, location, participant limits and status.
- The API URL can be set with `ASTROBOOKINGS_API_URL`, and defaults to `http://localhost:3000`.
- Customers see `API not reachable at <url>` when the API is down, and readable messages for API errors, instead of a stack trace.
