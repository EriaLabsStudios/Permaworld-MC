package net.serex.permaworld.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.IntPredicate;

/**
 * Utilidades puras para evaluar teclas guardadas por {@link net.minecraft.client.KeyMapping}.
 */
public final class KeyInput {

    private KeyInput() {
    }

    public static boolean isKeyboardKeyDown(String keyName, IntPredicate keyDown) {
        if (keyName == null || keyName.isEmpty()) return false;
        try {
            return isKeyboardKeyDown(InputConstants.getKey(keyName), keyDown);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isKeyboardKeyDown(InputConstants.Key key, IntPredicate keyDown) {
        if (key == null) return false;
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        int code = key.getValue();
        if (code <= 0) return false;
        return keyDown.test(code);
    }
}
