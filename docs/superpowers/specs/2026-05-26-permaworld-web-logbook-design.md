# Permaworld Web Logbook Design

**Goal**

Build an admin-only HTTP web interface inside the Fabric mod that exposes Permaworld player records from the current world in a fast, readable, Minecraft-forward UI suitable for investigation and recovery workflows tied to issue #18.

**Product shape**

The v1 is a local server dashboard served directly by the mod. It is not a public website, not a database-backed service, and not a separate deployable app. It reads the existing JSONL world records already written by the server record system and presents them through a browser UI optimized for:

- finding a player quickly
- filtering noisy event types down to useful investigation views
- inspecting a selected record in detail
- triggering existing recovery behavior for restorable inventory snapshots

**Audience**

The primary user is a trusted server admin or world owner troubleshooting player issues such as:

- "I died and lost items"
- "I changed dimension and something broke"
- "Why did this player suddenly lose progress or inventory?"
- "Which snapshot should I restore into chests?"

**Visual direction**

The UI should feel full Minecraft, but modern and useful rather than nostalgic cosplay. The target is "admin console built out of Minecraft menus and slots."

Key styling rules:

- Use `Monocraft` as the primary UI font.
- Use pixel-style panels, slot frames, hard borders, and stepped shadows.
- Use Minecraft-like neutrals: stone gray, charcoal, iron, moss/grass green, gold/yellow, lapis blue, redstone red.
- Add small grass or moss accents in headers, separators, or active states so the UI has a little life.
- Avoid official Minecraft textures or ripped assets. All textures, icons, and patterns should be custom CSS/SVG work inspired by the game language.
- Use pixel-art inspired icons for death, chest, path, dimension, advancement, and game mode.

**Information architecture**

The v1 browser app has one main screen with three fixed regions:

1. Left rail: player list and search
2. Center pane: filtered event timeline for the selected player
3. Right pane: selected record details and actions

This keeps the investigation loop tight. The admin should not bounce between multiple pages to answer basic questions.

**Core interactions**

1. Select a player from the left rail.
2. Choose a filter such as `Deaths`, `All`, `Path`, `Game Mode`, `Dimension`, or `Advancements`.
3. Review the timeline in newest-first order.
4. Select a record to inspect.
5. Trigger `Create recovery chest` when the selected record is restorable.

**Data model and backend scope**

The HTTP feature reuses the existing JSONL files under the world save and the existing restore logic. No new persistence layer is added.

The server should expose endpoints for:

- app shell HTML
- static CSS/font/icon assets
- player list summary
- filtered record list for one player
- single record detail
- restore action for a specific player record

The data served by the API should be normalized into simple web DTOs instead of leaking raw internal JSON directly into the UI.

**Security model**

The web UI is admin-only in practice.

V1 rules:

- Bind only to localhost by default.
- Feature is disabled by default unless explicitly enabled in config or constant-based v1 toggle.
- Show a clear "admin tool" label in the UI.
- Restore action requires server-side permission validation before invoking chest recovery.
- No authentication system is added in v1 beyond localhost scope and server-side permission checks.

**Noise reduction**

The web UI must improve on the current command output by making noisy records easier to filter and understand.

Rules:

- Default filter for opening a player investigation is `Deaths`.
- `Path Sample` should not dominate the primary flow. It is available, but secondary.
- Record cards should use human labels like `Death`, `Respawn`, `Path Sample`, `Game Mode Change`.
- Include timestamps, dimension, item count, and a short cause/summary on the record card when available.

**Restore UX**

The restore CTA should say `Create recovery chest`.

Behavior:

- Only visible for `inventory_snapshot` records.
- Uses the existing server-side chest restoration path.
- Shows success/failure feedback inline in the UI.
- Never inserts directly into a player inventory.

**Implementation shape**

Server-side work should live under a new focused package such as:

`src/main/java/net/serex/permaworld/server/web/`

Suggested units:

- `PermaworldWebFeature`: lifecycle wiring and startup/shutdown
- `PermaworldWebConfig`: host/port/enabled defaults
- `PermaworldHttpServer`: embedded HTTP server wrapper
- `WebRecordQueryService`: player list, filtering, DTO shaping
- `WebRestoreService`: restore endpoint adapter around existing restore logic
- `WebAssets`: HTML/CSS/font/icon asset serving

The frontend can be plain server-rendered HTML plus light client JS, or a tiny no-build asset bundle. Prefer the smallest approach that keeps the screen responsive and maintainable.

**Testing**

Tests should cover:

- feature registration/startup hooks
- localhost/default-disabled configuration behavior
- player list and record filtering DTOs
- restore endpoint permission checks
- static asset route availability
- HTML or asset smoke checks for key labels and routes

**Out of scope**

- public internet hosting
- login screens or user accounts
- live websocket streaming
- editing or deleting records
- direct inventory restore to players
- analytics dashboards beyond record investigation
