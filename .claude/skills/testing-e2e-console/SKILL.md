---
name: testing-e2e-console
description: Rules for writing and running end-to-end tests that drive the console menu against the real API, with one test per acceptance criterion. Use when verifying a spec's acceptance criteria, or when writing tests with the e2e tag.
---

# End-to-end testing of the console

Run the tests with `timeout 240 ./mvnw -B -ntp test -Pe2e`. They need the real API running, and they fail if it is
not (ADR-5).

## Rules

- **Never wait for input**: never leave the menu waiting for input. Tests inject the
  `InputStream`. A command that runs the client, such as `exec:java` or `java -jar`, gets its
  input through stdin: `echo "0" | timeout 240 ./mvnw -B -ntp compile exec:java`.
- **Time limit on Maven**: wrap every Maven command in `timeout`, as above, and run it in the
  foreground, with no background polling loop. If a command times out twice on a download, stop
  and report it.
- **Tag**: annotate each test class with `@Tag("e2e")`.
- **Location**: use one class per spec, named after it, such as `LaunchListingE2eTest`, under
  `src/test/java` in the package it exercises.
- **One test per criterion**: put the ID first in the name:
  `AC3_should_<expected result>_when_<condition>`.
- **Drive the menu**: build the layers as `Main` does, with simulated input from a
  `ByteArrayInputStream`, and assert on the output captured from a `PrintStream`.
- **Real API**: read the URL from `ASTROBOOKINGS_API_URL`, with `http://localhost:3000` as the
  default.
- **Unique email per run**: build it with a timestamp, such as
  `"e2e-" + System.currentTimeMillis() + "@example.com"`.
- **Isolation**: use `@TempDir` for the session file, and never touch the user's real one.
- **Shared data**: the API keeps its data between runs. Check only the bookings the test creates,
  and avoid a total price that is a multiple of 1000 unless the test means to get a 402.

## Criteria the real API cannot prove

Prove each criterion by the first method that works, and record which one you used:

1. **E2e test**: the default, for everything the real API can reach.
2. **Unreachable API**: an e2e test whose URL points at a local port with nothing listening.
3. **Build criteria**: run the `./mvnw` command the criterion names and check its result. No
   test class is needed for these.
4. **Unit test**: only if the real API cannot reach the behavior, such as a successful login
   while `POST /login` always responds 401. Mark the criterion "covered by unit test", citing
   the class and method. If that test does not exist, the criterion fails.

## Report

Report one row per acceptance criterion, then the final result:

| Criterion | Method | Test or command | Result |
|-----------|--------|-----------------|--------|
| AC1       | e2e    | `AC1_should_...` | pass  |

For each failure, say what was expected, what happened and why, with the relevant output.
