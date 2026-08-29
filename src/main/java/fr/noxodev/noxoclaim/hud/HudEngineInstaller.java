package fr.noxodev.noxoclaim.hud;

import fr.noxodev.noxoclaim.NoxoClaim;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;

/** Downloads the pinned HUDEngine plugin when it is not installed yet. */
public final class HudEngineInstaller {
    public static final String VERSION = "1.0.0";
    private static final String FILE_NAME = "HUDEngine-" + VERSION + ".jar";
    private static final String URL = "https://github.com/Nacvark/HUDEngine/releases/download/v1.0.0/HUDEngine-1.0.0.jar";
    private static final String SHA256 = "fa4bf2caa35a97f536ef2b4d5a3989edc1da3db75bd26953dfe9854d4ac692da";

    private HudEngineInstaller() {}

    public static void ensureInstalled(NoxoClaim plugin) {
        if (!plugin.getConfig().getBoolean("hudengine.auto-install", true)) return;
        if (plugin.getServer().getPluginManager().getPlugin("HUDEngine") != null) return;

        Path pluginsDir = plugin.getDataFolder().toPath().getParent();
        if (pluginsDir == null) {
            plugin.getLogger().warning("HUDEngine : dossier plugins introuvable.");
            return;
        }
        Path target = pluginsDir.resolve(FILE_NAME);
        if (Files.isRegularFile(target)) {
            plugin.getLogger().info("HUDEngine trouvé sur disque. Redémarre le serveur pour le charger.");
            return;
        }

        plugin.getLogger().info("HUDEngine absent : téléchargement automatique de HUDEngine " + VERSION + "...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Path tmp = pluginsDir.resolve(FILE_NAME + ".download");
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                        .timeout(Duration.ofMinutes(2))
                        .header("User-Agent", "NoxoClaim/" + plugin.getDescription().getVersion())
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                try (InputStream in = response.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                String actual = sha256(tmp);
                if (!SHA256.equalsIgnoreCase(actual)) {
                    Files.deleteIfExists(tmp);
                    throw new IOException("SHA-256 invalide: " + actual);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLogger().info(
                        "HUDEngine " + VERSION + " installé et vérifié. REDÉMARRE le serveur pour l'activer."));
            } catch (Exception e) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLogger().warning(
                        "Installation HUDEngine impossible : " + e.getMessage()));
            }
        });
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }
}
