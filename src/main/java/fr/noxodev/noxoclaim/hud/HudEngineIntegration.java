package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/** Optional HUDEngine bridge and graphical NoxoClaim mini-map. */
public final class HudEngineIntegration {
    public static final String HUD_KEY = "noxoclaim:minimap";
    private static final String PROVIDER = "io.github.nacvark.hudengine.api.HudEngineProvider";
    private static final int MAP_SIZE = 11;
    private static final int CELL_SIZE = 12;
    private static final int MAP_PIXELS = MAP_SIZE * CELL_SIZE;

    private final NoxoClaim plugin;
    private Object engine;
    private boolean ready;

    public HudEngineIntegration(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ready = false;
        engine = null;
        if (!plugin.getConfig().getBoolean("hudengine.enabled", true)) {
            plugin.getLogger().info("HUDEngine : intégration désactivée dans la configuration.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::resolveSafely, 20L);
    }

    private void resolveSafely() {
        try {
            var hudPlugin = Bukkit.getPluginManager().getPlugin("HUDEngine");
            if (hudPlugin == null || !hudPlugin.isEnabled()) {
                plugin.getLogger().info("HUDEngine : non disponible. NoxoClaim continue sans HUD.");
                return;
            }

            Class<?> providerClass = Class.forName(PROVIDER, false, hudPlugin.getClass().getClassLoader());
            Method find = providerClass.getMethod("find");
            Object result = find.invoke(null);
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
            plugin.getLogger().info("HUDEngine : mini-map graphique NoxoClaim prête (" + MAP_SIZE + "x" + MAP_SIZE + ").");
            for (Player player : Bukkit.getOnlinePlayers()) show(player);
            startRefreshTask();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("HUDEngine : API absente, intégration désactivée.");
        } catch (Throwable e) {
            ready = false;
            engine = null;
            plugin.getLogger().warning("HUDEngine : intégration désactivée après erreur : " + rootMessage(e));
        }
    }

    private void startRefreshTask() {
        long ticks = Math.max(2L, plugin.getConfig().getLong("hudengine.refresh-ticks", 5L));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isReady()) return;
            for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        }, ticks, ticks);
    }

    private void registerValues() {
        try {
            Object values = invokePublicApi(engine, "values");
            Method register = findPublicApiMethod(values, "register", String.class, Function.class);
            if (register == null) throw new NoSuchMethodException("values.register(String, Function)");

            // One small numeric value per cell. The image definitions use the value as a
            // 3-frame listener: 0=free, 1=own claim, 2=other claim.
            for (int screenZ = -5; screenZ <= 5; screenZ++) {
                for (int screenX = -5; screenX <= 5; screenX++) {
                    final int sx = screenX;
                    final int sz = screenZ;
                    String key = cellKey(sx, sz);
                    register.invoke(values, key, (Function<Player, String>) player -> cellState(player, sx, sz));
                }
            }

            register.invoke(values, "noxoclaim:chunk", (Function<Player, String>) player -> {
                Claim claim = plugin.claims().at(player.getLocation());
                return claim == null ? "Libre" : claim.getName();
            });
        } catch (Throwable e) {
            plugin.getLogger().warning("HUDEngine : enregistrement des valeurs impossible : " + rootMessage(e));
        }
    }

    private String cellState(Player player, int screenX, int screenZ) {
        if (player == null || !player.isOnline()) return "0";
        int[] relative = rotateRelative(screenX, screenZ, player.getLocation().getYaw());
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        Claim claim = plugin.claims().atChunk(player.getWorld().getName(), centerX + relative[0], centerZ + relative[1]);
        if (claim == null) return "0";
        return claim.getOwner().equals(player.getUniqueId()) ? "1" : "2";
    }

    /** Rotates the world around the player so the direction the player faces stays at the top. */
    private int[] rotateRelative(int screenX, int screenZ, float yaw) {
        double radians = Math.toRadians(-yaw);
        int worldX = (int) Math.round(screenX * Math.cos(radians) - screenZ * Math.sin(radians));
        int worldZ = (int) Math.round(screenX * Math.sin(radians) + screenZ * Math.cos(radians));
        return new int[]{worldX, worldZ};
    }

    /** Creates the graphical HUD config and the actual PNG sprites used by HUDEngine. */
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

    private void writeMapSprite(Path file) throws IOException {
        // Three 12x12 frames in one horizontal sprite sheet.
        BufferedImage image = new BufferedImage(CELL_SIZE * 3, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color[] colors = {new Color(35, 35, 35, 205), new Color(45, 190, 80, 235), new Color(220, 65, 65, 235)};
            for (int frame = 0; frame < 3; frame++) {
                int x = frame * CELL_SIZE;
                g.setColor(colors[frame]);
                g.fillRect(x + 1, 1, CELL_SIZE - 2, CELL_SIZE - 2);
                g.setColor(new Color(255, 255, 255, 70));
                g.setStroke(new BasicStroke(1f));
                g.drawRect(x + 1, 1, CELL_SIZE - 3, CELL_SIZE - 3);
            }
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private void writePlayerSprite(Path file) throws IOException {
        BufferedImage image = new BufferedImage(CELL_SIZE, CELL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(45, 140, 255, 255));
            g.fillRect(1, 1, CELL_SIZE - 2, CELL_SIZE - 2);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(6, 2, 6, 10);
            g.drawLine(2, 6, 10, 6);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private void writeFrameSprite(Path file) throws IOException {
        BufferedImage image = new BufferedImage(MAP_PIXELS + 8, MAP_PIXELS + 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0, 0, 0, 165));
            g.fillRoundRect(0, 0, image.getWidth(), image.getHeight(), 8, 8);
            g.setColor(new Color(255, 255, 255, 115));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(1, 1, image.getWidth() - 3, image.getHeight() - 3, 8, 8);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private void writeImageDefinitions(Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("noxoclaim-frame:\n  file: noxoclaim-frame.png\n  setting:\n    scale: 1\n\n");
        out.append("noxoclaim-player:\n  file: noxoclaim-player.png\n  setting:\n    scale: 1\n\n");
        for (int z = -5; z <= 5; z++) {
            for (int x = -5; x <= 5; x++) {
                String name = imageKey(x, z);
                out.append(name).append(":\n")
                        .append("  file: noxoclaim-cell.png\n")
                        .append("  type: listener\n")
                        .append("  split: 3\n")
                        .append("  split-type: left\n")
                        .append("  setting:\n")
                        .append("    scale: 1\n")
                        .append("    listener:\n")
                        .append("      value: \"").append(cellKey(x, z)).append("\"\n")
                        .append("      max: \"2\"\n\n");
            }
        }
        Files.writeString(file, out.toString());
    }

    private void writeLayout(Path file) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("noxoclaim-minimap:\n  x: -140\n  y: -4\n  images:\n");
        out.append("    1:\n      name: noxoclaim-frame\n      x: 0\n      y: 0\n      layer: 0\n");
        int id = 10;
        for (int z = -5; z <= 5; z++) {
            for (int x = -5; x <= 5; x++) {
                out.append("    ").append(id++).append(":\n")
                        .append("      name: ").append(imageKey(x, z)).append("\n")
                        .append("      x: ").append((x + 5) * CELL_SIZE + 4).append("\n")
                        .append("      y: ").append((z + 5) * CELL_SIZE + 4).append("\n")
                        .append("      layer: 1\n");
            }
        }
        out.append("    200:\n      name: noxoclaim-player\n      x: ").append(4 + 5 * CELL_SIZE).append("\n")
                .append("      y: ").append(4 + 5 * CELL_SIZE).append("\n      layer: 3\n");
        out.append("  texts:\n    1:\n      name: small\n      pattern: \"[x] [z] [direction_short]\"\n      x: 70\n      y: 140\n      scale: 1\n      color: white\n      align: center\n      outline: 1\n      layer: 4\n");
        Files.writeString(file, out.toString());
    }

    private void writeHud(Path file) throws IOException {
        Files.writeString(file, "noxoclaim:minimap:\n  layouts:\n    1:\n      name: noxoclaim-minimap\n      x: 99\n      y: 5\n");
    }

    private static String cellKey(int x, int z) {
        return "noxoclaim:cell_" + (x + 5) + "_" + (z + 5);
    }

    private static String imageKey(int x, int z) {
        return "noxoclaim_cell_" + (x + 5) + "_" + (z + 5);
    }

    public boolean isReady() {
        return ready && engine != null;
    }

    public void show(Player player) {
        invokePlayerAction(player, "show");
    }

    public void hide(Player player) {
        invokePlayerAction(player, "hide");
    }

    public void refresh(Player player) {
        invokePlayerAction(player, "refresh");
    }

    public void refreshAll() {
        if (!isReady()) return;
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    private void invokePlayerAction(Player player, String action) {
        if (!isReady() || player == null || !player.isOnline()) return;
        try {
            Object controller = invokePublicApi(engine, "player", Player.class, player);
            if (controller == null) return;
            if ("refresh".equals(action)) {
                invokePublicApi(controller, "refresh");
            } else {
                invokePublicApi(controller, action, String.class, HUD_KEY);
                invokePublicApiQuietly(controller, "refresh");
            }
        } catch (Throwable e) {
            plugin.getLogger().fine("HUDEngine " + action + " impossible : " + rootMessage(e));
        }
    }

    private static boolean hasPublicApiMethod(Object target, String name, Class<?>... parameterTypes) {
        return findPublicApiMethod(target, name, parameterTypes) != null;
    }

    private static Method findPublicApiMethod(Object target, String name, Class<?>... parameterTypes) {
        if (target == null) return null;
        Method method = findInInterfaces(target.getClass(), name, parameterTypes);
        if (method != null) return method;
        Class<?> superclass = target.getClass().getSuperclass();
        while (superclass != null) {
            method = findInInterfaces(superclass, name, parameterTypes);
            if (method != null) return method;
            superclass = superclass.getSuperclass();
        }
        return null;
    }

    private static Method findInInterfaces(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> iface : type.getInterfaces()) {
            try {
                Method method = iface.getMethod(name, parameterTypes);
                if (java.lang.reflect.Modifier.isPublic(iface.getModifiers())) return method;
            } catch (NoSuchMethodException ignored) {
            }
            Method nested = findInInterfaces(iface, name, parameterTypes);
            if (nested != null) return nested;
        }
        return null;
    }

    private static Object invokePublicApi(Object target, String name, Class<?> parameterType, Object argument) throws Exception {
        Method method = findPublicApiMethod(target, name, parameterType);
        if (method == null) throw new NoSuchMethodException(name);
        return method.invoke(target, argument);
    }

    private static Object invokePublicApi(Object target, String name) throws Exception {
        Method method = findPublicApiMethod(target, name);
        if (method == null) throw new NoSuchMethodException(name);
        return method.invoke(target);
    }

    private static void invokePublicApiQuietly(Object target, String name) {
        try {
            invokePublicApi(target, name);
        } catch (Throwable ignored) {
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
