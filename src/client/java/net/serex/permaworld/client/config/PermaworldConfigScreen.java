package net.serex.permaworld.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
        addControl(x, row, "permaworld.config.debug",
                () -> ConfigManager.get().config().debug,
                value -> ConfigManager.get().config().debug = value);
        row += 24;

        addNumberControl(x, row, "permaworld.config.packet_delay",
                () -> ConfigManager.get().config().packetDelayMs,
                value -> ConfigManager.get().config().packetDelayMs = clamp(value, 0, 250),
                5);
    }

    private void addSortControls(int x, int row) {

        addControl(x, row, "permaworld.config.sort.enabled",
                () -> ConfigManager.get().config().sort.enabled,
                value -> ConfigManager.get().config().sort.enabled = value);
        row += 24;

        addNumberControl(x, row, "permaworld.config.sort.button_size",
                () -> ConfigManager.get().config().sort.buttonSize,
                value -> ConfigManager.get().config().sort.buttonSize = clamp(value, 8, 24),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.sort.button_offset_x",
                () -> ConfigManager.get().config().sort.buttonOffsetX,
                value -> ConfigManager.get().config().sort.buttonOffsetX = clamp(value, -80, 80),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.sort.inventory_offset_y",
                () -> ConfigManager.get().config().sort.inventoryButtonOffsetY,
                value -> ConfigManager.get().config().sort.inventoryButtonOffsetY = clamp(value, 0, 140),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.sort.container_offset_y",
                () -> ConfigManager.get().config().sort.containerButtonOffsetY,
                value -> ConfigManager.get().config().sort.containerButtonOffsetY = clamp(value, 0, 80),
                1);
        row += 32;

        addRenderableWidget(Button.builder(Component.translatable("permaworld.config.reset_layout"), button -> {
            ConfigManager.get().config().sort.resetButtonLayout();
            ConfigManager.get().save();
            rebuildWidgets();
        }).bounds(x, row, 300, 20).build());
    }

    private void addQuickDropControls(int x, int row) {
        addControl(x, row, "permaworld.config.quick_drop.enabled",
                () -> ConfigManager.get().config().quickDrop.enabled,
                value -> ConfigManager.get().config().quickDrop.enabled = value);
        row += 24;

        addNumberControl(x, row, "permaworld.config.quick_drop.radius",
                () -> ConfigManager.get().config().quickDrop.radius,
                value -> ConfigManager.get().config().quickDrop.radius = clamp(value, 1, 32),
                1);
    }

    private void addSlotLockControls(int x, int row) {
        addControl(x, row, "permaworld.config.slot_lock.enabled",
                () -> ConfigManager.get().config().slotLock.enabled,
                value -> ConfigManager.get().config().slotLock.enabled = value);
        row += 24;

        addControl(x, row, "permaworld.config.slot_lock.protect_pickup",
                () -> ConfigManager.get().config().slotLock.protectPickup,
                value -> ConfigManager.get().config().slotLock.protectPickup = value);
        row += 24;

        addControl(x, row, "permaworld.config.slot_lock.drag_brush",
                () -> ConfigManager.get().config().slotLock.dragBrush,
                value -> ConfigManager.get().config().slotLock.dragBrush = value);
    }

    private void addTraderControls(int x, int row) {
        addControl(x, row, "permaworld.config.trader.enabled",
                () -> ConfigManager.get().config().trader.enabled,
                value -> ConfigManager.get().config().trader.enabled = value);
        row += 24;

        addControl(x, row, "permaworld.config.trader.marked_buy_buttons",
                () -> ConfigManager.get().config().trader.markedBuyButtons,
                value -> ConfigManager.get().config().trader.markedBuyButtons = value);
        row += 24;

        addNumberControl(x, row, "permaworld.config.trader.buy_button_size",
                () -> ConfigManager.get().config().trader.markedBuyButtonSize,
                value -> ConfigManager.get().config().trader.markedBuyButtonSize = clamp(value, 8, 24),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.trader.buy_button_gap",
                () -> ConfigManager.get().config().trader.markedBuyButtonGap,
                value -> ConfigManager.get().config().trader.markedBuyButtonGap = clamp(value, 0, 24),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.trader.buy_button_offset_x",
                () -> ConfigManager.get().config().trader.markedBuyButtonOffsetX,
                value -> ConfigManager.get().config().trader.markedBuyButtonOffsetX = clamp(value, -220, 220),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.trader.buy_button_offset_y",
                () -> ConfigManager.get().config().trader.markedBuyButtonOffsetY,
                value -> ConfigManager.get().config().trader.markedBuyButtonOffsetY = clamp(value, -80, 160),
                1);
        row += 32;

        addRenderableWidget(Button.builder(Component.translatable("permaworld.config.trader.reset_buy_buttons"), button -> {
            ConfigManager.get().config().trader.resetButtonLayout();
            ConfigManager.get().save();
            rebuildWidgets();
        }).bounds(x, row, 300, 20).build());
    }

    private void addHarvestControls(int x, int row) {
        addControl(x, row, "permaworld.config.harvest.enabled",
                () -> ConfigManager.get().config().harvest.enabled,
                value -> ConfigManager.get().config().harvest.enabled = value);
        row += 24;

        addNumberControl(x, row, "permaworld.config.harvest.stone_area",
                () -> ConfigManager.get().config().harvest.stoneHoeArea,
                value -> ConfigManager.get().config().harvest.stoneHoeArea = clamp(value, 1, 8),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.harvest.iron_area",
                () -> ConfigManager.get().config().harvest.ironHoeArea,
                value -> ConfigManager.get().config().harvest.ironHoeArea = clamp(value, 1, 8),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.harvest.diamond_area",
                () -> ConfigManager.get().config().harvest.diamondHoeArea,
                value -> ConfigManager.get().config().harvest.diamondHoeArea = clamp(value, 1, 8),
                1);
        row += 24;

        addNumberControl(x, row, "permaworld.config.harvest.netherite_area",
                () -> ConfigManager.get().config().harvest.netheriteHoeArea,
                value -> ConfigManager.get().config().harvest.netheriteHoeArea = clamp(value, 1, 8),
                1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        extractMenuBackground(extractor);
        extractor.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
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
        }).bounds(x, y, 300, 20).build();
        addRenderableWidget(button);
    }

    private void addNumberControl(int x, int y, String key, IntGetter getter, IntSetter setter, int step) {
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            setter.set(getter.get() - step);
            ConfigManager.get().save();
            rebuildWidgets();
        }).bounds(x, y, 20, 20).build());

        addRenderableWidget(Button.builder(numberLabel(key, getter.get()), ignored -> { })
                .bounds(x + 24, y, 252, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            setter.set(getter.get() + step);
            ConfigManager.get().save();
            rebuildWidgets();
        }).bounds(x + 280, y, 20, 20).build());
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
        HARVEST("permaworld.config.tab.harvest");

        private final String translationKey;

        ConfigTab(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
