---
name: goaltree-fix-prompt
description: Turn a described or screenshotted UI bug/change request for the GoalTree app (TreeIns.html) into a detailed, structured Russian-language fix-it prompt grounded in the actual code — without implementing anything. Use whenever the user shows a screenshot of broken or awkward behavior in the tree visualization (dot/branch rendering, drag, zoom/pan, layout, progress indicators, node edit popover) or describes a change they want there, and is asking for a plan/spec to act on later rather than an immediate code change. Also covers the follow-up case where the user says something like "улучши этот промт" / "исправь и это тоже" and points at a new problem or screenshot on top of a prompt from earlier in the conversation — this is the merge/revision mode: fold the new requirement into a single standalone updated prompt rather than writing a diff.
---

# GoalTree fix-prompt writer

The user (Russian-speaking) uses this to plan fixes for their GoalTree PWA (`TreeIns.html`) before
actually implementing them — the deliverable is always a prompt/spec document, never a code change.
Do not edit `TreeIns.html` while this skill is active unless the user separately and explicitly asks
for the fix to be implemented now.

## Why grounding in real code matters here

A generic "here's a nice-sounding fix plan" is close to useless for this project: `TreeIns.html` is a
single file that gets rewritten fast between sessions, and in practice a lot of what a fresh prompt
would ask for turns out to already be implemented by the time you read the code (e.g. drag-and-drop,
dot-collapsed branches, and the edit popover all went from "requested" to "already exists" within a
few iterations in this project's history). Skipping the read step produces a prompt that re-requests
solved problems and wastes the next session's effort re-diagnosing what's actually still broken. So:

1. **Read `CLAUDE.md` first** for the architecture overview (state model, render cycle, how the tree
   canvas layout/collapse/drag/zoom pieces fit together) — it orients you fast without re-deriving it.
2. **Read (or grep) the relevant part of `TreeIns.html` itself**, not just CLAUDE.md's summary — you
   need the actual current function names, CSS classes, and state variables to write a prompt someone
   can act on precisely (e.g. `computeTreeLayout`, `renderNodeDot`, `shouldCollapse`, `actionEditNode`,
   `NODE_POPOVER`, `.node-dot`, `.tree-lines` — but confirm names against the current file; don't
   assume prior names still apply).
3. **Check whether the thing being asked for already exists.** If the user's ask sounds like something
   already in the code, say so plainly instead of drafting a task for it — either it's actually done
   (tell the user), or it's done-but-buggy (scope the task as a fix to the existing mechanism, citing
   the specific function, not as new work).

## Diagnosing the gap

If given a screenshot, look at what it actually shows before theorizing — e.g. "connector lines look
diagonal even on a linear chain with no branching" is a much sharper diagnostic starting point than
"tree looks messy," and it rules out causes (branch-count clutter) that don't fit the evidence. Trace
the visual symptom back to the specific code responsible (a CSS rule, a layout function's assumption,
an event handler) rather than describing only the desired end state — the prompt should tell the next
session *why* the current approach produces this symptom, not just what "good" looks like.

## Output structure

Write the prompt in Russian, in a fenced code block so it's easy to copy, following this shape (adapt
section names to what's actually needed — don't force empty sections):

1. One-line framing sentence, in prose, before the code block (not part of it) — briefly say what
   you diagnosed and why, so the user knows you didn't just template it.
2. **КОНТЕКСТ КОДА** — bullet list of the real, current functions/classes/state relevant to the ask.
3. **ПРЕРЕКВИЗИТ(Ы)** (only if applicable) — foundational changes (data model fields, a rendering
   approach swap) that the main tasks depend on, called out separately so they're done first and once,
   not accidentally duplicated across tasks.
4. **ЗАДАЧА N — <short title>** for each distinct piece of work — concrete, numbered requirements
   under each, written so someone unfamiliar with this conversation could act on them without asking
   follow-up questions.
5. **КРИТЕРИИ ГОТОВНОСТИ** — a checklist of observable outcomes (what should be true when done), not
   implementation steps restated.

After the code block, add a short **"от меня"**-style section (2-5 bullets, outside the copyable
block) with your own concrete suggestions or things worth deciding explicitly before implementing —
framed as recommendations for the user to accept or wave off, not as decisions already made for them.

## Merge/revision mode

When the user points at a new problem on top of a prompt from earlier in this conversation:

- Re-read the code fresh — don't assume the previous prompt's "КОНТЕКСТ КОДА" is still accurate; the
  file may have changed since (by the user, by a prior session, or because part of the old prompt was
  already implemented).
- Produce one full, standalone, updated prompt that supersedes the old one — not a diff and not "see
  above, plus this." Keep the still-relevant constraints from before, fold in the new requirement
  where it naturally fits (extending an existing ЗАДАЧА if it's the same area, adding a new one or a
  new ПРЕРЕКВИЗИТ if it's foundational to the rest), and drop anything the fresh code read shows is
  already solved.
- If the new evidence changes the diagnosis of an earlier task (e.g. a symptom initially blamed on
  clutter turns out, from a cleaner test case, to be a layout bug instead), say so explicitly and
  correct that section rather than layering a patch over a now-wrong diagnosis.
