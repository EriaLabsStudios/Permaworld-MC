package net.serex.permaworld.server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.serex.permaworld.Permaworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class PermaworldHttpServer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;
    private final PermaworldWebConfig config;
    private final WebRecordQueryService queryService;
    private final WebRestoreService restoreService;
    private HttpServer httpServer;

    public PermaworldHttpServer(MinecraftServer server, PermaworldWebConfig config) {
        this.server = server;
        this.config = config;
        this.queryService = new WebRecordQueryService(server);
        this.restoreService = new WebRestoreService(server);
    }

    public void start() {
        if (httpServer != null) {
            Permaworld.LOGGER.info("Permaworld web ya estaba arrancada en http://{}:{}/", config.host(), config.port());
            return;
        }
        try {
            Permaworld.LOGGER.info("Montando servidor HTTP de Permaworld web en {}:{}...", config.host(), config.port());
            httpServer = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            httpServer.createContext("/", this::handleRequest);
            httpServer.start();
            InetSocketAddress address = httpServer.getAddress();
            Permaworld.LOGGER.info("Permaworld web montada correctamente.");
            Permaworld.LOGGER.info("Permaworld web escuchando en http://{}:{}/", address.getHostString(), address.getPort());
            Permaworld.LOGGER.info("Abre esa URL en el navegador de esta misma maquina para acceder a la consola web.");
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudo arrancar Permaworld web", e);
            stop();
        }
    }

    public void stop() {
        if (httpServer == null) {
            return;
        }
        InetSocketAddress address = httpServer.getAddress();
        httpServer.stop(0);
        Permaworld.LOGGER.info("Permaworld web detenida en http://{}:{}/", address.getHostString(), address.getPort());
        httpServer = null;
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                serveAsset(exchange, "assets/permaworld/web/index.html", "text/html; charset=utf-8");
                return;
            }
            if ("/app.css".equals(path)) {
                serveAsset(exchange, "assets/permaworld/web/app.css", "text/css; charset=utf-8");
                return;
            }
            if ("/app.js".equals(path)) {
                serveAsset(exchange, "assets/permaworld/web/app.js", "application/javascript; charset=utf-8");
                return;
            }
            if ("/api/session".equals(path)) {
                writeJson(exchange, queryService.sessionData());
                return;
            }
            if ("/api/players".equals(path)) {
                var payload = new com.google.gson.JsonObject();
                payload.add("players", queryService.playerSummaries());
                writeJson(exchange, payload);
                return;
            }
            if ("/api/item-texture".equals(path)) {
                String itemId = queryValue(exchange.getRequestURI().getQuery(), "itemId").orElse("");
                if (serveVanillaItemTexture(exchange, itemId)) {
                    return;
                }
                writeStatus(exchange, 404, "Missing item texture");
                return;
            }
            if (path.startsWith("/api/players/")) {
                handlePlayerApi(exchange, path);
                return;
            }
            writeStatus(exchange, 404, "Not found");
        } catch (RuntimeException e) {
            Permaworld.LOGGER.error("Error en request web {}", exchange.getRequestURI(), e);
            writeStatus(exchange, 500, "Internal error");
        }
    }

    private void handlePlayerApi(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        if (parts.length < 4) {
            writeStatus(exchange, 404, "Not found");
            return;
        }
        UUID playerId = UUID.fromString(parts[3]);
        if (parts.length == 5 && "records".equals(parts[4])) {
            String filter = queryValue(exchange.getRequestURI().getQuery(), "filter").orElse("DEATH");
            var payload = new com.google.gson.JsonObject();
            payload.add("records", queryService.playerRecords(playerId, filter));
            writeJson(exchange, payload);
            return;
        }
        if (parts.length == 5 && "stats".equals(parts[4]) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, queryService.playerStats(playerId));
            return;
        }
        if (parts.length >= 6 && "records".equals(parts[4])) {
            String recordId = parts[5];
            if (parts.length == 6 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Optional<com.google.gson.JsonObject> record = queryService.playerRecord(playerId, recordId);
                if (record.isEmpty()) {
                    writeStatus(exchange, 404, "Record not found");
                    return;
                }
                writeJson(exchange, record.get());
                return;
            }
            if (parts.length == 7 && "restore".equals(parts[6]) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String admin = queryValue(exchange.getRequestURI().getQuery(), "admin").orElse("");
                writeJson(exchange, restoreService.restore(admin, playerId, recordId));
                return;
            }
        }
        writeStatus(exchange, 404, "Not found");
    }

    private void serveAsset(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                writeStatus(exchange, 404, "Missing asset");
                return;
            }
            byte[] body = stream.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }

    private void writeJson(HttpExchange exchange, com.google.gson.JsonElement json) throws IOException {
        byte[] body = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void writeStatus(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private Optional<String> queryValue(String query, String key) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        for (String chunk : query.split("&")) {
            String[] parts = chunk.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return Optional.of(parts[1]);
            }
        }
        return Optional.empty();
    }

    private boolean serveVanillaItemTexture(HttpExchange exchange, String itemId) throws IOException {
        if (itemId == null || itemId.isBlank() || !itemId.contains(":")) {
            return false;
        }
        String[] parts = itemId.split(":", 2);
        String namespace = parts[0];
        String path = parts[1];
        return serveClasspathTexture(exchange, "assets/" + namespace + "/textures/item/" + path + ".png")
                || serveClasspathTexture(exchange, "assets/" + namespace + "/textures/block/" + path + ".png");
    }

    private boolean serveClasspathTexture(HttpExchange exchange, String resourcePath) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return false;
            }
            byte[] body = stream.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
            return true;
        }
    }
}
