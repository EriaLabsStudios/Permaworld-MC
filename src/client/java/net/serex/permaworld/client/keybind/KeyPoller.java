package net.serex.permaworld.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Detector de pulsaciones por polling GLFW.
 * <p>
 * Necesario porque {@link KeyMapping#consumeClick()} no se dispara mientras
 * hay una {@code Screen} abierta (Minecraft enruta input al Screen activo).
 * La mayoría de features de este mod (Sort, QuickDrop, ...) se usan
 * precisamente con el inventario abierto, así que dependemos del polling.
 * <p>
 * Mantiene el estado anterior de la tecla para emitir un evento solo en el
 * flanco de bajada→subida (just-pressed), evitando autorepeat.
 */
public final class KeyPoller {

    private final KeyMapping mapping;
    private boolean wasDown = false;

    public KeyPoller(KeyMapping mapping) {
        this.mapping = mapping;
    }

    /**
     * Devuelve {@code true} exactamente una vez por pulsación.
     * <p>
     * Usa la tecla actualmente vinculada al {@link KeyMapping} (respeta
     * rebindeos desde el menú de controles) consultando GLFW directamente.
     * Si la tecla está sin asignar o es un botón de ratón, no dispara.
     */
    public boolean justPressed() {
        boolean down = isDown();
        boolean fired = down && !wasDown;
        wasDown = down;
        return fired;
    }

    private boolean isDown() {
        InputConstants.Key key = currentKey();
        long handle = Minecraft.getInstance().getWindow().handle();
        return KeyInput.isKeyboardKeyDown(key, code -> GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS);
    }

    /**
     * Obtiene la tecla actual del mapping. En 26.1.2 la API pública de
     * {@link KeyMapping} no expone un getter directo; usamos
     * {@link KeyMapping#saveString()} y reconstruimos la {@code Key}, lo que
     * sí respeta rebindeos guardados.
     */
    private InputConstants.Key currentKey() {
        try {
            String name = mapping.saveString();
            if (name == null || name.isEmpty()) return null;
            return InputConstants.getKey(name);
        } catch (Exception e) {
            return mapping.getDefaultKey();
        }
    }
}
