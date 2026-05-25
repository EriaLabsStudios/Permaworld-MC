# Trader Quick Buy Design

## Context

Issue #7 asks for faster villager trading without accidental purchases. The player wants to mark one or more trades in the villager UI, then press one button to buy all marked trades for the current villager.

The current mod already has:

- `PermaworldConfig.TraderConfig.enabled`.
- A `favoriteTradeHashes` field for old/simple global favorites.
- Base translations for trader quick-buy labels.
- A modular client feature registry in `PermaworldClient`.
- Existing screen mixin patterns for adding small buttons and drawing slot overlays.

## Goals

- Let the player mark trades as local favorites for only the current villager.
- Let the player mark trades as global favorites for every villager with the same trade.
- Show local and global state clearly in the villager trading UI.
- Provide one button that buys all marked trades for the current villager.
- Stop safely when stock, player materials, or inventory space runs out.
- Use only vanilla client interactions and packets; no server-side mod or custom protocol.

## Non-Goals

- Automatically trading without a player pressing the buy button.
- Buying unmarked trades.
- Editing villager offers or bypassing stock/material requirements.
- Supporting non-merchant screens in this milestone.

## UI Behavior

Each visible trade row in `MerchantScreen` gets two small star controls on the left side when the mouse is hovering that row:

- Yellow star: local save for the current villager.
- Blue star: global save for this trade across villagers.

The trade row background is tinted according to the active save state:

- Blue tint when the trade is globally saved.
- Yellow tint when the trade is locally saved for the current villager.
- No tint when the trade is not saved.

Global saves have priority over local saves. If the player marks a yellow local trade as blue global, the local entry for that villager is removed because it is redundant. If the player removes the blue global save, the trade becomes unmarked unless the player explicitly marks it yellow again.

The screen also gets one main button: `Comprar marcados` / `Buy marked`. Pressing it attempts to buy all marked trades visible for the current villager. Marked means either:

- The trade hash exists in the global save set.
- The trade hash exists in the local save set for this villager.

## Data Model

`TraderConfig` should evolve from one flat `favoriteTradeHashes` set into two explicit save scopes:

```java
public static class TraderConfig {
    public boolean enabled = true;
    public Set<Integer> globalFavoriteTradeHashes = new HashSet<>();
    public Map<String, Set<Integer>> localFavoriteTradeHashes = new HashMap<>();

    @Deprecated
    public Set<Integer> favoriteTradeHashes = new HashSet<>();
}
```

`favoriteTradeHashes` stays temporarily for config migration and should be treated as old global favorites when loading existing configs.

The villager key should be stable enough for local favorites. Preferred order:

1. Use a stable merchant or villager UUID if the client-side screen handler exposes it.
2. If no UUID is exposed, derive a local key from the current merchant entity when it can be resolved client-side.
3. If the current merchant cannot be identified, disable local saving for that screen and show clear feedback; global saving remains available.

Trade hashes should be derived from the offer contents, not from list position. Include:

- First cost item id, count, and relevant component/NBT identity if available.
- Second cost item id/count when present.
- Result item id, count, and relevant component/NBT identity if available.

This makes the blue global save apply to equivalent trades from other villagers, while yellow local saves remain attached to one villager key.

## Components

### `TraderQuickBuyFeatureModule`

Registers the feature at client init and wires any screen hooks, callbacks, or helper state needed for merchant screens.

### `TradeFavoriteStore`

Owns favorite lookup and mutation:

- `isGlobalFavorite(tradeHash)`
- `isLocalFavorite(villagerKey, tradeHash)`
- `activeMark(villagerKey, tradeHash)` returning none/local/global
- `toggleLocal(villagerKey, tradeHash)`
- `toggleGlobal(villagerKey, tradeHash)`

It enforces the hierarchy: setting global removes the same local favorite from the active villager.

### `TradeIdentity`

Builds stable trade hashes and villager keys. It is pure enough to unit test for hash stability and hierarchy behavior.

### `MerchantScreen` Hook

Adds two hover-only star buttons per visible trade row and the `Buy marked` button. If Fabric screen events cannot place controls precisely enough, use a focused mixin against `MerchantScreen` following the existing `AbstractContainerScreenMixin` style.

The hook also draws yellow/blue row tinting. The tint should not hide vanilla text, prices, stock indicators, or hover state.

### `MarkedTradeBuyer`

Executes buying for the current merchant screen:

1. Read the current visible offers.
2. Filter offers to active local/global favorites.
3. For each marked offer, select it with vanilla merchant selection behavior.
4. Attempt to take the result through vanilla slot interactions.
5. Repeat while the offer remains buyable and the player can receive the output.
6. Stop that offer when stock, materials, or space runs out.
7. Continue to the next marked offer when the stop reason only applies to the current offer.

The buyer should never click an unmarked trade and should not run unless the user pressed the main button.

## Feedback

Use action bar or chat-system feedback consistent with existing feature feedback:

- No marked trades for this villager.
- Bought N trades / M items.
- Stopped because a trade is out of stock.
- Stopped because materials are missing.
- Stopped because inventory has no space.
- Local save unavailable because the villager could not be identified.

Debug mode should log trade hashes, villager keys, and stop reasons with the existing `DebugLog` style.

## Error Handling

- If trader quick-buy is disabled in config, do not add controls or execute buying.
- If the screen is not a merchant screen, do nothing.
- If local villager identity is unavailable, hide or disable the yellow star and keep blue global available.
- If an offer changes after the screen opens, recalculate its hash from the current offer before buying.
- If a vanilla click fails or the selected trade changes unexpectedly, stop and show feedback instead of continuing blindly.

## Testing

Unit tests:

- Global mark overrides local mark for the same villager/trade.
- Removing global does not automatically recreate a local mark.
- Local marks are isolated by villager key.
- Trade hash is stable for equivalent offers and different for meaningfully different offers.
- Mark filtering returns the expected set of offers for a villager.

Build verification:

- Run `gradlew.bat test`.
- Run `gradlew.bat build`.

Manual in-game verification:

- Open a villager and hover trades: yellow and blue stars appear on the left.
- Mark two local trades: rows tint yellow and `Buy marked` buys both.
- Mark one global trade: row tints blue for other villagers with the same trade.
- Mark a yellow trade as blue: local entry is removed and row becomes blue.
- Try buying with no materials, full inventory, and exhausted stock: buying stops with clear feedback.
- Confirm unmarked trades are never bought.

## Open Implementation Notes

- The exact `MerchantScreen` method names and offer accessors must be confirmed against the mapped Minecraft 26.1.2 sources during implementation.
- If precise row button placement is awkward with normal widgets, implement a small render/click mixin rather than forcing standard buttons into the scrolling trade list.
- The existing `favoriteTradeHashes` config field should be migrated conservatively to `globalFavoriteTradeHashes` so current users keep old favorites.
