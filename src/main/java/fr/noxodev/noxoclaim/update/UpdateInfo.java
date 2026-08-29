package fr.noxodev.noxoclaim.update;

public record UpdateInfo(boolean available, String currentVersion, String latestVersion, String releaseUrl, String changelog) {
    public static UpdateInfo upToDate(String current, String latest, String url) {
        return new UpdateInfo(false, current, latest, url, "");
    }
}
