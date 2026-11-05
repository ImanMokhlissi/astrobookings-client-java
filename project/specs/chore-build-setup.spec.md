# Build setup

- **Type**: chore
- **Status**: verified
- **Related**: TR1, TR3, TR8
- **Depends on**: none

## Problem

- As a developer, I want a build that compiles, runs and packages the client, so that every later spec can start from a working project.
- As a developer, I want the end-to-end tests kept apart from the default test run, so that `./mvnw test` stays fast and does not need the API.
- As a developer, I want the agreed packages and a minimal `Main` in place, so that each feature adds code to a known layer.

## Solution

- **build**: Extend the existing `pom.xml`, which already sets Java 21 and JUnit 5. Add `jackson-databind`, `exec-maven-plugin` with `Main` as its main class (ADR-6), `maven-shade-plugin` for a self-contained executable jar (ADR-8), a Surefire exclusion of the `e2e` tag, and an `e2e` profile that runs only that tag (ADR-9).
- **Main**: Add a minimal `dev.iman.astrobookings.Main`, outside the layers, that starts and exits normally. It wires nothing yet.
- **cli, service, api, model, session**: Create the five empty packages under `dev.iman.astrobookings` (ADR-1). They contain no behavior.

## Acceptance criteria

- **AC1**: WHEN `./mvnw compile` runs on a clean checkout, the system SHALL compile the sources for Java 21 without errors.
- **AC2**: The system SHALL have the packages `cli`, `service`, `api`, `model` and `session` under `dev.iman.astrobookings` in a clean checkout.
- **AC3**: WHEN `./mvnw compile exec:java` runs without `-Dexec.mainClass` and without the API running, the system SHALL run `dev.iman.astrobookings.Main` and finish without errors.
- **AC4**: WHEN `./mvnw package` runs, the system SHALL produce one executable jar that contains the `jackson-databind` classes.
- **AC5**: WHEN that jar runs with `java -jar` and without the API running, the system SHALL run `dev.iman.astrobookings.Main` and exit with code 0.
- **AC6**: WHEN `./mvnw test` runs, the system SHALL run the JUnit 5 tests that do not have the `e2e` tag.
- **AC7**: WHEN `./mvnw test` runs, the system SHALL NOT run the tests that have the `e2e` tag.
- **AC8**: WHEN `./mvnw test -Pe2e` runs, the system SHALL run only the tests that have the `e2e` tag.
- **AC9**: The system SHALL declare `jackson-databind` as its only dependency outside the test scope.

## Out of scope

- The console menu and the wiring of the layers in `Main` (ADR-2, ADR-7).
- Reading `ASTROBOOKINGS_API_URL` and making HTTP calls (TR2).
- Ignoring the API fields not listed in the constraints, which needs the DTOs that the feature specs add (TR3). This spec only provides Jackson.
- Records, DTOs and failure types in `model`.
- Feature tests, the in-memory fake HTTP server and the end-to-end tests themselves (ADR-5).
- Any change to the Maven Wrapper, which is already in the repository.
