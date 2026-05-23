# Stabilization Build Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current Eria Permaworld client mod reproducible to build and safer to continue by validating the existing sort, slot-lock, and harvest foundations.

**Architecture:** Keep the mod client-only and preserve the current feature-module shape. Restore Gradle wrapper files first, then use TDD for behavior changes, focusing on pure units where Minecraft runtime classes make direct tests expensive.

**Tech Stack:** Java 25, Gradle 9.5 wrapper, Fabric Loom, Fabric API, JUnit 5.

---

### Task 1: Restore Reproducible Gradle Wrapper

**Files:**
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Modify: `gradle/wrapper/gradle-wrapper.properties` only if copied wrapper metadata needs to stay aligned with Gradle 9.5.0

- [ ] Copy wrapper scripts from a local Gradle project and keep this repo's `distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip`.
- [ ] Copy a compatible `gradle-wrapper.jar` into `gradle/wrapper/gradle-wrapper.jar`.
- [ ] Run `.\gradlew.bat --version` with Java 25 in PATH.
- [ ] Expected: Gradle starts and reports version `9.5.0`.

### Task 2: Establish Baseline Tests

**Files:**
- No production edits.

- [ ] Run `.\gradlew.bat test` with Java 25 in PATH.
- [ ] If it fails, record the exact failure before changing code.
- [ ] Expected after stabilization: both `SortStrategyTest` and `CropReplanterTest` run.

### Task 3: Align Sort Contract With Executor

**Files:**
- Modify: `src/client/java/net/serex/permaworld/client/feature/sort/SortStrategy.java`
- Modify: `src/test/java/net/serex/permaworld/client/feature/sort/SortStrategyTest.java`

- [ ] Write a failing test showing duplicate stacks remain separate after sort, because the runtime executor currently performs swaps, not stack-combining transfer clicks.
- [ ] Run the focused test and verify it fails because current `SortStrategy` merges stacks.
- [ ] Change `SortStrategy` to sort movable non-empty slots without merging stack counts.
- [ ] Run focused sort tests and verify they pass.
- [ ] Run the full test suite.

### Task 4: Make Slot Lock Modifier Match Configurable Keybind

**Files:**
- Create: `src/client/java/net/serex/permaworld/client/keybind/KeyInput.java`
- Modify: `src/client/java/net/serex/permaworld/client/keybind/KeyPoller.java`
- Modify: `src/client/java/net/serex/permaworld/client/feature/slotlock/SlotLockManager.java`
- Create: `src/test/java/net/serex/permaworld/client/keybind/KeyInputTest.java`

- [ ] Extract key-string evaluation into a pure helper that can answer whether a `KeyMapping.saveString()` value is down.
- [ ] Write failing tests for `key.keyboard.left.alt`, `key.keyboard.right.alt`, unbound keys, and mouse keys.
- [ ] Implement minimal helper logic.
- [ ] Update `KeyPoller` and `SlotLockManager` to reuse the helper while preserving ALT fallback if the mapping is unavailable.
- [ ] Run focused keybind tests and full test suite.

### Task 5: Update Project Plan Snapshot

**Files:**
- Modify: `permaworld-client-utilities.md`

- [ ] Update `Current Implementation` to match the repo: config/keybind/module base, sort, slot lock, harvest implemented; quick drop, trader, and config screen pending.
- [ ] Update risks to mention sort no longer promises stack merging unless a future executor supports it.
- [ ] Update delivery status notes without expanding scope.

### Task 6: Final Verification

**Files:**
- No production edits.

- [ ] Run `.\gradlew.bat test`.
- [ ] Run `.\gradlew.bat build`.
- [ ] Check `git status --short`.
- [ ] Report exact commands, results, and any remaining manual in-game validation needed.
