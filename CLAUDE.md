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
  `{id, text, description?, children[], completed | completedDate, pos?}`.
  `pos:{x,y}` is only present once a node has been manually dragged (see below) — its
  absence means "let the layout algorithm place it." The special daily-habits tree
  (`id:'daily'`, `isDaily:true`) has a flat `root.children` with no nested goal
  hierarchy and no drag/collapse behavior — most tree-view logic branches on
  `tree.isDaily`.
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
- **Views**: `VIEW` is one of `'home' | 'tree' | 'stats' | 'settings'`, dispatched in
  `render()`. `MODAL` drives the new-tree wizard (`WIZ_STEP`/`WIZ_DRAFT`) and other
  overlays independently of `VIEW`.
- **Styling**: all colors go through CSS custom properties on `:root` /
  `html[data-theme="dark"]` (no hardcoded colors in component CSS) — `applyTheme()` sets
  `data-theme` from `DATA.settings.theme` (`light`/`dark`/`auto`, auto follows
  `prefers-color-scheme`). Keep new UI theme-aware by using existing `--*` variables
  rather than literal colors.
