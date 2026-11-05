# Plan: Session management

- **Spec**: [feat-session](feat-session.spec.md)
- **Branch**: feat/session

## Steps

Steps 1 to 9 are for the `coder`. The `tester` follows [Verification](#verification), which has
no checkboxes because the tester does not change this plan.

Rules for every step:

- Every run of the menu, in a test or a command, ends by itself. A test injects an `InputStream` whose script ends in `0` or at end of input. A command gets its input through stdin. Nothing waits for input.
- A test that makes an HTTP call uses `FakeApiServer`, never port 3000. A test that needs a session file uses `@TempDir`, never `~/.astrobookings`.
- The `coder` does not change `LaunchListingE2eTest` or `BuildSetupE2eTest`, yet both are compiled by `./mvnw test`. So `Main.run(environment, input, output)` stays until the `tester` has moved them to a temporary session file (step 5, Verification).

### 1. Read, write and delete the session file

Covers: AC4, AC7, AC8

- [ ] In `model`, add the `Session` record with `accessToken`, `username` and `email`. It has no password and no expiry (ADR-11).
- [ ] In `model`, add `SessionFileFailure`, a `RuntimeException` outside the sealed `ApiFailure`, because no API call is involved. It translates the I/O and Jackson failures of the session file at the boundary.
- [ ] In `session`, add `SessionStore`, with the file `Path` as a constructor parameter (ADR-7) and three methods: `read` returns an `Optional<Session>`, `write` creates the parent folders and writes the JSON, and `delete` uses `Files.deleteIfExists`. A missing file is an empty result on `read` and no failure on `delete`.
- [ ] In `SessionStore.read`, raise `SessionFileFailure` for any file that exists but holds no usable session: an I/O failure, invalid JSON, an empty file, the JSON literal `null`, or a missing, null or blank field. Jackson returns `null` for the literal without throwing, so check it. Ignore unknown fields.
- [ ] In `SessionStore.write` and `delete`, raise `SessionFileFailure` on an I/O failure.
- [ ] Add `SessionStoreTest` with `@TempDir`: `should_return_the_session_when_reading_a_written_file`, `should_create_the_folders_when_writing`, `should_store_only_the_token_username_and_email_when_writing`, `should_return_no_session_when_the_file_does_not_exist`, `should_fail_when_the_file_cannot_be_read`, repeated for invalid JSON, an empty file, `null`, a missing field, a blank field and a directory at the path, `should_fail_when_the_folder_cannot_be_created`, using a regular file as the parent, `should_delete_the_file_when_it_exists`, `should_not_fail_when_deleting_a_missing_file` and `should_fail_when_the_file_cannot_be_deleted`, using a non-empty directory at the path.

### 2. Call `POST /users` and `POST /login`

Covers: AC1, AC3, AC4, AC5

- [ ] In `model`, add the `User` record with `username` and `email`, and the `RegisterRequest`, `LoginRequest` and `AuthResponse` DTO records with the ADD's fields. `AuthResponse.user` is a `User`. The API's `id` and `terms` in `user` are ignored.
- [ ] In `ApiClient`, add `register` and `login`, which send the JSON with `Content-Type: application/json` and the existing timeout and failure mapping, and return a `Session`. `register` always sets `terms` to true. Only `api` touches these DTOs (ADR-3).
- [ ] In `ApiClient`, turn each malformed 2xx body into the generic `ApiErrorFailure`, as `fetchLaunches` does: invalid JSON, the JSON literal `null`, no `user`, or a missing or blank `username`, `email` or `accessToken`. The 401 already maps to `UnauthorizedFailure` in `throwIfFailed`, and the 400 keeps its field errors.
- [ ] In `FakeApiServer`, record the body of each request, with a method that returns them in order, and add a way to queue responses for consecutive requests. `respondWith` and every existing test keep working.
- [ ] Add to `ApiClientTest`: `should_send_the_username_email_password_and_terms_when_registering`, `should_send_the_email_and_password_when_logging_in`, `should_return_the_session_when_registering_succeeds`, `should_return_the_session_when_logging_in_succeeds`, `should_ignore_the_other_user_fields_when_the_api_sends_them`, `should_fail_with_the_field_errors_when_the_email_is_repeated`, `should_fail_as_unauthorized_when_login_responds_401` and `should_fail_with_a_generic_error_when_the_auth_response_is_incomplete`, repeated for the malformed bodies above.

### 3. Add the session lifecycle

Covers: AC4, AC5, AC6, AC7, AC8

- [ ] In `model`, add `InvalidCredentialsFailure` to the sealed `ApiFailure`. `api` still produces only the three types of ADR-10, and `service` raises this one when it gives a login 401 its meaning.
- [ ] In `ConsoleMenu.showFailure`, add its case, which shows the client's own text, `The email or password was not accepted.`, and not the API's message, which may also read "Authentication failed". The `switch` stays with no `default`.
- [ ] In `service`, add `SessionService`, with `ApiClient` and `SessionStore` as constructor parameters. It holds the current session in a private field, and `currentSession` returns it as an `Optional`.
- [ ] In `SessionService`, add `loadSavedSession`, which reads the file without calling the API (ADR-11) and starts the session. A `SessionFileFailure` passes through, with no session started and the file left in place.
- [ ] In `SessionService`, add `register` and `login`, which call `api`, write the file and then start the session. If the write fails, the failure passes through and the session does not change. `login` turns `UnauthorizedFailure` into `InvalidCredentialsFailure`. A 401 on either one deletes nothing, and other failures pass through unchanged.
- [ ] In `SessionService`, add `logout`, which ends the session first and then deletes the file, so a failed delete still ends the session.
- [ ] Add `SessionServiceTest`, with the fake server and `@TempDir`: `should_save_and_start_the_session_when_registering_succeeds`, `should_save_and_start_the_session_when_logging_in_succeeds`, `should_replace_the_session_when_logging_in_again`, `should_start_the_session_without_calling_the_api_when_loading_a_saved_file`, `should_have_no_session_when_there_is_no_file`, `should_have_no_session_and_keep_the_file_when_it_cannot_be_read`, `should_fail_as_invalid_credentials_when_login_responds_401`, `should_keep_the_file_and_session_when_login_responds_401`, `should_keep_the_file_and_session_when_registration_responds_401`, `should_have_no_session_when_saving_fails`, `should_delete_the_file_and_end_the_session_when_logging_out` and `should_not_fail_when_logging_out_without_a_file`.

### 4. Handle a rejected session

Covers: AC9

No authenticated call exists until feat-booking-creation, so this step provides the handling and no menu option reaches it yet.

- [ ] In `model`, add `SessionRejectedFailure` to the sealed `ApiFailure`, keeping the 401 as its cause. It is separate from `UnauthorizedFailure` because the public `GET /activities` keeps showing a 401 as an API error (feat-launch-listing AC8).
- [ ] In `SessionService`, add `runAuthenticated`, which takes the authenticated call as a `Supplier` and returns its result. On `UnauthorizedFailure` it ends the session, deletes the file and raises `SessionRejectedFailure`. A failed delete must not hide the rejection. Other failures pass through.
- [ ] In `ConsoleMenu.showFailure`, add its case, which asks the user to register again. Make `showFailure` package-private, so a test can reach it now and the booking specs can catch `ApiFailure` as `listLaunches` does.
- [ ] Add to `SessionServiceTest`: `should_delete_the_file_and_end_the_session_when_an_authenticated_call_responds_401`, `should_fail_as_session_rejected_when_an_authenticated_call_responds_401`, `should_fail_as_session_rejected_when_the_file_cannot_be_deleted`, `should_keep_the_session_when_an_authenticated_call_fails_otherwise` and `should_return_the_result_when_an_authenticated_call_succeeds`.
- [ ] Add to `ConsoleMenuTest`: `should_ask_to_register_again_when_the_session_is_rejected`. Keep `should_show_the_message_and_the_menu_again_when_the_api_responds_401` as it is.

### 5. Load the session at startup, show who is logged in and log out

Covers: AC6, AC7, AC8

- [ ] In `ConsoleMenu`, add `SessionService` to the constructor, and call `loadSavedSession` at the start of `run`, before the first menu. On `SessionFileFailure`, tell the user that the session file could not be read and continue with no session.
- [ ] In `ConsoleMenu.showMenu`, add `Logged in as <username> (<email>)` while there is a session. Keep `1. List launches` and `0. Exit`, and add `4. Log out`, always shown, also with no session, so that the numbers do not change. Steps 6 and 8 add `2. Register` and `3. Log in` in the same way.
- [ ] In `ConsoleMenu`, add the log out option, which calls `logout` and confirms it, also with no session or no file. On `SessionFileFailure`, say that the file could not be deleted and return to the menu.
- [ ] In `Main`, add a package-private `run(environment, input, output, sessionFile)` that builds `SessionStore` and `SessionService` and passes `SessionService` to the menu (ADR-7), and a package-private method that gives `.astrobookings/session.json` under a home folder. `main` passes the file under the `user.home` system property.
- [ ] Keep `Main.run(environment, input, output)`, delegating with the real path, only so that the two end-to-end classes keep compiling. Those runs only read the file. Move `MainTest` to the new method with `@TempDir`.
- [ ] Update the `ConsoleMenuTest` helper to build `SessionService` over a `@TempDir` file, with no change to what the existing tests assert.
- [ ] Add to `ConsoleMenuTest`: `should_show_who_is_logged_in_without_calling_the_api_when_started_with_a_saved_session`, `should_not_show_who_is_logged_in_when_started_without_a_session`, `should_tell_the_user_and_continue_with_no_session_when_the_file_cannot_be_read`, `should_delete_the_file_and_stop_showing_the_user_when_logging_out`, `should_show_no_error_when_logging_out_without_a_file` and `should_show_the_error_and_the_menu_again_when_the_file_cannot_be_deleted`.
- [ ] Add to `MainTest`: `should_place_the_session_file_in_the_astrobookings_folder_of_the_home_folder`.

### 6. Register from the menu

Covers: AC1, AC2, AC4

- [ ] In `ConsoleMenu`, add the register option. It prompts for the username, email and password, then asks the user to accept the terms with `y` or `n`. `y` or `yes`, in any case, accepts, and anything else declines.
- [ ] On declining, say that the terms were not accepted and return to the menu with no API call and no change to the session.
- [ ] Read the password from the injected input through its own method, without the trim that `readOption` applies, so that the password stays as typed. Never print it.
- [ ] At end of input in any prompt, return to the loop with no API call, so the menu ends cleanly (ADR-2).
- [ ] On success, show `Logged in as <username> (<email>)`, then the menu. On `ApiFailure`, use `showFailure`, then the menu. On `SessionFileFailure`, say that the session could not be saved, then the menu.
- [ ] Add to `ConsoleMenuTest`: `should_send_the_entered_values_with_terms_true_when_the_user_accepts`, `should_keep_the_spaces_of_the_password_when_registering`, `should_send_nothing_and_start_no_session_when_the_user_declines_the_terms`, `should_keep_the_saved_session_when_the_user_declines_the_terms`, `should_save_the_session_without_the_password_and_show_the_user_when_registering_succeeds`, `should_show_the_field_errors_and_the_menu_again_when_another_field_is_rejected`, `should_show_not_reachable_and_the_menu_again_when_registering_with_the_api_down`, `should_show_the_error_and_the_menu_again_when_the_session_cannot_be_saved` and `should_end_without_a_request_when_the_input_ends_while_registering`.

### 7. Ask for another email when the API rejects it

Covers: AC3

- [ ] In the register option of `ConsoleMenu`, when an `ApiErrorFailure` has a field error on `email`, show the error, ask for the email alone, and resend with the same username, password and accepted terms. Repeat for each such response (ADR-10). This applies to any field error on `email`, such as an invalid address, and does not parse the message.
- [ ] Any other failure, including a resend rejected only on another field, ends the option as in step 6, and end of input at the email prompt ends it with no API call.
- [ ] Add to `ConsoleMenuTest`, with queued responses: `should_show_the_error_and_resend_with_the_new_email_when_the_email_is_rejected`, which checks both request bodies, `should_ask_again_each_time_the_email_is_rejected`, `should_not_ask_for_the_other_values_again_when_the_email_is_rejected` and `should_end_without_a_request_when_the_input_ends_at_the_new_email_prompt`.

### 8. Log in from the menu

Covers: AC4, AC5

- [ ] In `ConsoleMenu`, add the log in option. It prompts for the email and the password, using the password method from step 6, and shows who is logged in on success. End of input and other failures behave as in step 6, and a field error on `email` gets no second prompt, because AC3 is about registration.
- [ ] On `InvalidCredentialsFailure`, show the explanation and offer to register with `y` or `n`, read as the terms answer is. Accepting starts the register option from its first prompt, and anything else returns to the menu.
- [ ] Add to `ConsoleMenuTest`: `should_save_the_session_and_show_the_user_when_logging_in_succeeds`, `should_explain_and_offer_to_register_when_login_responds_401`, `should_register_when_the_user_accepts_the_offer`, `should_return_to_the_menu_when_the_user_declines_the_offer`, `should_keep_the_saved_session_when_login_responds_401`, `should_show_not_reachable_and_the_menu_again_when_logging_in_with_the_api_down` and `should_end_without_a_request_when_the_input_ends_while_logging_in`.

### 9. Read passwords from the console when there is one

Covers: AC1, AC5 (ADR-2)

- [ ] In `cli`, add a `PasswordPrompt` interface with one method that shows a prompt and returns the password, or an empty result at end of input. `java.io.Console` is final, so this is what lets a test stand in for it.
- [ ] In `ConsoleMenu`, add a constructor that takes a `PasswordPrompt`, used by both password prompts. The existing constructor keeps reading from the injected input.
- [ ] In `Main`, add a `run` that also takes the `Console`, or `null` when there is none. With a console, it adapts `readPassword` to `PasswordPrompt`, passing the prompt as an argument to a `%s` format and treating `null` as end of input. `main` does the `System.console()` lookup and passes it, and every other `run` passes none, so a test run from a terminal still uses the injected stream.
- [ ] Add to `ConsoleMenuTest`: `should_read_the_password_from_the_prompt_when_there_is_one`, for registering and logging in, and `should_end_without_a_request_when_the_prompt_returns_no_password`.
- [ ] Run `printf '0\n' | ASTROBOOKINGS_API_URL=http://localhost:1 timeout 240 ./mvnw -B -ntp compile exec:java` and check that it shows the menu and ends with `BUILD SUCCESS` and no stack trace. This uses the real session file, so select no option that writes or deletes it. Do not pass `-Duser.home` to Maven, which would also move its repository.

## Checks

Run each one in the foreground with its time limit.

- [ ] `timeout 240 ./mvnw -B -ntp compile` passes.
- [ ] `timeout 240 ./mvnw -B -ntp test` passes.

## Verification

The `tester` does this after the coder finishes, recording the method for each criterion as
`testing-e2e-console` asks. Write `SessionE2eTest` in `dev.iman.astrobookings`, with `@Tag("e2e")`. It
calls `Main.run(environment, input, output, sessionFile)` with a `@TempDir` file and a
`ByteArrayInputStream` whose script ends in `0` or at end of input, and asserts on the captured output
and the file. Each registration uses a unique email with a timestamp. The menu options are `1` list,
`2` register, `3` log in, `4` log out and `0` exit.

- **Existing end-to-end tests**: move `LaunchListingE2eTest` and `BuildSetupE2eTest` to the four-argument `Main.run` with a `@TempDir` file, because a test never touches the real session file. In `LaunchListingE2eTest.AC9`, replace the option `2`, which is now Register, with another unknown number such as `8`. This keeps what the test proves. With no callers left, the three-argument `run` is dead code for the `cleaner` to remove. Both classes must still pass.
- **AC1, by e2e test**: register with `y` against the real API and check for the logged-in line. The API answers 201 only with `terms` set to true. For the body sent, cite `ConsoleMenuTest.should_send_the_entered_values_with_terms_true_when_the_user_accepts`.
- **AC2, by e2e test**: decline with `n`, and check that no logged-in line appears and no session file exists. Registering the same email afterwards succeeding proves that the API did not receive it. Also cite `ConsoleMenuTest.should_send_nothing_and_start_no_session_when_the_user_declines_the_terms`.
- **AC3, by e2e test**: register an email, then in a run with a new session file register the same email, and give a new one at the second prompt. Check for `Email is already registered` and the logged-in line with the new email. The repeated email is detected because both registrations happen after the API's start (PRD known limitations).
- **AC4, by e2e test and by command, and by unit test for login**: after registering, with the file in a folder that does not exist yet, check that the file holds the token, username and email, and that it does not contain the password. For the real path, run `timeout 240 ./mvnw -B -ntp package`, then pipe a register script ending in `0` into `timeout 60 java -Duser.home="<temporary folder>" -jar target/astrobookings-client-0.1.0.jar`, and check `<temporary folder>/.astrobookings/session.json`. `POST /login` always responds 401, so for login, mark it covered by unit test, citing `ConsoleMenuTest.should_save_the_session_and_show_the_user_when_logging_in_succeeds`.
- **AC5, by e2e test**: log in against the real API, which responds 401. Check for the explanation and the offer, then accept it and complete a registration in the same run.
- **AC6, by unreachable API**: register in one run, then start a second run with the same file and the URL at a local port with nothing listening, and check for the logged-in line with no register prompt and no `API not reachable`. For the absence of any request, cite `ConsoleMenuTest.should_show_who_is_logged_in_without_calling_the_api_when_started_with_a_saved_session`.
- **AC7, by e2e test**: start with a session file holding invalid JSON, and check for the message, no logged-in line, a working launch list and no `Exception`.
- **AC8, by e2e test**: register, log out, and check that the file is gone and the logged-in line no longer appears. Then log out with no file and check for no error or `Exception`.
- **AC9, covered by unit test**: no authenticated call exists until feat-booking-creation, so the real API cannot reach it through the menu. Cite `SessionServiceTest.should_delete_the_file_and_end_the_session_when_an_authenticated_call_responds_401` and `ConsoleMenuTest.should_ask_to_register_again_when_the_session_is_rejected`.
- **Earlier criteria**: `LaunchListingE2eTest` covers feat-launch-listing, including `AC5` on the word "activity". Check the new texts for it too, in the output of a run that registers, logs in with a 401 and logs out. Then run `echo "0" | timeout 60 java -Duser.home="<temporary folder>" -jar target/astrobookings-client-0.1.0.jar` and check for exit code 0.
