package fr.noxodev.noxoclaim.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test
    void artifactUrlsUseThePublishedUpdatesChannel() {
        String file = "NoxoClaim-Paper-26.2-944234fe68a8c9fb61797f4cfb83d595e813e352.jar";
        String[] urls = UpdateChecker.buildArtifactUrls(file);

        assertEquals(3, urls.length);
        assertEquals(
                "https://raw.githubusercontent.com/Noxo123/NoxoClaim/updates/assets/" + file,
                urls[0]);
        assertEquals(
                "https://github.com/Noxo123/NoxoClaim/raw/refs/heads/updates/assets/" + file,
                urls[1]);
        assertEquals(
                "https://api.github.com/repos/Noxo123/NoxoClaim/contents/assets/" + file + "?ref=updates",
                urls[2]);
        for (String url : urls) {
            assertTrue(url.contains(file));
            assertFalse(url.contains("release/download"));
        }
    }
}
