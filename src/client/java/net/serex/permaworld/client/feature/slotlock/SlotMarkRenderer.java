package net.serex.permaworld.client.feature.slotlock;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager.SlotMarkMode;

public final class SlotMarkRenderer {

    private SlotMarkRenderer() {
    }

    public static void renderIcon(GuiGraphicsExtractor extractor, int slotX, int slotY, SlotMarkMode mode) {
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        int size = 8;
        int x = slotX + 16 - size + 1;
        int y = slotY - 1;
        if (mode == SlotMarkMode.FAVORITE) {
            extractor.blit(pipeline, SlotLockManager.LOCK_TEXTURE,
                    x, y, 0.0F, 0.0F, size, size, 16, 16, 16, 16);
            return;
        }

        extractor.fill(x + 1, y + 3, x + 8, y + 8, 0xCC202020);
        extractor.fill(x + 2, y + 4, x + 7, y + 7, 0xCCB7B7B7);
        extractor.fill(x + 2, y + 1, x + 7, y + 3, 0xCC202020);
        extractor.fill(x + 3, y + 2, x + 6, y + 3, 0xCCB7B7B7);
    }
}
