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

/** Reliable asynchronous GitHub release checker. */
public final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/Noxo123/NoxoClaim/releases?per_page=20";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
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
                HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "NoxoClaim-UpdateChecker/" + plugin.getDescription().getVersion())
                        .GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("GitHub API HTTP " + response.statusCode());

                String json = response.body();
                Matcher tagMatcher = TAG.matcher(json), urlMatcher = URL.matcher(json), bodyMatcher = BODY.matcher(json), preMatcher = PRERELEASE.matcher(json);
                String latest = null, url = null, body = "";
                while (tagMatcher.find()) {
                    String candidate = normalize(tagMatcher.group(1));
                    boolean pre = preMatcher.find() && Boolean.parseBoolean(preMatcher.group(1));
                    if (!pre && (latest == null || compare(latest, candidate) < 0)) latest = candidate;
                }
                // The release list is sorted newest-first; recover URL/body for the selected tag.
                if (latest == null) throw new IllegalStateException("Aucune release stable trouvée");
                String escaped = Pattern.quote(latest);
                Matcher release = Pattern.compile("\\{[^{}]*?\"tag_name\"\\s*:\\s*\"[vV]?" + escaped + "\"[^{}]*?\\}").matcher(json);
                if (release.find()) {
                    String block = release.group();
                    url = group(URL, block);
                    String rawBody = group(BODY, block);
                    if (rawBody != null) body = unescape(rawBody);
                }

                String current = normalize(plugin.getDescription().getVersion());
                UpdateInfo info = new UpdateInfo(compare(current, latest) < 0, current, latest, url, body);
                plugin.setUpdateInfo(info);
                if (notifyConsole) {
                    if (info.available()) plugin.getLogger().warning("Nouvelle version disponible : " + latest + " (actuelle : " + current + ")" + (url == null ? "" : " | " + url));
                    else plugin.getLogger().info("NoxoClaim est à jour (" + current + ").");
                }
            } catch (Exception e) {
                if (notifyConsole) plugin.getLogger().warning("Vérification des mises à jour impossible : " + e.getMessage());
            } finally { checking = false; }
        });
    }

    private static String group(Pattern pattern, String input) { Matcher m=pattern.matcher(input); return m.find()?m.group(1):null; }
    private static String unescape(String s) { return s.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\"); }
    private static String normalize(String v) { return v == null ? "0.0.0" : v.trim().replaceFirst("^[vV]", "").toLowerCase(Locale.ROOT); }

    public static int compare(String a, String b) {
        String[] aa=normalize(a).split("[-+]",2), bb=normalize(b).split("[-+]",2);
        String[] an=aa[0].split("\\."), bn=bb[0].split("\\.");
        for(int i=0;i<Math.max(an.length,bn.length);i++){int x=part(i<an.length?an[i]:"0"),y=part(i<bn.length?bn[i]:"0");if(x!=y)return Integer.compare(x,y);}
        return 0;
    }
    private static int part(String s){String n=s.replaceAll("\\D.*","");try{return n.isEmpty()?0:Integer.parseInt(n);}catch(Exception e){return 0;}}
}
