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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Commit-based updater. Never replaces the running plugin jar in-place. */
public final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/Noxo123/NoxoClaim";
    private static final String COMMIT_API = API + "/commits/main";
    private static final String RELEASE_API = API + "/releases/tags/nightly";
    private static final String TAG_API = API + "/git/ref/tags/nightly";
    private static final String RELEASE_PAGE = "https://github.com/Noxo123/NoxoClaim/releases/tag/nightly";
    private static final Pattern SHA = Pattern.compile("\"sha\"\\s*:\\s*\"([0-9a-fA-F]{40})\"");
    private static final Pattern ASSET = Pattern.compile("\\{[^{}]*?\"name\"\\s*:\\s*\"([^\"]+\\.jar)\"[^{}]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\\}");
    private final NoxoClaim plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean checking;
    private volatile String lastDownloadedCommit;

    public UpdateChecker(NoxoClaim plugin) { this.plugin = plugin; }

    public void check(boolean notifyConsole) {
        if (!plugin.getConfig().getBoolean("updates.enabled", true) || checking) return;
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String remoteCommit = fetchMainCommit();
                String localCommit = getBuildCommit();
                if (remoteCommit.equalsIgnoreCase(localCommit) || remoteCommit.equalsIgnoreCase(lastDownloadedCommit)) {
                    if (notifyConsole) plugin.getLogger().info("NoxoClaim est à jour (commit " + shortSha(remoteCommit) + ").");
                    return;
                }

                Release release = fetchNightlyRelease();
                if (release == null || release.downloadUrl == null) {
                    if (notifyConsole) plugin.getLogger().info("Nouveau commit détecté, mais le build automatique n'est pas encore disponible.");
                    return;
                }
                if (release.commit != null && !remoteCommit.equalsIgnoreCase(release.commit)) {
                    if (notifyConsole) plugin.getLogger().info("Le build nightly est encore en cours pour le commit " + shortSha(remoteCommit) + ".");
                    return;
                }

                plugin.setUpdateInfo(new UpdateInfo(true, plugin.getDescription().getVersion(), release.version, RELEASE_PAGE, "Commit " + shortSha(remoteCommit)));
                if (plugin.getConfig().getBoolean("updates.auto-update", true)) {
                    downloadUpdate(release.downloadUrl, remoteCommit);
                    lastDownloadedCommit = remoteCommit;
                } else if (notifyConsole) {
                    plugin.getLogger().warning("Nouvelle mise à jour disponible : commit " + shortSha(remoteCommit));
                }
            } catch (Exception e) {
                if (notifyConsole) plugin.getLogger().warning("Vérification des mises à jour impossible : " + e.getMessage());
            } finally { checking = false; }
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
        Matcher assets = ASSET.matcher(json);
        if (!assets.find()) return null;

        String commit = null;
        try {
            String ref = request(TAG_API);
            commit = match("\"sha\"\\s*:\\s*\"([0-9a-fA-F]{40})\"", ref);
        } catch (Exception ignored) { }
        return new Release(tag == null ? "nightly" : tag, assets.group(2), commit);
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

        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) { in.transferTo(out); }
        if (Files.size(temp) < 10_000) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("JAR téléchargé invalide");
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        plugin.getLogger().info("Mise à jour automatique téléchargée : commit " + shortSha(commit) + ".");
        plugin.getLogger().info("Redémarrez le serveur pour que Paper installe la nouvelle version depuis plugins/update.");
    }

    private String getBuildCommit() {
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in == null) return "unknown";
            Properties p = new Properties(); p.load(in);
            return p.getProperty("commit", "unknown").trim();
        } catch (Exception e) { return "unknown"; }
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

    private static String match(String regex, String input) { Matcher m = Pattern.compile(regex).matcher(input); return m.find() ? m.group(1) : null; }
    private static String shortSha(String sha) { return sha == null || sha.length() < 7 ? sha : sha.substring(0, 7); }
    private record Release(String version, String downloadUrl, String commit) {}
}
