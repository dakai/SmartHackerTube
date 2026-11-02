package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HackerTubeDataTest {
    @Test
    public void likeAndDislikeReplaceEachOther() {
        Application context = RuntimeEnvironment.getApplication();
        HackerTubeData data = HackerTubeData.instance(context);

        assertTrue(data.toggleLike("abc"));
        assertTrue(data.isLiked("abc"));

        assertTrue(data.toggleDislike("abc"));
        assertTrue(data.isDisliked("abc"));
        assertFalse("A dislike drops the like", data.isLiked("abc"));

        assertFalse("Second press removes the dislike", data.toggleDislike("abc"));
        assertFalse(data.isDisliked("abc"));
    }
}
