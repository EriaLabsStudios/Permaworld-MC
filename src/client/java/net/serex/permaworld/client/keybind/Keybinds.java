package net.serex.permaworld.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.serex.permaworld.Permaworld;
import org.lwjgl.glfw.GLFW;

/**
 * Registro central de keybinds del mod.
 * Todas las teclas son rebindeables desde la pantalla de controles de Minecraft.
 */
public final class Keybinds {

    /** Categoría custom registrada en la pantalla de controles. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.withDefaultNamespace(Permaworld.MOD_ID));

    public static KeyMapping sortInventory;
    public static KeyMapping quickDropStack;
    public static KeyMapping slotLockModifier;
    public static KeyMapping openConfig;

    private Keybinds() {
    }

    public static void register() {
        sortInventory = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.permaworld.sort_inventory",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                CATEGORY
        ));

        quickDropStack = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.permaworld.quick_drop_stack",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY
        ));

        slotLockModifier = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.permaworld.slot_lock_modifier",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                CATEGORY
        ));

        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.permaworld.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));
    }
}
