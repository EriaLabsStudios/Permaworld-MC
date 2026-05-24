# Sort UI Buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three small inventory UI buttons for sort by name, amount, and category, with full tooltips plus sound/actionbar/visual feedback.

**Architecture:** Keep pure sorting logic in `feature/sort`, add a `SortMode` enum and category classifier, and expose `InventorySorter.sort(SortMode)`. Use the existing `AbstractContainerScreenMixin` to add screen widgets and render a short flash over sorted slots.

**Tech Stack:** Java 25, Fabric API, Minecraft 26.1.2 client GUI, Mixin, JUnit 5.

---

### Task 1: Sort Modes and Pure Strategy Tests

**Files:**
- Create: `src/client/java/net/serex/permaworld/client/feature/sort/SortMode.java`
- Create: `src/client/java/net/serex/permaworld/client/feature/sort/SortCategory.java`
- Modify: `src/client/java/net/serex/permaworld/client/feature/sort/SortableSlot.java`
- Modify: `src/client/java/net/serex/permaworld/client/feature/sort/SortStrategy.java`
- Modify: `src/test/java/net/serex/permaworld/client/feature/sort/SortStrategyTest.java`

- [ ] Add failing tests for name/count/category ordering.
- [ ] Run focused sort tests and confirm failures.
- [ ] Add `SortMode` and `SortCategory`.
- [ ] Extend `SortableSlot` with category data while keeping simple constructors usable.
- [ ] Update `SortStrategy.sort(current, lockedSlots, mode)`.
- [ ] Keep existing `sort(current, lockedSlots)` delegating to name mode.
- [ ] Run focused sort tests and full test suite.

### Task 2: Inventory Sorter API and Feedback State

**Files:**
- Modify: `src/client/java/net/serex/permaworld/client/feature/sort/InventorySorter.java`
- Modify: `src/client/java/net/serex/permaworld/client/feature/sort/SortFeatureModule.java`
- Create: `src/client/java/net/serex/permaworld/client/feature/sort/SortFeedback.java`

- [ ] Add `InventorySorter.sort(SortMode mode)` while preserving keybind behavior as name sort.
- [ ] Return or publish sorted menu slot ids so UI feedback can flash affected slots.
- [ ] Play a vanilla UI sound after a successful sort.
- [ ] Send actionbar text for the chosen mode.
- [ ] Keep no-op sorts quiet except optional debug logs.

### Task 3: Container UI Buttons

**Files:**
- Modify: `src/client/java/net/serex/permaworld/mixin/client/AbstractContainerScreenMixin.java`
- Modify: `src/client/resources/assets/permaworld/lang/en_us.json`
- Modify: `src/client/resources/assets/permaworld/lang/es_es.json`

- [ ] Inject after screen init and add three compact buttons: `A`, `#`, `T`.
- [ ] Each button uses tooltip text:
  - `Ordenar por nombre`
  - `Ordenar por cantidad`
  - `Ordenar por categoría`
- [ ] Position buttons at the top-right edge of the container GUI using `leftPos` and `topPos`.
- [ ] Button clicks call `InventorySorter.sort(SortMode.NAME/COUNT/CATEGORY)`.
- [ ] Render feedback flash over affected slots while the feedback timer is active.

### Task 4: Verification

**Files:**
- No planned source edits.

- [ ] Run `.\gradlew.bat test`.
- [ ] Run `.\gradlew.bat build`.
- [ ] Report remaining in-game validation: open player inventory, chest, barrel; verify buttons, tooltips, sort modes, sound, actionbar, and flash.
