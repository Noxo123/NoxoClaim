package fr.noxodev.noxoclaim.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test
    void artifactUrlsUseThePublishedUpdatesChannel() {
        String file = "NoxoClaim-Paper-26.2-944234fe68a8c9fb61797f4cfb83d595e813e352.jar";
        String[] urls = UpdateChecker.buildArtifactUrls(file);

        assertEquals(2, urls.length);
        assertEquals(
                "https://raw.githubusercontent.com/Noxo123/NoxoClaim/updates/assets/" + file,
                urls[0]);
        assertEquals(
                "https://api.github.com/repos/Noxo123/NoxoClaim/contents/assets/" + file + "?ref=updates",
                urls[1]);
        assertTrue(urls[0].contains(file));
        assertTrue(urls[1].contains(file));
        assertFalse(urls[0].contains("release/download"));
        assertFalse(urls[1].contains("release/download"));
    }
}
