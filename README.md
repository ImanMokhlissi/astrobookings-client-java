# AstroBookings Client

## Resumen en español

Cliente de consola en Java 21 para la API de ejemplo del curso Programación Inteligente: permite
listar lanzamientos, registrarse, reservar plazas y consultar las reservas. Se construyó con Claude
Code siguiendo Spec-Driven Development: cada funcionalidad parte de una spec con criterios de
aceptación y pasa por una cadena de agentes. Hubo revisión humana del PRD, el ADD y cada spec antes
de fusionarlos, una confirmación antes de cada release y pruebas manuales del cliente, que
encontraron un bug que los tests automáticos no detectaron: en Git Bash,
`System.console().readPassword()` devuelve una cadena vacía (solución provisional:
`winpty java -jar ...` o PowerShell). La versión actual es la v0.4.0, con 392 tests unitarios y 46 end-to-end. Es un ejercicio de formación, no un producto para
producción. El resto del documento está en inglés, como el proyecto.

## What it is

A console client written in Java 21 for the course's sample API,
[activity-bookings-express](https://github.com/Programacion-Inteligente/activity-bookings-express).
AstroBookings is a fictional space travel company, so the client says "launches" where the API says
"activities".

Features:

- List the launches open for booking.
- Register, log in and log out, with the session saved in `~/.astrobookings/session.json`.
- Book seats on a launch.
- List your bookings and view the details of one.

## How to run it

### Requirements

- JDK 21. The Maven Wrapper downloads Maven, so you do not need to install it.
- Node.js, to run the API. It was tested with Node 24.
- On Windows, a POSIX shell such as Git Bash for the commands below.

### Start the API

In a separate terminal, from a folder next to this repository:

```bash
git clone https://github.com/Programacion-Inteligente/activity-bookings-express.git
cd activity-bookings-express
npm install
npm run dev
```

It listens on `http://localhost:3000`. Set `PORT` to use another port.

### Build and run the client

```bash
./mvnw package
java -jar target/astrobookings-client-0.4.0.jar
```

**In Git Bash**, start the client with `winpty java -jar target/astrobookings-client-0.4.0.jar`, or
run it from PowerShell or CMD. Without this, the password prompt returns an empty string when you
register or log in (see `fix-password-input` in the [backlog](project/BACKLOG.md)).

The client calls `http://localhost:3000` by default. To use another URL, set
`ASTROBOOKINGS_API_URL`:

```bash
ASTROBOOKINGS_API_URL=http://localhost:4000 java -jar target/astrobookings-client-0.4.0.jar
```

If the API is down, the client shows `API not reachable at <url>` and returns to the menu.

The menu looks like this. Once you have a session, it also shows who is logged in:

```text
AstroBookings
1. List launches
2. Register
3. Log in
4. Log out
5. Book seats
6. List my bookings
7. View a booking
0. Exit
Select an option:
```

### Run the tests

```bash
# Unit tests, which do not need the API
./mvnw test

# End-to-end tests, with the API running
./mvnw test -Pe2e
```

The end-to-end tests write test data to the API, which saves it in its `db/` folder. To go back to
the original data, stop the API, run `git checkout -- db/` in its repository and start it again.

## How it was built

The client was built with [Claude Code](https://claude.com/claude-code), following AI-Driven and
Spec-Driven Development: each piece of work starts as a spec with acceptance criteria, and a chain
of specialized agents takes it from there.

The process included human review of the PRD, the ADD and every spec before merging, a confirmation
before every release, and manual testing of the client. The manual test found a bug the automated tests missed: in Git Bash,
`System.console().readPassword()` returns an empty string (workaround: `winpty java -jar ...` or
PowerShell).

| Step | Agent           | What it does                                                    | Output                                 |
|------|-----------------|-----------------------------------------------------------------|----------------------------------------|
| 1    | `analyst`       | Defines the product scope and requirements from the briefing.   | `project/PRD.md`                       |
| 2    | `architect`     | Defines the design and records the architecture decisions.      | `project/ADD.md`                       |
| 3    | `product-owner` | Writes the specs, with user stories and acceptance criteria.    | `project/specs/*.spec.md`              |
| 4    | `engineer`      | Creates the spec's branch and its implementation plan.          | `project/specs/*.plan.md`              |
| 5    | `coder`         | Implements the plan with unit tests.                            | Code and unit tests                    |
| 6    | `tester`        | Verifies each acceptance criterion against the real API.        | End-to-end tests and a report          |
| 7    | `cleaner`       | Refactors without changing behavior, keeping all tests passing. | Refactoring commits                    |
| 8    | `devops`        | Updates the changelog and version, merges, tags and pushes.     | A release on `main`                    |

The `/build <spec>` command orchestrates steps 4 to 8:

1. The `engineer` writes the plan.
2. The `coder` and the `tester` loop for at most 3 rounds, with the tester's report going back to
   the coder after each failure.
3. The `cleaner` refactors the verified code.
4. The command stops and asks a person to confirm the release.
5. The `devops` agent releases the spec.

Where everything is:

| Path               | Contents                                                                   |
|--------------------|----------------------------------------------------------------------------|
| `.claude/agents/`  | The agents in the table above.                                             |
| `.claude/skills/`  | The rules and procedures they follow, such as coding, testing and releasing. |
| `.claude/commands/` | The `/build` command.                                                     |
| `project/briefing.md` | The project briefing and the API's constraints.                         |
| `project/PRD.md`   | Product requirements.                                                      |
| `project/ADD.md`   | Architecture and decisions.                                                |
| `project/specs/`   | The specs and their plans.                                                 |
| `project/BACKLOG.md` | Improvements that were left for later.                                   |

## Result

- **Versions**: v0.1.0 to v0.4.0, one per feature spec. The `chore-build-setup` spec has no version
  of its own, and its work is listed under v0.1.0. See the [changelog](CHANGELOG.md).

  | Version | Spec                    | Adds                              |
  |---------|-------------------------|-----------------------------------|
  | v0.1.0  | `feat-launch-listing`   | The build, the menu and the launch list |
  | v0.2.0  | `feat-session`          | Register, log in, log out and the saved session |
  | v0.3.0  | `feat-booking-creation` | Booking seats                     |
  | v0.4.0  | `feat-booking-retrieval` | Listing and viewing bookings     |

- **Tests**: 392 unit tests and 46 end-to-end tests, all passing on v0.4.0.

## Findings about the API

These come from building against the API and were checked against its source and by calling it. The
[known limitations in the PRD](project/PRD.md#assumptions--constraints) cover the same ground.

- **`POST /login` always responds 401.** Each route module creates its own `UserService` when it is
  imported, which happens before `loadAllData()` fills the repositories, so the login service's
  email index starts empty. Registering does not fix it, because `routes/users.ts` and
  `routes/auth.ts` use separate instances, and the login one never learns of the new user. As a side
  effect, after a restart a repeated email is accepted and user ids are reused.

  The client cannot fix this, so it works around it. The token comes from registration and is saved
  for later runs. The login option is still there in case the API gets fixed: a 401 is explained
  and the client offers to register instead.
- **Free seats are not exposed.** No launch field gives them, so the client cannot show them or check
  them in advance. The API enforces capacity on `POST /bookings`, and the client shows the numbers
  from its error: `Capacity exceeded. Available: N, Requested: M`.
- **The API accepts bookings on draft launches.** The client avoids this: it offers only `published`
  and `confirmed` launches, and its booking use case refuses any other status without calling the
  API.

## Limitations and future work

Limits of the product, which mostly come from the API:

- Bookings cannot be cancelled, and the client shows no rocket data, because the API offers neither.
- Registering again creates a different user, who cannot see the earlier user's bookings.
- This is a training exercise, not for production. For example, the session file has no special
  protection.

Open items, from [`project/BACKLOG.md`](project/BACKLOG.md):

- **Password input**: in Git Bash, `System.console().readPassword()` returns an empty string, and
  the workaround is described in [Build and run the client](#build-and-run-the-client). The
  `fix-password-input` spec is on hold and not written yet.
- **Launch parsing**: one launch with a fractional value makes the whole launch list fail, and the
  booking option with it. The `fix-launch-parsing-robustness` spec covers this, along with URLs with
  a query or fragment and responses that stall after their headers. It is written but on hold, and
  one question about numeric strings needs deciding before it is built.
- **Fractional seats**: the API accepts them, and one such booking makes the customer's list of
  bookings show a generic error. The client itself only sends whole seats.
- **Console flows**: the email re-prompt has no way to cancel, and `ConsoleMenu` could be split into
  smaller views.
- **Documentation**: the ADD and the PRD need small updates to match the code and the findings above.
- **Tooling**: `/build` requires the API even for a spec that does not use it, `./mvnw package` gives
  overlapping-file warnings from the Jackson jars, and the `coding-java` examples have unreviewed
  compiler warnings.

## Course mapping

| Course module                | How it was applied                                                                 |
|------------------------------|------------------------------------------------------------------------------------|
| Rules                        | `AGENTS.md` and the skills in `.claude/skills/`.                                   |
| Prompts as code              | The `/build` command, in `.claude/commands/build.md`.                              |
| Analysis and architecture    | The `analyst` and `architect` agents: the PRD, and the ADD with ADRs and a Mermaid diagram. |
| Specs and plans              | Specs with EARS acceptance criteria from the `product-owner`, and plans from the `engineer`. |
| Implementation and release   | The `coder`, `tester`, `cleaner` and `devops` agents, with semver releases.        |
| Builder                      | `/build`, with its coder and tester loop of at most 3 rounds.                      |

Not used:

- **MCP**: the specs live in the repository, not in issues.
- **Background or cloud tasks**: they were not part of the workflow. `/build` runs its agents one at
  a time and waits for each to finish.
- **An orchestrator for the architecture phase**: the `analyst`, `architect` and `product-owner` were
  launched separately.

## Course material

This repository started from the course's template.

- [Original course template](https://github.com/Programacion-Inteligente/programacion-inteligente-practica-final-ejercicio-curso)
- [Exercise briefing](astrobookings-cli.briefing.md)
