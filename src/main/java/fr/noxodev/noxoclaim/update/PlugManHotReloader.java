package fr.noxodev.noxoclaim.update;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Optional PlugMan/PlugManX hot-reload bridge. No compile-time dependency is required. */
public final class PlugManHotReloader {
    private static final String PLUGIN_NAME = "NoxoClaim";

    private PlugManHotReloader() {}

    public static boolean isAvailable(NoxoClaim plugin) {
        if (!plugin.getConfig().getBoolean("plugman.enabled", true)
                || !plugin.getConfig().getBoolean("plugman.auto-detect", true)) return false;
        return findPlugMan() != null;
    }

    /**
     * Replaces the running JAR through PlugManX/PlugMan's supported unload/load commands.
     * All Bukkit lifecycle operations are performed synchronously on the server thread.
     */
    public static boolean reload(NoxoClaim plugin, Path downloadedJar) {
        if (!isAvailable(plugin) || !Files.isRegularFile(downloadedJar)) return false;

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> perform(plugin, downloadedJar, result));
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[NoxoClaim] Hot-reload PlugMan échoué : " + safeMessage(e));
            return false;
        }
    }

    private static void perform(NoxoClaim plugin, Path downloadedJar, CompletableFuture<Boolean> result) {
        Path pluginJar = plugin.getFile().toPath().toAbsolutePath().normalize();
        Path staged = pluginJar.resolveSibling(pluginJar.getFileName() + ".noxoclaim-new");
        Path backup = pluginJar.resolveSibling(pluginJar.getFileName() + ".noxoclaim-old");

        try {
            Files.copy(downloadedJar, staged, StandardCopyOption.REPLACE_EXISTING);

            if (!dispatch("unload " + PLUGIN_NAME))
                throw new IllegalStateException("PlugMan n'a pas accepté l'unload de NoxoClaim");

            Files.deleteIfExists(backup);
            Files.move(pluginJar, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(staged, pluginJar, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception moveFailure) {
                Files.move(backup, pluginJar, StandardCopyOption.REPLACE_EXISTING);
                throw moveFailure;
            }

            if (!dispatch("load " + PLUGIN_NAME) || !isEnabled())
                throw new IllegalStateException("le nouveau JAR n'a pas pu être chargé");

            Files.deleteIfExists(backup);
            Files.deleteIfExists(downloadedJar);
            result.complete(true);
            Bukkit.getLogger().info("[NoxoClaim] Mise à jour appliquée à chaud via " + findPlugMan().getName() + ".");
        } catch (Exception failure) {
            restoreOld(pluginJar, backup, staged);
            result.complete(false);
            Bukkit.getLogger().warning("[NoxoClaim] Hot-reload impossible, fallback vers plugins/update : " + safeMessage(failure));
        }
    }

    private static void restoreOld(Path pluginJar, Path backup, Path staged) {
        try {
            Plugin current = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (current != null && current.isEnabled()) dispatch("unload " + PLUGIN_NAME);
        } catch (Exception ignored) {
        }
        try {
            Files.deleteIfExists(staged);
            if (Files.isRegularFile(backup)) {
                Files.deleteIfExists(pluginJar);
                Files.move(backup, pluginJar, StandardCopyOption.REPLACE_EXISTING);
                dispatch("load " + PLUGIN_NAME);
            }
        } catch (Exception restoreFailure) {
            Bukkit.getLogger().severe("[NoxoClaim] Impossible de restaurer l'ancien JAR : " + safeMessage(restoreFailure));
        }
    }

    private static boolean dispatch(String arguments) {
        Plugin plugMan = findPlugMan();
        if (plugMan == null || !plugMan.isEnabled()) return false;
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman " + arguments);
    }

    private static boolean isEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return plugin != null && plugin.isEnabled();
    }

    private static Plugin findPlugMan() {
        Plugin plugMan = Bukkit.getPluginManager().getPlugin("PlugManX");
        if (plugMan != null) return plugMan;
        return Bukkit.getPluginManager().getPlugin("PlugMan");
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
