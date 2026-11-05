---
name: testing-unit-junit
description: Rules and an example for writing unit tests with JUnit 5 and a fake HTTP server. Use when writing or changing unit tests, which run with ./mvnw test.
---

# Unit testing with JUnit 5

## Rules

- **Location**: a test lives under `src/test/java`, in the same package as the class it tests.
- **Names**: use `should_<expected result>_when_<condition>`.
- **Structure**: follow Arrange-Act-Assert, with one behavior per test.
- **No real API**: use the fake server from
  [FakeApiServerTest.java](FakeApiServerTest.java), with
  `com.sun.net.httpserver.HttpServer` on a free port. Unit tests never need the API running.
- **No real files**: use JUnit's `@TempDir`, and never touch the user's real session file.
- **Console**: drive the menu with a `ByteArrayInputStream` and assert on a `PrintStream` over a
  `ByteArrayOutputStream`.
- **Exit code**: call the `run` method of `Main`, as `coding-java` describes, and never trigger
  `System.exit` from a test.
- **No waiting**: no `Thread.sleep`, and no dependence on test order.
- **Acceptance criteria**: cover every acceptance criterion that a unit test can prove,
  especially those that the real API cannot reach, such as a successful login. The e2e tests
  of the spec cite these tests by name.

Do not use the `e2e` tag here: `testing-e2e-console` owns those tests.
