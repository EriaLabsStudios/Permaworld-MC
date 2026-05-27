# Permaworld Web Logbook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build an embedded localhost-only admin web UI inside the mod that reads existing JSONL records, lets admins investigate player logs, and triggers chest-based recovery for restorable snapshots.

**Architecture:** Add a small server-side web feature wired from mod initialization, backed by a focused query layer that reshapes existing record-store data into browser-friendly DTOs. Serve a single Minecraft-styled HTML app with lightweight JS and CSS assets, and keep restore operations server-authoritative by routing them through the existing recovery services.

**Tech Stack:** Fabric mod Java 25, built-in JDK HTTP server or equivalent small embedded HTTP server, existing Gson/record store code, static HTML/CSS/JS assets served from mod resources, JUnit signature and service tests.

---

### Task 1: Lock the embedded web feature entrypoint

**Files:**
- Modify: `src/main/java/net/serex/permaworld/Permaworld.java`
- Create: `src/main/java/net/serex/permaworld/server/web/PermaworldWebFeature.java`
- Test: `src/test/java/net/serex/permaworld/server/web/PermaworldWebFeatureSignatureTest.java`

- [x] **Step 1: Write the failing source/signature test**
- [x] **Step 2: Run the targeted test and verify it fails**
- [x] **Step 3: Add the minimal `PermaworldWebFeature` skeleton and register it from `Permaworld`**
- [x] **Step 4: Run the targeted test and verify it passes**
- [x] **Step 5: Commit**

### Task 2: Add safe web configuration defaults

**Files:**
- Create: `src/main/java/net/serex/permaworld/server/web/PermaworldWebConfig.java`
- Modify: `src/main/java/net/serex/permaworld/server/web/PermaworldWebFeature.java`
- Test: `src/test/java/net/serex/permaworld/server/web/PermaworldWebConfigTest.java`

- [x] **Step 1: Write failing tests for `disabled by default`, `localhost host`, and `default port` behavior**
- [x] **Step 2: Run the config test and verify it fails**
- [x] **Step 3: Implement minimal config object/constants and wire them into the feature**
- [x] **Step 4: Run the config test and verify it passes**
- [x] **Step 5: Commit**

### Task 3: Stand up the embedded HTTP server shell

**Files:**
- Create: `src/main/java/net/serex/permaworld/server/web/PermaworldHttpServer.java`
- Modify: `src/main/java/net/serex/permaworld/server/web/PermaworldWebFeature.java`
- Test: `src/test/java/net/serex/permaworld/server/web/PermaworldHttpServerSignatureTest.java`

- [x] **Step 1: Write failing signature tests for start/stop lifecycle and localhost binding usage**
- [x] **Step 2: Run the targeted signature test and verify it fails**
- [x] **Step 3: Implement the minimal server wrapper and lifecycle hooks**
- [x] **Step 4: Run the targeted signature test and verify it passes**
- [x] **Step 5: Commit**

### Task 4: Build DTOs and query service over existing records

**Files:**
- Create: `src/main/java/net/serex/permaworld/server/web/WebRecordQueryService.java`
- Create: `src/main/java/net/serex/permaworld/server/web/WebDtos.java`
- Modify: `src/main/java/net/serex/permaworld/server/record/PermaworldRecordStore.java`
- Test: `src/test/java/net/serex/permaworld/server/web/WebRecordQueryServiceTest.java`

- [x] **Step 1: Write failing tests for player summary listing, newest-first filtered record listing, and single-record detail mapping**
- [x] **Step 2: Run the query-service test and verify it fails**
- [x] **Step 3: Implement DTO mapping and any minimal record-store helpers needed for web queries**
- [x] **Step 4: Run the query-service test and verify it passes**
- [x] **Step 5: Commit**

### Task 5: Expose player list and record JSON endpoints

**Files:**
- Modify: `src/main/java/net/serex/permaworld/server/web/PermaworldHttpServer.java`
- Modify: `src/main/java/net/serex/permaworld/server/web/WebRecordQueryService.java`
- Test: `src/test/java/net/serex/permaworld/server/web/PermaworldHttpRoutesTest.java`

