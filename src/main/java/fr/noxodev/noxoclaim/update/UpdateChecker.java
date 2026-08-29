package fr.noxodev.noxoclaim.update;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commit-based updater. It detects the current main commit, verifies the
 * matching nightly release asset, and places the new jar in Paper's update
 * directory. The running jar is never replaced in-place.
 */
public final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/Noxo123/NoxoClaim";
    private static final String COMMIT_API = API + "/commits/main";
    private static final String RELEASE_API = API + "/releases/tags/nightly";
    private static final String RELEASE_PAGE = "https://github.com/Noxo123/NoxoClaim/releases/tag/nightly";
    private static final Pattern SHA = Pattern.compile("\"sha\"\\s*:\\s*\"([0-9a-fA-F]{40})\"");
    private static final Pattern ASSET = Pattern.compile("\\{[^{}]*?\"name\"\\s*:\\s*\"([^\"]+\\.jar)\"[^{}]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\\}");

    private final NoxoClaim plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean checking;

    public UpdateChecker(NoxoClaim plugin) { this.plugin = plugin; }

    public void check(boolean notifyConsole) {
        if (!plugin.getConfig().getBoolean("updates.enabled", true) || checking) return;
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String remoteCommit = fetchMainCommit();
                String localCommit = getBuildCommit();

                if (remoteCommit.equalsIgnoreCase(localCommit)) {
                    if (notifyConsole) plugin.getLogger().info("NoxoClaim est à jour (commit " + shortSha(remoteCommit) + ").");
                    return;
                }

                Release release = fetchNightlyRelease();
                if (release == null || release.downloadUrl == null) {
                    plugin.getLogger().warning("Nouveau commit détecté (" + shortSha(remoteCommit) + "), mais aucun JAR de mise à jour n'est encore disponible. Le build GitHub est probablement en cours.");
                    return;
                }

                if (release.commit != null && !remoteCommit.equalsIgnoreCase(release.commit)) {
                    plugin.getLogger().warning("Le build nightly n'est pas encore au dernier commit. Mise à jour reportée.");
                    return;
                }

                UpdateInfo info = new UpdateInfo(true, plugin.getDescription().getVersion(), release.version, RELEASE_PAGE, "Commit " + shortSha(remoteCommit));
                plugin.setUpdateInfo(info);

                if (plugin.getConfig().getBoolean("updates.auto-update", true)) {
                    downloadUpdate(release.downloadUrl, remoteCommit);
                } else if (notifyConsole) {
                    plugin.getLogger().warning("Nouvelle mise à jour disponible : commit " + shortSha(remoteCommit));
                }
            } catch (Exception e) {
                if (notifyConsole) plugin.getLogger().warning("Vérification des mises à jour impossible : " + e.getMessage());
            } finally {
                checking = false;
            }
        });
    }

    private String fetchMainCommit() throws Exception {
        String json = request(COMMIT_API);
        Matcher m = SHA.matcher(json);
        if (!m.find()) throw new IllegalStateException("SHA GitHub introuvable");
        return m.group(1);
    }

    private Release fetchNightlyRelease() throws Exception {
        String json = request(RELEASE_API);
        String tag = match("\"tag_name\"\\s*:\\s*\"([^\"]+)\"", json);
        String target = match("\"target_commitish\"\\s*:\\s*\"([^\"]+)\"", json);
        Matcher assets = ASSET.matcher(json);
        if (!assets.find()) return null;
        return new Release(tag == null ? "nightly" : tag, assets.group(2), target);
    }

    private void downloadUpdate(String url, String commit) throws Exception {
        Path updateDir = plugin.getDataFolder().getParentFile().toPath().resolve("update");
        Files.createDirectories(updateDir);
        Path target = updateDir.resolve("NoxoClaim.jar");
        Path temp = updateDir.resolve("NoxoClaim.jar.download");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "NoxoClaim-Updater/" + plugin.getDescription().getVersion()).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IllegalStateException("Téléchargement HTTP " + response.statusCode());

        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
            in.transferTo(out);
        }
        if (Files.size(temp) < 10_000) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("JAR téléchargé invalide");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        plugin.getLogger().info("Mise à jour téléchargée automatiquement : commit " + shortSha(commit) + ".");
        plugin.getLogger().info("Paper installera NoxoClaim.jar depuis plugins/update au prochain redémarrage.");
    }

    private String getBuildCommit() {
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in == null) return "unknown";
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("commit", "unknown").trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String request(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "NoxoClaim-Updater/" + plugin.getDescription().getVersion()).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("GitHub HTTP " + response.statusCode());
        return response.body();
    }

    private static String match(String regex, String input) {
        Matcher m = Pattern.compile(regex).matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private static String shortSha(String sha) { return sha == null || sha.length() < 7 ? sha : sha.substring(0, 7); }

    public static int compare(String a, String b) {
        return normalize(a).compareTo(normalize(b));
    }

    private static String normalize(String v) {
        return v == null ? "0.0.0" : v.trim().replaceFirst("^[vV]", "").split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private record Release(String version, String downloadUrl, String commit) {}
}
