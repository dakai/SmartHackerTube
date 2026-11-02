package com.liskovsoft.smartyoutubetv2.common.hackertube;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HackerTubeCommentsTest {
    private static final String RESPONSE = "{\"video\":{\"vid\":\"fNk_zzaMoSs\"},\"comments\":["
            + "{\"id\":48946645,\"author\":\"zvmaz\",\"ym\":\"2026-07\",\"story\":\"Immersive Linear Algebra Book\","
            + "\"stance\":\"strong_rec\",\"why\":\"Gave intuitive understanding\","
            + "\"text\":\"3Blue1Brown opened my eyes on linear algebra.\"},"
            + "{\"id\":45800005,\"author\":\"seanhunter\",\"ym\":\"2025-11\",\"story\":\"An Illustrated Introduction\","
            + "\"stance\":\"reference\",\"text\":\"Visual intuition building series.\"}]}";

    @Test
    public void hnCommentsBecomeReadableItems() throws Exception {
        CommentGroup group = HackerTubeComments.parse(RESPONSE);

        assertEquals(2, group.getComments().size());
        assertNull("One page only", group.getNextCommentsKey());

        CommentItem first = group.getComments().get(0);

        assertEquals("48946645", first.getId());
        assertEquals("zvmaz", first.getAuthorName());
        assertEquals("2026-07", first.getPublishedDate());
        assertNull("HN has no replies or likes", first.getNestedCommentsKey());
        assertNull(first.getLikeCount());

        String message = first.getMessage();

        assertTrue(message, message.startsWith("3Blue1Brown opened my eyes on linear algebra."));
        assertTrue(message, message.contains("Gave intuitive understanding"));
        assertTrue(message, message.contains("Strongly recommends"));
        assertTrue(message, message.contains("Immersive Linear Algebra Book"));
    }

    @Test
    public void missingOptionalFieldsDoNotBreakTheMessage() throws Exception {
        CommentItem second = HackerTubeComments.parse(RESPONSE).getComments().get(1);

        assertEquals("Visual intuition building series.\n\nCites · in “An Illustrated Introduction”", second.getMessage());
    }
}
