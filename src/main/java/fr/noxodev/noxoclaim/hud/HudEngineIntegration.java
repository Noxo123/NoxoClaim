package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Optional HUDEngine integration for NoxoClaim. */
public final class HudEngineIntegration {
    public static final String HUD_KEY = "noxoclaim:minimap";
    private static final String HUD_ENGINE_PLUGIN = "HUDEngine";
    private static final String PROVIDER_CLASS = "io.github.nacvark.hudengine.api.HudEngineProvider";
    private static final int MAP_SIZE = 13;
    private static final int MAP_RADIUS = MAP_SIZE / 2;
    private static final int CELL_SIZE = 10;
    private static final int MAP_PIXELS = MAP_SIZE * CELL_SIZE;
    private static final int FRAME_WIDTH = MAP_PIXELS + 20;
    private static final int HEADER_HEIGHT = 25;
    private static final int FOOTER_HEIGHT = 22;
    private static final int FRAME_HEIGHT = HEADER_HEIGHT + MAP_PIXELS + FOOTER_HEIGHT + 12;
    private static final int MAP_X = 10;
    private static final int MAP_Y = HEADER_HEIGHT + 6;
    private static final int MIN_REFRESH_TICKS = 5;
    private static final long DEFAULT_REFRESH_TICKS = 10L;

    private final NoxoClaim plugin;
    private final Map<UUID, HudState> lastStates = new HashMap<>();
    private Object engine;
    private boolean ready;
    private int refreshTask = -1;

    public HudEngineIntegration(NoxoClaim plugin) { this.plugin = plugin; }

