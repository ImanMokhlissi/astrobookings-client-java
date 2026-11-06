# Backlog

Improvements that block nothing. Add them here and carry on with the current work.

## Specs on hold

`/build` with no argument skips these specs. Naming one, as in `/build <spec>`, builds it.

- `fix-launch-parsing-robustness`: a URL with a query or fragment, fractional numbers in a launch
  and a response that stalls after its headers.
- `fix-password-input`: in Git Bash, `System.console().readPassword()` returns an empty string. Manual
  testing found this and the automated tests missed it. The workaround is `winpty java -jar ...` or
  PowerShell. The spec is not written yet.

## Improvements

- **ADD**: the system architecture section describes `Main` without saying that it returns the exit
  code, which ADR-7 now states.
- **`/build`**: it requires the API to respond even for a spec that does not use it.
- **Shade warnings**: `./mvnw package` warns about overlapping `MANIFEST.MF`, `LICENSE`, `NOTICE`
  and `module-info` files from the Jackson jars. The jar works, and they are not filtered.
- **`coding-java` examples**: compiling `ExampleApiClient.java` with `-Xlint:all` gives warnings
  that have not been reviewed.
- **`fix-launch-parsing-robustness`, before building it**: decide whether a numeric string such as
  `"5"` counts as non-numeric in AC7. One launch with a missing or null value still fails the whole
  list, even a draft, and the API may send `null` for an overflowing value such as `1e999`.
- **`feat-session` email re-prompt**: the AC3 prompt has no way to cancel, because ADR-13's `0`
  applies only to booking and selection flows. A user with no unused email can only leave by ending
  the input.
- **ADD, ADR-10**: it lists three `ApiFailure` subclasses, while `feat-session` adds
  `InvalidCredentialsFailure` and `SessionRejectedFailure`, and `feat-booking-creation` adds
  `NoSessionFailure` and `LaunchNotBookableFailure`, all raised only by `service`.
- **`ConsoleMenu` size**: at about 275 lines, it could split its launch display and session flows
  into separate views.
- **Fractional launches block booking**: until `fix-launch-parsing-robustness` is built, one launch
  with a fractional value makes `GET /activities` fail, so the booking option shows the generic
  error too.
- **Fractional seats block the bookings list**: the API accepts `participants: 1.5`, but
  `BookingResponse.participants` is an `Integer`. One such booking makes options 6 and 7 show the
  generic error for that customer, which read strictly conflicts with AC1 of
  `feat-booking-retrieval`. A test pins this behaviour. The client itself only sends whole seats.
- **Booking id `..`**: `ApiClient` encodes the id as one path segment, but dots stay as they are, so
  an id of exactly `..` is not handled or tested. The API's ids have the form `booking-N`.
- **Web, DNS rebinding**: ADR-17 does not check the `Host` header, so a malicious page could read
  a user's bookings through a rebound domain, though it cannot change anything. Rejecting unknown
  `Host` values would close this.
- **`testing-e2e-console`**: its "Drive the menu" rule covers only the console. For web tests,
  ADR-18 applies, and the skill could gain a section for them.
- **Docs after the web layer**: `project/briefing.md` still says "from a terminal", the Launch and
  Booking rows of the ADD's data model name only `cli`, and the `AGENTS.md` folder tree and tech
  stack list neither `src/main/resources` nor the HTTP server.
- **PRD, known limitations**: the `POST /login` entry names only the services being created before
  `loadAllData()`. Also, `routes/auth.ts` and `routes/users.ts` each create their own `UserService`,
  so a user registered during the run cannot log in either.
