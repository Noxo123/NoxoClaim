package fr.noxodev.noxoclaim.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import fr.noxodev.noxoclaim.models.ClaimRole;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Authenticated HTTP API and activity/webhook bridge. */
public final class NoxoClaimApi {
    private final NoxoClaim plugin;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final List<Activity> activities = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private HttpServer server;
    private ScheduledExecutorService scheduler;
    private volatile long lastRevision = -1L;

    public NoxoClaimApi(NoxoClaim plugin) { this.plugin = plugin; }

    public synchronized void start() {
        if (server != null || !plugin.getConfig().getBoolean("api.enabled", false)) return;
        try {
            String host = plugin.getConfig().getString("api.host", "127.0.0.1");
            int port = Math.max(1, Math.min(65535, plugin.getConfig().getInt("api.port", 8765)));
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/v1/status", this::status);
            server.createContext("/api/v1/version", this::version);
            server.createContext("/api/v1/health", this::health);
            server.createContext("/api/v1/server", this::serverInfo);
            server.createContext("/api/v1/online", this::online);
            server.createContext("/api/v1/players", this::players);
            server.createContext("/api/v1/claims", this::claims);
            server.createContext("/api/v1/stats", this::stats);
            server.createContext("/api/v1/activity", this::activity);
            server.setExecutor(Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "NoxoClaim-API"); t.setDaemon(true); return t; }));
            server.start();
            lastRevision = plugin.claims().revision();
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "NoxoClaim-API-Monitor"); t.setDaemon(true); return t; });
            scheduler.scheduleAtFixedRate(this::monitorRevision, 1, 1, TimeUnit.SECONDS);
            plugin.getLogger().info("API NoxoClaim active sur http://" + host + ":" + port);
        } catch (IOException e) {
            server = null;
            plugin.getLogger().severe("Impossible de démarrer l'API NoxoClaim: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        if (server != null) { server.stop(0); server = null; }
    }

    public boolean isRunning() { return server != null; }

    /** Records a player-caused action with actor, claim and action context. */
    public void recordActivity(Player actor, String action, Claim claim, String details) {
        if (actor == null || action == null || action.isBlank()) return;
        Activity event = new Activity(action, claim == null ? null : claim.getId(), actor.getUniqueId(), actor.getName(), details, plugin.claims().revision(), System.currentTimeMillis());
        activities.add(event);
        while (activities.size() > 500) activities.remove(0);
        webhook(event);
    }

    private void status(HttpExchange e) throws IOException { if (!authorized(e)) return; json(e, 200, sync(() -> "{\"ok\":true,\"plugin\":\"NoxoClaim\",\"version\":\"" + esc(plugin.getDescription().getVersion()) + "\",\"claims\":" + plugin.claims().all().size() + ",\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size() + "}")); }
    private void version(HttpExchange e) throws IOException { if (!authorized(e)) return; json(e, 200, "{\"plugin\":\"NoxoClaim\",\"version\":\"" + esc(plugin.getDescription().getVersion()) + "\",\"apiVersion\":\"v1\"}"); }
    private void health(HttpExchange e) throws IOException { if (!authorized(e)) return; boolean dirty = sync(() -> plugin.claims().isDirty()); json(e, dirty ? 503 : 200, "{\"ok\":" + !dirty + ",\"api\":true,\"storageDirty\":" + dirty + "}"); }
    private void serverInfo(HttpExchange e) throws IOException { if (!authorized(e)) return; json(e, 200, sync(() -> "{\"name\":\"" + esc(Bukkit.getServer().getName()) + "\",\"version\":\"" + esc(Bukkit.getVersion()) + "\",\"bukkitVersion\":\"" + esc(Bukkit.getBukkitVersion()) + "\",\"motd\":\"" + esc(Bukkit.getMotd()) + "\",\"online\":" + Bukkit.getOnlinePlayers().size() + ",\"maxPlayers\":" + Bukkit.getMaxPlayers() + ",\"tps\":" + number(Bukkit.getTPS()[0]) + "}")); }

    private void online(HttpExchange e) throws IOException {
        if (!authorized(e)) return;
        json(e, 200, sync(() -> { StringBuilder out = new StringBuilder("{\"count\":").append(Bukkit.getOnlinePlayers().size()).append(",\"players\":["); boolean first = true; for (Player p : Bukkit.getOnlinePlayers()) { if (!first) out.append(','); first = false; out.append(playerJson(p)); } return out.append("]}").toString(); }));
    }

    private void players(HttpExchange e) throws IOException {
        if (!authorized(e)) return;
        String base = "/api/v1/players", path = e.getRequestURI().getPath(), id = path.length() > base.length() + 1 ? path.substring(base.length() + 1) : "";
        if (id.isBlank()) { json(e, 400, "{\"error\":\"player_uuid_required\"}"); return; }
        try {
            UUID uuid = UUID.fromString(id);
            json(e, 200, sync(() -> { Player p = Bukkit.getPlayer(uuid); StringBuilder out = new StringBuilder("{\"uuid\":\"").append(uuid).append("\",\"name\":").append(p == null ? "null" : "\"" + esc(p.getName()) + "\""); Collection<Claim> owned = plugin.claims().owned(uuid); out.append(",\"online\":").append(p != null && p.isOnline()).append(",\"ownedClaims\":").append(owned.size()).append(",\"claims\":["); boolean first = true; for (Claim c : owned) { if (!first) out.append(','); first = false; out.append(claimJson(c)); } return out.append("]}").toString(); }));
        } catch (IllegalArgumentException ex) { json(e, 400, "{\"error\":\"invalid_player_uuid\"}"); }
    }

    private void claims(HttpExchange e) throws IOException {
        if (!authorized(e)) return;
        String base = "/api/v1/claims", path = e.getRequestURI().getPath();
        if (!path.equals(base) && !path.equals(base + "/")) {
            try { UUID id = UUID.fromString(path.substring((base + "/").length())); Claim c = sync(() -> plugin.claims().get(id)); if (c == null) { json(e, 404, "{\"error\":\"claim_not_found\"}"); return; } json(e, 200, claimJson(c)); }
            catch (IllegalArgumentException ex) { json(e, 400, "{\"error\":\"invalid_claim_id\"}"); }
            return;
        }
        json(e, 200, sync(() -> { Collection<Claim> cs = plugin.claims().all(); StringBuilder out = new StringBuilder("{\"count\":").append(cs.size()).append(",\"claims\":["); boolean first = true; for (Claim c : cs) { if (!first) out.append(','); first = false; out.append(claimJson(c)); } return out.append("]}").toString(); }));
    }

    private void stats(HttpExchange e) throws IOException { if (!authorized(e)) return; json(e, 200, sync(() -> { int claims = plugin.claims().all().size(); long chunks = 0, blocks = 0, members = 0; for (Claim c : plugin.claims().all()) { chunks += c.chunkCount(); blocks += c.size(); members += c.getMembers().size(); } return "{\"claims\":" + claims + ",\"chunks\":" + chunks + ",\"blocks\":" + blocks + ",\"members\":" + members + ",\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size() + "}"; })); }

    private void activity(HttpExchange e) throws IOException {
        if (!authorized(e)) return;
        int limit = 50; String q = e.getRequestURI().getQuery(); if (q != null && q.startsWith("limit=")) try { limit = Math.max(1, Math.min(200, Integer.parseInt(q.substring(6)))); } catch (NumberFormatException ignored) {}
        int from = Math.max(0, activities.size() - limit); StringBuilder out = new StringBuilder("{\"count\":").append(Math.min(limit, activities.size())).append(",\"events\":["); boolean first = true;
        for (int i = activities.size() - 1; i >= from; i--) { if (!first) out.append(','); first = false; out.append(activities.get(i).json()); }
        json(e, 200, out.append("]}").toString());
    }

    private String claimJson(Claim c) {
        StringBuilder members = new StringBuilder("["); boolean first = true; for (Map.Entry<UUID, ClaimRole> entry : c.getMemberRoles().entrySet()) { if (!first) members.append(','); first = false; members.append("{\"uuid\":\"").append(entry.getKey()).append("\",\"role\":\"").append(entry.getValue().name()).append("\"}"); }
        StringBuilder flags = new StringBuilder("{"); first = true; for (Map.Entry<ClaimFlag, Boolean> entry : c.getFlags().entrySet()) { if (!first) flags.append(','); first = false; flags.append("\"").append(entry.getKey().name().toLowerCase()).append("\":").append(entry.getValue()); }
        return "{\"id\":\"" + c.getId() + "\",\"name\":\"" + esc(c.getName()) + "\",\"owner\":\"" + c.getOwner() + "\",\"world\":\"" + esc(c.getWorld()) + "\",\"minX\":" + c.getMinX() + ",\"minZ\":" + c.getMinZ() + ",\"maxX\":" + c.getMaxX() + ",\"maxZ\":" + c.getMaxZ() + ",\"chunks\":" + c.chunkCount() + ",\"blocks\":" + c.size() + ",\"members\":" + members.append(']') + ",\"roles\":{\"OWNER\":true,\"ADMIN\":true,\"MEMBER\":true,\"BUILDER\":true,\"VISITOR\":false},\"flags\":" + flags.append('}') + "}";
    }

    private static String playerJson(Player p) { return "{\"uuid\":\"" + p.getUniqueId() + "\",\"name\":\"" + esc(p.getName()) + "\",\"world\":\"" + esc(p.getWorld().getName()) + "\",\"x\":" + number(p.getLocation().getX()) + ",\"y\":" + number(p.getLocation().getY()) + ",\"z\":" + number(p.getLocation().getZ()) + "}"; }

    private void monitorRevision() {
        try {
            long revision = sync(() -> plugin.claims().revision());
            if (lastRevision >= 0 && revision != lastRevision) {
                Activity event = new Activity("claims.changed", null, null, null, "Modification détectée hors d'une action instrumentée.", revision, System.currentTimeMillis());
                activities.add(event); while (activities.size() > 500) activities.remove(0); webhook(event);
            }
            lastRevision = revision;
        } catch (RuntimeException ignored) { }
    }

    private void webhook(Activity event) {
        if (!plugin.getConfig().getBoolean("api.webhooks.enabled", false)) return;
        boolean discord = plugin.getConfig().getBoolean("api.webhooks.discord.enabled", false);
        String url = discord ? plugin.getConfig().getString("api.webhooks.discord.url", "") : plugin.getConfig().getString("api.webhooks.url", "");
        if (url == null || url.isBlank() || !(url.startsWith("https://") || (url.startsWith("http://") && plugin.getConfig().getBoolean("api.webhooks.allow-http", false)))) return;
        if (discord) sendDiscordWebhook(event, url); else sendHttpWebhook(event, url, plugin.getConfig().getString("api.webhooks.secret", ""));
    }

    private void sendHttpWebhook(Activity event, String url, String secret) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(event.json()));
        if (!secret.isBlank()) builder.header("X-NoxoClaim-Webhook", secret);
        httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding()).exceptionally(ex -> null);
    }

    private void sendDiscordWebhook(Activity event, String url) {
        String username = plugin.getConfig().getString("api.webhooks.discord.username", "NoxoClaim");
        String avatar = plugin.getConfig().getString("api.webhooks.discord.avatar", "");
        String color = plugin.getConfig().getString("api.webhooks.discord.color", "5865F2");
        if (color == null || !color.matches("[0-9A-Fa-f]{6}")) color = "5865F2";
        String actor = event.actorName() == null ? "Système / API" : event.actorName();
        String actorUuid = event.actorUuid() == null ? "" : event.actorUuid().toString();
        String head = actorUuid.isBlank() ? avatar : "https://mc-heads.net/avatar/" + actorUuid + "/128";
        String title = "NoxoClaim • " + humanAction(event.event());
        String description = event.details() == null || event.details().isBlank() ? "Une action a modifié les claims." : event.details();
        String payload = "{\"username\":\"" + esc(username) + "\"" + (avatar == null || avatar.isBlank() ? "" : ",\"avatar_url\":\"" + esc(avatar) + "\"") + ",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{\"title\":\"" + esc(title) + "\",\"description\":\"" + esc(description) + "\",\"color\":" + Integer.parseInt(color, 16) + ",\"author\":{\"name\":\"" + esc(actor) + "\",\"icon_url\":\"" + esc(head) + "\"},\"fields\":[{\"name\":\"Joueur\",\"value\":\"**" + esc(actor) + "**\n`" + esc(actorUuid) + "`\",\"inline\":true},{\"name\":\"Action\",\"value\":\"`" + esc(event.event()) + "`\",\"inline\":true},{\"name\":\"Claim\",\"value\":\"`" + (event.claimId() == null ? "Aucun" : event.claimId().toString()) + "`\",\"inline\":false}],\"thumbnail\":{\"url\":\"" + esc(head) + "\"},\"timestamp\":\"" + java.time.Instant.ofEpochMilli(event.timestamp()) + "\",\"footer\":{\"text\":\"NoxoClaim • Journal d'activité\"}}]}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenAccept(response -> { if (response.statusCode() == 429) plugin.getLogger().warning("Discord webhook NoxoClaim limité par Discord (429)."); else if (response.statusCode() < 200 || response.statusCode() >= 300) plugin.getLogger().warning("Discord webhook NoxoClaim refusé (HTTP " + response.statusCode() + ")."); }).exceptionally(ex -> { plugin.getLogger().warning("Échec du webhook Discord NoxoClaim: " + ex.getClass().getSimpleName()); return null; });
    }

    private static String humanAction(String action) { return switch (action) { case "claim.created" -> "Claim créé"; case "claim.removed" -> "Claim supprimé"; case "member.added" -> "Membre ajouté"; case "member.removed" -> "Membre retiré"; case "role.changed" -> "Rôle modifié"; case "flag.changed" -> "Protection modifiée"; case "home.changed" -> "Point de téléportation modifié"; default -> "Activité des claims"; }; }

    private boolean authorized(HttpExchange e) throws IOException { if (!rateAllowed(e)) return false; String configured = plugin.getConfig().getString("api.token", ""); if (configured == null || configured.isBlank()) { json(e, 503, "{\"error\":\"api_token_not_configured\"}"); return false; } String provided = e.getRequestHeaders().getFirst("Authorization"); if (provided == null || !provided.startsWith("Bearer ") || !configured.equals(provided.substring(7))) { json(e, 401, "{\"error\":\"unauthorized\"}"); return false; } return true; }
    private boolean rateAllowed(HttpExchange e) throws IOException { int limit = Math.max(1, plugin.getConfig().getInt("api.rate-limit-per-minute", 120)); String ip = e.getRemoteAddress().getAddress().getHostAddress(); long minute = System.currentTimeMillis() / 60000L; RateWindow w = rateWindows.compute(ip, (k, old) -> old == null || old.minute != minute ? new RateWindow(minute, 1) : new RateWindow(minute, old.count + 1)); if (w.count <= limit) return true; json(e, 429, "{\"error\":\"rate_limit_exceeded\"}"); return false; }
    private <T> T sync(Supplier<T> supplier) { try { return Bukkit.getScheduler().callSyncMethod(plugin, supplier::get).get(2, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException("Bukkit operation timeout", e); } }
    private static String number(double value) { return Double.isFinite(value) ? Double.toString(value) : "0"; }
    private static String esc(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static void json(HttpExchange e, int status, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); e.getResponseHeaders().set("Cache-Control", "no-store"); e.sendResponseHeaders(status, bytes.length); try (var out = e.getResponseBody()) { out.write(bytes); } }
    private record RateWindow(long minute, int count) {}
    private record Activity(String event, UUID claimId, UUID actorUuid, String actorName, String details, long revision, long timestamp) { String json() { return "{\"event\":\"" + esc(event) + "\",\"claimId\":" + (claimId == null ? "null" : "\"" + claimId + "\"") + ",\"actor\":{" + "\"uuid\":\"" + (actorUuid == null ? "" : actorUuid) + "\",\"name\":\"" + esc(actorName) + "\"},\"details\":\"" + esc(details) + "\",\"revision\":" + revision + ",\"timestamp\":" + timestamp + "}"; } }
}
