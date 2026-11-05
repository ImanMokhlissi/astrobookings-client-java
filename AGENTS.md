# AstroBookings Client

## Product overview

Java console client for AstroBookings, a fictional space travel company.
Customers list rocket launches, register, book seats and check their bookings.
It consumes the existing activity-bookings-express REST API, at the URL in
`ASTROBOOKINGS_API_URL` (default http://localhost:3000).
The client says "launches"; the API says "activities". Read `project/briefing.md` for the
API constraints before working on any feature.

## Tech stack

- Language: Java 21
- Build: Maven, through the Maven Wrapper
- HTTP: `java.net.http.HttpClient`
- JSON: Jackson
- Tests: JUnit 5
- No heavy frameworks

## Development workflow

```bash
# Compile the client
./mvnw compile

# Run the tests
./mvnw test

# Run the end-to-end tests (needs the API running)
./mvnw test -Pe2e

# Run the client (needs the API running)
./mvnw compile exec:java -Dexec.mainClass=dev.iman.astrobookings.Main

# Start the API from its sibling repository (run npm install the first time)
cd ../activity-bookings-express && npm run dev
```

## Folder structure

```text
.
├── .claude/
│   ├── agents/
│   ├── commands/
│   └── skills/
├── .mvn/
│   └── wrapper/
├── project/
│   └── specs/
└── src/
    ├── main/
    │   └── java/
    └── test/
        └── java/
```

- `.claude/`: agent tooling, reusable across projects.
- `project/`: documentation specific to this project.
- `src/main/java/dev/iman/astrobookings/`: packages `cli`, `service`, `api`, `model`, `session`.

## Environment

- Write code and documentation in English.
- Reply in the language of the prompt.
- Be concise.
- OS: Windows with Git Bash. Use POSIX shell syntax and `./mvnw`.

## Naming conventions

- Specs in `project/specs/`: `feat-<slug>`, `fix-<slug>`, `chore-<slug>`.
- Branches: `feat/<slug>`, `fix/<slug>`, `chore/<slug>`. The default branch is `main`.
- Commits: Conventional Commits. Use the `committing-changes` skill.
