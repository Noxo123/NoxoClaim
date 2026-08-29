package fr.noxodev.noxoclaim.update;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Non-blocking GitHub release checker. */
public final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/Noxo123/NoxoClaim/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BODY = Pattern.compile("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private final NoxoClaim plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public UpdateChecker(NoxoClaim plugin) { this.plugin = plugin; }

    public void check(boolean notifyConsole) {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "NoxoClaim/" + plugin.getDescription().getVersion())
                        .GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IOException("GitHub HTTP " + response.statusCode());

                String json = response.body();
                String latest = group(TAG, json);
                String url = group(URL, json);
                String body = group(BODY, json).replace("\\r", "").replace("\\n", "\n").replace("\\\"", "\"");
                if (latest == null) throw new IOException("Release version missing");
                latest = latest.replaceFirst("^[vV]", "");

                String current = plugin.getDescription().getVersion().replaceFirst("^[vV]", "");
                boolean available = compare(current, latest) < 0;
                UpdateInfo info = new UpdateInfo(available, current, latest, url, body);
                plugin.setUpdateInfo(info);

                if (notifyConsole && available) {
                    plugin.getLogger().warning("Nouvelle version disponible : " + latest + " (actuelle : " + current + ")");
                    if (url != null) plugin.getLogger().warning("Release : " + url);
                }
            } catch (Exception e) {
                if (notifyConsole) plugin.getLogger().fine("Impossible de vérifier les mises à jour : " + e.getMessage());
            }
        });
    }

    private static String group(Pattern p, String input) {
        Matcher m = p.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    /** Semantic-ish comparison supporting versions such as 1.2.3, 1.2.3-beta. */
    public static int compare(String a, String b) {
        String[] aa = a.replaceFirst("^[vV]", "").split("[-+.]", 4);
        String[] bb = b.replaceFirst("^[vV]", "").split("[-+.]", 4);
        for (int i = 0; i < 3; i++) {
            int x = i < aa.length ? number(aa[i]) : 0;
            int y = i < bb.length ? number(bb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return a.compareToIgnoreCase(b);
    }

    private static int number(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D.*", "")); }
        catch (Exception ignored) { return 0; }
    }
}
