---
name: generating-add
description: Generates the Architecture Design Document in project/ADD.md from the PRD. Use when starting the architecture phase, or when the user asks to create or update the ADD, the system design or the architecture decisions.
---

# Generating an ADD

1. Read `project/PRD.md`, `project/briefing.md` and `AGENTS.md`. If `project/PRD.md` does not
   exist, stop and report that the PRD must be generated first. If `project/ADD.md` already
   exists, read it too and preserve its decision IDs.

2. Ask the user about any doubtful constraint that would change the architecture. If you
   cannot ask directly, for example when running as a subagent, do not write the document.
   Return the open questions as your final message instead.

3. Fill in [ADD.template.md](ADD.template.md). Replace every `{placeholder}` and remove the
   template's instructions. Trace the design back to the PRD's FR and TR IDs where it helps.

4. Save the result to `project/ADD.md`.

5. Update `AGENTS.md` only if the tech stack or the folder structure changed. Keep it under
   100 lines of under 100 characters each.

6. Report the paths you changed and any decision that needs the user's review.
