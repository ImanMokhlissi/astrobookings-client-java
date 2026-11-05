# Conventional Commits

```text
<type>(<optional-scope>): <subject>

<optional body>

<optional footer>
```

## Types

| Type       | Use it for                                  | Example                                    |
|------------|---------------------------------------------|--------------------------------------------|
| `feat`     | A new user-facing feature                   | `feat(bookings): add seat booking command` |
| `fix`      | A bug fix                                   | `fix(api): handle empty error list`        |
| `docs`     | Documentation only                          | `docs: add project briefing`               |
| `style`    | Formatting with no change in behavior       | `style: format launch printer`             |
| `refactor` | Code change with no change in behavior      | `refactor(api): extract request builder`   |
| `perf`     | A performance improvement                   | `perf(api): reuse the HTTP client`         |
| `test`     | Adding or correcting tests                  | `test(bookings): cover capacity error`     |
| `build`    | Build system or dependencies                | `build: add Jackson dependency`            |
| `ci`       | Continuous integration                      | `ci: run tests on pull requests`           |
| `chore`    | Maintenance that fits no other type         | `chore: add agent tooling folders`         |
| `revert`   | Reverting a previous commit                 | `revert: remove seat booking command`      |

## Rules

- Write the subject in the imperative mood: "add", not "added" or "adds".
- Start the subject in lowercase and do not end it with a period.
- Keep the subject line to 72 characters or fewer.
- Use the body to explain why the change was made, not how.
- Mark a breaking change with `!` after the type or scope, and add a
  `BREAKING CHANGE:` footer that describes it.

## Example with body and footer

```text
feat(api)!: map launches to the activities endpoint

The API has no launches resource, so the client now maps them internally.

BREAKING CHANGE: the launches configuration key is removed.
```
