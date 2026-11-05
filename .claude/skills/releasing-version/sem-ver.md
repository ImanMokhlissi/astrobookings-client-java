# Semantic versioning

| Spec type | Version bump | Example         | Tag   | CHANGELOG section |
|-----------|--------------|-----------------|-------|-------------------|
| `feat`    | Minor        | 0.2.3 → 0.3.0   | Yes   | New version       |
| `fix`     | Patch        | 0.3.0 → 0.3.1   | Yes   | New version       |
| `chore`   | None         | 0.3.1 → 0.3.1   | No    | `[Unreleased]`    |

## The first release

A `-SNAPSHOT` version in `pom.xml` means that base version has not been released. If the tag of
that base does not exist, the first `feat` or `fix` releases the base as it is, without
`-SNAPSHOT`: `0.1.0-SNAPSHOT` → `0.1.0`. Later releases bump from there.

A `chore` leaves `pom.xml` as it is, including any `-SNAPSHOT`.
