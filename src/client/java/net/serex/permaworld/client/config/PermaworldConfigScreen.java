package net.serex.permaworld.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PermaworldConfigScreen extends Screen {

    private final Screen parent;
    private ConfigTab selectedTab = ConfigTab.GENERAL;

    public PermaworldConfigScreen(Screen parent) {
        super(Component.translatable("permaworld.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 150;
        int row = 48;

        addTabButtons(x, row);
        row += 30;

        switch (selectedTab) {
            case GENERAL -> addGeneralControls(x, row);
            case SORT -> addSortControls(x, row);
            case QUICK_DROP -> addQuickDropControls(x, row);
            case SLOT_LOCK -> addSlotLockControls(x, row);
            case TRADER -> addTraderControls(x, row);
            case HARVEST -> addHarvestControls(x, row);
            case RESOURCE_PACK -> addResourcePackControls(x, row);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20)
                .build());
    }

    private void addTabButtons(int x, int y) {
        int tabWidth = 50;
        for (ConfigTab tab : ConfigTab.values()) {
            Button button = Button.builder(Component.translatable(tab.translationKey), ignored -> {
                selectedTab = tab;
                rebuildWidgets();
            }).bounds(x + tab.ordinal() * tabWidth, y, tabWidth, 20).build();
            button.active = selectedTab != tab;
            addRenderableWidget(button);
        }
    }

    private void addGeneralControls(int x, int row) {
        // Important Settings
        addNumberControl(x, 92, "permaworld.config.packet_delay",
                () -> ConfigManager.get().config().packetDelayMs,
                value -> ConfigManager.get().config().packetDelayMs = clamp(value, 0, 250),
                5);

        // Secondary Settings
        addControl(x, 138, "permaworld.config.debug",
                () -> ConfigManager.get().config().debug,
                value -> ConfigManager.get().config().debug = value);
    }

    private void addSortControls(int x, int row) {
        // Important Settings
        addControl(x, 92, "permaworld.config.sort.enabled",
                () -> ConfigManager.get().config().sort.enabled,
                value -> ConfigManager.get().config().sort.enabled = value);

        addControl(x, 116, "permaworld.config.sort.layout_edit_mode",
                () -> ConfigManager.get().config().sort.layoutEditMode,
                value -> ConfigManager.get().config().sort.layoutEditMode = value);

        addControl(x, 140, "permaworld.config.sort.show_name",
                () -> ConfigManager.get().config().sort.showSortByName,
                value -> ConfigManager.get().config().sort.showSortByName = value);

        addControl(x, 164, "permaworld.config.sort.show_count",
                () -> ConfigManager.get().config().sort.showSortByCount,
                value -> ConfigManager.get().config().sort.showSortByCount = value);

        addControl(x, 188, "permaworld.config.sort.show_category",
                () -> ConfigManager.get().config().sort.showSortByCategory,
                value -> ConfigManager.get().config().sort.showSortByCategory = value);

        // Secondary Settings
        addNumberControl(x, 234, "permaworld.config.sort.button_size",
                () -> ConfigManager.get().config().sort.buttonSize,
                value -> ConfigManager.get().config().sort.buttonSize = clamp(value, 8, 24),
                1);

        addNumberControl(x, 258, "permaworld.config.sort.button_offset_x",
                () -> ConfigManager.get().config().sort.buttonOffsetX,
                value -> ConfigManager.get().config().sort.buttonOffsetX = clamp(value, -80, 80),
                1);

        addNumberControl(x, 282, "permaworld.config.sort.inventory_offset_y",
                () -> ConfigManager.get().config().sort.inventoryButtonOffsetY,
                value -> ConfigManager.get().config().sort.inventoryButtonOffsetY = clamp(value, 0, 140),
                1);

        addNumberControl(x, 306, "permaworld.config.sort.container_offset_y",
                () -> ConfigManager.get().config().sort.containerButtonOffsetY,
                value -> ConfigManager.get().config().sort.containerButtonOffsetY = clamp(value, 0, 80),
                1);

        addRenderableWidget(Button.builder(Component.translatable("permaworld.config.reset_layout"), button -> {
            ConfigManager.get().config().sort.resetButtonLayout();
            ConfigManager.get().save();
            rebuildWidgets();
        })
        .bounds(x, 332, 300, 20)
        .tooltip(Tooltip.create(Component.translatable("permaworld.config.reset_layout.tooltip")))
        .build());
    }

    private void addQuickDropControls(int x, int row) {
        // Important Settings
        addControl(x, 92, "permaworld.config.quick_drop.enabled",
                () -> ConfigManager.get().config().quickDrop.enabled,
                value -> ConfigManager.get().config().quickDrop.enabled = value);

        // Secondary Settings
        addControl(x, 138, "permaworld.config.quick_drop.show_button",
                () -> ConfigManager.get().config().quickDrop.showButton,
                value -> ConfigManager.get().config().quickDrop.showButton = value);

        addNumberControl(x, 162, "permaworld.config.quick_drop.radius",
                () -> ConfigManager.get().config().quickDrop.radius,
                value -> ConfigManager.get().config().quickDrop.radius = clamp(value, 1, 32),
                1);
    }

    private void addSlotLockControls(int x, int row) {
        // Important Settings
        addControl(x, 92, "permaworld.config.slot_lock.enabled",
                () -> ConfigManager.get().config().slotLock.enabled,
                value -> ConfigManager.get().config().slotLock.enabled = value);

        addControl(x, 116, "permaworld.config.slot_lock.protect_pickup",
                () -> ConfigManager.get().config().slotLock.protectPickup,
                value -> ConfigManager.get().config().slotLock.protectPickup = value);

        // Secondary Settings
        addControl(x, 162, "permaworld.config.slot_lock.drag_brush",
                () -> ConfigManager.get().config().slotLock.dragBrush,
                value -> ConfigManager.get().config().slotLock.dragBrush = value);
    }

    private void addTraderControls(int x, int row) {
        // Important Settings
        addControl(x, 92, "permaworld.config.trader.enabled",
                () -> ConfigManager.get().config().trader.enabled,
                value -> ConfigManager.get().config().trader.enabled = value);

        addControl(x, 116, "permaworld.config.trader.marked_buy_buttons",
                () -> ConfigManager.get().config().trader.markedBuyButtons,
                value -> ConfigManager.get().config().trader.markedBuyButtons = value);

        // Secondary Settings
        addNumberControl(x, 162, "permaworld.config.trader.buy_button_size",
                () -> ConfigManager.get().config().trader.markedBuyButtonSize,
                value -> ConfigManager.get().config().trader.markedBuyButtonSize = clamp(value, 8, 24),
                1);

        addNumberControl(x, 186, "permaworld.config.trader.buy_button_gap",
                () -> ConfigManager.get().config().trader.markedBuyButtonGap,
                value -> ConfigManager.get().config().trader.markedBuyButtonGap = clamp(value, 0, 24),
                1);

        addNumberControl(x, 210, "permaworld.config.trader.buy_button_offset_x",
                () -> ConfigManager.get().config().trader.markedBuyButtonOffsetX,
                value -> ConfigManager.get().config().trader.markedBuyButtonOffsetX = clamp(value, -220, 220),
                1);

        addNumberControl(x, 234, "permaworld.config.trader.buy_button_offset_y",
                () -> ConfigManager.get().config().trader.markedBuyButtonOffsetY,
                value -> ConfigManager.get().config().trader.markedBuyButtonOffsetY = clamp(value, -80, 160),
                1);

        addRenderableWidget(Button.builder(Component.translatable("permaworld.config.trader.reset_buy_buttons"), button -> {
            ConfigManager.get().config().trader.resetButtonLayout();
            ConfigManager.get().save();
            rebuildWidgets();
        })
        .bounds(x, 258, 300, 20)
        .tooltip(Tooltip.create(Component.translatable("permaworld.config.trader.reset_buy_buttons.tooltip")))
        .build());
    }

    private void addHarvestControls(int x, int row) {
        // Important Settings
        addControl(x, 92, "permaworld.config.harvest.enabled",
                () -> ConfigManager.get().config().harvest.enabled,
                value -> ConfigManager.get().config().harvest.enabled = value);

        addControl(x, 116, "permaworld.config.harvest.bonemeal_area",
                () -> ConfigManager.get().config().harvest.bonemealArea,
                value -> ConfigManager.get().config().harvest.bonemealArea = value);

        addControl(x, 140, "permaworld.config.harvest.bonemeal_from_hotbar",
                () -> ConfigManager.get().config().harvest.bonemealFromHotbar,
                value -> ConfigManager.get().config().harvest.bonemealFromHotbar = value);

        // Secondary Settings
        addNumberControl(x, 186, "permaworld.config.harvest.stone_area",
                () -> ConfigManager.get().config().harvest.stoneHoeArea,
                value -> ConfigManager.get().config().harvest.stoneHoeArea = clamp(value, 1, 8),
                1);

        addNumberControl(x, 210, "permaworld.config.harvest.iron_area",
                () -> ConfigManager.get().config().harvest.ironHoeArea,
                value -> ConfigManager.get().config().harvest.ironHoeArea = clamp(value, 1, 8),
                1);

        addNumberControl(x, 234, "permaworld.config.harvest.diamond_area",
                () -> ConfigManager.get().config().harvest.diamondHoeArea,
                value -> ConfigManager.get().config().harvest.diamondHoeArea = clamp(value, 1, 8),
                1);

        addNumberControl(x, 258, "permaworld.config.harvest.netherite_area",
                () -> ConfigManager.get().config().harvest.netheriteHoeArea,
                value -> ConfigManager.get().config().harvest.netheriteHoeArea = clamp(value, 1, 8),
                1);
    }

    private void addResourcePackControls(int x, int row) {
        // Important Settings
        addControl(x, 92, "permaworld.config.resource_pack.enabled",
                () -> ConfigManager.get().config().resourcePack.enabled,
                value -> ConfigManager.get().config().resourcePack.enabled = value);

        // Secondary Settings
        addControl(x, 138, "permaworld.config.resource_pack.delete_button",
                () -> ConfigManager.get().config().resourcePack.deleteButton,
                value -> ConfigManager.get().config().resourcePack.deleteButton = value);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        extractMenuBackground(extractor);
        extractor.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFF);

        // Render Important and Secondary subheaders dynamically
        int importantY = 78;
        int secondaryY;
        switch (selectedTab) {
            case SORT -> secondaryY = 220;
            case SLOT_LOCK, TRADER -> secondaryY = 148;
            case HARVEST -> secondaryY = 172;
            default -> secondaryY = 124;
        }

        extractor.centeredText(this.font, Component.translatable("permaworld.config.section.important"), this.width / 2, importantY, 0xFFFFAA00);
        extractor.centeredText(this.font, Component.translatable("permaworld.config.section.secondary"), this.width / 2, secondaryY, 0xFFAAAAAA);

        super.extractRenderState(extractor, mouseX, mouseY, tickDelta);
    }

    @Override
    public void onClose() {
        ConfigManager.get().save();
        this.minecraft.setScreen(parent);
    }

    private void addControl(int x, int y, String key, BoolGetter getter, BoolSetter setter) {
        Button button = Button.builder(enabledLabel(key, getter.get()), ignored -> {
            setter.set(!getter.get());
            ConfigManager.get().save();
            rebuildWidgets();
        })
        .bounds(x, y, 300, 20)
        .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
        .build();
        addRenderableWidget(button);
    }

    private void addNumberControl(int x, int y, String key, IntGetter getter, IntSetter setter, int step) {
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            setter.set(getter.get() - step);
            ConfigManager.get().save();
            rebuildWidgets();
        })
        .bounds(x, y, 20, 20)
        .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
        .build());

        addRenderableWidget(Button.builder(numberLabel(key, getter.get()), ignored -> { })
                .bounds(x + 24, y, 252, 20)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            setter.set(getter.get() + step);
            ConfigManager.get().save();
            rebuildWidgets();
        })
        .bounds(x + 280, y, 20, 20)
        .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
        .build());
    }

    private static Component enabledLabel(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private static Component numberLabel(String key, int value) {
        return Component.translatable(key, value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @FunctionalInterface
    private interface BoolGetter {
        boolean get();
    }

    @FunctionalInterface
    private interface BoolSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    private interface IntGetter {
        int get();
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    private enum ConfigTab {
        GENERAL("permaworld.config.tab.general"),
        SORT("permaworld.config.tab.sort"),
        QUICK_DROP("permaworld.config.tab.quick_drop"),
        SLOT_LOCK("permaworld.config.tab.slot_lock"),
        TRADER("permaworld.config.tab.trader"),
        HARVEST("permaworld.config.tab.harvest"),
        RESOURCE_PACK("permaworld.config.tab.resource_pack");

        private final String translationKey;

        ConfigTab(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
