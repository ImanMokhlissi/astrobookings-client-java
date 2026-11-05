# Plan: Build setup

- **Spec**: [chore-build-setup](chore-build-setup.spec.md)
- **Branch**: chore/build-setup

## Steps

Steps 1 to 6 are for the `coder`. The `tester` follows [Verification](#verification), which has
no checkboxes because the tester does not change this plan.

### 1. Add a minimal `Main` with an untagged unit test

Covers: AC1, AC6

- [ ] Add `dev.iman.astrobookings.Main` in the root package, outside the layers. `main` delegates to a `run` method that returns exit code 0, in the shape `coding-java` describes. It wires nothing and prints nothing.
- [ ] Add the untagged `MainTest` in the same package, with `should_return_exit_code_0_when_run_without_the_api`. It calls `run` and never `main`, because `main` calls `System.exit`.

### 2. Create the five layer packages

Covers: AC2

- [ ] Add a `package-info.java` to each of `cli`, `service`, `api`, `model` and `session` under `dev.iman.astrobookings`. Git does not track empty folders, so without a file the packages do not exist in a clean checkout. Add no types.

### 3. Keep the tests with the `e2e` tag apart

Covers: AC6, AC7, AC8

- [ ] In `pom.xml`, configure `maven-surefire-plugin` to exclude the `e2e` tag by default (ADR-9).
- [ ] In `pom.xml`, add a profile `e2e` that selects only the `e2e` tag and also lifts the default exclusion. If the exclusion stays, the profile selects nothing and the run still passes with no tests.
- [ ] Run `./mvnw test` and check that `MainTest` still runs. Do not add a test with the `e2e` tag: the tester writes it.

### 4. Add Jackson

Covers: AC9

- [ ] In `pom.xml`, add `jackson-databind` with the compile scope, its version in a property like `junit.version`. It stays the only dependency outside the test scope.

### 5. Run the client from Maven

Covers: AC3

- [ ] In `pom.xml`, add `exec-maven-plugin` with `dev.iman.astrobookings.Main` as its main class, configured at plugin level so that a direct `exec:java` picks it up (ADR-6).
- [ ] Make `exec:java` survive the `System.exit` in `main`, which would otherwise stop Maven's JVM before the build ends. Use the plugin's `blockSystemExit` option, in a version that has it.
- [ ] Run `./mvnw compile exec:java` without `-Dexec.mainClass` and check that it ends with `BUILD SUCCESS`.

### 6. Package the executable jar

Covers: AC4, AC5

- [ ] In `pom.xml`, add `maven-shade-plugin` in the `package` phase, with `dev.iman.astrobookings.Main` as the jar's main class (ADR-8). Do not enable `minimizeJar`: `Main` does not use Jackson yet, so it would remove the Jackson classes.
- [ ] Run `./mvnw package`, then `java -jar` on the jar, and check that it exits with code 0.

## Checks

- [ ] `./mvnw compile` passes.
- [ ] `./mvnw test` passes.

## Verification

The `tester` does this after the coder finishes, recording the method for each criterion as
`testing-e2e-console` asks. Nothing here needs the API.

- **Test with the `e2e` tag**: write `BuildSetupE2eTest` in `dev.iman.astrobookings`, with `@Tag("e2e")`, that passes without the API. AC6, AC7 and AC8 cannot be checked without it. Build criteria are otherwise proved by command, with no test class.
- **AC1, AC2**: in a fresh clone of the branch, in a temporary folder outside the repository, run `./mvnw compile` and check that the five package folders exist.
- **AC3, AC5**: run `./mvnw compile exec:java` and `java -jar` with `ASTROBOOKINGS_API_URL` pointing at a local port with nothing listening, since the tester does not stop the API. Check `BUILD SUCCESS` and exit code 0.
- **AC4**: run `./mvnw package` and list the jar's contents to find `com/fasterxml/jackson/databind/ObjectMapper.class`. Only one jar in `target/` may have a main class: the shade plugin's `original-*.jar` is its non-executable backup.
- **AC6, AC7**: run `./mvnw test` and read the console output: `MainTest` runs and `BuildSetupE2eTest` does not. Do not rely on `target/surefire-reports`, which keeps files from earlier runs.
- **AC8**: run `./mvnw test -Pe2e` and read the console output: `BuildSetupE2eTest` runs and `MainTest` does not.
- **AC9**: read `pom.xml`: `jackson-databind` is the only declared dependency without the test scope.
