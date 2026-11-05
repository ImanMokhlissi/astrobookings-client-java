---
name: coding-java
description: Rules and examples for writing production Java 21 code in this client. Use when implementing or refactoring Java code.
---

# Coding in Java

Follow the layers and decisions in `project/ADD.md`. If a rule here conflicts with it, the ADD
wins, and you report the conflict.

## Rules

- **Records for DTOs**: use records for DTOs and other plain data. Validate in the compact
  constructor.
- **Constructor injection**: a class receives what it needs through its constructor. Only the
  composition root creates the layers.
- **No mutable static state**: `static final` is only for constants that cannot change.
- **Optional in returns**: a lookup that may find nothing returns `Optional`, never `null`. Do
  not use `Optional` in fields or parameters.
- **No null in collections**: never hold `null` in a collection, and never return a `null`
  collection. Return an empty one, and copy with `List.copyOf`.
- **Short methods**: each method does one thing. Extract a method when you need a comment to
  separate its parts.
- **Clear names**: names say what something is, in full. Avoid abbreviations and names like
  `data` or `manager`.
- **The client's own exceptions**: translate `IOException`, Jackson and HTTP failures into the
  client's exceptions at the boundary. Never let them reach the user as a stack trace. Restore
  the interrupt flag when catching `InterruptedException`.
- **Testable exit code**: `main` only delegates to a `run` method that returns the exit code, so
  tests call `run` and never trigger `System.exit`:

  ```java
  public static void main(String[] args) { System.exit(run(System.getenv(), System.in, System.out)); }
  ```

## Examples

These examples are generic and show the shape, not this project's names. They keep supporting
types in one file for brevity; in the project each type goes in its own file and layer.

- [ExampleRecord.java](ExampleRecord.java): a record with validation and a safe collection.
- [ExampleService.java](ExampleService.java): constructor injection, `Optional` and client rules.
- [ExampleApiClient.java](ExampleApiClient.java): `HttpClient`, Jackson and the mapping of the
  `{message, errors}` error.
- [ExampleMenu.java](ExampleMenu.java): injected streams, numbered selection and 0 to cancel.
