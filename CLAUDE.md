# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

GoalTree (Дерево целей) — a single-file, offline-first PWA for tracking goals as trees:
each goal is a root node, broken into branches and sub-branches of tasks, rendered as a
mind-map-style diagram. Everything (markup, CSS, JS) lives in one HTML file; there is no
build step, package manager, bundler, or test suite.

- `TreeIns.html` — the entire app (HTML + inline `<style>` + inline `<script>`). This is
  the file to edit.
- `TreeIns-artifact.html` — a snapshot of an earlier version (predates the drag/dot canvas
  rework, still on the old `<ul><li>` CSS-tree renderer). Not wired to anything; don't
  edit it as if it were live, and don't assume it reflects current behavior.
- `manifest.json`, `sw.js`, `apple-touch-icon.png`, `icon-192.png`, `icon-512.png` — PWA
  installability (Add to Home Screen) and offline caching. `sw.js` does network-first
  caching of exactly the files listed in its `ASSETS` array — add new top-level assets
  there if any are introduced, and bump `CACHE_NAME` when `ASSETS` changes so old caches
  get evicted.
- `art/` — the master artwork every shipped image is derived from, at full resolution:
  `logo-mark.png` (transparent, no rim) is the in-app mark, inlined into `TreeIns.html`
  and `install.html` as a base64 data URI so neither page needs an extra file;
  `app-icon.png` (cyan rim, drop shadow, opaque `#F8F8F8`) is the launcher/PWA icon and is
  downscaled into `icon-*.png`, `apple-touch-icon.png` and `android/.../mipmap-*/`.
  Regenerate the derivatives from these rather than editing a downscaled PNG.

## Running / testing changes

No build or install step. To try changes:

```bash
python -m http.server 8934
```

then open `http://localhost:8934/TreeIns.html`. Opening the file directly via `file://`
also works for everything except the service worker (registration is skipped on
`file://` by design — see the guard at the bottom of the `<script>`).

There is no test suite and no linter configured. Verify changes by exercising the app in
a browser (ideally at a narrow mobile width — the whole UI is designed for `#app{max-width:460px}`)
and checking the console for errors.

State persists in `localStorage` under the key in `const KEY` (currently `'goaltree:data:v2'`).
If you change the shape of `DATA`, bump this key or write a migration (see
`migrateRemoveCorner()` for the existing pattern of dissolving/upgrading old saved state
on `load()`), otherwise old localStorage data will be loaded as-is against new code.

## Architecture

Everything is global state + a full re-render on every mutation — there is no virtual
DOM, no component framework, no diffing.

- **State**: a handful of module-level `let`/`const` globals (`DATA`, `VIEW`,
  `CURRENT_TREE`, `MODAL`, `NODE_POPOVER`, `TREE_ZOOM`, etc.) hold all UI and app state.
  `DATA` is the persisted part (trees, streak, settings); the rest is ephemeral view
  state that resets on reload.
- **Data model**: `DATA.trees[]`, each tree has a `root` node. A node is
  `{id, text, description?, children[], completed | completedDate, pos?}`, plus the
  completion history: `completedAt` (the day a goal task was ticked) or
  `completedDays[]` (every day a daily habit was ticked, since `completedDate` only
  survives until the next tick). Both are absent on anything ticked before they
  existed, and an absent date means *unknown* — never today.
  `pos:{x,y}` is only present once a node has been manually dragged (see below) — its
  absence means "let the layout algorithm place it." The special daily-habits tree
  (`id:'daily'`, `isDaily:true`) has a flat `root.children` with no nested goal
  hierarchy and no drag/collapse behavior — most tree-view logic branches on
  `tree.isDaily`.
- **Notes**: `DATA.notes[]` of `{id, title, body, treeId, createdAt, updatedAt}`. They used
  to be one HTML field per tree (`tree.notebook`); `migrateNotesToSection()` moved them and
  kept `treeId`, so a tree's own button still opens the same text — `openNoteForTree()`
  makes that note on first use rather than seeding a blank one per tree. A note with no
  `treeId` stands alone, which is the point of the section. Structure comes from links, not
  from a parent field: `data-notelink` between notes, `data-nodelink` to a task (only
  offered when the note belongs to a tree). Both labels are resolved at *display* time from
  the target's current title, so renaming propagates and a deleted target leaves a broken
  link that keeps its wording. `sanitizeNotebook()` strips every attribute and restores
  exactly these two — a link kind it does not know about is silently unwrapped into text.
- **Two file formats, one app**: sharing a tree writes `{app, format, tree}` and
  deliberately strips ticks, history and notes; a backup writes `{app, kind:'backup',
  format, savedAt, data}` and is the whole of `DATA`, losing nothing. `kind` is what keeps
  them apart — each reader recognises the other's file and names the button that does take
  it. Restoring replaces everything, so `normalizeRestored()` repairs the file first:
  `renderHome()` reads the daily tree without checking it exists, and the layout pass
  assumes ids and `children` arrays, so a damaged backup would otherwise crash the app on
  the next paint with no way back in.
- **Progress views** read only from the completion dates (`completedAt`, `completedDays[]`)
  via `completionIndex()`; ticks made before those were recorded carry no date and are
  deliberately absent rather than guessed onto a day. When `streak.totalCompleted` runs
  ahead of what the index holds, the growth screen says so instead of letting the chart
  look wrong. The Today rings are Habits (done/total today), Tasks (goal-tree tasks today
  against `settings.dailyGoal`) and Week (active days of the last 7 against `WEEK_GOAL`,
  which is 5 on purpose — a target you lose by taking one day off stops motivating).
