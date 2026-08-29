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
    private static final String RELEASES_API = API + "/releases?per_page=20";
    private static final String RELEASE_PAGE = "https://github.com/Noxo123/NoxoClaim/releases";
    private static final Pattern SHA = Pattern.compile("\\\"sha\\\"\\s*:\\s*\\\"([0-9a-fA-F]{40})\\\"");
    private static final Pattern RELEASE = Pattern.compile("\\{(?:(?!\\{).)*?\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"(?:(?!\\{).)*?\\\"target_commitish\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"(?:(?!\\{).)*?\\\"assets\\\"\\s*:\\s*\\[(.*?)\\](?:(?!\\}).)*?\\}", Pattern.DOTALL);
    private static final Pattern ASSET = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\".*?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL);
    private final NoxoClaim plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean checking;
    private volatile String lastDownloadedCommit;

    public UpdateChecker(NoxoClaim plugin) { this.plugin = plugin; }
    public void check(boolean notifyConsole) { checkInternal(notifyConsole, null); }

    /** Manual check used by /claimadmin update. */
    public void checkManual(CommandSender sender) {
        if (checking) { sender.sendMessage("§e[NoxoClaim] Une vérification des mises à jour est déjà en cours."); return; }
        sender.sendMessage("§b[NoxoClaim] Vérification des mises à jour GitHub...");
        checkInternal(true, sender);
    }

    private void checkInternal(boolean notifyConsole, CommandSender manualSender) {
        if (checking) { if (manualSender != null) manualSender.sendMessage("§e[NoxoClaim] Une vérification est déjà en cours."); return; }
        if (manualSender == null && !plugin.getConfig().getBoolean("updates.enabled", true)) return;
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String remoteCommit = fetchMainCommit();
                String localCommit = getBuildCommit();
                if (remoteCommit.equalsIgnoreCase(localCommit) || remoteCommit.equalsIgnoreCase(lastDownloadedCommit)) {
                    report(manualSender, notifyConsole, "§a[NoxoClaim] NoxoClaim est à jour (commit " + shortSha(remoteCommit) + ").", false);
                    return;
                }

                Release release = fetchMatchingRelease(remoteCommit);
                if (release == null) {
                    String message = "§e[NoxoClaim] Nouveau commit détecté (§f" + shortSha(remoteCommit) + "§e), mais son build n'est pas encore disponible. Le workflow GitHub Actions est probablement encore en cours.";
                    report(manualSender, notifyConsole, message, false);
                    scheduleBuildRetry(remoteCommit, manualSender, notifyConsole, 1);
                    return;
                }

                plugin.setUpdateInfo(new UpdateInfo(true, plugin.getDescription().getVersion(), release.version, release.url, "Commit " + shortSha(remoteCommit)));
                if (plugin.getConfig().getBoolean("updates.auto-update", true)) {
                    downloadUpdate(release.downloadUrl, remoteCommit);
                    lastDownloadedCommit = remoteCommit;
                    report(manualSender, true, "§a[NoxoClaim] ✓ Mise à jour téléchargée pour le commit §f" + shortSha(remoteCommit) + "§a. Redémarrez le serveur pour l'appliquer.", false);
                } else {
                    report(manualSender, notifyConsole, "§e[NoxoClaim] Nouvelle mise à jour disponible : commit " + shortSha(remoteCommit), true);
                }
            } catch (Exception e) {
                report(manualSender, notifyConsole, "§c[NoxoClaim] Vérification des mises à jour impossible : " + e.getMessage(), false);
            } finally { checking = false; }
        });
    }

    private void scheduleBuildRetry(String expectedCommit, CommandSender sender, boolean notifyConsole, int attempt) {
        if (attempt > 10) {
            if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c[NoxoClaim] Le build du commit §f" + shortSha(expectedCommit) + "§c n'est toujours pas disponible après 5 minutes."));
            return;
        }
        long delay = 30L * 20L;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (checking) return;
            checking = true;
            try {
                Release release = fetchMatchingRelease(expectedCommit);
                if (release == null) {
                    if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§e[NoxoClaim] Build toujours en cours... nouvelle vérification dans 30 secondes (§f" + attempt + "/10§e)."));
                    scheduleBuildRetry(expectedCommit, sender, notifyConsole, attempt + 1);
                    return;
                }
                plugin.setUpdateInfo(new UpdateInfo(true, plugin.getDescription().getVersion(), release.version, release.url, "Commit " + shortSha(expectedCommit)));
                if (plugin.getConfig().getBoolean("updates.auto-update", true)) {
                    downloadUpdate(release.downloadUrl, expectedCommit);
                    lastDownloadedCommit = expectedCommit;
                    if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a[NoxoClaim] ✓ Build terminé et mise à jour téléchargée. Redémarrez le serveur pour l'appliquer."));
                } else if (sender != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§a[NoxoClaim] ✓ Le build du commit §f" + shortSha(expectedCommit) + "§a est maintenant disponible."));
                }
            } catch (Exception e) {
                if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c[NoxoClaim] Erreur pendant la vérification du build : " + e.getMessage()));
            } finally { checking = false; }
        }, delay);
    }

    private Release fetchMatchingRelease(String expectedCommit) throws Exception {
        String json = request(RELEASES_API);
        Matcher releaseMatcher = RELEASE.matcher(json);
        while (releaseMatcher.find()) {
            String tag = releaseMatcher.group(1);
            String target = releaseMatcher.group(2);
            String assets = releaseMatcher.group(3);
            Matcher asset = ASSET.matcher(assets);
            if (!asset.find()) continue;
            // The workflow publishes releases named build-<full SHA>, which is the strongest commit binding.
            if (tag.equalsIgnoreCase("build-" + expectedCommit) || target.equalsIgnoreCase(expectedCommit)) {
                return new Release(tag, RELEASE_PAGE + "/tag/" + tag, asset.group(2));
            }
        }
        return null;
    }

    private void report(CommandSender sender, boolean console, String message, boolean warning) {
        if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        if (console) { String plain = message.replaceAll("§.", ""); if (warning) plugin.getLogger().warning(plain); else plugin.getLogger().info(plain); }
    }

    private String fetchMainCommit() throws Exception { Matcher m = SHA.matcher(request(COMMIT_API)); if (!m.find()) throw new IllegalStateException("SHA GitHub introuvable"); return m.group(1); }

    private void downloadUpdate(String url, String commit) throws Exception {
        Path updateDir = plugin.getDataFolder().getParentFile().toPath().resolve("update");
        Files.createDirectories(updateDir);
        Path target = updateDir.resolve("NoxoClaim.jar");
        Path temp = updateDir.resolve("NoxoClaim.jar.download");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", "NoxoClaim-Updater/" + plugin.getDescription().getVersion()).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IllegalStateException("Téléchargement HTTP " + response.statusCode());
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) { in.transferTo(out); }
        if (Files.size(temp) < 10_000) { Files.deleteIfExists(temp); throw new IllegalStateException("JAR téléchargé invalide"); }
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (Exception ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        plugin.getLogger().info("Mise à jour automatique téléchargée : commit " + shortSha(commit) + ".");
    }

    private String getBuildCommit() {
        try (InputStream in = plugin.getResource("build-info.properties")) { if (in == null) return "unknown"; Properties p = new Properties(); p.load(in); return p.getProperty("commit", "unknown").trim(); } catch (Exception e) { return "unknown"; }
    }

    private String request(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "NoxoClaim-Updater/" + plugin.getDescription().getVersion()).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("GitHub HTTP " + response.statusCode());
        return response.body();
    }

    private static String shortSha(String sha) { return sha == null || sha.length() < 7 ? sha : sha.substring(0, 7); }
    private record Release(String version, String url, String downloadUrl) {}
}
