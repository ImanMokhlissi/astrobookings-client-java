# Plan: Launch listing

- **Spec**: [feat-launch-listing](feat-launch-listing.spec.md)
- **Branch**: feat/launch-listing

## Steps

Steps 1 to 9 are for the `coder`. The `tester` follows [Verification](#verification), which has
no checkboxes because the tester does not change this plan.

Rules for every step:

- Every run of the menu, in a test or a command, ends by itself. A test injects an `InputStream` whose script ends in `0` or at end of input. A command gets its input through stdin. Nothing waits for input.
- A test that makes an HTTP call uses the fake server from `testing-unit-junit`, never port 3000. Build a test's URL from the fake server.

### 1. Add the model

Covers: AC7, AC8

- [x] In `model`, add the `Launch` record with the ADD's fields. `id`, `name`, `slug`, `date`, `location` and `status` are `String`, `price` is `BigDecimal` because the API allows fractions, and `duration`, `minParticipants` and `maxParticipants` are `int`.
- [x] In `model`, add the `FieldError` record, and the `ApiError` record with `status`, `message` and `fieldErrors`, whose list is copied and never null.
- [x] In `model`, add the `ActivityResponse`, `ErrorResponse` and `FieldErrorResponse` DTO records with the ADD's fields. In `ActivityResponse`, use `Integer` for the whole numbers, so that a missing one reads as null and not as 0.
- [x] In `model`, add the sealed `ApiFailure` base, a `RuntimeException`, with three subclasses: `ApiNotReachableFailure` holding the configured URL text, and `UnauthorizedFailure` and `ApiErrorFailure` each holding an `ApiError`. The 401 carries its `ApiError` so that AC8 holds for it, and `cli` needs no meaning for a 401, which belongs to feat-session.
- [x] Add `ApiErrorTest` with `should_hold_an_empty_list_when_the_field_errors_are_null`.

### 2. Read launches from the API

Covers: AC2, AC4, AC6

- [x] In `api`, add `ApiClient`, with the `HttpClient`, the configured URL text and the request timeout as constructor parameters, and a `fetchLaunches` method that sends `GET /activities` with that timeout and no `Authorization` header.
- [x] Build the request URL by appending `/activities` to the configured URL, without its trailing slash if it has one. `URI.resolve` with an absolute path would drop a path in the configured URL.
- [x] In `ApiClient`, read the JSON with Jackson, ignoring unknown properties, and translate each `ActivityResponse` into a `Launch`. Only `api` touches the DTOs (ADR-3).
- [x] Add `ApiClientTest` with the fake server: `should_return_every_launch_with_its_fields_when_the_api_responds_ok`, `should_return_no_launches_when_the_api_responds_with_an_empty_list`, `should_ignore_the_field_when_an_activity_holds_an_unknown_one` and `should_call_the_configured_url_when_it_has_a_path_or_a_trailing_slash`.

### 3. Turn every API failure into a failure type

Covers: AC4, AC7, AC8

- [x] In `ApiClient`, turn `IOException` into `ApiNotReachableFailure`, which covers connection failures and timeouts, and restore the interrupt flag on `InterruptedException`.
- [x] In `ApiClient`, turn a 401 into `UnauthorizedFailure` and any other status outside 2xx into `ApiErrorFailure`, keeping the status, the message and the field errors.
- [x] In `ApiClient`, turn each malformed body into a generic `ApiErrorFailure`, whose text does not say "activity". On a 2xx: invalid JSON, an empty body, the JSON literal `null`, a body that is not a list, a `null` entry, or an entry with a missing field. On an error status: invalid JSON, the JSON literal `null`, no `message`, or `errors` that are not a list of entries. Jackson returns `null` for the literal `null` without throwing, and `coding-java`'s `ExampleApiClient` does not handle it, so check it here and let no `NullPointerException` out. `errors` absent means no field errors.
- [x] Add to `ApiClientTest`: `should_fail_as_not_reachable_when_nothing_listens_on_the_port` and `should_fail_as_not_reachable_when_the_call_times_out`. The second passes a short timeout to `ApiClient`, and its handler blocks on a latch that `@AfterEach` releases before stopping the server, with no `Thread.sleep`.
- [x] Add to `ApiClientTest`: `should_fail_as_unauthorized_when_the_api_responds_401`, `should_fail_with_the_message_and_field_errors_when_the_api_responds_with_an_error`, `should_ignore_the_field_when_an_error_holds_an_unknown_one`, `should_fail_with_a_generic_error_when_the_body_is_not_valid_json`, `should_fail_with_a_generic_error_when_the_body_is_json_null` and `should_fail_with_a_generic_error_when_an_activity_misses_a_field`.

### 4. Keep only the launches open for booking

Covers: AC2, AC3

- [x] In `service`, add `LaunchService` with `ApiClient` as a constructor parameter and a `listOpenLaunches` method that keeps only `published` and `confirmed`, as an allow-list with exact matching (ADR-12). It lets `ApiFailure` through, because this spec gives no failure a use-case meaning.
- [x] Add `LaunchServiceTest` with the fake server: `should_keep_published_and_confirmed_launches_when_listing`, `should_leave_out_every_other_status_when_listing`, covering `draft`, `sold-out`, `done`, `cancelled` and an unknown value, and `should_return_no_launches_when_none_is_open_for_booking`.

### 5. Add the menu loop

Covers: AC1, AC5, AC9, AC10, AC11

- [x] In `cli`, add `ConsoleMenu`, with `LaunchService`, `InputStream` and `PrintStream` as constructor parameters and a `run` method that shows the menu in English, with `1` to list launches and `0` to exit. Keep `0` as exit: `BuildSetupE2eTest` sends it and must keep passing. The menu makes no HTTP call until the user selects the list option.
- [x] In `ConsoleMenu`, show an error for an unknown option, including an empty line, and show the menu again.
- [x] In `ConsoleMenu`, end `run` normally on `0` and at end of input. An `IOException` while reading also counts as the end of input, unlike the `UncheckedIOException` in `coding-java`'s `ExampleMenu`, which would reach the user as a stack trace.
- [x] Add `ConsoleMenuTest`, driven with a `ByteArrayInputStream`: `should_show_the_list_and_exit_options_when_started`, `should_show_an_error_and_the_menu_again_when_the_option_is_unknown`, `should_end_when_the_user_selects_exit` and `should_end_when_the_input_ends`.

### 6. Show the launch list

Covers: AC2, AC3, AC5

- [x] In `cli`, wire the list option of `ConsoleMenu` to `LaunchService`, and show the name, price, date, duration, location, minimum participants, maximum participants and status of each launch, then the menu again. Show the price with `toPlainString`, so `158` does not become `158.0`, and add no currency, because the API gives none.
- [x] In `cli`, show exactly `No launches open for booking.` when the list is empty.
- [x] In `cli`, add `LaunchDateFormatter`, which handles the two forms the API's data holds: a date such as `2026-12-20` and an instant such as `2026-08-25T14:00:00.000Z`. Use an explicit English locale, so the month does not depend on the machine's language, and show instants in UTC, so the text does not depend on its time zone. Any other text is shown as it came, without an exception, because the date stays plain text and the API accepts more forms.
- [x] Add `LaunchDateFormatterTest`: `should_format_the_date_when_it_has_no_time`, `should_format_the_date_in_utc_when_it_is_an_instant` and `should_keep_the_text_when_the_date_cannot_be_parsed`.
- [x] Add to `ConsoleMenuTest`, with the fake server: `should_show_every_field_of_each_open_launch_when_listing`, `should_leave_out_other_launches_when_listing`, `should_show_the_exact_message_when_no_launch_is_open_for_booking` and `should_not_say_activity_when_showing_its_own_texts`.

### 7. Show API failures and return to the menu

Covers: AC7, AC8

- [x] In `ConsoleMenu`, catch `ApiFailure` around the list option, then show the menu again. Handle the three subclasses with a `switch` over the sealed type, with no `default`.
- [x] Show `API not reachable at <url>` with the URL text as configured.
- [x] Show the message of an `ApiError` verbatim, followed by the field and message of each field error, for both `ApiErrorFailure` and `UnauthorizedFailure`. Replace nothing, so "Activity not found" reaches the user unchanged (ADR-3).
- [x] Add to `ConsoleMenuTest`, each with input that selects the list and then `0`: `should_show_not_reachable_with_the_url_and_the_menu_again_when_the_api_is_down`, `should_show_not_reachable_and_the_menu_again_when_the_call_times_out`, `should_show_the_message_and_field_errors_and_the_menu_again_when_the_api_responds_with_an_error`, `should_show_the_message_verbatim_when_it_says_activity`, `should_show_the_message_and_the_menu_again_when_the_api_responds_401` and `should_show_a_generic_error_and_the_menu_again_when_the_body_is_not_valid_json`.

### 8. Read the configuration and reject an invalid URL

Covers: AC6, AC12

- [x] In `cli`, add `StartupErrorView`, whose constructor takes only a `PrintStream`, with a method that shows the invalid URL error for the configured text. It takes no `LaunchService` and no URL object, because this error comes before the layers are wired with a URL, and `ConsoleMenu` cannot exist yet.
- [x] In `Main`, add a package-private method that reads `ASTROBOOKINGS_API_URL` once from the environment map, and gives `http://localhost:3000` when it is unset or blank.
- [x] In `Main`, add the validity check: the URL must parse, have the scheme `http` or `https` and a host, and any port must be between 1 and 65535. `URI` alone accepts `not-a-url`, `localhost:3000` and `http://localhost:99999`, and an out-of-range port would later throw from `HttpClient` as an `IllegalArgumentException`, not an `IOException`.
- [x] In `Main.run`, when the URL is invalid, have `StartupErrorView` show the error and return exit code 1. `Main` prints nothing itself, and `main` still only delegates to `run`.
- [x] Add to `MainTest`: `should_use_the_default_url_when_the_variable_is_unset`, `should_use_the_default_url_when_the_variable_is_blank` and `should_use_the_configured_url_when_the_variable_is_set`, calling the reading method so that no test depends on port 3000.
- [x] Add to `MainTest`: `should_show_an_error_and_return_exit_code_1_when_the_url_is_invalid`, repeated for `not a url`, `not-a-url`, `localhost:3000`, `ftp://localhost`, `http://` and `http://localhost:99999`. It asserts the error text, and it asserts that the output has no `Exception` and no menu.
- [x] Keep `should_return_exit_code_0_when_run_without_the_api`, unchanged.

### 9. Wire the layers in `Main`

Covers: AC1, AC2, AC6, AC7, AC10, AC11

- [x] In `Main.run`, when the URL is valid, build the `HttpClient`, `ApiClient` with a timeout constant, `LaunchService` and `ConsoleMenu` by constructor injection (ADR-7), run the menu with the injected streams, and return exit code 0. `Main` holds no presentation or use-case logic.
- [x] Add to `MainTest`, with the fake server's URL in the environment map: `should_list_launches_from_the_configured_url_when_the_user_selects_the_list` and `should_show_not_reachable_and_return_exit_code_0_when_the_api_is_down`, each with input that selects the list and then `0`.
- [x] Run `printf '1\n0\n' | ASTROBOOKINGS_API_URL=http://localhost:1 timeout 240 ./mvnw -B -ntp compile exec:java` and check that it shows `API not reachable at http://localhost:1`, then the menu, and ends with `BUILD SUCCESS` and no stack trace.

## Checks

Run each one in the foreground with its time limit.

- [x] `timeout 240 ./mvnw -B -ntp compile` passes.
- [x] `timeout 240 ./mvnw -B -ntp test` passes.

## Verification

The `tester` does this after the coder finishes, recording the method for each criterion as
`testing-e2e-console` asks. Write `LaunchListingE2eTest` in `dev.iman.astrobookings`, with
`@Tag("e2e")`. It calls `Main.run` with an environment map and a `ByteArrayInputStream` whose script
ends in `0` or at end of input, and asserts on the captured output. Any command that runs the client
gets its input through stdin.

The API's seed data holds `draft` and `published` launches, with dates in both forms, and no
`confirmed` one. `GET /activities` always has a `published` launch and has no error the client can
trigger, so some criteria fall back on unit tests, as stated below. Do not change the API's data.

- **AC1, by e2e test**: start with input `0` and find the list and exit options in the output.
- **AC2, by e2e test, and by unit test for `confirmed`**: select the list against the real API, read the launches from `GET /activities` in the test, and check that each `published` one appears with its eight fields and that a `draft` one does not. For `confirmed` and the other statuses, cite `LaunchServiceTest.should_keep_published_and_confirmed_launches_when_listing` and `LaunchServiceTest.should_leave_out_every_other_status_when_listing`.
- **AC3, covered by unit test**: the real API always has a published launch. Cite `ConsoleMenuTest.should_show_the_exact_message_when_no_launch_is_open_for_booking`.
- **AC4, by e2e test**: the real API sends `userId` and `createdAt`, which the constraints do not list, so a successful list proves it for an activity. For an error response, cite `ApiClientTest.should_ignore_the_field_when_an_error_holds_an_unknown_one`.
- **AC5, by e2e test**: check that the text does not contain "activity", in any case, in the output of a run with the exit option, an unknown option, an unreachable API and an invalid URL. These runs show no API data, which may use the word. For the launch list, cite `ConsoleMenuTest.should_not_say_activity_when_showing_its_own_texts`.
- **AC6, by e2e test**: list with the variable set to the test's API URL, and with it blank and unset, which reach the API when it runs on the default URL. Also cite `MainTest.should_use_the_default_url_when_the_variable_is_unset`, because the API may run elsewhere.
- **AC7, by unreachable API**: with the URL at a local port with nothing listening, select the list and then `0`. Check for the exact message with that URL, the menu shown again and no `Exception`. For the timeout, cite `ConsoleMenuTest.should_show_not_reachable_and_the_menu_again_when_the_call_times_out`.
- **AC8, covered by unit test**: the client cannot trigger an error on `GET /activities`. Cite `ConsoleMenuTest.should_show_the_message_and_field_errors_and_the_menu_again_when_the_api_responds_with_an_error`.
- **AC9, by e2e test**: enter an unknown option, then `0`. Check for the error, the menu shown again and exit code 0.
- **AC10, by e2e test**: enter `0` and check for exit code 0 and no `Exception`.
- **AC11, by e2e test**: use input that ends with no exit option, both empty and after selecting the list. Check for exit code 0 and no `Exception`.
- **AC12, by e2e test and by command**: call `Main.run` with an invalid URL and check for the error, exit code 1 and no `Exception`. Then run `timeout 240 ./mvnw -B -ntp package`, followed by `ASTROBOOKINGS_API_URL='not a url' timeout 60 java -jar target/astrobookings-client-0.1.0-SNAPSHOT.jar </dev/null`, and check that the process exits with code 1. Use the jar and not `exec:java`, whose `blockSystemExit` keeps the code from reaching the shell.
- **Earlier build criteria**: run `echo "0" | ASTROBOOKINGS_API_URL=http://localhost:1 timeout 240 ./mvnw -B -ntp compile exec:java` and check for `BUILD SUCCESS`, then run `echo "0" | timeout 60 java -jar target/astrobookings-client-0.1.0-SNAPSHOT.jar` and check for exit code 0. `BuildSetupE2eTest` must still pass, because its input exits before any HTTP call.
