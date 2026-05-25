package net.serex.permaworld.mixin.client;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PackSelectionModel.class)
public interface PackSelectionModelAccessor {

    @Accessor("repository")
    PackRepository permaworld$getRepository();

    @Accessor("selected")
    List<Pack> permaworld$getSelectedPacks();

    @Accessor("unselected")
    List<Pack> permaworld$getUnselectedPacks();
}