- [x] **Step 1: Write failing route tests for `/api/players`, `/api/players/{player}/records`, and `/api/players/{player}/records/{recordId}`**
- [x] **Step 2: Run the route test and verify it fails**
- [x] **Step 3: Implement the JSON handlers and error responses for missing player or record**
- [x] **Step 4: Run the route test and verify it passes**
- [x] **Step 5: Commit**

### Task 6: Expose restore action endpoint with permission checks

**Files:**
- Create: `src/main/java/net/serex/permaworld/server/web/WebRestoreService.java`
- Modify: `src/main/java/net/serex/permaworld/server/web/PermaworldHttpServer.java`
- Modify: `src/main/java/net/serex/permaworld/server/record/InventoryChestRestorer.java`
- Test: `src/test/java/net/serex/permaworld/server/web/WebRestoreServiceTest.java`

- [x] **Step 1: Write failing tests for `inventory_snapshot only`, permission validation, and successful recovery result mapping**
- [x] **Step 2: Run the restore-service test and verify it fails**
- [x] **Step 3: Implement the restore adapter and POST restore route**
- [x] **Step 4: Run the restore-service test and verify it passes**
- [x] **Step 5: Commit**

### Task 7: Add the web app shell and Minecraft-style static assets

**Files:**
- Create: `src/main/resources/assets/permaworld/web/index.html`
- Create: `src/main/resources/assets/permaworld/web/app.css`
- Create: `src/main/resources/assets/permaworld/web/app.js`
- Create: `src/main/resources/assets/permaworld/web/icons/`
- Modify: `src/main/java/net/serex/permaworld/server/web/PermaworldHttpServer.java`
- Test: `src/test/java/net/serex/permaworld/server/web/WebAssetsSignatureTest.java`

- [x] **Step 1: Write failing smoke tests for the app shell route and required static assets**
- [x] **Step 2: Run the asset test and verify it fails**
- [x] **Step 3: Implement the HTML shell and static asset serving**
- [x] **Step 4: Build the full Minecraft-modern visual language in CSS: Monocraft, slot borders, hard shadows, stone/grass accents, and responsive three-pane layout**
- [x] **Step 5: Add lightweight JS to load players, filters, records, selected detail, and restore feedback**
- [x] **Step 6: Run the asset test and verify it passes**
- [x] **Step 7: Commit**

### Task 8: Reduce record noise and humanize labels in the web layer

**Files:**
- Modify: `src/main/java/net/serex/permaworld/server/web/WebDtos.java`
- Modify: `src/main/resources/assets/permaworld/web/app.js`
- Modify: `src/main/resources/assets/permaworld/web/index.html`
- Test: `src/test/java/net/serex/permaworld/server/web/WebRecordQueryServiceTest.java`

- [x] **Step 1: Write failing assertions for human-friendly labels, default `Deaths` filter, and path records remaining secondary**
- [x] **Step 2: Run the DTO/UI-adjacent test and verify it fails**
- [x] **Step 3: Implement reason label mapping and default investigation behavior**
- [x] **Step 4: Run the updated tests and verify they pass**
- [x] **Step 5: Commit**

### Task 9: Final integration verification

**Files:**
- Modify: `src/test/java/net/serex/permaworld/server/web/PermaworldWebFeatureSignatureTest.java`
- Modify: `src/test/java/net/serex/permaworld/server/web/PermaworldHttpRoutesTest.java`

- [x] **Step 1: Add final source checks for the approved UI hooks and route wiring**
- [x] **Step 2: Run `j25; .\\gradlew.bat test --tests net.serex.permaworld.server.web.*` and confirm the web tests pass**
- [x] **Step 3: Run `j25; .\\gradlew.bat test` and confirm the full suite passes**
- [x] **Step 4: Run `j25; .\\gradlew.bat build` and confirm the build passes**
- [x] **Step 5: Commit**
