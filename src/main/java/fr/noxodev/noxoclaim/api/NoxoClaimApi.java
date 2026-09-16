package fr.noxodev.noxoclaim.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/** Lightweight read-only HTTP API for external dashboards. Disabled by default. */
public final class NoxoClaimApi {
    private final NoxoClaim plugin;
    private HttpServer server;

    public NoxoClaimApi(NoxoClaim plugin) { this.plugin = plugin; }

    public synchronized void start() {
        if (server != null) return;
        if (!plugin.getConfig().getBoolean("api.enabled", false)) return;
        try {
            String host = plugin.getConfig().getString("api.host", "127.0.0.1");
            int port = Math.max(1, Math.min(65535, plugin.getConfig().getInt("api.port", 8765)));
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/v1/status", this::status);
            server.createContext("/api/v1/claims", this::claims);
            server.createContext("/api/v1/stats", this::stats);
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "NoxoClaim-API");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            plugin.getLogger().info("API NoxoClaim active sur http://" + host + ":" + port);
        } catch (IOException e) {
            server = null;
            plugin.getLogger().severe("Impossible de démarrer l'API NoxoClaim: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() { return server != null; }

    private void status(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        json(exchange, 200, "{\"ok\":true,\"plugin\":\"NoxoClaim\",\"version\":\"" + esc(plugin.getDescription().getVersion()) + "\",\"claims\":" + plugin.claims().all().size() + "}");
    }

    private void claims(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        String path = exchange.getRequestURI().getPath();
        String base = "/api/v1/claims";
        if (!path.equals(base) && !path.equals(base + "/")) {
            String id = path.substring((base + "/").length());
            try {
                Claim claim = plugin.claims().get(UUID.fromString(id));
                if (claim == null) { json(exchange, 404, "{\"error\":\"claim_not_found\"}"); return; }
                json(exchange, 200, claimJson(claim));
            } catch (IllegalArgumentException e) {
                json(exchange, 400, "{\"error\":\"invalid_claim_id\"}");
            }
            return;
        }
        Collection<Claim> claims = plugin.claims().all();
        StringBuilder out = new StringBuilder("{\"count\":").append(claims.size()).append(",\"claims\":[");
        boolean first = true;
        for (Claim claim : claims) {
            if (!first) out.append(',');
            first = false;
            out.append(claimJson(claim));
        }
        out.append("]}");
        json(exchange, 200, out.toString());
    }

    private void stats(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        int claims = plugin.claims().all().size();
        long chunks = 0;
        long blocks = 0;
        for (Claim claim : plugin.claims().all()) { chunks += claim.chunkCount(); blocks += claim.size(); }
        json(exchange, 200, "{\"claims\":" + claims + ",\"chunks\":" + chunks + ",\"blocks\":" + blocks + ",\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size() + "}");
    }

    private boolean authorized(HttpExchange exchange) throws IOException {
        String configured = plugin.getConfig().getString("api.token", "");
        if (configured.isBlank()) { json(exchange, 503, "{\"error\":\"api_token_not_configured\"}"); return false; }
        String provided = exchange.getRequestHeaders().getFirst("Authorization");
        if (provided == null || !provided.startsWith("Bearer ") || !configured.equals(provided.substring(7))) {
            json(exchange, 401, "{\"error\":\"unauthorized\"}");
            return false;
        }
        return true;
    }

    private String claimJson(Claim c) {
        return "{\"id\":\"" + c.getId() + "\",\"name\":\"" + esc(c.getName()) + "\",\"owner\":\"" + c.getOwner() + "\",\"world\":\"" + esc(c.getWorld()) + "\",\"minX\":" + c.getMinX() + ",\"minZ\":" + c.getMinZ() + ",\"maxX\":" + c.getMaxX() + ",\"maxZ\":" + c.getMaxZ() + ",\"chunks\":" + c.chunkCount() + ",\"blocks\":" + c.size() + ",\"members\":" + c.getMembers().size() + "}";
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
