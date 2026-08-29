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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Secure commit-based updater using the dedicated GitHub updates branch. */
public final class UpdateChecker {
    private static final String UPDATE_BASE = "https://raw.githubusercontent.com/Noxo123/NoxoClaim/updates/";
    private static final String MANIFEST_URL = UPDATE_BASE + "update.json";
    private static final Pattern COMMIT = Pattern.compile("\\\"commit\\\"\\s*:\\s*\\\"([0-9a-fA-F]{40})\\\"");
    private static final Pattern DOWNLOAD = Pattern.compile("\\\"(26\\\\.2|26\\\\.1\\\\.2)\\\"\\s*:\\s*\\{\\s*\\\"file\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-fA-F]{64})\\\"");

    private final NoxoClaim plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private volatile boolean checking;

    public UpdateChecker(NoxoClaim plugin) { this.plugin = plugin; }

    public void check(boolean notifyConsole) { checkInternal(notifyConsole, null); }

    /** Manual check used by /claimadmin update. */
    public void checkManual(CommandSender sender) {
        if (checking) {
            sender.sendMessage("§e[NoxoClaim] Une vérification des mises à jour est déjà en cours.");
            return;
        }
        sender.sendMessage("§b[NoxoClaim] Vérification du canal de mise à jour...");
        checkInternal(true, sender);
    }

