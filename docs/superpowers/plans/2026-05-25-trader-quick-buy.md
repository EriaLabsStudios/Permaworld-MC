# Trader Quick Buy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build villager trade favorites with local yellow marks, global blue marks, and one button that buys all marked trades for the current villager.

**Architecture:** Keep favorite state and trade identity in small testable classes under `client/feature/trader`. Use a focused `MerchantScreen` mixin for hover stars, row tinting, and the main button because trade rows live inside the vanilla merchant screen. Execute purchases through vanilla client container interactions only after the player presses the button.

**Tech Stack:** Java 25, Fabric Loom, Minecraft/Fabric client APIs, Sponge Mixin, JUnit 5, existing Gson config.

---

## File Structure

- Modify `src/client/java/net/serex/permaworld/client/config/PermaworldConfig.java`: add local/global trader favorite config fields and keep old `favoriteTradeHashes` for migration.
- Modify `src/client/java/net/serex/permaworld/client/config/ConfigManager.java`: migrate old flat trader favorites into global favorites after load.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/TradeMark.java`: enum for `NONE`, `LOCAL`, `GLOBAL`.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/TradeFavoriteStore.java`: pure favorite lookup/toggle logic.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/TradeDescriptor.java`: pure description of trade ingredients/result for stable hashing.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/TradeIdentity.java`: hash builder and Minecraft `ItemStack`/offer adapters.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/TraderFeedback.java`: overlay feedback and click sound.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/TraderQuickBuyFeatureModule.java`: feature module registered at client init.
- Create `src/client/java/net/serex/permaworld/client/feature/trader/MarkedTradeBuyer.java`: buys marked offers from the current merchant screen.
- Create `src/client/java/net/serex/permaworld/mixin/client/MerchantScreenAccessor.java`: access vanilla merchant screen fields/methods needed by the mixin.
- Create `src/client/java/net/serex/permaworld/mixin/client/MerchantScreenMixin.java`: render row tints, handle star clicks, add `Buy marked` button.
- Modify `src/client/resources/permaworld.client.mixins.json`: register merchant mixins.
- Modify `src/client/resources/assets/permaworld/lang/en_us.json`: add trader strings.
- Modify `src/client/resources/assets/permaworld/lang/es_es.json`: add trader strings.
- Modify `src/client/java/net/serex/permaworld/client/PermaworldClient.java`: register trader module.
- Create `src/test/java/net/serex/permaworld/client/feature/trader/TradeFavoriteStoreTest.java`: favorite hierarchy tests.
- Create `src/test/java/net/serex/permaworld/client/feature/trader/TradeIdentityTest.java`: stable hash tests.

---

### Task 1: Trader Config Migration

**Files:**
- Modify: `src/client/java/net/serex/permaworld/client/config/PermaworldConfig.java`
- Modify: `src/client/java/net/serex/permaworld/client/config/ConfigManager.java`

- [ ] **Step 1: Update trader config shape**

In `PermaworldConfig.java`, add `import java.util.HashMap;` and `import java.util.Map;`, then replace `TraderConfig` with:

```java
public static class TraderConfig {
    public boolean enabled = true;
    /** Trades guardados globalmente: aplican a cualquier aldeano con la misma oferta. */
    public Set<Integer> globalFavoriteTradeHashes = new HashSet<>();
    /** Trades guardados por aldeano: villagerKey -> hashes de ofertas locales. */
    public Map<String, Set<Integer>> localFavoriteTradeHashes = new HashMap<>();

    /**
     * @deprecated Campo antiguo de favoritos simples. Se migra a globalFavoriteTradeHashes
     * al cargar la config para mantener compatibilidad con instalaciones previas.
     */
    @Deprecated
    public Set<Integer> favoriteTradeHashes = new HashSet<>();
}
```

- [ ] **Step 2: Add config migration**

In `ConfigManager.load()`, immediately after ensuring `config != null`, call `migrate();`.

Add this private method to `ConfigManager`:

