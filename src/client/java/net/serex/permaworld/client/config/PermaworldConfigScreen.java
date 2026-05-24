package net.serex.permaworld.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PermaworldConfigScreen extends Screen {

    private final Screen parent;

    public PermaworldConfigScreen(Screen parent) {
        super(Component.translatable("permaworld.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int row = this.height / 2 - 92;
        int x = this.width / 2 - 120;

        addControl(x, row, "permaworld.config.sort.enabled",
                () -> ConfigManager.get().config().sort.enabled,
                value -> ConfigManager.get().config().sort.enabled = value);
        row += 24;

        addControl(x, row, "permaworld.config.slot_lock.protect_pickup",
                () -> ConfigManager.get().config().slotLock.protectPickup,
                value -> ConfigManager.get().config().slotLock.protectPickup = value);
        row += 24;

        addControl(x, row, "permaworld.config.slot_lock.drag_brush",
                () -> ConfigManager.get().config().slotLock.dragBrush,
                value -> ConfigManager.get().config().slotLock.dragBrush = value);
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
        }).bounds(x, row, 116, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x + 124, row, 116, 20)
                .build());
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
        }).bounds(x, y, 240, 20).build();
        addRenderableWidget(button);
    }

    private void addNumberControl(int x, int y, String key, IntGetter getter, IntSetter setter, int step) {
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            setter.set(getter.get() - step);
            ConfigManager.get().save();
            rebuildWidgets();
        }).bounds(x, y, 20, 20).build());

        addRenderableWidget(Button.builder(numberLabel(key, getter.get()), ignored -> { })
                .bounds(x + 24, y, 192, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            setter.set(getter.get() + step);
            ConfigManager.get().save();
            rebuildWidgets();
        }).bounds(x + 220, y, 20, 20).build());
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
}
