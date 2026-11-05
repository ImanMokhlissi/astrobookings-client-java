---
name: generating-specs
description: Generates a feature, fix or chore specification in project/specs/ from the PRD and the ADD. Use when the user asks to create or update a spec, or to define the acceptance criteria of a piece of work before implementing it.
---

# Generating a spec

1. Read `project/PRD.md`, `project/ADD.md` and the existing specs in `project/specs/`. If the
   PRD or the ADD does not exist, stop and report that it must be generated first. If the spec
   already exists, update it and preserve its acceptance criteria numbers.

2. Decide the type of the spec:

   - **feat**: new behavior the user can see.
   - **fix**: corrects behavior that does not match the PRD or the ADD.
   - **chore**: maintenance or setup with no behavior the user can see.

3. Ask the user only what cannot be deduced from those sources. If you cannot ask directly, for
   example when running as a subagent, do not write the spec. Return the open questions as your
   final message instead.

4. Fill in [spec.template.md](spec.template.md). Replace every `{placeholder}` and remove the
   template's instructions. Write the acceptance criteria following [EARS.md](EARS.md). Do not
   add requirements that the PRD and the ADD do not support.

5. Save the result to `project/specs/<type>-<slug>.spec.md`. A new spec starts as `draft`.

6. In `project/PRD.md`, set the status of the affected FR and TR to `in-progress`. Change only
   the Status column.

7. Report the path, the affected FR and TR, and any assumptions you made.

## Spec statuses

`draft` → `planned` → `in-progress` → `verified` → `released`
