---
description: Start the GoalTree dev server and open it in the Browser pane for manual testing
---

Start the GoalTree app and get it ready for manual testing:

1. Call `preview_start` with `name: "goaltree"` — this reuses the config in `.claude/launch.json`
   (`python -m http.server 8935`) and opens the Browser pane at `http://localhost:8935/`. If a
   server is already running, it's reused.
2. Navigate to `http://localhost:8935/TreeIns.html` (the server root is a directory listing, not
   the app itself — there's no index.html).
3. Take a screenshot (or `read_page`) to confirm the home screen loaded ("Мой сад целей" / "Привычки").
3. Report that the app is up and ready, and mention the URL in case manual navigation is needed.

Do not use `Bash`/`PowerShell` to start the server directly — `preview_start` is the supported
path for running dev servers in this environment.
