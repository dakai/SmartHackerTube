package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HackerTubeCatalogTest {
    private static final long LOAD_TIMEOUT_MS = 10_000;

    @Test
    public void bundledCatalogIsParsedIntoPlayableRows() {
        HackerTubeCatalog catalog = loadedCatalog();

        assertRowShape(catalog.topPicks());
        assertRowShape(catalog.mostRecommended());
        assertRowShape(catalog.recent());
        assertRowShape(catalog.evergreen());
    }

    @Test
    public void catalogKnowsWhichVideosHaveHackerNewsComments() {
        HackerTubeCatalog catalog = loadedCatalog();
        String videoId = catalog.topPicks().get(0).videoId;

        assertTrue(catalog.hasVideo(videoId));
        assertTrue(!catalog.hasVideo("not-a-hackertube-video"));
        assertTrue(!catalog.hasVideo(null));
    }

    @Test
    public void everyAreaSectionHasVideos() {
        HackerTubeCatalog catalog = loadedCatalog();

        for (int i = 0; i < HackerTubeCatalog.AREA_IDS.length; i++) {
            List<Video> videos = catalog.byArea(i);

            assertTrue("Empty area section: " + HackerTubeCatalog.AREA_IDS[i], videos.size() > 0);
            assertEquals("wrong videoId url " + videos.get(0).videoId,
                    "https://i.ytimg.com/vi/" + videos.get(0).videoId + "/hqdefault.jpg", videos.get(0).cardImageUrl);
        }
    }

    @Test
    public void areaSectionIdsSitOutsideMediaGroupTypes() {
        assertTrue(HackerTubeCatalog.isAreaSection(HackerTubeCatalog.sectionId(0)));
        assertTrue(HackerTubeCatalog.isAreaSection(HackerTubeCatalog.sectionId(HackerTubeCatalog.AREA_IDS.length - 1)));
        assertTrue(!HackerTubeCatalog.isAreaSection(HackerTubeCatalog.sectionId(HackerTubeCatalog.AREA_IDS.length)));
        assertTrue(!HackerTubeCatalog.isAreaSection(0));
        assertEquals(HackerTubeCatalog.AREA_IDS.length, HackerTubeCatalog.AREA_TITLES.length);
    }

    private static void assertRowShape(List<Video> videos) {
        assertTrue(videos.size() > 0);
        assertTrue(videos.size() <= 50);

        Set<String> ids = new HashSet<>();

        for (Video video : videos) {
            assertNotNull(video.videoId);
            assertNotNull(video.title);
            assertNotNull(video.getSecondTitle());
            assertTrue(ids.add(video.videoId));
        }
    }

    private static HackerTubeCatalog loadedCatalog() {
        Application context = RuntimeEnvironment.getApplication();
        HackerTubeCatalog catalog = HackerTubeCatalog.instance(context);

        catalog.load(null);

        long deadline = System.currentTimeMillis() + LOAD_TIMEOUT_MS;

        while (!catalog.isLoaded() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.yield();
        }

        assertTrue("Catalog asset wasn't parsed", catalog.isLoaded());

        return catalog;
    }
}
