# Plan: Booking retrieval

- **Spec**: [feat-booking-retrieval](feat-booking-retrieval.spec.md)
- **Branch**: feat/booking-retrieval

## Steps

Steps 1 to 8 are for the `coder`. The `tester` follows [Verification](#verification), which has
no checkboxes because the tester does not change this plan.

Rules for every step:

- Every run of the menu, in a test or a command, ends by itself. A test injects an `InputStream` whose script ends in `0` or at end of input. A command gets its input through stdin. Nothing waits for input.
- A test that makes an HTTP call uses `FakeApiServer`, never port 3000. A test that needs a session file uses `@TempDir`, never `~/.astrobookings`.
- The word "activity" stays in `api` and in the DTOs (ADR-3). The client's own texts say "launch".
- `model` gets no new records, DTOs or failure types (spec, Solution).

### 1. Call `GET /bookings` with the token

Covers: AC1, AC2, AC4

- [ ] In `ApiClient`, add `fetchBookings(token)`, which sends `GET /bookings` with `Authorization: Bearer <token>` and the existing timeout, and returns a list of `Booking`, in the API's order, reusing `toBooking(BookingResponse)`. Do not add the header to `GET /activities`.
- [ ] In `ApiClient`, make the invalid-token handling of `buildAuthenticatedPostRequest` shared with the new authenticated GET, so that a token a header cannot hold becomes the generic `ApiErrorFailure` with `NO_RESPONSE_STATUS`.
- [ ] In `ApiClient`, add a completeness check for a retrieved booking that also requires `activity`, because AC1 and AC3 show launch data and a created booking is the only one that may lack it. It also refuses a null element in the list, which `isComplete(BookingResponse)` does not check. `createBooking` keeps its check unchanged. Each malformed 2xx body becomes the generic `ApiErrorFailure`, through `readComplete`, and no malformed body may raise a `NullPointerException`.
- [ ] Add to `ApiClientTest`: `should_send_a_get_with_the_token_as_a_bearer_header_when_fetching_the_bookings`, `should_return_the_bookings_in_the_api_order_with_their_launch_summary_when_fetching_the_bookings`, `should_return_no_bookings_when_the_list_is_empty`, `should_keep_a_payment_status_the_client_does_not_know_when_fetching_the_bookings`, `should_keep_a_missing_payment_id_as_null_when_fetching_the_bookings`, `should_keep_a_fractional_duration_when_fetching_the_bookings` and `should_fail_with_a_generic_error_when_the_bookings_response_is_malformed`, repeated for invalid JSON, the literal `null`, an object instead of a list, a null element, a booking with no `activity`, a booking whose `activity` lacks a field and fractional `participants`.

### 2. Call `GET /bookings/:id` with the token

Covers: AC3, AC4

- [ ] In `ApiClient`, add `fetchBooking(token, bookingId)`, which sends `GET /bookings/:id` with the same header and timeout, and returns a `Booking` that passes the check from step 1.
- [ ] Percent-encode the id as one path segment, so that an id from the API with a space, `/` or `?` can neither raise an `IllegalArgumentException` in `URI.create` nor change the path.
- [ ] Add to `ApiClientTest`: `should_request_the_chosen_id_with_the_token_as_a_bearer_header_when_fetching_a_booking`, `should_return_the_booking_with_its_launch_summary_when_fetching_a_booking`, `should_keep_a_fractional_duration_when_fetching_a_booking`, `should_encode_the_id_as_one_path_segment_when_fetching_a_booking`, repeated for an id with a space and with `?`, and `should_fail_with_a_generic_error_when_the_booking_response_is_malformed`, repeated for invalid JSON, the literal `null`, a booking with no `activity` and a booking whose `activity` lacks a field.

### 3. Map the failures of both calls

Covers: AC7, AC8, AC9

- [ ] Reuse `send` and `throwIfFailed` in both calls: a 401 becomes `UnauthorizedFailure`, any other error an `ApiErrorFailure` with the status and message, and a connection failure or timeout an `ApiNotReachableFailure` with the configured URL. Add production code only if a test below shows a gap.
- [ ] Add to `ApiClientTest`, each repeated for `fetchBookings` and `fetchBooking`: `should_fail_as_unauthorized_when_the_booking_request_responds_401`, `should_keep_the_status_and_message_when_the_booking_request_responds_with_an_error`, `should_fail_as_not_reachable_when_the_booking_request_finds_the_api_down` and `should_fail_with_a_generic_error_when_the_token_of_the_booking_request_cannot_be_sent_in_a_header`.

### 4. Add the two use cases

Covers: AC4, AC5, AC7, AC8, AC9

- [ ] In `BookingService`, add `listBookings()` and `getBooking(bookingId)`. Each raises `NoSessionFailure` with no session, with no API call, reads the token from `SessionService.currentSession` and calls `ApiClient` inside `SessionService.runAuthenticated`, so that a 401 ends the session and deletes the file (ADR-10, ADR-11). Update the class Javadoc, which only mentions booking seats.
- [ ] Add to `BookingServiceTest`, each repeated for `listBookings` and `getBooking`: `should_fail_as_no_session_without_calling_the_api_when_retrieving_with_no_session`, `should_send_the_saved_token_when_retrieving`, `should_delete_the_file_and_fail_as_session_rejected_when_retrieving_responds_401` and `should_keep_the_session_when_retrieving_fails_otherwise`, with an API error and with the API down. Also add `should_return_the_bookings_when_listing_them` and `should_return_the_booking_when_getting_it_by_its_id`.

### 5. Share the number prompt

Covers: AC6, AC10

- [ ] In `cli`, move `promptForNumber` and `readWholeNumber` from `BookingFlow` to a package-private `NumberPrompt`, built with the menu's `promptFor` and the `PrintStream`, so that the launch and booking selections share one rule (ADR-13). Keep the behavior as it is: `03` is 3, `00` is 0, and a sign, a decimal, a blank line, a non-ASCII digit or a very long number never raises an exception.
- [ ] Give `BookingFlow` a `NumberPrompt`, built in `ConsoleMenu`. No existing test changes, and `./mvnw test` still passes, which proves that this step changes no behavior.

### 6. Add the two menu options and show the numbered list

Covers: AC1, AC2, AC5, AC7, AC8, AC9

- [ ] In `cli`, add a package-private `BookingRetrievalFlow`, with `BookingService`, the `NumberPrompt`, the `PrintStream` and the menu's `showFailure` as a consumer. It goes in its own class because `ConsoleMenu` is already long (`project/BACKLOG.md`).
- [ ] In `ConsoleMenu`, add `6. List my bookings` and `7. View a booking`, always shown, also with no session, as `5. Book seats` is. Build the flow in both constructors. `Main` and the `ConsoleMenuTest` helpers do not change, because `BookingService` already reaches the menu. `8` and `9` stay unknown options, which `LaunchListingE2eTest` and `ConsoleMenuTest` check.
- [ ] In `BookingRetrievalFlow`, add the list option. Get a fresh list from `listBookings`. With no bookings, show `You have no bookings.` Otherwise show one line for each, in the API's order, as its number from 1, then `<id> - <launch name> - Seats: <n> - Payment status: <paymentStatus as the API sends it>`. Then return to the menu.
- [ ] Catch `ApiFailure` around the whole option and pass it to `showFailure`, which already covers every failure the flow can get, so that it returns to the menu with no stack trace.
- [ ] Add to `ConsoleMenuTest`: `should_show_the_options_to_list_and_view_bookings_when_started`, `should_number_the_bookings_from_1_with_id_launch_name_seats_and_payment_status_when_listing_them`, `should_show_the_payment_status_as_the_api_sends_it_when_listing_the_bookings`, `should_show_the_message_and_the_menu_again_when_the_user_has_no_bookings`, `should_ask_for_nothing_after_listing_the_bookings`, `should_fetch_a_fresh_list_each_time_the_list_option_is_selected`, `should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_listing_the_bookings_with_no_session`, `should_delete_the_file_and_ask_to_register_again_when_listing_the_bookings_responds_401`, `should_show_the_message_and_the_menu_again_when_listing_the_bookings_responds_with_an_error` and `should_show_not_reachable_and_the_menu_again_when_listing_the_bookings_with_the_api_down`.
- [ ] Run `printf '0\n' | ASTROBOOKINGS_API_URL=http://localhost:1 timeout 240 ./mvnw -B -ntp compile exec:java` and check that the menu shows the new options and that it ends with `BUILD SUCCESS` and no stack trace. This reads the real session file, so select no option that writes or deletes it.

### 7. Choose a booking by its number

Covers: AC1, AC2, AC5, AC6, AC10

- [ ] In `BookingRetrievalFlow`, add the view option. It starts like the list option, with the same fresh list, the same lines and the same no-bookings message, so share that code. With no bookings, it returns, asking for nothing.
- [ ] Prompt through `NumberPrompt` with `Booking number (0 to cancel):`. On a number in the list, resolve it to the booking in the displayed list, so that `service` and `api` only see its id (ADR-13). On 0 or at end of input, return with no request. On anything else, show `Enter a booking number from the list.` and ask again, with no new list and no request.
- [ ] Add to `ConsoleMenuTest`: `should_number_the_bookings_from_1_when_viewing_a_booking`, `should_say_that_0_cancels_when_asking_for_the_booking_number`, `should_cancel_without_requesting_the_booking_when_the_booking_number_is_0`, `should_ask_for_nothing_when_viewing_with_no_bookings`, `should_fetch_a_fresh_list_each_time_the_view_option_is_selected`, `should_ask_again_without_a_request_when_the_booking_number_is_invalid`, repeated for a number past the list, a negative number, text, a decimal, a blank line and a number too long for an `int`, `should_end_without_requesting_the_booking_when_the_input_ends_at_the_booking_number_prompt` and `should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_viewing_a_booking_with_no_session`.

### 8. Show the chosen booking and the errors of its request

Covers: AC3, AC7, AC8, AC9

- [ ] In `BookingRetrievalFlow`, call `getBooking` with the chosen booking's id and show the response, one labelled line each: `Booking id:`, `Seats:`, `Created:`, `Payment id:`, `Payment status:`, `Launch:`, `Price:`, `Date:`, `Duration:`, `Location:` and `Launch status:`. `Launch status:` keeps it apart from the payment status. Then return to the menu.
- [ ] Format `createdAt` and the launch's date through `LaunchDateFormatter`. Show `price` and `duration` with `toPlainString`, so that a fractional duration appears with no rounding (`project/BACKLOG.md`, "Booking specs"). Show `paymentStatus` as the API sends it. `paymentId` may be null (the `Booking` Javadoc): show `Payment id: none` then.
- [ ] Add to `ConsoleMenuTest`: `should_request_the_id_of_the_chosen_booking_when_choosing_one_that_is_not_the_first`, `should_show_every_field_of_the_chosen_booking_and_the_menu_again`, `should_show_a_fractional_duration_with_no_rounding_when_viewing_a_booking`, `should_show_the_payment_status_as_the_api_sends_it_when_viewing_a_booking`, `should_say_there_is_no_payment_id_when_the_booking_has_none`, `should_delete_the_file_and_ask_to_register_again_when_viewing_the_booking_responds_401`, `should_show_the_message_and_the_menu_again_when_the_chosen_booking_no_longer_exists`, for a 404 `Booking not found`, `should_show_a_generic_error_and_the_menu_again_when_the_booking_response_is_malformed` and `should_show_not_reachable_and_the_menu_again_when_the_api_goes_down_before_the_booking_is_requested`. The last one injects an `InputStream` that stops the fake server when it hands over the booking number, and delivers each line separately, because `BufferedReader` reads ahead.
- [ ] Add `should_not_say_activity_when_showing_the_retrieval_texts`, next to the existing tests, covering both lists, the prompts, the re-prompt, the no-bookings message, the details and the API-down error.

## Checks

Run each one in the foreground with its time limit.

- [ ] `timeout 240 ./mvnw -B -ntp compile` passes.
- [ ] `timeout 240 ./mvnw -B -ntp test` passes.

## Verification

The `tester` does this after the coder finishes, recording the method for each criterion as
`testing-e2e-console` asks. Write `BookingRetrievalE2eTest` in `dev.iman.astrobookings`, with
`@Tag("e2e")`. It calls `Main.run(environment, input, output, sessionFile)` with a `@TempDir` file and a
`ByteArrayInputStream` whose script ends in `0` or at end of input, and asserts on the captured output.
Each registration uses a unique email with a timestamp. The menu options are `1` list, `2` register,
`3` log in, `4` log out, `5` book seats, `6` list my bookings, `7` view a booking and `0` exit.

Each test that needs bookings registers its own customer and creates them directly: `POST /activities`
with the token, a unique name, a future date, `status` `published`, a price of 7 and a whole duration, then
`POST /bookings`. Work out a booking's number from `GET /bookings` with the token, because ids reused after an
API restart can show a new user old bookings (ADR-5). Do not create a launch with a fractional value: one
makes `GET /activities` fail for every later test (`project/BACKLOG.md`, "Fractional launches block booking").
Restore the data as ADR-5 says when the run ends, and always before restarting the API, because the AC8 test
leaves a booking whose launch is gone, and `GET /bookings` responds 500 to any user who reuses its owner's id.

- **AC1, by e2e test**: with two created bookings, select `6` and, in another run, `7` then `0`. Check that each run shows every booking from `GET /bookings`, in that order, numbered from 1, with its id, launch name, seats and `paid`.
- **AC2, by e2e test**: with a newly registered customer, select `6` and `7`. Check for `You have no bookings.` each time, no booking number prompt and the menu again. First assert that `GET /bookings` is empty, and if it is not, fail with a message that asks to restore the data as ADR-5 says.
- **AC3, by e2e test**: with two created bookings, select `7` and the number of the second. Check for its id, seats, the creation date and launch date in the format `LaunchDateFormatter` gives, `paymentId` and `paymentStatus` from `GET /bookings/:id`, and the launch's name, price, date, duration, location and status. For a fractional duration and a missing `paymentId`, which the real API must not be made to hold, cite `ConsoleMenuTest.should_show_a_fractional_duration_with_no_rounding_when_viewing_a_booking` and `ConsoleMenuTest.should_say_there_is_no_payment_id_when_the_booking_has_none`.
- **AC4, by e2e test**: start a new run with only the saved file holding the token, and check that options `6` and `7` show this customer's booking, and that a second customer's run does not show it. For the exact header, cite `ApiClientTest.should_send_a_get_with_the_token_as_a_bearer_header_when_fetching_the_bookings` and `ApiClientTest.should_request_the_chosen_id_with_the_token_as_a_bearer_header_when_fetching_a_booking`.
- **AC5, by e2e test**: select `6` and `7` with no session file, and check for `Register or log in first.` each time, no booking number prompt and the menu again. For the absence of a request, cite `ConsoleMenuTest.should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_listing_the_bookings_with_no_session` and `ConsoleMenuTest.should_tell_the_user_to_register_or_log_in_without_calling_the_api_when_viewing_a_booking_with_no_session`.
- **AC6, by e2e test**: enter a number past the list, text, a negative number, a decimal and a blank line, then 0. Check for the prompt and the invalid-entry line each time, one list only and no booking's details. For the absence of a request, cite `ConsoleMenuTest.should_ask_again_without_a_request_when_the_booking_number_is_invalid`.
- **AC7, by e2e test**: start with a session file holding an invalid token, select `6` in one run and `7` in another, and check for the request to register again and that the file is gone. A real token cannot be rejected between the list and the chosen booking, so for that 401 on `GET /bookings/:id` cite `ConsoleMenuTest.should_delete_the_file_and_ask_to_register_again_when_viewing_the_booking_responds_401`.
- **AC8, by e2e test**: create a booking, delete its launch with `DELETE /activities/:id`, and select `6` and `7`. Check for `Failed to retrieve bookings`, unchanged, and the menu again each time. The list is fetched just before the choice and the API cannot delete bookings, so for the 404 on `GET /bookings/:id` cite `ConsoleMenuTest.should_show_the_message_and_the_menu_again_when_the_chosen_booking_no_longer_exists`.
- **AC9, by unreachable API**: start with a saved session and the URL at a local port with nothing listening, select `6` and `7`, and check for `API not reachable at <url>` each time, the menu again, no `Exception` and that the session file remains. The API cannot go down between the list and the chosen booking in an e2e run, so for `GET /bookings/:id` cite `ConsoleMenuTest.should_show_not_reachable_and_the_menu_again_when_the_api_goes_down_before_the_booking_is_requested`.
- **AC10, by e2e test**: select `7`, enter 0, and check for the menu again and no booking's details. For the absence of a request, cite `ConsoleMenuTest.should_cancel_without_requesting_the_booking_when_the_booking_number_is_0`.
- **Earlier criteria**: `LaunchListingE2eTest`, `SessionE2eTest` and `BookingCreationE2eTest` must still pass. Check the output of the retrieval runs for the word "activity" (feat-launch-listing AC5).
