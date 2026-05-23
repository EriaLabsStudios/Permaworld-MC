package net.serex.permaworld.client.debug;

import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;

/**
 * Helper centralizado para los logs del modo debug.
 * <p>
 * Cuando {@code config.debug} está activo, cada llamada emite una línea a
 * nivel INFO con el prefijo {@code [Permaworld][debug][feature]} para que sea
 * fácil grepear el log del cliente.
 * <p>
 * Cuando está desactivado, las llamadas son prácticamente gratis: solo una
 * lectura de un boolean. Por eso es seguro instrumentar puntos calientes
 * (por ejemplo el render extractor).
 */
public final class DebugLog {

    private DebugLog() {
    }

    /** ¿Está activo el modo debug? */
    public static boolean enabled() {
        return ConfigManager.get().config().debug;
    }

    /** Loguea un mensaje formateado con el prefijo de la feature dada. */
    public static void log(String feature, String fmt, Object... args) {
        if (!enabled()) return;
        Permaworld.LOGGER.info("[Permaworld][debug][{}] " + fmt, prepend(feature, args));
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] out = new Object[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }
}
