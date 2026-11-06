# Plan: Web interface

- **Spec**: [feat-web-ui](feat-web-ui.spec.md)
- **Branch**: feat/web-ui

## Steps

Steps 1 to 9 are for the `coder`. The `tester` follows [Verification](#verification), which has
no checkboxes because the tester does not change this plan.

### Rules for every step

- `java -jar ... --web` does not end by itself. Any manual run uses `timeout` and is stopped afterwards, and a console run gets its input through stdin, such as `echo "0" | timeout 60 java -jar ...`. Never leave anything waiting. Step 8 has the command to use.
- Server tests start the server on port 0, use `@TempDir` for the session file and stop the server when finished (`@AfterEach`). They never bind 8080 or touch `~/.astrobookings`. Manual runs pass `-Duser.home=target/manual-home` for the same reason.
- Unit tests use `FakeApiServer`, never port 3000. It answers in the order responses are queued, so queue them in the exact order the handler calls the API, and assert on `receivedRequests()` and `receivedBodies()` to prove the absence of a request.
- Tests send requests with a `java.net.http.HttpClient`, with a timeout on every request. It does not follow redirects by default, which lets a test assert on the 303 and its `Location`. Assert that the `Location` starts with a single `/`.
- `web` imports only `service`, `model` and the JDK (ADR-14): never `cli`, `api` or `session`. `Main` alone wires the layers (ADR-7).
- The server uses its default executor, so `setExecutor` is not called with a thread pool, and there are no locks, because `SessionService` holds unsynchronized state and is safe only with one request at a time (ADR-14).
- Every text from the user or the API goes through `HtmlEscaper`, in element content and in attributes. API messages are otherwise verbatim (ADR-3, ADR-15).
- The client's own texts say "launch" and never "activity", including field names and CSS classes.
- A page is rendered after its `service` call, so that the logged-in line shows the session as it is after that call.
- A failure shown in a page answers 200 with the page, not a redirect: only the checks under the routes table use another status, and an unexpected failure answers 500. Tests therefore assert on "not 303" and on the text.
- Do not write end-to-end tests or documentation.

### Routes

The spec does not fix them, so this plan does. One context on `/` holds the router, so that exact matching and the `Origin` check cover every path.

| Method | Path             | Purpose                                                                                       |
|--------|------------------|-----------------------------------------------------------------------------------------------|
| GET    | `/`              | Launches page, and the target of the redirect after registering, logging in and logging out.  |
| GET    | `/register`      | Registration form.                                                                            |
| POST   | `/register`      | Registers: 303 to `/`, or the form again.                                                     |
| GET    | `/login`         | Login form, with the statement that the API's login is broken.                                |
| POST   | `/login`         | Logs in: 303 to `/`, or the form again.                                                       |
| POST   | `/logout`        | Logs out: 303 to `/`.                                                                         |
| GET    | `/book`          | Booking form.                                                                                 |
| POST   | `/book`          | Books: 303 to `/bookings/<id>`, or the form again. Fields `launchId` and `seats`.             |
| GET    | `/bookings`      | My bookings.                                                                                  |
| GET    | `/bookings/<id>` | Booking detail, with the id as one percent-encoded path segment.                              |
| GET    | `/style.css`     | The CSS file from the classpath.                                                              |

An unknown path answers 404, and a known path with another method answers 405, so `GET /logout` changes nothing. `/book` is apart from `/bookings/...`, so no booking id can collide with a form's path. A foreign `Origin` on any POST answers 403 before all of this.

### Texts

Reuse the console's texts, so that both interfaces say the same: `No launches open for booking.`, `Logged in as <username> (<email>)`, `Register or log in first.`, `Your session is no longer valid. Please register again.`, `You have no bookings.`, `The seats must be a whole number of at least 1.`, `This launch is not open for booking.`, `No booking was created.`, `The session could not be saved.`, `The session file could not be deleted.`, `The session file could not be read. Continuing with no session.` and `The terms were not accepted, so you were not registered.` Avoid apostrophes in the web layer's own texts, so that a test can match them as plain text.

### 1. Move the date formatter and the seats check to `service`

Covers: spec Solution (service, cli)

- [ ] Move `LaunchDateFormatter` from `cli` to `service`, making the class and `format` public, and update `ConsoleMenu`, `BookingFlow` and `BookingRetrievalFlow`. Move `LaunchDateFormatterTest` to the `service` test package with no other change.
- [ ] The seats check is not one piece today: `NumberPrompt.readWholeNumber` holds the digit rule and `promptForNumber` turns 0 into cancelling. Add a public `SeatsCheck` to `service` with `readWholeNumber(String)`, which returns `Optional<BigInteger>` for ASCII digits of any length, and `readSeats(String)`, which also requires at least 1.
- [ ] Make `NumberPrompt` call `SeatsCheck.readWholeNumber`, which it also uses for the launch and booking numbers, and keep the 0 that cancels in `NumberPrompt` (ADR-13, ADR-14). `Main` does not change here.
- [ ] Add `SeatsCheckTest` in `service`: `should_accept_a_whole_number_of_at_least_1_when_reading_seats`, repeated for `1`, `03` and a number too long for an `int`, and `should_reject_the_entry_when_reading_seats`, repeated for `0`, `00`, `1.5`, `-1`, `+1`, text, a blank entry and a non-ASCII digit.
- [ ] No existing test changes apart from the moved one's package, and `ConsoleMenuTest` still passes, which proves that the console's behaviour stays the same.

### 2. Book by a launch id against a fresh list

Covers: AC6, AC7

- [ ] `BookingService.book` takes a `Launch` and a form has only an id. Add `BookingService.book(String launchId, BigInteger seats)`: it requires the session, looks the id up in a fresh `listBookableLaunches`, raises `LaunchNotBookableFailure` when the id is not there and otherwise delegates to the existing `book`. A `Launch` is never built from form fields (ADR-12, ADR-14). The existing `book` stays as it is, for the console.
- [ ] Add to `BookingServiceTest`: `should_send_the_id_of_the_launch_in_the_fresh_list_when_booking_by_id`, `should_fail_as_not_bookable_without_a_booking_request_when_the_id_is_not_in_the_list`, `should_fail_as_not_bookable_without_a_booking_request_when_the_id_is_of_a_launch_that_is_not_open`, for a draft in the API's response, and `should_fail_as_no_session_without_calling_the_api_when_booking_by_id_with_no_session`.

### 3. Serve an empty site safely on 127.0.0.1

Covers: AC11, AC12, spec Solution (unreadable session file)

- [ ] Add `web` with a `package-info.java`, and a public `WebServer` built from `LaunchService`, `SessionService` and `BookingService`. `WebServer.start(..., port)` binds `HttpServer` to the literal address 127.0.0.1, not `getLoopbackAddress` or a wildcard, loads the saved session and starts. It offers `port()`, read from the bound address, and `stop()`. It accepts port 0, because the 1 to 65535 rule belongs to `Main` (ADR-16). A failed bind becomes a new unchecked `WebServerStartFailure` in `web`, with the `IOException` as its cause.
- [ ] Catch `SessionFileFailure` when loading the session and keep it as a flag, so that the layout shows `The session file could not be read. Continuing with no session.` while there is no session (ADR-11, ADR-17).
- [ ] Add a package-private `HtmlEscaper`, for `&`, `<`, `>`, `"` and `'`, a `WebResponse` record for an HTML page, a 303 with a `Location`, or the CSS, written as UTF-8 with `text/html; charset=utf-8`, and a `PageLayout` with the navigation, the `Logged in as` line, the logout form when there is a session, and the link to `/style.css`.
- [ ] Add a `FormData` parser for a URL-encoded UTF-8 body. A missing field reads as empty text, and the first of repeated fields wins.
- [ ] Add a router, which holds the routes table as the next steps add each page. Its first check applies `OriginCheck` to each POST: every `Origin` value present must be exactly `http://127.0.0.1:<bound port>` or `http://localhost:<bound port>`, with a fixed list that does not read `Host`, a POST with no `Origin` is accepted, and anything else, including `null` and an empty value, answers 403 (ADR-17). The router catches every remaining `RuntimeException`, including a malformed form body, and answers 500 with a generic error page and no stack trace, because the JDK server would otherwise drop the connection.
- [ ] Add `src/main/resources/web/style.css`, one simple file served from the classpath with `getResourceAsStream`, so that it works from the jar. `pom.xml` does not change, because the shade plugin has no filters and includes project resources.
- [ ] Add `HtmlEscaperTest` and `WebServerTest`, with a shared test fixture that builds the layers as `Main` does, with the fake API, a `@TempDir` file and port 0: `should_escape_each_html_character_when_escaping`, `should_listen_only_on_127_0_0_1_when_started`, which checks the bound address and that a request to 127.0.0.2 fails to connect, `should_report_the_bound_port_when_started_on_port_0`, `should_fail_with_a_start_failure_when_the_port_is_busy`, which holds the port with a `ServerSocket` on 127.0.0.1, `should_answer_404_when_the_path_is_unknown`, `should_answer_405_when_the_method_does_not_match`, `should_serve_the_css_when_requested`, `should_show_a_notice_when_the_session_file_cannot_be_read`, `should_answer_403_without_calling_the_api_when_a_post_has_a_foreign_origin`, repeated for a foreign site, another port, `null`, an empty value and another scheme, `should_keep_the_session_when_the_origin_is_foreign`, `should_accept_a_post_when_the_origin_is_the_server_itself`, repeated for both addresses with the bound port, and `should_accept_a_post_when_it_has_no_origin`. Until the actions exist, "accept" means any answer other than 403, and the notice appears on the 404 page, which uses the layout.

### 4. Show the launches and present every failure

Covers: AC1, AC10, AC12

- [ ] Add a package-private `FailureView` in `web`, with an exhaustive `switch` on the sealed `ApiFailure`, as `ConsoleMenu.showFailure` has, that returns escaped HTML: `API not reachable at <url>` with the configured URL, the message of an `UnauthorizedFailure` or `ApiErrorFailure` followed by the field and message of each field error, and the texts above for the rest. `SessionRejectedFailure` and `NoSessionFailure` include a link to `/register`, and the latter also to `/login`. `SessionFileFailure` is not an `ApiFailure`, so each handler that can get one catches it itself (steps 5 and 6).
- [ ] Add the launches page on `GET /`, from `LaunchService.listOpenLaunches`: the name, price with `toPlainString`, date through `LaunchDateFormatter`, duration, location, minimum and maximum participants and status, or `No launches open for booking.`
- [ ] Add a package-private test class for this page, `LaunchesPageTest`: `should_show_every_field_of_an_open_launch_when_opening_the_launches_page`, `should_leave_out_a_launch_when_its_status_is_not_open`, repeated for `draft`, `sold-out`, `done`, `cancelled` and an unknown status, `should_show_the_no_launches_message_when_none_qualifies`, `should_escape_markup_when_a_launch_has_it_in_its_text`, `should_keep_non_ascii_text_when_a_launch_has_it`, `should_show_not_reachable_with_the_configured_url_when_the_api_is_down`, `should_show_not_reachable_when_the_api_call_times_out`, with a short `ApiClient` timeout and `blockResponses`, `should_show_the_message_and_each_field_error_when_the_api_responds_with_an_error`, and `should_show_a_generic_error_with_no_stack_trace_when_the_response_is_not_json`.

### 5. Register, log in and log out

Covers: AC2, AC3, AC4, AC5, AC10, AC12

- [ ] Add the registration page and action. With the terms not accepted, show the form again with no API call. Otherwise call `SessionService.register`, with the username and email trimmed as the console does and the password as typed. Success answers 303 to `/`. An `ApiFailure` shows the form again with the failure, the typed username and email escaped, and an empty password. A `SessionFileFailure` shows `The session could not be saved.` in the same way. The API decides on empty fields, as in the console.
- [ ] Add the login page and action. The page always states that the login of the API is currently broken and rejects every attempt. `InvalidCredentialsFailure` shows the form again, with the typed email and an empty password, the explanation that the API did not accept the email or password and that its login is broken today, and a link to `/register`. Other failures and a `SessionFileFailure` are shown as in registration.
- [ ] Add the logout action on POST, through `SessionService.logout`: a 303 to `/`, also when no file exists. A `SessionFileFailure` shows `The session file could not be deleted.` with no redirect. A saved session or a logout clears the unreadable-file flag from step 3.
- [ ] Add `SessionPagesTest` with the fake API: `should_send_the_fields_with_terms_true_when_registering`, `should_save_the_session_without_the_password_and_redirect_with_303_when_registering`, `should_show_who_is_logged_in_on_the_page_it_redirects_to_when_registering`, `should_save_the_session_and_redirect_with_303_when_login_succeeds`, `should_show_who_is_logged_in_when_login_succeeds`, `should_show_the_form_without_a_request_when_the_terms_are_not_accepted`, `should_show_the_form_with_the_typed_values_and_an_empty_password_when_the_email_is_already_registered`, `should_start_no_session_when_the_email_is_already_registered`, `should_escape_the_typed_values_in_their_attributes_when_showing_the_form_again`, `should_never_echo_the_password_when_showing_a_form_again`, repeated for registration and login, `should_state_that_the_login_is_broken_when_opening_the_login_page`, `should_explain_and_link_to_registration_without_a_redirect_when_login_responds_401`, `should_keep_the_session_when_login_responds_401`, `should_delete_the_file_and_redirect_with_303_when_logging_out`, `should_redirect_with_no_error_when_logging_out_with_no_file`, `should_not_log_out_when_a_get_reaches_the_logout_path`, `should_show_an_error_with_no_stack_trace_when_the_session_cannot_be_saved`, repeated for registration and login, with a folder in place of the file, `should_show_an_error_with_no_stack_trace_when_the_session_file_cannot_be_deleted`, with a non-empty folder in place of the file, and `should_answer_a_page_with_no_stack_trace_when_the_form_body_is_malformed`.

### 6. Book seats

Covers: AC5, AC6, AC7, AC8, AC10, AC12

- [ ] Add the booking page on `GET /book`, from `BookingService.listBookableLaunches`: a form with one choice for each launch, whose value is its id, and a text field for the seats, or `No launches open for booking.` With no session it shows the no-session failure and makes no API call. A number field is avoided, so that the check in `service` is the one the user sees.
- [ ] Add the action on `POST /book`, checked in this order: with no session, show the no-session failure with no API call. With seats that fail `SeatsCheck.readSeats`, after trimming, show the seats error and the form, with no `POST /bookings`. Then call `BookingService.book(launchId, seats)`. Success answers 303 to `/bookings/<encoded id>`, whose detail page (step 7) shows the id, launch name, seats and `paymentStatus`. 0 is simply invalid here (ADR-14).
- [ ] A failed action shows the form again, from a new fresh list, with the failure, the typed seats escaped and the chosen launch selected when it is still offered. If that list also fails, the page keeps the original failure and adds no form, so a capacity or 402 message is never lost. `SessionRejectedFailure` shows only its message and link, because there is no session left for a form. A 402 adds `No booking was created.`
- [ ] Add `BookingPagesTest`: `should_offer_only_the_open_launches_when_opening_the_booking_page`, `should_show_the_no_launches_message_when_none_can_be_booked`, `should_send_the_launch_id_and_seats_with_the_bearer_token_when_booking`, `should_redirect_with_303_to_a_relative_path_when_the_api_responds_201`, `should_show_the_booking_on_the_page_it_redirects_to_when_booking`, `should_show_an_error_with_no_booking_request_when_there_is_no_session`, `should_show_an_error_with_no_booking_request_when_the_seats_are_invalid`, repeated for `0`, `1.5`, `-1`, text and blank, `should_show_an_error_with_no_booking_request_when_the_launch_is_not_in_the_fresh_list`, repeated for a forged id and a launch now `draft`, `should_show_the_capacity_message_unchanged_on_the_form_when_the_api_responds_400`, `should_show_the_message_and_that_no_booking_was_created_when_the_api_responds_402`, `should_keep_the_failure_when_the_fresh_list_also_fails_after_a_failed_booking`, `should_delete_the_file_and_ask_to_register_again_with_a_link_when_booking_responds_401`, `should_show_not_reachable_when_booking_with_the_api_down`, and `should_escape_the_typed_seats_and_the_launch_id_when_showing_the_form`.

### 7. Show my bookings and a booking's detail

Covers: AC5, AC6, AC9, AC10, AC12

- [ ] Add `GET /bookings`, from `BookingService.listBookings`: each booking's id, launch name, seats and `paymentStatus` as the API sends it, with a link to its detail, or `You have no bookings.`
- [ ] Add `GET /bookings/<id>`, from `BookingService.getBooking`: the id, seats, creation date, `paymentId`, `Payment id: none` when it is null as in the console, `paymentStatus`, and the launch's name, price, date, duration, location and status. Format dates through `LaunchDateFormatter`, and show price and duration with `toPlainString`.
- [ ] Take the id from the raw path, as exactly one segment, and percent-decode it without turning `+` into a space, because `getPath` would merge an encoded `/`. A malformed encoding, an empty segment, `.` or `..` answers 404 with no API call (`project/BACKLOG.md`, "Booking id `..`"). Links and the redirect percent-encode the id, and links are also attribute-escaped.
- [ ] With no session, both pages show the no-session failure, with no request to `/bookings`. `SessionRejectedFailure` and every other failure go through `FailureView`.
- [ ] Add `BookingRetrievalPagesTest`: `should_request_the_bookings_with_the_bearer_token_when_opening_my_bookings`, `should_show_each_booking_with_a_link_to_its_detail_when_opening_my_bookings`, `should_show_the_payment_status_as_the_api_sends_it`, `should_show_a_message_when_the_user_has_no_bookings`, `should_request_the_id_with_the_bearer_token_when_opening_a_detail`, `should_show_every_field_when_opening_a_detail`, `should_say_there_is_no_payment_id_when_the_booking_has_none`, `should_tell_the_user_to_register_or_log_in_with_no_request_when_opening_either_page_with_no_session`, `should_delete_the_file_and_ask_to_register_again_with_a_link_when_either_page_responds_401`, `should_show_the_message_when_the_booking_is_not_found`, `should_show_not_reachable_when_opening_either_page_with_the_api_down`, `should_keep_an_encoded_slash_in_one_segment_when_opening_a_detail`, `should_answer_404_with_no_api_call_when_the_id_is_a_dot_segment`, and `should_escape_an_id_with_markup_in_the_link_and_the_text`.

### 8. Start one interface or the other from `Main`

Covers: AC11

- [ ] `main` today calls `System.exit(run(...))`, so the `--web` branch must not go through it. In `main`, with `--web` in the arguments, call a package-private `startWeb(environment, output, sessionFile)`, and call `System.exit(1)` only when it returns an empty `Optional<WebServer>`. After a successful start `main` returns, and the server's own thread keeps the process alive. Without `--web`, `main` keeps its current line, and `run` keeps its signature, so that `MainTest` does not change. This follows ADR-16, which wins over the `coding-java` rule that `main` only delegates to `run`.
- [ ] `startWeb` has no `InputStream`, so web mode cannot read standard input. It reads the URL and checks it with the existing `isValidApiUrl`, then reads `ASTROBOOKINGS_WEB_PORT` once: unset or blank is 8080, and a valid value is ASCII digits from 1 to 65535, reusing `MIN_PORT` and `MAX_PORT`, with no `NumberFormatException` for a value too long for an `int`. Share the layer wiring with `buildMenu` in one private method (ADR-7). Rename `EXIT_CODE_INVALID_URL` to fit both uses.
- [ ] Add to `StartupErrorView`: `showInvalidWebPort(configuredPort)`, `showWebPortUnavailable(port)` and `showWebAddress(port)`, which prints `http://127.0.0.1:<port>/`. `Main` calls them, catching `WebServerStartFailure`, and `web` does not depend on `cli` (ADR-16).
- [ ] Add to `MainTest`: `should_detect_web_mode_when_the_argument_is_present`, `should_use_port_8080_when_the_web_port_is_unset_or_blank`, which tests the reading alone and binds nothing, `should_show_an_error_and_start_nothing_when_the_web_port_is_invalid`, repeated for `abc`, `0`, `65536`, `-1`, `+80`, `80.5`, ` 8080` and `99999999999`, `should_show_an_error_and_start_nothing_when_the_web_port_is_busy`, `should_show_an_error_and_start_nothing_when_the_api_url_is_invalid_in_web_mode`, and `should_start_the_server_and_show_its_address_when_the_web_port_is_free`, which takes a free port from a `ServerSocket` and stops the server in `finally`. Assert that no output holds `Exception`.
- [ ] Run this manual check, which ends by itself. It proves that the jar holds the CSS, and that the process stays up until `timeout` stops it, with exit status 124 as the expected result:

  ```bash
  timeout 240 ./mvnw -B -ntp clean package -DskipTests
  jar tf target/astrobookings-client-*.jar | grep web/style.css
  ASTROBOOKINGS_WEB_PORT=18080 timeout 15 java -Duser.home=target/manual-home -jar target/astrobookings-client-*.jar --web </dev/null >target/manual-web.log 2>&1 &
  curl -s -o /dev/null -w '%{http_code}\n' --retry 10 --retry-connrefused --retry-delay 1 --max-time 12 http://127.0.0.1:18080/style.css
  wait $!; echo "exit=$?"
  curl -s -o /dev/null --max-time 2 http://127.0.0.1:18080/ && echo "STILL RUNNING"
  ```

  If it prints `STILL RUNNING`, find the process with `netstat -ano | grep :18080`, stop it with `taskkill //F //PID <pid>`, and report it. Do not stop every `java` process. Also run `echo "0" | timeout 60 java -Duser.home=target/manual-home -jar target/astrobookings-client-*.jar` and check that the console menu appears and ends.

### 9. Check the whole interface against its rules

Covers: AC1, AC12, ADR-14

- [ ] Add `should_not_say_activity_in_any_page`, over every page and form, with and without a session, using API responses free of that word.
- [ ] Add `should_escape_markup_from_the_api_in_every_page`, with a launch name, location, status, payment status, payment id, booking id, session username and API message that each hold `<script>` and `"'`, and check that none appears unescaped on any page.
- [ ] Add `should_not_change_the_session_file_when_any_get_is_requested`, over every GET route.
- [ ] Check with a search that `src/main/java/dev/iman/astrobookings/web` imports no `cli`, `api` or `session`, and that no page holds `<script`.

## Checks

Run each one in the foreground with its time limit.

- [ ] `timeout 240 ./mvnw -B -ntp compile` passes.
- [ ] `timeout 240 ./mvnw -B -ntp test` passes, with every console test still green.

## Verification

The `tester` does this after the coder finishes, recording the method for each criterion.

The `testing-e2e-console` skill applies, except its "Drive the menu" rule, which does not apply to web tests. ADR-18 applies instead: real HTTP requests to the web server against the real API. Its other rules still apply: one test per criterion, the `ACn_should_..._when_...` naming, a unique email per run, the `@Tag("e2e")` tag, `@TempDir` and the rules on shared data.

Write `WebUiE2eTest` in `dev.iman.astrobookings.web`. Build the layers as `Main` does, with the URL from `ASTROBOOKINGS_API_URL` or the default. Start `WebServer` on port 0 with a `@TempDir` session file, stop it in `@AfterEach`, and give each request a timeout, with redirects not followed. Create launches as `BookingRetrievalE2eTest` does, with a unique name, a future date, a price of 7 and a whole duration, and never with a fractional value (`project/BACKLOG.md`). Check only the bookings the test creates, and avoid a total price that is a multiple of 1000. Any run of the jar follows the command in step 8, with `timeout`, `</dev/null` and the check that it stopped, or stdin for the console.

- **AC1, by e2e test**: create a `published`, a `confirmed` and a `draft` launch. `GET /` shows every field of the first two and not the third, and does not say "activity". For the empty message, which shared data cannot reach, cite `LaunchesPageTest.should_show_the_no_launches_message_when_none_qualifies`.
- **AC2, by e2e test**: `POST /register` with a unique email and the terms. Check the 303, a `Location` with a relative path, a session file with the token, username and email and no password, and `Logged in as` on the redirect's page. For a successful login, cite `SessionPagesTest.should_save_the_session_and_redirect_with_303_when_login_succeeds`.
- **AC3, by e2e test**: register twice with one email, and check that the second shows `Email is already registered`, the typed values and an empty password, with no 303, and that the session file still holds the first user. Without the terms, check for no 303 and no session file. For the absence of a request, cite `SessionPagesTest.should_show_the_form_without_a_request_when_the_terms_are_not_accepted`.
- **AC4, by e2e test**: `GET /login` states that the login is broken. `POST /login` shows the explanation, the link to `/register`, an empty password and no 303.
- **AC5, by e2e test**: log out with a session and again with no file, each a 303, with the file gone. Start with an invalid token in the file, open `/bookings`, and check for the request to register again, the link and that the file is gone, then repeat for `/bookings/<id>` and `POST /book`. `GET /logout` is a 405 and keeps the file.
- **AC6, by e2e test**: `GET /book` offers the `published` and `confirmed` launches and not a `draft`. `POST /book` answers a 303 to a relative path, whose page shows the booking id, launch name, seats and `paid`, and `GET /bookings` with the token holds that booking on that launch. A reload of the redirect's page creates no second booking. For the exact header and body, cite `BookingPagesTest.should_send_the_launch_id_and_seats_with_the_bearer_token_when_booking`. For the empty message, cite `BookingPagesTest.should_show_the_no_launches_message_when_none_can_be_booked`.
- **AC7, by e2e test**: post with no session, with seats of `0`, `1.5` and text, and with a forged id and a `draft` launch's id. Check for an error and no 303 each time, and that `GET /bookings` with the token shows no new booking.
- **AC8, by e2e test**: request more seats than `maxParticipants`, and check that the form shows `Capacity exceeded. Available: N, Requested: M` with its numbers. For the 402, cite `BookingPagesTest.should_show_the_message_and_that_no_booking_was_created_when_the_api_responds_402` (ADR-18).
- **AC9, by e2e test**: with two created bookings, check `GET /bookings` and the detail of the second, field by field, against `GET /bookings/:id` with the token. A new customer sees the no-bookings message, after first asserting that their `GET /bookings` is empty, and otherwise failing with a message that asks to restore the data as ADR-5 says. With no session, both pages tell the user to register or log in. For the exact header and the absence of a request, cite `BookingRetrievalPagesTest`.
- **AC10, by unreachable API**: with the URL at a local port with nothing listening, open every page and submit every action, with a saved session where one is needed, and check for `API not reachable at <url>`, no `Exception` and that the session file remains. Against the real API, a repeated email shows the message followed by the field and its message. For a timeout, cite `LaunchesPageTest.should_show_not_reachable_when_the_api_call_times_out`.
- **AC11, by e2e test and by running the jar**: the test checks that a request to 127.0.0.2 fails to connect while one to 127.0.0.1 works. With the jar, check that `--web` prints the address and stays up until `timeout` stops it, that an invalid `ASTROBOOKINGS_WEB_PORT` and a busy port each give a readable error, no stack trace and exit code 1, and that `echo "0" | ...` with no `--web` shows the console menu. For the default port, cite `MainTest.should_use_port_8080_when_the_web_port_is_unset_or_blank`.
- **AC12, by e2e test**: create a launch whose name holds `<b>"'`, and check for the escaped text on `/`, on `/book`, including inside the option's attributes, and on a booking's pages. Also check a typed email with markup when the form is shown again. A POST with a foreign `Origin` answers 403, creates no booking and keeps the session, while each own origin, with the bound port, and no `Origin` are accepted. For the absence of an API call, cite `WebServerTest.should_answer_403_without_calling_the_api_when_a_post_has_a_foreign_origin`.
- **Earlier criteria**: `LaunchListingE2eTest`, `SessionE2eTest`, `BookingCreationE2eTest`, `BookingRetrievalE2eTest` and `BuildSetupE2eTest` must still pass, which proves that the console's behaviour has not changed.
