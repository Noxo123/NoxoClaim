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
import java.util.function.Function;

/** Optional HUDEngine bridge. */
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

            // Important: do not call methods on HUDEngineImpl directly. Its concrete
            // implementation can be package-private. Resolve the provider through
            // the public API and keep every invocation on a public API Method.
            Class<?> providerClass = Class.forName(PROVIDER, false, hudPlugin.getClass().getClassLoader());
            Method find = providerClass.getMethod("find");
            Object result = find.invoke(null);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                plugin.getLogger().warning("HUDEngine : fournisseur API introuvable.");
                return;
            }

            engine = optional.get();
            ensureMinimapConfig();
            invokePublicApiQuietly(engine, "reload");
            registerValues();

            if (!hasPublicApiMethod(engine, "values")) {
                plugin.getLogger().warning("HUDEngine : API values() indisponible.");
                engine = null;
                return;
            }

            ready = true;
            plugin.getLogger().info("HUDEngine : intégration NoxoClaim activée.");
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

            register.invoke(values, "noxoclaim:map", (Function<Player, String>) this::renderMap);
            register.invoke(values, "noxoclaim:chunk", (Function<Player, String>) player -> {
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

    /** 11x11 chunk minimap, centered on the player. */
    private String renderMap(Player player) {
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        StringBuilder map = new StringBuilder(11 * 3 * 11);

        for (int dz = -5; dz <= 5; dz++) {
            if (dz != -5) map.append('\n');
            for (int dx = -5; dx <= 5; dx++) {
                if (dx != -5) map.append(' ');
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

    private static boolean hasPublicApiMethod(Object target, String name, Class<?>... parameterTypes) {
        return findPublicApiMethod(target, name, parameterTypes) != null;
    }

    /** Find methods declared by public API interfaces, never by a package-private implementation. */
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
