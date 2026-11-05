---
name: generating-prd
description: Generates the Product Requirements Document in project/PRD.md from the project briefing and any existing code. Use when starting the analysis phase, or when the user asks to create or update the PRD or the product requirements.
---

# Generating a PRD

1. Read `project/briefing.md`, `AGENTS.md` and the code, if any exists. If `project/PRD.md`
   already exists, read it too and preserve its requirement IDs.

2. Decide what kind of project this is:

   - **Greenfield**: there is no application code. All requirements start as `not-started`.
   - **Brownfield**: there is application code. Set each status from what the code implements.

3. Ask the user only what cannot be deduced from those sources, such as scope limits or
   priorities. If you cannot ask directly, for example when running as a subagent, do not
   write the document. Return the open questions as your final message instead.

4. Fill in [PRD.template.md](PRD.template.md). Replace every `{placeholder}` and remove the
   template's instructions. Do not invent requirements that the sources do not support, and
   record anything you assumed under "Assumptions & constraints".

5. Save the result to `project/PRD.md`, then report the path and any assumptions you made.
