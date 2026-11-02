package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import android.app.Application;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.hackertube.HackerTubeCatalog;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SidebarServiceTest {
    private static final String CHANNEL_ID = "UC_x5XG1OV2P6uZZ5FSM9Ttw";

    @Test
    public void legacyYouTubeSidebarIsReplacedButPinnedChannelsSurvive() {
        Application context = RuntimeEnvironment.getApplication();

        Video pinnedChannel = new Video();
        pinnedChannel.channelId = CHANNEL_ID;
        pinnedChannel.title = "Pinned channel";

        AppPrefs.instance(context).setSidebarData(Helpers.mergeData(
                Arrays.asList(sectionItem(MediaGroup.TYPE_HOME), sectionItem(MediaGroup.TYPE_TRENDING), pinnedChannel),
                MediaGroup.TYPE_HOME, true));

        List<Video> sections = new ArrayList<>(SidebarService.instance(context).getPinnedItems());

        assertTrue(hasSection(sections, MediaGroup.TYPE_HOME));
        assertFalse(hasSection(sections, MediaGroup.TYPE_TRENDING));
        assertTrue(hasChannel(sections, CHANNEL_ID));

        for (int i = 0; i < HackerTubeCatalog.AREA_IDS.length; i++) {
            assertTrue("Missing " + HackerTubeCatalog.AREA_IDS[i], hasSection(sections, HackerTubeCatalog.sectionId(i)));
        }
    }

    private static Video sectionItem(int sectionId) {
        Video item = new Video();
        item.sectionId = sectionId;

        return item;
    }

    private static boolean hasSection(List<Video> items, int sectionId) {
        return Helpers.findFirst(items, item -> item.sectionId == sectionId) != null;
    }

    private static boolean hasChannel(List<Video> items, String channelId) {
        return Helpers.findFirst(items, item -> channelId.equals(item.channelId)) != null;
    }
}
