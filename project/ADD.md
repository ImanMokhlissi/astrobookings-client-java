# AstroBookings Client: Architecture Design Document

## Table of contents

- [Tech stack & dev tools](#tech-stack--dev-tools)
- [System architecture](#system-architecture)
- [Data model](#data-model)
- [Architecture Decision Records](#architecture-decision-records)

## Tech stack & dev tools

**Tech stack**:

- Java 21: language and runtime, with records for the model (TR1).
- `java.net.http.HttpClient`: HTTP calls to the API (TR2).
- Jackson (`jackson-databind`): JSON for the API and for the session file (TR3).
- activity-bookings-express: external REST API, the client's only backend.

**Dev tools**:

- Maven through the Maven Wrapper: build and dependencies (TR1).
- JUnit 5: unit and end-to-end tests (TR8).
- `com.sun.net.httpserver`: in-memory fake HTTP server for unit tests (ADR-5).
- `maven-surefire-plugin`: selects tests by the `e2e` tag (ADR-5, ADR-9).
- `exec-maven-plugin`: runs the client from Maven (ADR-6).
- `maven-shade-plugin`: packages the executable jar with Jackson inside (ADR-8).

## System architecture

The client is a single-process console application split into five layers under
`dev.iman.astrobookings`. `Main`, in the root package, sits outside the layers: it reads the
API URL from `ASTROBOOKINGS_API_URL` and wires the layers together (ADR-4, ADR-7).

| Layer     | Responsibility                                                                                                  | Traces to                    |
|-----------|-----------------------------------------------------------------------------------------------------------------|------------------------------|
| `cli`     | Interactive menu, prompts, input validation and all presentation. It is the only layer that reads or prints.    | FR1 to FR9, TR7, TR9         |
| `service` | Use cases and client rules: bookable status filter, session lifecycle, and the meaning of each API failure.      | FR1 to FR9                   |
| `api`     | HTTP calls, JSON mapping, Bearer header, activity-to-launch translation and mapping of API errors.               | TR2, TR3, TR4, TR6, TR7, TR9 |
| `model`   | Records for Launch, Booking, Session and their DTOs, plus the failure types shared by the layers.                | TR3, TR4                     |
| `session` | Reads, writes and deletes the session file.                                                                      | FR3, FR4, TR5                |

Dependencies point one way only: `cli` calls `service`, and `service` calls `api` and
`session`. All of them use `model`, which depends on no other layer. `api` and `session` do not
know each other: `service` reads the token from `session` and passes it to `api` on each
authenticated call.

```mermaid
flowchart TD
    User([User]) -->|"Console menu"| Cli

    subgraph Client["AstroBookings Client"]
        Cli["cli: menu and presentation"] --> Service["service: use cases and client rules"]
        Service --> Api["api: HTTP client and error mapping"]
        Service --> Session["session: token persistence"]
        Cli --> Model["model: records and DTOs"]
        Service --> Model
        Api --> Model
        Session --> Model
    end

    Session -->|"JSON file"| SessionFile[("~/.astrobookings/session.json")]
    Api -->|"HTTP and JSON, at ASTROBOOKINGS_API_URL"| API[("activity-bookings-express API")]
```

## Data model

The client stores no data apart from the session file. Everything else is read from the API
on each request. API fields not listed below are ignored (TR3).

### Entities

| Entity        | Fields                                                                                  | Description                                                                                                                                                                                  |
|---------------|-----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Launch        | id, name, slug, price, date, duration, location, minParticipants, maxParticipants, status | A rocket launch as the customer sees it. Only `published` and `confirmed` launches are listed or bookable. The date stays as the ISO text the API sends, and `cli` formats it for display.  |
| LaunchSummary | name, slug, price, date, duration, location, status                                     | The launch data embedded in a booking. It has no id or participant limits.                                                                                                                  |
| Booking       | id, launchId, userId, participants, createdAt, paymentId, paymentStatus, launch         | Seats booked by a user. The payment status is pending, paid or refunded. The launch is a LaunchSummary, and is absent on a newly created booking, when `cli` shows the launch the user chose. |
| User          | username, email                                                                         | The person the session belongs to, used to show who is logged in. The API's user id is not used (see AuthResponse).                                                                         |
| Session       | accessToken, username, email                                                            | The saved session, stored as JSON in `~/.astrobookings/session.json`. The password is never stored. The 24 h expiry is not stored, because the API decides it (ADR-11).                      |
| ApiError      | status, message, fieldErrors                                                            | A failed API response. The status comes from the HTTP response. The list of field errors may be empty.                                                                                       |
| FieldError    | field, message                                                                          | One invalid field in a failed API response, such as `email` when the address is already registered.                                                                                          |

### DTOs

Only `api` reads or writes these DTOs (ADR-3). They keep the API's names, so in the code outside
`api` the word "activity" appears only in these DTOs.

| DTO                     | Direction | Fields                                                                                    | Maps to       |
|-------------------------|-----------|-------------------------------------------------------------------------------------------|---------------|
| ActivityResponse        | Response  | id, name, slug, price, date, duration, location, minParticipants, maxParticipants, status | Launch        |
| RegisterRequest         | Request   | username, email, password, terms (always true, sent only after the user accepts)          | User          |
| LoginRequest            | Request   | email, password                                                                           | User          |
| AuthResponse            | Response  | user (username, email), accessToken. Used by registration and login.                      | User, Session |
| CreateBookingRequest    | Request   | activityId, participants                                                                  | Booking       |
| BookingResponse         | Response  | id, activityId, userId, participants, createdAt, paymentId, paymentStatus, activity       | Booking       |
| BookingActivityResponse | Response  | name, slug, price, date, duration, location, status                                       | LaunchSummary |
| ErrorResponse           | Response  | message, errors                                                                           | ApiError      |
| FieldErrorResponse      | Response  | field, message                                                                            | FieldError    |

`POST /bookings` also uses BookingResponse, with `activity` absent and paymentStatus "paid".

## Architecture Decision Records

| ID     | Decision | Reason | Consequences | Status |
|--------|----------|--------|--------------|--------|
| ADR-1  | Organize the code in five packages under `dev.iman.astrobookings`: `cli` (console menu and presentation), `service` (use cases and client rules such as filtering by status), `api` (HTTP client with `java.net.http` and mapping of API errors), `model` (records for Launch, Booking, Session and their DTOs) and `session` (token persistence in `~/.astrobookings/session.json`). | Each concern can change and be tested on its own, without a framework (TR1). | Dependencies point one way, as shown in the diagram. Client rules such as FR1 and FR6 live only in `service`, not in the menu. | accepted |
| ADR-2  | The interface is an interactive console menu, with input and output injected as `InputStream` and `PrintStream`. | Tests can script a whole session and check what the user sees, with no real terminal. | `cli` never touches `System.in` or `System.out` directly: only `Main` passes them in. End of input ends the menu cleanly. Passwords are read with `System.console().readPassword()` when there is a real console, and from the injected `InputStream` when there is none, as in the tests. `Main` also does the console lookup and passes the console to `cli`, so a test run from a terminal still uses the injected stream. | accepted |
| ADR-3  | A Launch in the client equals an Activity in the API, and the translation happens only in the `api` layer (TR4). | The user's vocabulary stays consistent, and the API's naming is confined to one place. | `service` and `cli` deal only in launches. Activity DTOs sit in `model` (ADR-1), but only `api` may use them. The client's own texts say "launch". API error messages are shown as they are, with no substitutions, so one such as "Activity not found" can reach the user unchanged. | accepted |
| ADR-4  | The API URL comes from the environment variable `ASTROBOOKINGS_API_URL`, with `http://localhost:3000` as the default. | The client and its tests can point to an API elsewhere without a rebuild (TR2). | Every HTTP call uses the configured URL, and the TR9 message shows it: "API not reachable at", followed by the URL. `Main` reads the variable once: a blank value means the default, and an invalid one gives a readable error at startup. | accepted |
| ADR-5  | Unit tests use JUnit 5 with an in-memory fake HTTP server (`com.sun.net.httpserver`), so they do not depend on the API. End-to-end tests drive the menu against the real API, are tagged `@Tag("e2e")` and are excluded from the default `mvn test`. | `./mvnw test` stays fast and works anywhere (TR8), while the end-to-end tests still check the real API contract, including the constraints a fake cannot prove. | Unit tests start the fake server on a free local port and use a temporary session file. End-to-end tests need the API running and fail if it is not. They change the API's data: each run adds users and uses up seats. Running `git checkout db/` in the API's repository restores the data, and the API then needs a restart because it keeps its data in memory. Each run registers a unique email with a timestamp, and the tests check only the bookings they create and avoid a total price that is a multiple of 1000 unless they mean to test a rejected payment, which the API answers with a 402. Unit tests cover that case with the fake server. Reused ids after an API restart (PRD known limitations) can make a new user see old bookings. | accepted |
| ADR-6  | Run the client with `exec-maven-plugin` through `./mvnw exec:java`, and also package an executable jar with `Main` as its main class. | Maven is the quick way to run it during development, and the jar runs without Maven. | `exec:java` does not compile, so a clean checkout needs `./mvnw compile exec:java`. The plugin configures the main class, so `-Dexec.mainClass` becomes optional. The jar needs Jackson at runtime (ADR-8). | accepted |
| ADR-7  | `Main` is the composition root: it builds the layers with plain constructor injection and passes in the streams, the console when there is one, the API URL and the session file path. | ADR-2, ADR-4 and ADR-5 all need these values to be replaceable, and TR1 rules out a heavy framework. | Tests build the same layers with their own values and never touch the user's real session file. `Main` holds only wiring, so it has no logic to test. | accepted |
| ADR-8  | Build the executable jar with `maven-shade-plugin`, so that it contains Jackson. | A jar that only names its main class fails at runtime, because Jackson is not on the classpath. A single file is also the simplest to run. | `./mvnw package` produces one self-contained jar. `.gitignore` already covers `dependency-reduced-pom.xml`, which this plugin generates. | accepted |
| ADR-9  | Select the tests with a Maven profile named `e2e`: Surefire excludes the `e2e` tag by default, and `./mvnw test -Pe2e` runs only the tests with that tag. | A profile gives a short command that is easy to remember, and keeps TR8's `./mvnw test` unchanged. | `pom.xml` needs Surefire configuration and the profile, which the first implementation spec, `chore-build-setup`, adds. | accepted |
| ADR-10 | `api` turns every failure into one of three types, defined in `model` so that `cli` does not depend on `api`: API not reachable, unauthorized, and API error carrying an ApiError. `service` decides what each one means, and `cli` is the only layer that shows them. | The same 401 means different things by use case: bad credentials on login (FR2) and a rejected session elsewhere (FR5). Showing errors in one place guarantees no stack traces reach the user (TR7, TR9). | Connection failures and timeouts become the TR9 message. Capacity errors (FR7) carry their numbers in the message with no field errors, so `cli` shows the message as it is and the client does not parse it. A 402 "Payment could not be processed" from `POST /bookings` also has no field errors and maps to an API error with status 402. `cli` shows its message and states that no booking was created, and the client does not try to predict the gateway's rule. A field error on `email` during registration makes `cli` ask for the email again. A response that is not valid JSON becomes a generic API error, which matters when the URL is configurable (ADR-4). This extends `model` beyond records (ADR-1). | accepted |
| ADR-11 | `service` loads the session at startup and trusts it without calling the API. The token is checked only when an authenticated call is made. When such a call returns 401, `service` deletes the session file and `cli` asks the user to register again. A session file that cannot be read counts as no session, and the user is told. | The API has no endpoint to validate a token, and `GET /activities` is public. Only the API knows if a token is still valid, so the client does not calculate the 24 h expiry itself (FR3, FR5). | Starting the client makes no authenticated call and needs no working session. An expired session is found on the first booking action. A rejected token is not kept, so the next run does not fail the same way. `session` creates the folder when needed, and logging out with no file is not an error (FR4). | accepted |
| ADR-12 | `service` filters launches with an allow-list of `published` and `confirmed`, and uses it both for the list and before each booking. | The API accepts bookings on draft launches (PRD constraints) and also has the statuses `sold-out`, `done` and `cancelled`, none of which are bookable under FR1 and FR6. An allow-list also rejects any status added later. | Status stays plain text, so an unknown value cannot break parsing. The booking flow offers only launches from a fresh list. `cli` also checks that the seats are a whole number of at least 1, since the API accepts fractions. | accepted |
| ADR-13 | Use numbered selection in console menus: when the user chooses an item from a displayed list, they choose it by its number in that list, never by its id. Entering 0 at a prompt of a booking or selection flow, including the seats prompt, cancels and returns to the menu, and an invalid number makes the client ask again. | Ids are API details, so the user should not have to type them. A number can only point at an item the client has just displayed, which keeps the choice within the list on offer, such as the launches that pass the ADR-12 filter (FR6, FR9). | `cli` resolves the number to the item in the list it displayed, so `service` and `api` still work with ids and never see the numbers. It applies today to choosing a launch to book (`feat-booking-creation`) and a booking to view (`feat-booking-retrieval`), whose specs hold the detailed criteria. Numbering covers only choices from a list, and a seats value is not one: apart from the 0 that cancels, it must pass its own check (ADR-12), or the client asks again with no request to the API. Asking again stops at end of input, which still ends the menu cleanly (ADR-2). | accepted |
