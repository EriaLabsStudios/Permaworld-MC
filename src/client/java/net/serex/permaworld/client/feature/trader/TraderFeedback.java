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
