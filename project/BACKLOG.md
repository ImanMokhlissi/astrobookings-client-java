# Backlog

Improvements that block nothing. Add them here and carry on with the current work.

## Specs on hold

`/build` with no argument skips these specs. Naming one, as in `/build <spec>`, builds it.

- `fix-launch-parsing-robustness`: a URL with a query or fragment, fractional numbers in a launch
  and a response that stalls after its headers.

## Improvements

- **Booking specs**: `feat-booking-creation` and `feat-booking-retrieval` show `duration` through a
  DTO with no code yet, and should accept fractional values when they are built.
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
  `InvalidCredentialsFailure` and `SessionRejectedFailure`, raised only by `service`.
