# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-05

### Added

- The client can be compiled, run and tested through the Maven Wrapper on Java 21.
- `./mvnw package` produces a self-contained executable jar that runs with `java -jar`.
- `./mvnw test` runs without the API, and `./mvnw test -Pe2e` runs only the end-to-end tests.
- An interactive console menu lets customers list the launches open for booking, with their name, price, date, duration, location, participant limits and status.
- The API URL can be set with `ASTROBOOKINGS_API_URL`, and defaults to `http://localhost:3000`.
- Customers see `API not reachable at <url>` when the API is down, and readable messages for API errors, instead of a stack trace.