    private void checkInternal(boolean notifyConsole, CommandSender sender) {
        if (checking) {
            if (sender != null) sender.sendMessage("§e[NoxoClaim] Une vérification est déjà en cours.");
            return;
        }
        if (sender == null && !plugin.getConfig().getBoolean("updates.enabled", true)) return;

        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Manifest manifest = fetchManifest();
                String localCommit = getBuildCommit();

                // A downloaded update is already staged. Never download the same
                // build again on every startup while waiting for the restart.
                Path updateDir = updateDirectory();
                Path pending = updateDir.resolve("NoxoClaim.pending");
                if (Files.isRegularFile(pending)) {
                    String pendingCommit = Files.readString(pending).trim();
                    if (manifest.commit.equalsIgnoreCase(pendingCommit)) {
                        report(sender, notifyConsole,
                                "§e[NoxoClaim] Mise à jour §f" + shortSha(manifest.commit)
                                        + "§e déjà préparée. Redémarrez le serveur pour l'appliquer.", false);
                        return;
                    }
                    Files.deleteIfExists(pending);
                }

                if (manifest.commit.equalsIgnoreCase(localCommit)) {
                    plugin.setUpdateInfo(UpdateInfo.upToDate(
                            plugin.getDescription().getVersion(),
                            shortSha(manifest.commit),
                            UPDATE_BASE));
                    report(sender, notifyConsole,
                            "§a[NoxoClaim] NoxoClaim est à jour (§f" + shortSha(manifest.commit) + "§a).", false);
                    return;
                }

                String paperVersion = detectSupportedPaperVersion();
                if (paperVersion == null) {
                    report(sender, notifyConsole,
                            "§e[NoxoClaim] Une mise à jour existe, mais aucune build compatible avec cette version de Paper n'est publiée.", false);
                    return;
                }

                Artifact artifact = manifest.artifact(paperVersion);
                if (artifact == null) {
                    report(sender, notifyConsole,
                            "§e[NoxoClaim] Aucun artefact compatible pour Paper " + paperVersion + ".", false);
                    return;
                }

                plugin.setUpdateInfo(new UpdateInfo(
                        true,
                        plugin.getDescription().getVersion(),
                        "build-" + shortSha(manifest.commit),
                        UPDATE_BASE,
                        "Commit " + shortSha(manifest.commit)
                ));

                if (!plugin.getConfig().getBoolean("updates.auto-update", true)) {
                    report(sender, notifyConsole,
                            "§e[NoxoClaim] Nouvelle build disponible : §f" + shortSha(manifest.commit), true);
                    return;
                }

                downloadAndVerify(artifact, manifest.commit);
                Files.createDirectories(updateDir);
                Files.writeString(pending, manifest.commit);

                report(sender, true,
                        "§a[NoxoClaim] ✓ Mise à jour vérifiée et préparée (§f" + shortSha(manifest.commit)
                                + "§a). Redémarrez le serveur pour l'appliquer.", false);
            } catch (Exception e) {
                report(sender, notifyConsole,
                        "§c[NoxoClaim] Mise à jour impossible : " + safeMessage(e), false);
            } finally {
                checking = false;
            }
        });
    }

    private Manifest fetchManifest() throws Exception {
        String json = request(MANIFEST_URL);
        Matcher commitMatcher = COMMIT.matcher(json);
        if (!commitMatcher.find()) throw new IllegalStateException("commit absent du manifeste");

        Manifest manifest = new Manifest(commitMatcher.group(1));
        Matcher artifactMatcher = DOWNLOAD.matcher(json);
        while (artifactMatcher.find()) {
            manifest.add(new Artifact(artifactMatcher.group(1), artifactMatcher.group(2), artifactMatcher.group(3)));
        }
        if (manifest.artifacts.isEmpty()) throw new IllegalStateException("aucun artefact dans le manifeste");
        return manifest;
    }

    private void downloadAndVerify(Artifact artifact, String expectedCommit) throws Exception {
        if (artifact.file.contains("..") || artifact.file.contains("/") || artifact.file.contains("\\")) {
            throw new IllegalStateException("nom de fichier d'artefact refusé");
        }
        if (!artifact.file.contains(expectedCommit)) {
            throw new IllegalStateException("artefact non lié au commit demandé");
        }

        Path updateDir = updateDirectory();
        Files.createDirectories(updateDir);
        Path temp = updateDir.resolve("NoxoClaim.jar.download");
        Path target = updateDir.resolve("NoxoClaim.jar");
        Files.deleteIfExists(temp);

        String url = UPDATE_BASE + "assets/" + artifact.file;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", "NoxoClaim-Updater/" + plugin.getDescription().getVersion())
                .header("Accept", "application/octet-stream")
                .GET().build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IllegalStateException("téléchargement HTTP " + response.statusCode());

        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
            in.transferTo(out);
        }

        long size = Files.size(temp);
        if (size < 10_000 || size > 100_000_000L) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("taille du JAR téléchargé invalide");
        }

        String actualSha256 = sha256(temp);
        if (!actualSha256.equalsIgnoreCase(artifact.sha256)) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("SHA-256 invalide : le fichier ne correspond pas au manifeste");
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        plugin.getLogger().info("Mise à jour préparée : " + artifact.file + " (SHA-256 vérifié).");
    }

    private Path updateDirectory() {
        return plugin.getDataFolder().getParentFile().toPath().resolve("update");
    }

    private String detectSupportedPaperVersion() {
        String version = Bukkit.getMinecraftVersion();
        if (version == null) return null;
        version = version.toLowerCase(Locale.ROOT).trim();
        if (version.startsWith("26.2")) return "26.2";
        if (version.startsWith("26.1.2")) return "26.1.2";
        return null;
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
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "NoxoClaim-Updater/" + plugin.getDescription().getVersion())
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("GitHub HTTP " + response.statusCode());
        return response.body();
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format("%02x", b));
        return result.toString();
    }

    private void report(CommandSender sender, boolean console, String message, boolean warning) {
        if (sender != null) Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        if (console) {
            String plain = message.replaceAll("§.", "");
            if (warning) plugin.getLogger().warning(plain); else plugin.getLogger().info(plain);
        }
    }

    private static String shortSha(String sha) { return sha == null || sha.length() < 7 ? sha : sha.substring(0, 7); }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static final class Manifest {
        private final String commit;
        private final Map<String, Artifact> artifacts = new HashMap<>();
        private Manifest(String commit) { this.commit = commit; }
        private void add(Artifact artifact) { artifacts.put(artifact.paper, artifact); }
        private Artifact artifact(String paper) { return artifacts.get(paper); }
    }

    private record Artifact(String paper, String file, String sha256) {}
}
