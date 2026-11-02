package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hacker News comments for a video, served by hackertube.org. One request returns every labeled comment,
 * so there is no next page key.
 */
public final class HackerTubeComments {
    private static final String TAG = HackerTubeComments.class.getSimpleName();
    private static final String API_URL = "https://hackertube.org/api/video?vid=";
    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "SmartHackerTube";
    private static final String LINE_BREAK = "\n\n";
    private static final Map<String, String> STANCES = new HashMap<>();

    static {
        STANCES.put("strong_rec", "Strongly recommends");
        STANCES.put("rec", "Recommends");
        STANCES.put("reference", "Cites");
        STANCES.put("joke", "Joke");
        STANCES.put("critical", "Critical");
    }

    private HackerTubeComments() {
    }

    /**
     * Blocking call. The caller must run it on a background thread.<br/>
     * Returns null when the request or the answer fails.
     */
    public static CommentGroup load(String videoId) {
        if (TextUtils.isEmpty(videoId)) {
            return null;
        }

        try {
            return parse(read(API_URL + URLEncoder.encode(videoId, "UTF-8")));
        } catch (Exception e) {
            Log.e(TAG, "Cannot load HN comments for %s: %s", videoId, e.getMessage());
            return null;
        }
    }

    static CommentGroup parse(String response) throws JSONException {
        JSONArray comments = new JSONObject(response).optJSONArray("comments");
        List<CommentItem> items = new ArrayList<>();

        if (comments != null) {
            for (int i = 0; i < comments.length(); i++) {
                items.add(new CommentItemImpl(comments.getJSONObject(i)));
            }
        }

        return new CommentGroupImpl(items);
    }

    private static String read(String urlSpec) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlSpec).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected response " + connection.getResponseCode());
            }

            StringBuilder result = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }

            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static final class CommentGroupImpl implements CommentGroup {
        private final List<CommentItem> mComments;

        private CommentGroupImpl(List<CommentItem> comments) {
            mComments = comments;
        }

        @Override
        public List<CommentItem> getComments() {
            return mComments;
        }

        @Override
        public String getNextCommentsKey() {
            return null;
        }
    }

    private static final class CommentItemImpl implements CommentItem {
        private final String mId;
        private final String mMessage;
        private final String mAuthorName;
        private final String mPublishedDate;

        private CommentItemImpl(JSONObject comment) {
            mId = String.valueOf(comment.optInt("id"));
            mAuthorName = comment.optString("author", null);
            mPublishedDate = comment.optString("ym", null);
            mMessage = buildMessage(comment);
        }

        private static String buildMessage(JSONObject comment) {
            StringBuilder message = new StringBuilder(comment.optString("text", ""));
            String why = comment.optString("why", null);

            if (!TextUtils.isEmpty(why)) {
                message.append(LINE_BREAK).append(why);
            }

            String stance = STANCES.get(comment.optString("stance", ""));
            String story = comment.optString("story", null);

            if (stance != null || !TextUtils.isEmpty(story)) {
                message.append(LINE_BREAK);

                if (stance != null) {
                    message.append(stance);
                }

                if (!TextUtils.isEmpty(story)) {
                    message.append(stance != null ? " · in “" : "In “").append(story).append("”");
                }
            }

            return message.toString();
        }

        @Override
        public String getId() {
            return mId;
        }

        @Override
        public String getMessage() {
            return mMessage;
        }

        @Override
        public String getAuthorName() {
            return mAuthorName;
        }

        @Override
        public String getAuthorPhoto() {
            return null;
        }

        @Override
        public String getPublishedDate() {
            return mPublishedDate;
        }

        @Override
        public String getNestedCommentsKey() {
            return null;
        }

        @Override
        public boolean isLiked() {
            return false;
        }

        @Override
        public String getLikeCount() {
            return null;
        }

        @Override
        public String getReplyCount() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