    public void start() {
        stopRefreshTask();
        lastStates.clear();
        ready = false;
        engine = null;
        if (!plugin.getConfig().getBoolean("hudengine.enabled", true)) {
            plugin.getLogger().info("HUDEngine : intégration désactivée dans la configuration.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::resolveSafely, 20L);
    }

    public void stop() {
        stopRefreshTask();
        for (Player player : Bukkit.getOnlinePlayers()) hide(player);
        lastStates.clear();
        ready = false;
        engine = null;
    }

    private void resolveSafely() {
        try {
            var hudPlugin = Bukkit.getPluginManager().getPlugin(HUD_ENGINE_PLUGIN);
            if (hudPlugin == null || !hudPlugin.isEnabled()) {
                plugin.getLogger().info("HUDEngine : non disponible. NoxoClaim continue sans HUD.");
                return;
            }
            Class<?> providerClass = Class.forName(PROVIDER_CLASS, false, hudPlugin.getClass().getClassLoader());
            Method findMethod = providerClass.getMethod("find");
            Object result = findMethod.invoke(null);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                plugin.getLogger().warning("HUDEngine : fournisseur API introuvable.");
                return;
            }
            engine = optional.get();
            ensureMinimapAssets(hudPlugin.getDataFolder().toPath());
            invokePublicApiQuietly(engine, "reload");
            registerValues();
            if (!hasPublicApiMethod(engine, "values")) {
                plugin.getLogger().warning("HUDEngine : API values() indisponible.");
                engine = null;
                return;
            }
            ready = true;
            plugin.getLogger().info("HUDEngine : HUD NoxoClaim premium prêt (" + MAP_SIZE + "x" + MAP_SIZE + ").");
            for (Player player : Bukkit.getOnlinePlayers()) show(player);
            startRefreshTask();
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().info("HUDEngine : API absente, intégration désactivée.");
        } catch (Throwable throwable) {
            ready = false;
            engine = null;
            plugin.getLogger().warning("HUDEngine : intégration désactivée après erreur : " + rootMessage(throwable));
        }
    }

    private void startRefreshTask() {
        stopRefreshTask();
        long refreshTicks = Math.max(MIN_REFRESH_TICKS,
                plugin.getConfig().getLong("hudengine.refresh-ticks", DEFAULT_REFRESH_TICKS));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isReady()) return;
            for (Player player : Bukkit.getOnlinePlayers()) refreshIfChanged(player);
        }, refreshTicks, refreshTicks).getTaskId();
    }

    private void stopRefreshTask() {
        if (refreshTask == -1) return;
        Bukkit.getScheduler().cancelTask(refreshTask);
        refreshTask = -1;
    }

    private void registerValues() {
        try {
            Object values = invokePublicApi(engine, "values");
            Method registerMethod = findPublicApiMethod(values, "register", String.class, Function.class);
            if (registerMethod == null) throw new NoSuchMethodException("values.register(String, Function)");
            for (int screenZ = -MAP_RADIUS; screenZ <= MAP_RADIUS; screenZ++) {
                for (int screenX = -MAP_RADIUS; screenX <= MAP_RADIUS; screenX++) {
                    final int x = screenX;
                    final int z = screenZ;
                    registerMethod.invoke(values, cellKey(x, z),
                            (Function<Player, String>) player -> cellState(player, x, z));
                }
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("HUDEngine : enregistrement des valeurs impossible : " + rootMessage(throwable));
        }
    }

    private String cellState(Player player, int screenX, int screenZ) {
        if (player == null || !player.isOnline()) return "0";
        var location = player.getLocation();
        int[] relative = rotateRelative(screenX, screenZ, location.getYaw());
        int centerX = Math.floorDiv(location.getBlockX(), 16);
        int centerZ = Math.floorDiv(location.getBlockZ(), 16);
        Claim claim = plugin.claims().atChunk(player.getWorld().getName(), centerX + relative[0], centerZ + relative[1]);
        if (claim == null) return "0";
        return claim.getOwner().equals(player.getUniqueId()) ? "1" : "2";
    }

    private int[] rotateRelative(int screenX, int screenZ, float yaw) {
        double radians = Math.toRadians(-yaw);
        int worldX = (int) Math.round(screenX * Math.cos(radians) - screenZ * Math.sin(radians));
        int worldZ = (int) Math.round(screenX * Math.sin(radians) + screenZ * Math.cos(radians));
        return new int[]{worldX, worldZ};
    }

    private void refreshIfChanged(Player player) {
        if (!isReady() || player == null || !player.isOnline()) return;
        var location = player.getLocation();
        int chunkX = Math.floorDiv(location.getBlockX(), 16);
        int chunkZ = Math.floorDiv(location.getBlockZ(), 16);
        int yawBucket = Math.floorMod(Math.round(location.getYaw() / 10f), 36);
        HudState state = new HudState(chunkX, chunkZ, yawBucket, plugin.claims().revision());
        if (state.equals(lastStates.put(player.getUniqueId(), state))) return;
        refresh(player);
    }

    /** Generates the entire HUD theme automatically in HUDEngine's data folder. */
    private void ensureMinimapAssets(Path hudEngineData) throws IOException {
        Path images = hudEngineData.resolve("images");
        Path layouts = hudEngineData.resolve("layouts");
        Path huds = hudEngineData.resolve("huds");
        Files.createDirectories(images);
        Files.createDirectories(layouts);
        Files.createDirectories(huds);
        writeMapSprite(images.resolve("noxoclaim-cell.png"));
        writePlayerSprite(images.resolve("noxoclaim-player.png"));
        writeFrameSprite(images.resolve("noxoclaim-frame.png"));
        writeImageDefinitions(images.resolve("noxoclaim-minimap.yml"));
        writeLayout(layouts.resolve("noxoclaim-minimap.yml"));
        writeHud(huds.resolve("noxoclaim-minimap.yml"));
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private void writeMapSprite(Path file) throws IOException {
        BufferedImage image = new BufferedImage(CELL_SIZE * 3, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        try {
            Color[] fills = {new Color(17, 22, 28, 235), new Color(44, 190, 112, 245), new Color(220, 72, 84, 245)};
            for (int state = 0; state < 3; state++) {
                int x = state * CELL_SIZE;
                g.setColor(fills[state]); g.fillRect(x, 0, CELL_SIZE, CELL_SIZE);
                g.setColor(new Color(255, 255, 255, 38)); g.drawRect(x, 0, CELL_SIZE - 1, CELL_SIZE - 1);
            }
        } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }

    private void writePlayerSprite(Path file) throws IOException {
        BufferedImage image = new BufferedImage(CELL_SIZE, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        try {
            int mid = CELL_SIZE / 2;
            g.setColor(new Color(0, 0, 0, 170)); g.fillOval(0, 0, CELL_SIZE - 1, CELL_SIZE - 1);
            g.setColor(new Color(65, 170, 255));
            int[] x = {mid, 1, mid, CELL_SIZE - 2}; int[] y = {1, CELL_SIZE - 2, mid + 1, CELL_SIZE - 2};
            g.fillPolygon(x, y, 4); g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1f)); g.drawPolygon(x, y, 4);
        } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }

    private void writeFrameSprite(Path file) throws IOException {
        BufferedImage image = new BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        try {
            RoundRectangle2D panel = new RoundRectangle2D.Float(1, 1, FRAME_WIDTH - 2, FRAME_HEIGHT - 2, 14, 14);
            g.setColor(new Color(7, 10, 15, 232)); g.fill(panel); g.setColor(new Color(82, 174, 255, 170)); g.setStroke(new BasicStroke(1.4f)); g.draw(panel);
            g.setColor(new Color(70, 165, 255, 35)); g.fillRoundRect(4, 4, FRAME_WIDTH - 8, HEADER_HEIGHT - 1, 10, 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 11)); g.setColor(new Color(235, 244, 255)); g.drawString("NOXOCLAIM", 9, 15);
            g.setFont(new Font("SansSerif", Font.PLAIN, 7)); g.setColor(new Color(145, 165, 185)); g.drawString("TERRITORY", 9, 22);
            g.setFont(new Font("SansSerif", Font.BOLD, 7)); String[] directions = {"N", "E", "S", "W"};
            int[] dx = {FRAME_WIDTH / 2 - 2, FRAME_WIDTH - 12, FRAME_WIDTH / 2 - 2, 5};
            int[] dy = {HEADER_HEIGHT + 4, MAP_Y + MAP_PIXELS / 2 + 3, MAP_Y + MAP_PIXELS + 8, MAP_Y + MAP_PIXELS / 2 + 3};
            for (int i = 0; i < 4; i++) { g.setColor(i == 0 ? new Color(95, 190, 255) : new Color(150, 165, 180)); g.drawString(directions[i], dx[i], dy[i]); }
            g.setColor(new Color(8, 12, 18, 230)); g.fillRoundRect(MAP_X, MAP_Y, MAP_PIXELS, MAP_PIXELS, 5, 5); g.setColor(new Color(255, 255, 255, 22)); g.drawRoundRect(MAP_X, MAP_Y, MAP_PIXELS, MAP_PIXELS, 5, 5);
            int footerY = MAP_Y + MAP_PIXELS + 11;
            drawLegend(g, 9, footerY, new Color(44, 190, 112), "OWN"); drawLegend(g, 51, footerY, new Color(220, 72, 84), "OTHER"); drawLegend(g, 104, footerY, new Color(65, 170, 255), "YOU");
        } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }

    private void drawLegend(Graphics2D g, int x, int y, Color color, String label) {
        g.setColor(color); g.fillRoundRect(x, y - 6, 6, 6, 2, 2); g.setFont(new Font("SansSerif", Font.BOLD, 6)); g.setColor(new Color(165, 180, 195)); g.drawString(label, x + 9, y - 1);
    }

    private void writeImageDefinitions(Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("noxoclaim-frame:\n  file: noxoclaim-frame.png\n  setting:\n    scale: 1\n\n");
        out.append("noxoclaim-player:\n  file: noxoclaim-player.png\n  setting:\n    scale: 1\n\n");
        for (int z = -MAP_RADIUS; z <= MAP_RADIUS; z++) for (int x = -MAP_RADIUS; x <= MAP_RADIUS; x++) {
            out.append(imageKey(x, z)).append(":\n  file: noxoclaim-cell.png\n  type: listener\n  split: 3\n  split-type: left\n  setting:\n    scale: 1\n    listener:\n      value: \"").append(cellKey(x, z)).append("\"\n      max: \"2\"\n\n");
        }
        Files.writeString(file, out.toString());
    }

    private void writeLayout(Path file) throws IOException {
        StringBuilder out = new StringBuilder("noxoclaim-minimap:\n  x: -170\n  y: 4\n  images:\n    1:\n      name: noxoclaim-frame\n      x: 0\n      y: 0\n      layer: 0\n");
        int imageId = 10;
        for (int z = -MAP_RADIUS; z <= MAP_RADIUS; z++) for (int x = -MAP_RADIUS; x <= MAP_RADIUS; x++) {
            out.append("    ").append(imageId++).append(":\n      name: ").append(imageKey(x, z)).append("\n      x: ").append(MAP_X + (x + MAP_RADIUS) * CELL_SIZE).append("\n      y: ").append(MAP_Y + (z + MAP_RADIUS) * CELL_SIZE).append("\n      layer: 1\n");
        }
        out.append("    200:\n      name: noxoclaim-player\n      x: ").append(MAP_X + MAP_RADIUS * CELL_SIZE).append("\n      y: ").append(MAP_Y + MAP_RADIUS * CELL_SIZE).append("\n      layer: 3\n");
        Files.writeString(file, out.toString());
    }

    private void writeHud(Path file) throws IOException {
        Files.writeString(file, "noxoclaim:minimap:\n  layouts:\n    1:\n      name: noxoclaim-minimap\n      x: 99\n      y: 5\n");
    }

    private static String cellKey(int x, int z) { return "noxoclaim:cell_" + (x + MAP_RADIUS) + "_" + (z + MAP_RADIUS); }
    private static String imageKey(int x, int z) { return "noxoclaim_cell_" + (x + MAP_RADIUS) + "_" + (z + MAP_RADIUS); }
    public boolean isReady() { return ready && engine != null; }
    public void show(Player player) { invokePlayerAction(player, "show"); }
    public void hide(Player player) { invokePlayerAction(player, "hide"); }
    public void refresh(Player player) { invokePlayerAction(player, "refresh"); }
    public void refreshAll() { if (!isReady()) return; for (Player player : Bukkit.getOnlinePlayers()) refresh(player); }

    private void invokePlayerAction(Player player, String action) {
        if (!isReady() || player == null || !player.isOnline()) return;
        try {
            Object controller = invokePublicApi(engine, "player", Player.class, player);
            if (controller == null) return;
            if ("refresh".equals(action)) { invokePublicApi(controller, "refresh"); return; }
            invokePublicApi(controller, action, String.class, HUD_KEY); invokePublicApiQuietly(controller, "refresh");
        } catch (Throwable throwable) { plugin.getLogger().fine("HUDEngine " + action + " impossible : " + rootMessage(throwable)); }
    }

    private static boolean hasPublicApiMethod(Object target, String name, Class<?>... parameterTypes) { return findPublicApiMethod(target, name, parameterTypes) != null; }
    private static Method findPublicApiMethod(Object target, String name, Class<?>... parameterTypes) {
        if (target == null) return null;
        Method method = findInInterfaces(target.getClass(), name, parameterTypes);
        if (method != null) return method;
        Class<?> superclass = target.getClass().getSuperclass();
        while (superclass != null) { method = findInInterfaces(superclass, name, parameterTypes); if (method != null) return method; superclass = superclass.getSuperclass(); }
        return null;
    }
    private static Method findInInterfaces(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> interfaceClass : type.getInterfaces()) {
            try { Method method = interfaceClass.getMethod(name, parameterTypes); if (Modifier.isPublic(interfaceClass.getModifiers())) return method; }
            catch (NoSuchMethodException ignored) { }
            Method nested = findInInterfaces(interfaceClass, name, parameterTypes); if (nested != null) return nested;
        }
        return null;
    }
    private static Object invokePublicApi(Object target, String name, Class<?> parameterType, Object argument) throws Exception { Method method = findPublicApiMethod(target, name, parameterType); if (method == null) throw new NoSuchMethodException(name); return method.invoke(target, argument); }
    private static Object invokePublicApi(Object target, String name) throws Exception { Method method = findPublicApiMethod(target, name); if (method == null) throw new NoSuchMethodException(name); return method.invoke(target); }
    private static void invokePublicApiQuietly(Object target, String name) { try { invokePublicApi(target, name); } catch (Throwable ignored) { } }
    private static String rootMessage(Throwable throwable) { Throwable current = throwable; while (current.getCause() != null) current = current.getCause(); String message = current.getMessage(); return message == null || message.isBlank() ? current.getClass().getSimpleName() : message; }
    private record HudState(int chunkX, int chunkZ, int yawBucket, long revision) { }
}
