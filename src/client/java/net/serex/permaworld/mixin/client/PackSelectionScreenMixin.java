package net.serex.permaworld.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.serex.permaworld.client.feature.resourcepack.ResourcePackManagerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen {

    protected PackSelectionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void permaworld$addManagerButton(CallbackInfo ci) {
        Button button = Button.builder(Component.translatable("permaworld.resourcepack.manager"), ignored ->
                Minecraft.getInstance().setScreen(new ResourcePackManagerScreen((Screen) (Object) this))
        ).bounds(this.width - 112, 8, 104, 20).build();

        ((ScreenAccessor) this).permaworld$addRenderableWidget(button);
    }
}