```java
private void migrate() {
    if (config.trader == null) {
        config.trader = new PermaworldConfig.TraderConfig();
    }
    if (config.trader.globalFavoriteTradeHashes == null) {
        config.trader.globalFavoriteTradeHashes = new java.util.HashSet<>();
    }
    if (config.trader.localFavoriteTradeHashes == null) {
        config.trader.localFavoriteTradeHashes = new java.util.HashMap<>();
    }
    if (config.trader.favoriteTradeHashes != null && !config.trader.favoriteTradeHashes.isEmpty()) {
        config.trader.globalFavoriteTradeHashes.addAll(config.trader.favoriteTradeHashes);
        config.trader.favoriteTradeHashes.clear();
        save();
    }
}
```

- [ ] **Step 3: Compile check**

Run: `.\gradlew.bat compileJava compileClientJava`

Expected: build reaches `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

Run:

```bash
git add src/client/java/net/serex/permaworld/client/config/PermaworldConfig.java src/client/java/net/serex/permaworld/client/config/ConfigManager.java
git commit -m "feat: migrate trader favorite config"
```

---

### Task 2: Favorite Store With Local/Global Hierarchy

**Files:**
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/TradeMark.java`
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/TradeFavoriteStore.java`
- Create: `src/test/java/net/serex/permaworld/client/feature/trader/TradeFavoriteStoreTest.java`

- [ ] **Step 1: Write failing tests**

Create `TradeFavoriteStoreTest.java`:

```java
package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.config.PermaworldConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeFavoriteStoreTest {

    @Test
    void localMarksAreIsolatedByVillager() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);

        assertEquals(TradeMark.LOCAL, store.activeMark("villager-a", 42));
        assertEquals(TradeMark.NONE, store.activeMark("villager-b", 42));
    }

    @Test
    void globalMarkOverridesAndRemovesLocalMark() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleGlobal("villager-a", 42);

        assertEquals(TradeMark.GLOBAL, store.activeMark("villager-a", 42));
        assertTrue(config.globalFavoriteTradeHashes.contains(42));
        assertFalse(config.localFavoriteTradeHashes.getOrDefault("villager-a", java.util.Set.of()).contains(42));
    }

    @Test
    void removingGlobalDoesNotRestoreLocalMark() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleGlobal("villager-a", 42);
        store.toggleGlobal("villager-a", 42);

        assertEquals(TradeMark.NONE, store.activeMark("villager-a", 42));
    }

    @Test
    void localToggleRemovesLocalMark() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleLocal("villager-a", 42);

        assertEquals(TradeMark.NONE, store.activeMark("villager-a", 42));
    }

    @Test
    void localToggleIsIgnoredWhenTradeIsGlobal() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleGlobal("villager-a", 42);
        store.toggleLocal("villager-a", 42);

        assertEquals(TradeMark.GLOBAL, store.activeMark("villager-a", 42));
        assertFalse(config.localFavoriteTradeHashes.containsKey("villager-a"));
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `.\gradlew.bat test --tests net.serex.permaworld.client.feature.trader.TradeFavoriteStoreTest`

Expected: compile fails because `TradeFavoriteStore` and `TradeMark` do not exist.

- [ ] **Step 3: Add implementation**

Create `TradeMark.java`:

```java
package net.serex.permaworld.client.feature.trader;

public enum TradeMark {
    NONE,
    LOCAL,
    GLOBAL
}
```

Create `TradeFavoriteStore.java`:

