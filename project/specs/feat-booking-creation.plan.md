# Plan: Booking creation

- **Spec**: [feat-booking-creation](feat-booking-creation.spec.md)
- **Branch**: feat/booking-creation

## Steps

Steps 1 to 8 are for the `coder`. The `tester` follows [Verification](#verification), which has
no checkboxes because the tester does not change this plan.

Rules for every step:

- Every run of the menu, in a test or a command, ends by itself. A test injects an `InputStream` whose script ends in `0` or at end of input. A command gets its input through stdin. Nothing waits for input.
- A test that makes an HTTP call uses `FakeApiServer`, never port 3000. A test that needs a session file uses `@TempDir`, never `~/.astrobookings`.
- The word "activity" stays in `api` and in the DTOs (ADR-3). The client's own texts say "launch".

### 1. Add the booking records and DTOs

Covers: AC3, AC5

- [x] In `model`, add the `LaunchSummary` record with the ADD's fields. `price` and `duration` are `BigDecimal`, so that a fractional duration can be read and shown with no rounding (`project/BACKLOG.md`, "Booking specs"). `Launch.duration` stays an `int`, because `fix-launch-parsing-robustness` owns it. Date and status stay as text.
- [x] In `model`, add the `Booking` record with the ADD's fields, with `participants` as an `int` and `paymentStatus` as text, as the API sends it. `id`, `launchId`, `userId`, `createdAt` and `paymentStatus` are required in the compact constructor. `launch` and `paymentId` may be null, and its Javadoc says when: `launch` is absent on a created booking, and the API declares `paymentId` optional.
- [x] In `model`, add the DTO records with the ADD's fields: `CreateBookingRequest`, `BookingResponse` and `BookingActivityResponse`. `CreateBookingRequest.participants` is a `BigInteger` (step 7), `BookingResponse.participants` is an `Integer`, so that a missing value reads as null, and `BookingActivityResponse.price` and `duration` are `BigDecimal`.
- [x] Add `model/BookingTest`: `should_accept_no_launch_and_no_payment_id_when_built_for_a_created_booking` and `should_fail_when_a_required_field_is_null`, repeated for each required field.

### 2. Call `POST /bookings` with the token

Covers: AC3, AC4

- [x] In `ApiClient`, add `createBooking(token, launchId, seats)`, which sends `CreateBookingRequest` to `/bookings` with `Content-Type: application/json`, `Authorization: Bearer <token>` and the existing timeout, and returns a `Booking`. Do not add the header to `GET /activities` or to the session calls.
- [x] Map `BookingResponse` to `Booking` in `ApiClient`, translating `activityId` to `launchId` and `activity` to `LaunchSummary` when it is present, so that `feat-booking-retrieval` can reuse the mapping.
- [x] Turn each malformed 2xx body into the generic `ApiErrorFailure`, as `toSession` does: invalid JSON, the JSON literal `null`, a missing or blank `id`, `activityId`, `userId`, `createdAt` or `paymentStatus`, a missing or fractional `participants`, or an `activity` that is present with a missing field. No malformed body may reach a compact constructor and raise a `NullPointerException`.
- [x] Add to `ApiClientTest`: `should_send_the_launch_id_as_activity_id_and_the_seats_as_participants_when_creating_a_booking`, which checks the method, path and exact body, `should_send_the_token_as_a_bearer_header_when_creating_a_booking`, `should_return_the_booking_with_no_launch_when_the_response_has_no_activity`, `should_return_the_launch_summary_when_the_response_has_an_activity`, `should_keep_a_fractional_duration_when_the_response_has_an_activity`, `should_ignore_the_other_fields_when_the_booking_response_has_them` and `should_fail_with_a_generic_error_when_the_booking_response_is_malformed`, repeated for the bodies above.

### 3. Map the failures of `POST /bookings`

Covers: AC7, AC8

- [x] Reuse `throwIfFailed`: the capacity 400 and the 402 become an `ApiErrorFailure` with the status and the message, with no parsing of the message, and a 401 becomes `UnauthorizedFailure`.
- [x] A session file can hold a token with a control character, which `HttpRequest.Builder.header` refuses with an `IllegalArgumentException`. In `createBooking`, turn it into the generic `ApiErrorFailure` with `NO_RESPONSE_STATUS`, so that no stack trace reaches the user.
- [x] Add to `ApiClientTest`: `should_keep_the_status_and_exact_message_when_the_capacity_is_exceeded`, `should_keep_the_status_and_exact_message_when_the_payment_is_rejected`, `should_fail_as_unauthorized_when_creating_a_booking_responds_401`, `should_fail_as_not_reachable_when_creating_a_booking_with_the_api_down` and `should_fail_with_a_generic_error_when_the_token_cannot_be_sent_in_a_header`.

### 4. Add the booking use case

Covers: AC1, AC2, AC4, AC9, AC12

- [x] In `model`, add `NoSessionFailure` and `LaunchNotBookableFailure` to the sealed `ApiFailure`, raised only by `service`, and update its Javadoc, which counts the types. `feat-booking-retrieval` reuses `NoSessionFailure`.
- [x] In `ConsoleMenu.showFailure`, add their cases, which show the client's own texts: `Register or log in first.` and `This launch is not open for booking.` The `switch` stays with no `default`.
- [x] In `LaunchService`, add a public method that says whether a launch is open for booking, and use it in `listOpenLaunches`, so that the list and the booking share one allow-list (ADR-12).
- [x] In `service`, add `BookingService`, with `ApiClient`, `LaunchService` and `SessionService` as constructor parameters. `listBookableLaunches` raises `NoSessionFailure` with no session, with no API call, and otherwise returns `LaunchService.listOpenLaunches`, one fresh list for each selection of the option. A 401 on this public call stays an API error (feat-launch-listing AC8).
- [x] In `BookingService`, add `book(launch, seats)`. It raises `NoSessionFailure` with no session and `LaunchNotBookableFailure` when the launch fails the allow-list, in both cases with no API call. It reads the token from `SessionService.currentSession` and calls `createBooking` inside `SessionService.runAuthenticated`, so that a 401 ends the session and deletes the file.
- [x] Add `service/BookingServiceTest`, with the fake server and `@TempDir`: `should_fail_as_no_session_without_calling_the_api_when_listing_with_no_session`, `should_return_only_published_and_confirmed_launches_when_listing`, `should_return_no_launches_when_none_is_open`, `should_fail_as_not_bookable_without_calling_the_api_when_the_launch_is_not_open`, repeated for `draft`, `sold-out`, `done`, `cancelled` and an unknown status, `should_fail_as_no_session_without_calling_the_api_when_booking_with_no_session`, `should_send_the_saved_token_when_booking`, `should_return_the_booking_when_the_launch_is_published`, the same for `confirmed`, `should_delete_the_file_and_fail_as_session_rejected_when_booking_responds_401` and `should_keep_the_session_when_booking_fails_otherwise`.
- [x] Add to `LaunchServiceTest`: `should_say_a_launch_is_open_only_when_it_is_published_or_confirmed`.
- [x] Add to `ConsoleMenuTest`: `should_ask_to_register_or_log_in_when_there_is_no_session` and `should_say_the_launch_is_not_open_when_the_booking_is_refused`, both calling `showFailure` directly, as `should_ask_to_register_again_when_the_session_is_rejected` does.

### 5. Add the menu option, wire it and show the launches

Covers: AC1, AC9, AC12

- [x] In `cli`, add a package-private `BookingFlow`, with `BookingService`, the menu's `promptFor` as a function, the `PrintStream` and the menu's `showFailure` as a consumer. Sharing `promptFor` keeps one `BufferedReader` over the input. It goes in its own class because `ConsoleMenu` is already long (`project/BACKLOG.md`), and the existing flows stay where they are.
- [x] In `ConsoleMenu`, add `5. Book seats`, always shown, also with no session, so that the numbers do not change. Add `BookingService` to both constructors, and build `BookingFlow` there.
- [x] In `Main.buildMenu`, build `BookingService` and pass it to the menu (ADR-7). Update the `ConsoleMenuTest` helpers, with no change to what the existing tests assert.
- [x] In `BookingFlow`, get the list from `listBookableLaunches`. With no launches, show `No launches open for booking.` and return, asking for nothing. Otherwise show each launch as its number from 1, its name, its date through `LaunchDateFormatter` and its price, so that launches with the same name can be told apart.
- [x] Catch `ApiFailure` around the whole flow and pass it to `showFailure`, so that the flow returns to the menu with no stack trace.
- [x] Add to `ConsoleMenuTest`: `should_show_the_option_to_book_seats_when_started`, `should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_booking_with_no_session`, `should_number_only_the_open_launches_from_1_when_booking`, `should_show_the_exact_message_and_ask_for_nothing_when_no_launch_is_open_for_booking` and `should_show_not_reachable_and_the_menu_again_when_booking_with_the_api_down`.
- [x] Run `printf '0\n' | ASTROBOOKINGS_API_URL=http://localhost:1 timeout 240 ./mvnw -B -ntp compile exec:java` and check that the menu shows the new option and that it ends with `BUILD SUCCESS` and no stack trace. This reads the real session file, so select no option that writes or deletes it.

### 6. Choose a launch by its number

Covers: AC6, AC11

- [x] In `BookingFlow`, add a method that reads an entry as a whole number and returns an empty result otherwise. It accepts only the ASCII digits, and it compares by value, so `03` is 3 and `00` is 0. A sign, a decimal, a blank line and a non-ASCII digit are invalid. It uses `BigInteger`, so that a very long number does not raise a `NumberFormatException`.
- [x] In `BookingFlow`, prompt with `Launch number (0 to cancel):`. On a number in the list, resolve it to the launch in the displayed list, so that `service` and `api` never see the number (ADR-13). On 0 or at end of input, return with no request. On anything else, say the entry is not valid and ask again, with no new list and no request.
- [x] Add to `ConsoleMenuTest`: `should_say_that_0_cancels_when_asking_for_the_launch_number`, `should_cancel_without_a_booking_request_when_the_launch_number_is_0`, `should_fetch_a_fresh_list_each_time_the_booking_option_is_selected`, `should_ask_again_without_a_request_when_the_launch_number_is_invalid`, repeated for a number past the list, a negative number, text, a decimal, a blank line and a number too long for an `int`, and `should_end_without_a_booking_request_when_the_input_ends_at_the_launch_number_prompt`.

### 7. Ask for the seats, send the booking and confirm it

Covers: AC3, AC5, AC10, AC11

- [x] In `BookingFlow`, after the launch, prompt with `Seats (0 to cancel):`, reading the entry with the method from step 6. On 0 or at end of input, return with no request. On an entry that is not a whole number, say that it must be a whole number of at least 1 and ask again, with no request.
- [x] Keep the seats as a `BigInteger` as far as `CreateBookingRequest`. A very long whole number is still valid under AC10, and the client does not check capacity, so the API answers it with its capacity error (spec, out of scope).
- [x] Call `book` with the chosen launch and the seats. On success, show `Booking created.` and then the booking id, the chosen launch's name, the seats from the response and the `paymentStatus` as the API sends it, each on a labelled line. The name comes from the chosen launch, because the created booking has no launch data.
- [x] Add to `ConsoleMenuTest`: `should_send_the_chosen_launch_id_and_the_seats_when_booking`, which chooses a launch other than the first and checks the request body, `should_show_the_id_launch_name_seats_and_payment_status_when_the_booking_is_created`, `should_say_that_0_cancels_when_asking_for_the_seats`, `should_cancel_without_a_booking_request_when_the_seats_are_0`, `should_ask_again_without_a_request_when_the_seats_are_invalid`, repeated for a negative number, text, a decimal, a sign and a blank line, `should_send_the_seats_when_they_are_too_long_for_an_int` and `should_end_without_a_booking_request_when_the_input_ends_at_the_seats_prompt`.
- [x] Extend `should_not_say_activity_when_showing_its_own_texts`, or add a test next to it, so that it also covers the booking prompts, the re-prompts and the confirmation.

### 8. Show the capacity, payment and session errors of a booking

Covers: AC7, AC8

- [x] In `BookingFlow`, catch `ApiErrorFailure` from `book` before the general catch: pass it to `showFailure`, and when its status is 402, then show `No booking was created.` Decide by the status alone, with no parsing of the message (ADR-10). Every error returns to the menu with no new prompt.
- [x] Add to `ConsoleMenuTest`: `should_show_the_capacity_message_unchanged_and_the_menu_again_when_the_capacity_is_exceeded`, which also checks that the 402 statement does not appear, `should_show_the_message_and_say_no_booking_was_created_when_the_payment_is_rejected`, `should_say_no_booking_was_created_when_the_402_body_is_not_valid_json`, `should_show_the_message_verbatim_when_booking_on_a_launch_the_api_cannot_find`, `should_delete_the_file_and_ask_to_register_again_when_booking_responds_401`, `should_ask_to_register_or_log_in_when_booking_again_after_a_rejected_session` and `should_show_a_generic_error_and_the_menu_again_when_the_booking_response_is_malformed`.

## Checks

Run each one in the foreground with its time limit.

- [x] `timeout 240 ./mvnw -B -ntp compile` passes.
- [x] `timeout 240 ./mvnw -B -ntp test` passes.

## Verification

The `tester` does this after the coder finishes, recording the method for each criterion as
`testing-e2e-console` asks. Write `BookingCreationE2eTest` in `dev.iman.astrobookings`, with
`@Tag("e2e")`. It calls `Main.run(environment, input, output, sessionFile)` with a `@TempDir` file and a
`ByteArrayInputStream` whose script ends in `0` or at end of input, and asserts on the captured output.
Each registration uses a unique email with a timestamp. The menu options are `1` list, `2` register,
`3` log in, `4` log out, `5` book seats and `0` exit.

The seeded launches cannot get a 402, because no total within their capacity is a multiple of 1000, and
earlier runs use up their seats. So each test that books creates its own launch: register, then call
`POST /activities` directly with the token and a unique name, a future date, `status` `published` and the
price and `maxParticipants` it needs. Work out the launch's number from `GET /activities`, filtered by
status in the API's order, before building the script. Restore the data as ADR-5 says.

- **AC1, by e2e test**: select the option with a saved session, and check that the numbered list holds a `published` launch and no name of a `draft` launch from `GET /activities`.
- **AC2, covered by unit test**: the menu only offers launches that pass the filter, so the real API cannot reach it. Cite `BookingServiceTest.should_fail_as_not_bookable_without_calling_the_api_when_the_launch_is_not_open`.
- **AC3, by e2e test**: book 2 seats on a created launch that is not the first in the list, then check with `GET /bookings` and the saved token that the new booking has that launch's id as `activityId` and 2 as `participants`.
- **AC4, by e2e test**: the 201 of AC3 proves that the API accepted the saved token. For the exact header, cite `ApiClientTest.should_send_the_token_as_a_bearer_header_when_creating_a_booking`.
- **AC5, by e2e test**: check that the output of AC3 shows the booking id that `GET /bookings` returns, the launch's name, the seats and `paid`.
- **AC6, by e2e test**: enter a number past the list, text and a negative number, then 0. Check for the prompt each time, and that `GET /bookings` with the token is empty. Also cite `ConsoleMenuTest.should_ask_again_without_a_request_when_the_launch_number_is_invalid`.
- **AC7, by e2e test**: on a created launch with a `maxParticipants` of 2, request 3 seats, and check for exactly `Capacity exceeded. Available: 2, Requested: 3`, and for no booking in `GET /bookings`.
- **AC8, by e2e test**: on a created launch with a price of 500, request 2 seats, and check for `Payment could not be processed` and the statement that no booking was created, and for no booking in `GET /bookings`.
- **AC9, by e2e test**: select the option with no session file, and check for the message, no launch prompt and the menu again. For the absence of a request, cite `ConsoleMenuTest.should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_booking_with_no_session`.
- **AC10, by e2e test**: enter invalid seats, such as `-1`, `abc` and `1.5`, then 0. Check for the prompt each time, and that `GET /bookings` is empty, which also shows that the API's acceptance of fractions was not reached.
- **AC11, by e2e test**: cancel with 0 at the launch prompt in one run and at the seats prompt in another, and check for the menu again and an empty `GET /bookings`.
- **AC12, covered by unit test**: the real API's seeded launches stay published, so its list cannot be emptied safely. Cite `ConsoleMenuTest.should_show_the_exact_message_and_ask_for_nothing_when_no_launch_is_open_for_booking`.
- **Rejected session, by e2e test**: this is the first authenticated call the menu reaches, so feat-session AC9 can now be proved. Start with a session file holding an invalid token, book on a created launch, and check for the request to register again and that the file is gone.
- **Unreachable API, by unreachable API**: start with a saved session and the URL at a local port with nothing listening, select the option, and check for `API not reachable at <url>`, the menu again and no `Exception`.
- **Earlier criteria**: `LaunchListingE2eTest` and `SessionE2eTest` must still pass. Check the output of a booking run for the word "activity" (feat-launch-listing AC5).
