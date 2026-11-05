---
name: planning-specs
description: Writes the implementation plan of a spec as ordered steps with task checkboxes, next to the spec in project/specs/. Use when a spec is ready to be built, or when the user asks to plan a spec.
---

# Planning a spec

1. Read the spec, `project/PRD.md`, `project/ADD.md` and the specs listed in its **Depends on**.
   If a dependency's status is not `released`, stop and report it.

2. Read the existing code that the spec touches, so the plan builds on what is there.

3. Fill in [PLAN.template.md](PLAN.template.md):

   - Write at most 9 ordered steps. Each step leaves the project compiling.
   - Write each task as a `- [ ]` checkbox small enough to do and check in one sitting.
   - Exception: write the tester's tasks as plain bullets in the **Verification** section, not as
     checkboxes, because the tester does not edit the plan.
   - Name the layers, classes and tests to change. Do not write the code.
   - Make every acceptance criterion traceable to at least one step or Verification bullet, using
     its `ACn` ID.
   - Do not add work that the spec does not ask for.

4. Save the plan next to the spec, as `project/specs/<type>-<slug>.plan.md`.

5. In the spec, change **Status** to `planned`. Change nothing else in it.

6. Report the path, the number of steps and any acceptance criterion that needs a decision.
