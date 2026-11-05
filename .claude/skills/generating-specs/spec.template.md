# {Spec title}

<!-- Type: feat, fix or chore. -->
<!-- Status: draft, planned, in-progress, verified or released. -->
<!-- Depends on: the slugs of the specs to implement first, or "none". -->

- **Type**: {feat | fix | chore}
- **Status**: draft
- **Related**: {FR and TR IDs from the PRD}
- **Depends on**: {type-slug, type-slug}

## Problem

<!-- One to three user stories. -->

- As a {role}, I want {capability}, so that {benefit}.

## Solution

<!-- Summarize the change by layer. Do not include code. Omit the layers it does not touch. -->

- **cli**: {What changes in the console menu and presentation.}
- **service**: {What changes in the use cases and client rules.}
- **api**: {What changes in the HTTP client and error mapping.}
- **model**: {What changes in the records and DTOs.}
- **session**: {What changes in the token persistence.}

## Acceptance criteria

<!-- At most 9, numbered, in EARS format. See EARS.md. -->

- **AC1**: {WHEN trigger, the system SHALL response.}

## Out of scope

- {Something this spec explicitly does not cover.}