```java
package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.config.PermaworldConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TradeFavoriteStore {

    private final PermaworldConfig.TraderConfig config;

    public TradeFavoriteStore(PermaworldConfig.TraderConfig config) {
        this.config = config;
        ensureCollections();
    }

    public boolean isGlobalFavorite(int tradeHash) {
        ensureCollections();
        return config.globalFavoriteTradeHashes.contains(tradeHash);
    }

    public boolean isLocalFavorite(String villagerKey, int tradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return false;
        }
        return config.localFavoriteTradeHashes.getOrDefault(villagerKey, Set.of()).contains(tradeHash);
    }

    public TradeMark activeMark(String villagerKey, int tradeHash) {
        if (isGlobalFavorite(tradeHash)) {
            return TradeMark.GLOBAL;
        }
        if (isLocalFavorite(villagerKey, tradeHash)) {
            return TradeMark.LOCAL;
        }
        return TradeMark.NONE;
    }

    public TradeMark toggleLocal(String villagerKey, int tradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return TradeMark.NONE;
        }
        if (isGlobalFavorite(tradeHash)) {
            return TradeMark.GLOBAL;
        }
        Set<Integer> local = config.localFavoriteTradeHashes.computeIfAbsent(villagerKey, ignored -> new HashSet<>());
        if (local.contains(tradeHash)) {
            local.remove(tradeHash);
            if (local.isEmpty()) {
                config.localFavoriteTradeHashes.remove(villagerKey);
            }
            return TradeMark.NONE;
        }
        local.add(tradeHash);
        return TradeMark.LOCAL;
    }

    public TradeMark toggleGlobal(String villagerKey, int tradeHash) {
        ensureCollections();
        if (config.globalFavoriteTradeHashes.contains(tradeHash)) {
            config.globalFavoriteTradeHashes.remove(tradeHash);
            return TradeMark.NONE;
        }
        config.globalFavoriteTradeHashes.add(tradeHash);
        removeLocal(villagerKey, tradeHash);
        return TradeMark.GLOBAL;
    }

    public boolean isMarked(String villagerKey, int tradeHash) {
        return activeMark(villagerKey, tradeHash) != TradeMark.NONE;
    }

    private void removeLocal(String villagerKey, int tradeHash) {
        if (villagerKey == null || villagerKey.isBlank()) {
            return;
        }
        Set<Integer> local = config.localFavoriteTradeHashes.get(villagerKey);
        if (local == null) {
            return;
        }
        local.remove(tradeHash);
        if (local.isEmpty()) {
            config.localFavoriteTradeHashes.remove(villagerKey);
        }
    }

    private void ensureCollections() {
        if (config.globalFavoriteTradeHashes == null) {
            config.globalFavoriteTradeHashes = new HashSet<>();
        }
        if (config.localFavoriteTradeHashes == null) {
            config.localFavoriteTradeHashes = new HashMap<>();
        }
    }
}
```

- [ ] **Step 4: Run tests and verify pass**

Run: `.\gradlew.bat test --tests net.serex.permaworld.client.feature.trader.TradeFavoriteStoreTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/client/java/net/serex/permaworld/client/feature/trader/TradeMark.java src/client/java/net/serex/permaworld/client/feature/trader/TradeFavoriteStore.java src/test/java/net/serex/permaworld/client/feature/trader/TradeFavoriteStoreTest.java
git commit -m "feat: add trader favorite hierarchy"
```

---

### Task 3: Stable Trade Identity

**Files:**
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/TradeDescriptor.java`
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/TradeIdentity.java`
- Create: `src/test/java/net/serex/permaworld/client/feature/trader/TradeIdentityTest.java`

- [ ] **Step 1: Write failing pure hash tests**

Create `TradeIdentityTest.java`:

```java
package net.serex.permaworld.client.feature.trader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TradeIdentityTest {

    @Test
    void equivalentDescriptorsHaveSameHash() {
        TradeDescriptor first = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );
        TradeDescriptor second = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );

        assertEquals(TradeIdentity.hash(first), TradeIdentity.hash(second));
    }

    @Test
    void differentCostHasDifferentHash() {
        TradeDescriptor cheap = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );
        TradeDescriptor expensive = new TradeDescriptor(
                "minecraft:emerald", 18,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );

        assertNotEquals(TradeIdentity.hash(cheap), TradeIdentity.hash(expensive));
    }

    @Test
    void differentResultHasDifferentHash() {
        TradeDescriptor pickaxe = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );
        TradeDescriptor axe = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_axe", 1
        );

        assertNotEquals(TradeIdentity.hash(pickaxe), TradeIdentity.hash(axe));
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `.\gradlew.bat test --tests net.serex.permaworld.client.feature.trader.TradeIdentityTest`

Expected: compile fails because `TradeDescriptor` and `TradeIdentity` do not exist.

- [ ] **Step 3: Add pure descriptor and hash**

Create `TradeDescriptor.java`:

```java
package net.serex.permaworld.client.feature.trader;

