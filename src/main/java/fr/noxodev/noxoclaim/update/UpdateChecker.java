package fr.noxodev.noxoclaim.update;

import fr.noxodev.noxoclaim.NoxoClaim;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reliable asynchronous GitHub update checker. */
public final class UpdateChecker {
    private static final String RELEASES_API = "https://api.github.com/repos/Noxo123/NoxoClaim/releases?per_page=20";
    private static final String TAGS_API = "https://api.github.com/repos/Noxo123/NoxoClaim/tags?per_page=50";
    private static final Pattern OBJECT = Pattern.compile("\\{(?:[^{}]|\\{[^{}]*\\})*\\}");
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BODY = Pattern.compile("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern PRERELEASE = Pattern.compile("\"prerelease\"\\s*:\\s*(true|false)");
    private final NoxoClaim plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean checking;

    public UpdateChecker(NoxoClaim plugin) { this.plugin = plugin; }

    public void check(boolean notifyConsole) {
        if (!plugin.getConfig().getBoolean("updates.enabled", true) || checking) return;
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Release latest = findLatestRelease();
                String current = normalize(plugin.getDescription().getVersion());
                if (latest == null) {
                    UpdateInfo info = new UpdateInfo(false, current, current, null, "");
                    plugin.setUpdateInfo(info);
                    if (notifyConsole) plugin.getLogger().info("Aucune version publiée sur GitHub pour le moment (" + current + ").");
                    return;
                }
                boolean available = compare(current, latest.version) < 0;
                UpdateInfo info = new UpdateInfo(available, current, latest.version, latest.url, latest.body);
                plugin.setUpdateInfo(info);
                if (notifyConsole) {
                    if (available) plugin.getLogger().warning("Nouvelle version disponible : " + latest.version + " (actuelle : " + current + ")" + (latest.url == null ? "" : " | " + latest.url));
                    else plugin.getLogger().info("NoxoClaim est à jour (" + current + ").");
                }
            } catch (Exception e) {
                if (notifyConsole) plugin.getLogger().warning("Vérification des mises à jour impossible : GitHub inaccessible (" + e.getClass().getSimpleName() + ").");
            } finally { checking = false; }
        });
    }

    private Release findLatestRelease() throws Exception {
        String releases = request(RELEASES_API);
        Release best = null;
        Matcher objects = OBJECT.matcher(releases);
        while (objects.find()) {
            String block = objects.group();
            Matcher pre = PRERELEASE.matcher(block);
            if (pre.find() && Boolean.parseBoolean(pre.group(1))) continue;
            String tag = group(TAG, block);
            if (tag == null) continue;
            String version = normalize(tag);
            if (!isVersion(version)) continue;
            String url = group(URL, block);
            String rawBody = group(BODY, block);
            String body = rawBody == null ? "" : unescape(rawBody);
            if (best == null || compare(best.version, version) < 0) best = new Release(version, url, body);
        }
        if (best != null) return best;

        // A repository may use tags without publishing GitHub Releases.
        String tags = request(TAGS_API);
        objects = OBJECT.matcher(tags);
        while (objects.find()) {
            String block = objects.group();
            String tag = group(NAME, block);
            if (tag == null) continue;
            String version = normalize(tag);
            if (!isVersion(version)) continue;
            String url = "https://github.com/Noxo123/NoxoClaim/releases/tag/" + tag;
            if (best == null || compare(best.version, version) < 0) best = new Release(version, url, "");
        }
        return best;
    }

    private String request(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "NoxoClaim-UpdateChecker/" + plugin.getDescription().getVersion()).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return response.body();
    }

    private static String group(Pattern p, String input) { Matcher m=p.matcher(input); return m.find()?m.group(1):null; }
    private static String unescape(String s) { return s.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\"); }
    private static String normalize(String v) { return v == null ? "0.0.0" : v.trim().replaceFirst("^[vV]", "").split("\\s+")[0].toLowerCase(Locale.ROOT); }
    private static boolean isVersion(String v) { return v.matches("\\d+(?:\\.\\d+){0,3}(?:[-+].*)?"); }
    public static int compare(String a,String b){String[] aa=normalize(a).split("[-+]",2),bb=normalize(b).split("[-+]",2);String[] an=aa[0].split("\\."),bn=bb[0].split("\\.");for(int i=0;i<Math.max(an.length,bn.length);i++){int x=part(i<an.length?an[i]:"0"),y=part(i<bn.length?bn[i]:"0");if(x!=y)return Integer.compare(x,y);}return 0;}
    private static int part(String s){try{return Integer.parseInt(s.replaceAll("\\D.*",""));}catch(Exception e){return 0;}}
    private record Release(String version,String url,String body) {}
}
