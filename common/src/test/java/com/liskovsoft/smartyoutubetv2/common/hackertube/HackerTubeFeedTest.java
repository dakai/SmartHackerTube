package com.liskovsoft.smartyoutubetv2.common.hackertube;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HackerTubeFeedTest {
    private static final String CLASSIC = "An HN classic";
    private static final String EXPLORE = "Explore: Games & Game Development";

    @Test
    public void feedRowsKeepRankingAndUseTheReasonAsTheTitle() {
        Map<String, List<Video>> rows = HackerTubeFeed.parse(items(24, CLASSIC, 6, EXPLORE), "views", "For you");

        assertEquals(2, rows.size());

        List<String> titles = new ArrayList<>(rows.keySet());

        assertEquals(CLASSIC, titles.get(0));
        assertEquals(EXPLORE, titles.get(1));
        assertEquals(24, rows.get(CLASSIC).size());
        assertEquals(6, rows.get(EXPLORE).size());
    }

    @Test
    public void feedItemCarriesPosterDurationAndReason() {
        Video video = HackerTubeFeed.parse(items(1, CLASSIC, 0, null), "views", "For you").get("For you").get(0);

        assertEquals("fNk_zzaMoSs", video.videoId);
        assertEquals("Vectors | Chapter 1, Essence of linear algebra", video.title);
        assertEquals("3Blue1Brown", video.author);
        assertNotNull(video.cardImageUrl);
        assertEquals(592_000L, video.getDurationMs());
        assertEquals("9:52", video.badge);
        assertEquals("3Blue1Brown • 13M views", video.secondTitle);
        assertTrue(video.description.contains("go-to recommendation for learning linear algebra"));
        assertTrue(video.description.contains(CLASSIC));
    }

    @Test
    public void smallReasonsArePooledIntoTheFallbackRow() {
        Map<String, List<Video>> rows = HackerTubeFeed.parse(items(20, CLASSIC, 2, EXPLORE), "views", "For you");

        assertEquals(2, rows.size());
        assertEquals(20, rows.get(CLASSIC).size());
        assertEquals("Two sparse picks share one row", 2, rows.get("For you").size());
    }

    @Test
    public void brokenAnswerGivesNoRows() {
        assertTrue(HackerTubeFeed.parse("<html>oops</html>", "views", "For you").isEmpty());
        assertTrue(HackerTubeFeed.parse("[]", "views", "For you").isEmpty());
    }

    private static String items(int firstCount, String firstReason, int secondCount, String secondReason) {
        List<String> items = new ArrayList<>();

        for (int i = 0; i < firstCount + secondCount; i++) {
            String reason = i < firstCount ? firstReason : secondReason;
            items.add(item("item" + i, reason));
        }

        return "[" + String.join(",", items) + "]";
    }

    private static String item(String videoId, String reason) {
        return "{\"vid\":\"fNk_zzaMoSs\",\"title\":\"Vectors | Chapter 1, Essence of linear algebra\","
                + "\"channel\":\"3Blue1Brown\",\"thumbnail\":\"https://i.ytimg.com/vi/fNk_zzaMoSs/mqdefault.jpg\","
                + "\"duration_s\":592,\"views\":12988516,"
                + "\"summary\":\"Intro to vectors.\",\"why_hn\":\"HN's go-to recommendation for learning linear algebra.\","
                + "\"reason\":\"" + reason + "\"}".replace("fNk_zzaMoSs", videoId);
    }
}
