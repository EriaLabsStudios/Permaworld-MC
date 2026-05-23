package net.serex.permaworld.client.keybind;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyInputTest {

    @Test
    void reconoceTeclaConfiguradaPorSaveString() {
        assertTrue(KeyInput.isKeyboardKeyDown(
                "key.keyboard.left.alt",
                code -> code == GLFW.GLFW_KEY_LEFT_ALT
        ));
    }

    @Test
    void permiteRebindAUnaTeclaDistinta() {
        assertTrue(KeyInput.isKeyboardKeyDown(
                "key.keyboard.g",
                code -> code == GLFW.GLFW_KEY_G
        ));
        assertFalse(KeyInput.isKeyboardKeyDown(
                "key.keyboard.g",
                code -> code == GLFW.GLFW_KEY_LEFT_ALT
        ));
    }

    @Test
    void ignoraTeclasSinAsignarOMouseButtons() {
        assertFalse(KeyInput.isKeyboardKeyDown("", code -> true));
        assertFalse(KeyInput.isKeyboardKeyDown("key.keyboard.unknown", code -> true));
        assertFalse(KeyInput.isKeyboardKeyDown("key.mouse.left", code -> true));
    }
}
