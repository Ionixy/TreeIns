# Prompt: generate a GoalTree goal tree

This is the **source** of the text the app shows on the screen
"Settings -> Build a tree with AI" when its language is English. Edit the prompt here.
The Russian original lives in `ai-prompt.md`.

Someone copies the prompt, hands it to any chatbot, describes their goal, and gets back
JSON which they paste into the app ("Paste a tree as text", or the field on that same
screen). Nothing has to be saved to a file along the way.

The app interpolates these values from the constants that actually enforce them, so the
copy below is what the reader really sees:

| In the prompt | Constant in `TreeIns.html` |
| --- | --- |
| `"app": "goaltree"` | `SHARE_APP` |
| `"format": 1` | `SHARE_FORMAT` |
| the list of types and colours | `TYPES`, `COLORS` |
| the length limits | `SHARE_LIM` |
| the node and depth caps | `IMPORT_MAX_NODES`, `IMPORT_MAX_DEPTH` |

Changing a constant changes the prompt in the app automatically; this file has to be
updated by hand to match.

---

You are helping me plan a goal and lay it out as a task tree for an app called
GoalTree. What I need back is a ready-made tree file in JSON, which I will
import into the app.

## MY GOAL

(fill these in, delete what does not apply)

- Goal: ...
- Deadline: ...
- Where I am now: ...
- What is already done: ...
- Constraints: ...
- Time I can put in: ...

## What I need from you

Break the goal into a tree of tasks and return it strictly in the shape described
under "File format".

### Requirements for the content

1. 6-9 top-level branches — major directions of work, not individual actions.
   Together they must cover the whole goal: not only the "main" part, but also
   what people usually forget (money, paperwork, health, deadlines, other people,
   a fallback plan).
2. 3-6 subtasks per branch. Maximum depth is 3 levels from the root
   (branch -> subtask -> sub-subtask). Use the third level only where it is
   genuinely needed, not everywhere.
3. Every task is a finished action with a checkable result. "Score 6.5 on IELTS"
   — yes. "English" — no. The wording must answer "what exactly will I do, and
   how will I know it is done".
4. Tasks inside a branch go in the order they are actually done. If something has
   to start well in advance because of a waiting time, say so in its description.
5. Add a description only where it carries information: a concrete number, a
   deadline, the order of steps, a common mistake, where to look. Do not restate
   the title in other words. If there is nothing to say, leave the field out.
6. 35-60 nodes in total. Fewer and the tree is useless; more and people stop
   using it.
7. Give deadlines and checkpoints a branch of their own: which dates are
   critical, what to verify and when, what happens if something slips.
8. Write in English, except for proper names (exams, organisations, programmes).

### About facts

If the goal depends on external rules, dates, exams, prices or procedures, check
them online if you can, and state in the description when the data was accurate.
If you cannot check, do not invent specific dates or amounts: write what needs to
be verified and exactly where (site, page, authority). A wrong deadline in a
tracker is worse than no deadline at all.

## File format

The answer is a single JSON object of exactly this shape:

{
  "app": "goaltree",
  "format": 1,
  "tree": {
    "name": "Short name",
    "goal": "The goal in one phrase",
    "type": "oak",
    "color": "green",
    "root": {
      "text": "The goal in one phrase",
      "description": "One phrase about the horizon and the main condition for success.",
      "children": [
        {
          "text": "Branch name",
          "description": "Why this branch exists.",
          "children": [
            { "text": "Task with a description", "description": "Specifics: a number, a date, the order of steps.", "children": [] },
            { "text": "Task without a description", "children": [] },
            {
              "text": "Task with nesting",
              "children": [
                { "text": "Sub-subtask", "children": [] }
              ]
            }
          ]
        }
      ]
    }
  }
}

Hard rules:

- "app": "goaltree" and "format": 1 — exactly so, or the app will refuse to
  open the tree.
- A node is only text, an optional description and children. Do not add id,
  completed, pos or any other field: the owner of the tree ticks things off
  themselves.
- Every node has children; leaves get an empty array [].
- type — exactly one of: oak 🌳, pine 🌲, palm 🌴, sakura 🌸, bush 🌿, maple 🍁.
  color — one of: green, blue, purple, red, orange. Any other value is silently replaced
  with oak / green.
- tree.goal and root.text are the same phrase.
- Length limits: name <= 120 characters (better <= 30, so it fits on a card),
  goal <= 400, node text <= 200, description <= 2000. Anything longer is cut on import.
- Line breaks inside texts are not preserved — write each description as one paragraph.
- It must be valid JSON: double quotes, no comments, no trailing commas. Escape
  quotes inside text as \" — or use typographic quotes.

## Answer format

1. First, 5-10 lines: the list of top-level branches and one phrase on what is in each.
2. Then the whole JSON as one code block, complete, with no "..." anywhere.
3. At the end, what I should check myself: facts you are unsure about, and
   decisions that are mine to make.

Add nothing else.
