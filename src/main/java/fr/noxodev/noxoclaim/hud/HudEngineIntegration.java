package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Optional HUDEngine bridge.
 *
 * IMPORTANT: this class deliberately does not import HUDEngine classes. The API is
 * resolved through reflection so NoxoClaim remains fully functional when HUDEngine
 * is not installed.
 */
public final class HudEngineIntegration {
    public static final String HUD_KEY = "noxoclaim:minimap";
    private static final String PROVIDER = "io.github.nacvark.hudengine.api.HudEngineProvider";

    private final NoxoClaim plugin;
    private Object engine;
    private boolean ready;

    public HudEngineIntegration(NoxoClaim plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("hudengine.enabled", true)) {
            plugin.getLogger().info("HUDEngine : intégration désactivée dans la configuration.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::resolveSafely, 20L);
    }

    private void resolveSafely() {
        try {
            if (Bukkit.getPluginManager().getPlugin("HUDEngine") == null) {
                plugin.getLogger().info("HUDEngine : non disponible. NoxoClaim continue sans HUD.");
                return;
            }

            Class<?> providerClass = Class.forName(PROVIDER, false,
                    Bukkit.getPluginManager().getPlugin("HUDEngine").getClass().getClassLoader());
            Method find = providerClass.getMethod("find");
            Object result = find.invoke(null);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                plugin.getLogger().warning("HUDEngine : fournisseur API introuvable.");
                return;
            }

            engine = optional.get();
            Method running = engine.getClass().getMethod("isRunning");
            if (!(Boolean) running.invoke(engine)) {
                plugin.getLogger().warning("HUDEngine est chargé mais son moteur n'est pas opérationnel.");
                engine = null;
                return;
            }

            ensureMinimapConfig();
            tryInvoke(engine, "reload");
            registerValues();
            ready = true;
            plugin.getLogger().info("HUDEngine : intégration NoxoClaim activée.");
            for (Player player : Bukkit.getOnlinePlayers()) show(player);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("HUDEngine : API absente, intégration désactivée.");
        } catch (Throwable e) {
            ready = false;
            engine = null;
            plugin.getLogger().warning("HUDEngine : intégration désactivée après erreur : " + rootMessage(e));
        }
    }

    private void registerValues() {
        try {
            Object values = engine.getClass().getMethod("values").invoke(engine);
            Method register = values.getClass().getMethod("register", String.class, java.util.function.Function.class);
            register.invoke(values, "noxoclaim:map", (java.util.function.Function<Player, String>) this::renderMap);
            register.invoke(values, "noxoclaim:chunk", (java.util.function.Function<Player, String>) player -> {
                Claim claim = plugin.claims().at(player.getLocation());
                return claim == null ? "Libre" : claim.getName();
            });
        } catch (Throwable e) {
            plugin.getLogger().warning("HUDEngine : enregistrement des valeurs impossible : " + rootMessage(e));
        }
    }

    private void ensureMinimapConfig() {
        try {
            var hudPlugin = Bukkit.getPluginManager().getPlugin("HUDEngine");
            if (hudPlugin == null) return;
            Path data = hudPlugin.getDataFolder().toPath();
            Path huds = data.resolve("huds");
            Path layouts = data.resolve("layouts");
            Files.createDirectories(huds);
            Files.createDirectories(layouts);
            writeIfMissing(huds.resolve("noxoclaim-minimap.yml"), """
                    noxoclaim:minimap:
                      layouts:
                        1:
                          name: noxoclaim-minimap
                          x: 86
                          y: 4
                    """);
            writeIfMissing(layouts.resolve("noxoclaim-minimap.yml"), """
                    noxoclaim-minimap:
                      x: 0
                      y: 0
                      texts:
                        1:
                          name: default
                          pattern: "[noxoclaim:map]"
                          x: 0
                          y: 0
                          scale: 1
                          color: white
                          align: left
                          outline: 1
                          layer: 1
                    """);
        } catch (Exception e) {
            plugin.getLogger().warning("HUDEngine : impossible de créer la configuration minimap : " + e.getMessage());
        }
    }

    private void writeIfMissing(Path file, String content) throws IOException {
        if (!Files.exists(file)) Files.writeString(file, content);
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

    private void invokePlayerAction(Player player, String action) {
        if (!isReady()) return;
        try {
            Object controller = engine.getClass().getMethod("player", Player.class).invoke(engine, player);
            if ("refresh".equals(action)) {
                tryInvoke(controller, "refresh");
            } else {
                controller.getClass().getMethod(action, String.class).invoke(controller, HUD_KEY);
                tryInvoke(controller, "refresh");
            }
        } catch (Throwable e) {
            plugin.getLogger().fine("HUDEngine " + action + " impossible : " + rootMessage(e));
        }
    }

    private String renderMap(Player player) {
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        StringBuilder map = new StringBuilder();
        for (int dz = -4; dz <= 4; dz++) {
            if (dz != -4) map.append('\n');
            for (int dx = -4; dx <= 4; dx++) {
                if (dx != -4) map.append(' ');
                if (dx == 0 && dz == 0) {
                    map.append('●');
                    continue;
                }
                Claim claim = plugin.claims().atChunk(player.getWorld().getName(), centerX + dx, centerZ + dz);
                if (claim == null) map.append('·');
                else if (claim.getOwner().equals(player.getUniqueId())) map.append('■');
                else map.append('□');
            }
        }
        return map.toString();
    }

    private static void tryInvoke(Object target, String method) {
        try {
            target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