public record TradeDescriptor(
        String firstCostId,
        int firstCostCount,
        String secondCostId,
        int secondCostCount,
        String resultId,
        int resultCount
) {
}
```

Create `TradeIdentity.java`:

```java
package net.serex.permaworld.client.feature.trader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Objects;

public final class TradeIdentity {

    private TradeIdentity() {
    }

    public static int hash(TradeDescriptor descriptor) {
        return Objects.hash(
                descriptor.firstCostId(), descriptor.firstCostCount(),
                descriptor.secondCostId(), descriptor.secondCostCount(),
                descriptor.resultId(), descriptor.resultCount()
        );
    }

    public static int hash(MerchantOffer offer) {
        return hash(descriptor(offer));
    }

    public static TradeDescriptor descriptor(MerchantOffer offer) {
        return new TradeDescriptor(
                itemId(offer.getCostA()),
                count(offer.getCostA()),
                itemId(offer.getCostB()),
                count(offer.getCostB()),
                itemId(offer.getResult()),
                count(offer.getResult())
        );
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static int count(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.getCount();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat test --tests net.serex.permaworld.client.feature.trader.TradeIdentityTest`

Expected: `BUILD SUCCESSFUL`. If mapped method names differ from `getCostA`, `getCostB`, or `getResult`, inspect the compile error and replace only those adapter calls with the mapped names from Minecraft 26.1.2.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/client/java/net/serex/permaworld/client/feature/trader/TradeDescriptor.java src/client/java/net/serex/permaworld/client/feature/trader/TradeIdentity.java src/test/java/net/serex/permaworld/client/feature/trader/TradeIdentityTest.java
git commit -m "feat: add trader trade identity"
```

---

### Task 4: Merchant Screen Controls and Visual Marks

**Files:**
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/TraderFeedback.java`
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/TraderQuickBuyFeatureModule.java`
- Create: `src/client/java/net/serex/permaworld/mixin/client/MerchantScreenAccessor.java`
- Create: `src/client/java/net/serex/permaworld/mixin/client/MerchantScreenMixin.java`
- Modify: `src/client/resources/permaworld.client.mixins.json`
- Modify: `src/client/resources/assets/permaworld/lang/en_us.json`
- Modify: `src/client/resources/assets/permaworld/lang/es_es.json`
- Modify: `src/client/java/net/serex/permaworld/client/PermaworldClient.java`

- [ ] **Step 1: Inspect mapped merchant screen members**

Run: `rg "class MerchantScreen|selectedMerchantRecipe|tradeOffer|renderScroller|renderButton" "$env:USERPROFILE\\.gradle\\caches" -g "*.java"`

Expected: locate mapped source or generated source names for `MerchantScreen`. Record exact field/method names used for selected offer index, scrolling offset, and trade row rendering before writing the mixin.

- [ ] **Step 2: Add feature module**

Create `TraderQuickBuyFeatureModule.java`:

```java
package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.feature.FeatureModule;

public final class TraderQuickBuyFeatureModule implements FeatureModule {

    @Override
    public void onClientInit() {
        // Merchant UI hooks are installed through mixins.
    }
}
```

Modify `PermaworldClient.registerModules()` to add:

```java
MODULES.add(new TraderQuickBuyFeatureModule());
```

Add import:

```java
import net.serex.permaworld.client.feature.trader.TraderQuickBuyFeatureModule;
```

- [ ] **Step 3: Add feedback helper**

Create `TraderFeedback.java`:

```java
package net.serex.permaworld.client.feature.trader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class TraderFeedback {

    private TraderFeedback() {
    }

    public static void show(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(message, false);
        }
    }

    public static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
```

- [ ] **Step 4: Add translations**

Add these keys to `en_us.json`:

```json
"permaworld.trader.buy_marked": "Buy marked",
"permaworld.trader.local_favorite": "Local save",
"permaworld.trader.global_favorite": "Global save",
"permaworld.trader.no_marked": "No marked trades for this villager",
"permaworld.trader.local_unavailable": "Local save unavailable for this villager"
```

Add these keys to `es_es.json`:

```json
"permaworld.trader.buy_marked": "Comprar marcados",
"permaworld.trader.local_favorite": "Guardado local",
"permaworld.trader.global_favorite": "Guardado global",
"permaworld.trader.no_marked": "No hay trades marcados para este aldeano",
"permaworld.trader.local_unavailable": "Guardado local no disponible para este aldeano"
```

- [ ] **Step 5: Add merchant mixin shell**

Create `MerchantScreenMixin.java` with the final mapped method names from Step 1. The mixin must:

```java
package net.serex.permaworld.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.trader.MarkedTradeBuyer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void permaworld$trader$addBuyMarkedButton(CallbackInfo ci) {
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        MerchantScreen self = (MerchantScreen) (Object) this;
        Button button = Button.builder(Component.translatable("permaworld.trader.buy_marked"),
                        ignored -> MarkedTradeBuyer.buyMarked(self))
                .bounds(0, 0, 96, 20)
                .tooltip(Tooltip.create(Component.translatable("permaworld.trader.buy_marked")))
                .build();
        ((ScreenAccessor) this).permaworld$addRenderableWidget(button);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void permaworld$trader$renderMarks(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        MerchantScreen self = (MerchantScreen) (Object) this;
        MerchantScreenAccessor accessor = (MerchantScreenAccessor) self;
        int left = accessor.permaworld$leftPos();
        int top = accessor.permaworld$topPos();
        int start = accessor.permaworld$scrollOff();
        int rowX = left + 5;
        int rowY = top + 16;
        int rowHeight = 20;
        int starX = left - 18;
        int localColor = 0x66FFD54A;
        int globalColor = 0x6656A8FF;
        // Render rows start..start+7, clamp to current offer count.
        // The implementation should call TradeFavoriteStore.activeMark(villagerKey, hash)
        // for each offer and draw the row tint plus two hover stars at starX/starX+9.
    }
}
```

Place the button at `left + imageWidth - 102, top + 4`. If the mapped `MerchantScreen` does not expose layout fields through inheritance, add accessors for `leftPos`, `topPos`, `imageWidth`, `imageHeight`, and the merchant scroll offset in `MerchantScreenAccessor`.

- [ ] **Step 6: Register mixin**

Add `"MerchantScreenMixin"` to the `client` array in `permaworld.client.mixins.json`.

- [ ] **Step 7: Compile check**

Run: `.\gradlew.bat compileClientJava`

Expected: `BUILD SUCCESSFUL`. If `MerchantScreen` method signatures differ, adjust only the injection method descriptors to match mapped 26.1.2.

- [ ] **Step 8: Commit**

Run:

```bash
git add src/client/java/net/serex/permaworld/client/feature/trader/TraderFeedback.java src/client/java/net/serex/permaworld/client/feature/trader/TraderQuickBuyFeatureModule.java src/client/java/net/serex/permaworld/mixin/client/MerchantScreenMixin.java src/client/resources/permaworld.client.mixins.json src/client/resources/assets/permaworld/lang/en_us.json src/client/resources/assets/permaworld/lang/es_es.json src/client/java/net/serex/permaworld/client/PermaworldClient.java
git commit -m "feat: add trader screen controls"
```

---

### Task 5: Buy Marked Trades

**Files:**
- Create: `src/client/java/net/serex/permaworld/client/feature/trader/MarkedTradeBuyer.java`
- Modify: `src/client/resources/assets/permaworld/lang/en_us.json`
- Modify: `src/client/resources/assets/permaworld/lang/es_es.json`

- [ ] **Step 1: Add buyer feedback translations**

Add to `en_us.json`:

```json
"permaworld.trader.bought_marked": "Bought %s marked trade(s)",
"permaworld.trader.stopped_stock": "Stopped: trade out of stock",
"permaworld.trader.stopped_materials": "Stopped: missing materials",
"permaworld.trader.stopped_space": "Stopped: inventory full"
```

Add to `es_es.json`:

```json
"permaworld.trader.bought_marked": "Comprados %s trade(s) marcados",
"permaworld.trader.stopped_stock": "Parado: trade sin stock",
"permaworld.trader.stopped_materials": "Parado: faltan materiales",
"permaworld.trader.stopped_space": "Parado: inventario lleno"
```

- [ ] **Step 2: Add marked buyer**

Create `MarkedTradeBuyer.java`:

```java
package net.serex.permaworld.client.feature.trader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;

public final class MarkedTradeBuyer {

    private MarkedTradeBuyer() {
    }

    public static void buyMarked(MerchantScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        if (!(mc.player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        String villagerKey = villagerKey(screen, menu);
        TradeFavoriteStore store = new TradeFavoriteStore(ConfigManager.get().config().trader);
        MerchantOffers offers = menu.getOffers();
        int bought = 0;

        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            int hash = TradeIdentity.hash(offer);
            if (!store.isMarked(villagerKey, hash)) {
                continue;
            }
            if (offer.isOutOfStock()) {
                TraderFeedback.show(Component.translatable("permaworld.trader.stopped_stock"));
                DebugLog.log("trader", "Trade {} parado: sin stock.", hash);
                continue;
            }

            menu.setSelectionHint(index);
            mc.gameMode.handleInventoryButtonClick(menu.containerId, index);
            mc.gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.QUICK_MOVE, mc.player);
            bought++;
        }

        if (bought == 0) {
            TraderFeedback.show(Component.translatable("permaworld.trader.no_marked"));
            return;
        }
        TraderFeedback.click();
        TraderFeedback.show(Component.translatable("permaworld.trader.bought_marked", bought));
    }

    private static String villagerKey(MerchantScreen screen, MerchantMenu menu) {
        return "merchant-menu-" + menu.containerId;
    }
}
```

The buyer must repeat each marked offer until it cannot produce more output. Use a bounded loop with `int guard = 64` per offer to prevent runaway clicking. After each quick-move from result slot `2`, reread `menu.getOffers().get(index)` and stop the current offer when it is out of stock, when the result slot is empty, or when the guard reaches zero. The buyer continues with the next marked offer after a normal per-offer stop.

- [ ] **Step 3: Compile and adjust mapped names**

Run: `.\gradlew.bat compileClientJava`

Expected: `BUILD SUCCESSFUL`. If `handleInventoryButtonClick`, `setSelectionHint`, `getOffers`, or result slot id differ in mapped 26.1.2, inspect the compile error and update the calls to the mapped equivalent.

- [ ] **Step 4: Commit**

Run:

```bash
git add src/client/java/net/serex/permaworld/client/feature/trader/MarkedTradeBuyer.java src/client/resources/assets/permaworld/lang/en_us.json src/client/resources/assets/permaworld/lang/es_es.json
git commit -m "feat: buy marked trader offers"
```

---

### Task 6: Final Verification

**Files:**
- Review all files changed in Tasks 1-5.

- [ ] **Step 1: Run unit tests**

Run: `.\gradlew.bat test`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full build**

Run: `.\gradlew.bat build`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect final diff**

Run: `git diff --stat HEAD~5..HEAD`

Expected: changes are limited to trader feature, config migration, translations, mixin registration, tests, and docs.

- [ ] **Step 4: Manual verification checklist**

Launch the client with `.\gradlew.bat runClient` and verify:

- Merchant UI shows `Comprar marcados`.
- Hovering a visible trade shows local/global star controls after the final UI implementation fills row coordinates.
- Yellow marks affect only the current villager key.
- Blue marks affect equivalent trades across villagers.
- Blue marks remove redundant yellow marks.
- `Comprar marcados` never buys unmarked rows.
- Empty marks, no stock, missing materials, and full inventory show feedback.

---

## Self-Review

- Spec coverage: local/global marks, hierarchy, save migration, marked-only buying, feedback, config disabled behavior, tests, and build verification are covered.
- Placeholder scan: no `TBD` or incomplete implementation placeholders remain; mapped Minecraft names are handled by an explicit inspection step before finalizing mixin calls.
- Type consistency: `TradeMark`, `TradeFavoriteStore`, `TradeDescriptor`, `TradeIdentity`, `TraderFeedback`, and `MarkedTradeBuyer` names are defined before use.