- **Actions** (`action*` functions) mutate `DATA` directly, call `save()`, then
  `render()`. There's no event/reducer layer — read an action function top-to-bottom to
  see its full effect.
- **Render**: `render()` rebuilds `#app.innerHTML` from scratch every time and then calls
  `bindEvents()` to (re)attach all listeners, since the DOM it's attaching to was just
  replaced. Any new interactive element needs its listener wired in `bindEvents()`
  (delegated via `data-*` attributes on the element, e.g. `data-toggle`, `data-addform`).
- **Tree canvas layout** (the part most likely to need touching): nodes are NOT laid out
  by DOM flow (no nested `<ul><li>`, despite that being the old approach in
  `TreeIns-artifact.html`). `computeTreeLayout()` computes an `{x, y}` per node in a
  Reingold-Tilford-lite pass (parent x = average of children's x; a node with a saved
  `node.pos` uses that instead and doesn't consume an auto x-slot). `renderTreeCanvas()`
  turns that into absolutely-positioned `.node-pos` divs plus an SVG `<path>` per edge
  (bezier curves between parent-bottom and child-top anchors) — this decoupling of
  "position" from "content size" is deliberate, so a node's own text/button width can
  never throw off alignment with its siblings (see the comment above `computeTreeLayout`
  for the reasoning). If you touch layout, keep positions and rendering separate: compute
  coordinates first, place elements second.
- **Collapsed vs full nodes**: `shouldCollapse()` decides per node — any non-root node
  with children collapses to a small dot (`renderNodeDot`); leaves and the root always
  render as full cards (`renderNodeCard`). Clicking a dot opens `NODE_POPOVER`, a modal
  showing the same "view" or "edit" body a full card would show inline
  (`renderNodeViewBody` / `renderNodeEditForm`), via `renderNodeActionsRow()` which both
  the full card and the popover share so their action buttons stay in sync.
- **Node dragging**: `bindNodeDrag()` / `onNodeDragStart|Move|End` implement pointer-based
  free repositioning (mouse + touch via Pointer Events) for any non-root node. During a
  drag, `updateEdgesForNode()` patches only the SVG paths touching that node (not a full
  re-render) for smooth motion; on drop, the node's `pos` is written in **raw layout
  space** (not screen space) and persisted — see the comment on `onNodeDragEnd` for why
  raw-space deltas are required (moving a node can shift the auto-computed positions of
  its own unpositioned ancestors, which shifts the next render's canvas offset).
- **Canvas pan/zoom**: separate from node dragging — `TREE_ZOOM{scale,x,y}` is applied as
  a CSS `transform` on `#tree-canvas` via `applyTreeZoom()`. `bindTreeZoomPan()` handles
  drag-to-pan and pinch-to-zoom on the `#tree-viewport` background (ignoring clicks that
  start on a button/input/`.node-draggable` so it doesn't fight node dragging), plus
  Ctrl+wheel zoom on desktop.
- **Views**: `VIEW` is one of `'home' | 'today' | 'tree' | 'notebook' | 'stats' |
  `'settings' | 'aiprompt'`, dispatched in `render()`. `MODAL` drives the new-tree wizard (`WIZ_STEP`/`WIZ_DRAFT`) and other
  overlays independently of `VIEW`.
- **Languages**: every user-facing string lives in `STRINGS.ru` / `STRINGS.en` and is read
  with `tr('key', {vars})`; `{placeholders}` are substituted from the second argument, and
  counted nouns go through `pluralize(n, key)` because Russian needs three plural forms and
  English two. Both tables must hold the same keys — a missing one silently falls back to
  Russian. New UI must not contain prose: put it in the table. `DATA.settings.lang` is
  `auto | ru | en` (`auto` follows `navigator.language`); saves written before this existed
  are migrated to `ru`, so nobody's UI changes language under them. `localeTag()` feeds
  `toLocaleDateString`/`toLocaleString`, and `buildAiPrompt()` picks between `aiPromptRu()`
  and `aiPromptEn()`, whose sources are `ai-prompt.md` and `ai-prompt.en.md`.
- **Styling**: the design language is Apple/iOS (HIG) — system font stack, iOS semantic
  colors, hairline separators, grouped inset lists, translucent blurred nav/tab bars,
  restrained shadows. Tokens are named after Apple's own vocabulary (`--label`, `--fill`,
  `--separator`, `--tint`, `--bg-elev`), so new UI should reach for those rather than
  inventing a colour. Chrome icons are inline stroked SVGs (`NAV_ICONS` / `ACT_ICONS`);
  emoji are kept only where they're content (tree species, task state).
- **Safe areas**: `--safe-t/-b/-l/-r` on `:root` default to `env(safe-area-inset-*)`, which
  covers browsers and the iOS standalone PWA. Android WebView reports those as 0 for the
  status and gesture bars, so `android/.../MainActivity.java` reads the real window insets
  and writes the same four properties as *inline* styles on `<html>` (inline beats `:root`,
  so no branch is needed). Anything pinned to a screen edge — the sticky topbar, the fixed
  tab bar, sheets, the toast — must spend the matching token, and `#app` has no top padding
  on purpose so the topbar's own background reaches behind the status bar. Renaming a token
  silently breaks the APK: it is referenced from Java by string.
  All colors go through CSS custom properties on `:root` /
  `html[data-theme="dark"]` (no hardcoded colors in component CSS) — `applyTheme()` sets
  `data-theme` from `DATA.settings.theme` (`light`/`dark`/`auto`, auto follows
  `prefers-color-scheme`). Keep new UI theme-aware by using existing `--*` variables
  rather than literal colors.
