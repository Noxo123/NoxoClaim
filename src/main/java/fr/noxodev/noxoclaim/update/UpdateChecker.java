package fr.noxodev.noxoclaim.update;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

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
        checkInternal(notifyConsole, null);
    }

    /** Manual check used by /claimadmin update. It ignores the automatic-check toggle. */
    public void checkManual(CommandSender sender) {
        if (checking) {
            sender.sendMessage("§e[NoxoClaim] Une vérification des mises à jour est déjà en cours.");
            return;
        }
        sender.sendMessage("§b[NoxoClaim] Vérification des mises à jour GitHub...");
        checkInternal(true, sender);
    }

    private void checkInternal(boolean notifyConsole, CommandSender manualSender) {
        if (checking) {
            if (manualSender != null) manualSender.sendMessage("§e[NoxoClaim] Une vérification est déjà en cours.");
            return;
        }
        if (manualSender == null && !plugin.getConfig().getBoolean("updates.enabled", true)) return;
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String remoteCommit = fetchMainCommit();
                String localCommit = getBuildCommit();
                if (remoteCommit.equalsIgnoreCase(localCommit) || remoteCommit.equalsIgnoreCase(lastDownloadedCommit)) {
                    String message = "§a[NoxoClaim] NoxoClaim est à jour (commit " + shortSha(remoteCommit) + ").";
                    sendManual(manualSender, message);
                    if (notifyConsole && manualSender == null) plugin.getLogger().info(strip(message));
                    return;
                }

                Release release = fetchNightlyRelease();
                if (release == null || release.downloadUrl == null) {
                    String message = "§e[NoxoClaim] Nouveau commit détecté, mais le build automatique n'est pas encore disponible.";
                    sendManual(manualSender, message);
                    if (notifyConsole && manualSender == null) plugin.getLogger().info(strip(message));
                    return;
                }
                if (release.commit != null && !remoteCommit.equalsIgnoreCase(release.commit)) {
                    String message = "§e[NoxoClaim] Le build nightly est encore en cours pour le commit " + shortSha(remoteCommit) + ".";
                    sendManual(manualSender, message);
                    if (notifyConsole && manualSender == null) plugin.getLogger().info(strip(message));
                    return;
                }

                plugin.setUpdateInfo(new UpdateInfo(true, plugin.getDescription().getVersion(), release.version, RELEASE_PAGE, "Commit " + shortSha(remoteCommit)));
                if (plugin.getConfig().getBoolean("updates.auto-update", true)) {
                    downloadUpdate(release.downloadUrl, remoteCommit);
                    lastDownloadedCommit = remoteCommit;
                    sendManual(manualSender, "§a[NoxoClaim] ✓ Mise à jour téléchargée pour le commit §f" + shortSha(remoteCommit) + "§a. Redémarrez le serveur pour l'appliquer.");
                } else {
                    String message = "§e[NoxoClaim] Nouvelle mise à jour disponible : commit " + shortSha(remoteCommit);
                    sendManual(manualSender, message);
                    if (notifyConsole && manualSender == null) plugin.getLogger().warning(strip(message));
                }
            } catch (Exception e) {
                String message = "§c[NoxoClaim] Vérification des mises à jour impossible : " + e.getMessage();
                sendManual(manualSender, message);
                if (notifyConsole && manualSender == null) plugin.getLogger().warning(strip(message));
            } finally { checking = false; }
        });
    }

    private void sendManual(CommandSender sender, String message) {
        if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    private static String strip(String message) { return message.replaceAll("§.", ""); }

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
