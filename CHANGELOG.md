# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- The client can be compiled, run and tested through the Maven Wrapper on Java 21.
- `./mvnw package` produces a self-contained executable jar that runs with `java -jar`.
- `./mvnw test` runs without the API, and `./mvnw test -Pe2e` runs only the end-to-end tests.
