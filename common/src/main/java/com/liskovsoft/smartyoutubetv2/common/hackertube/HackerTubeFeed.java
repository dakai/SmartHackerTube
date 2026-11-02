package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.liskovsoft.googlecommon.common.helpers.ServiceHelper;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * hackertube.org personalization. The site keeps likes, dislikes and seen videos in the browser and asks the
 * server for a mixed feed: {@code /api/feed?like=..&dislike=..&seen=..&seed=..&offset=..&n=..}.<br/>
 * Every item arrives with a reason ("An HN classic", "Because you liked X", "Explore: ...") and the app turns
 * those reasons into TV rows, which keeps the server ranking and still gives readable row titles.
 */
public final class HackerTubeFeed {
    private static final String TAG = HackerTubeFeed.class.getSimpleName();
    private static final String API_URL = "https://hackertube.org/api/feed";
    private static final int TIMEOUT_MS = 15_000;
    private static final int FEED_SIZE = 120;
    private static final int MIN_ROW_SIZE = 5;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final long STALE_MS = 60 * 60 * 1_000L;
    private static final long RETRY_MS = 5 * 60 * 1_000L;
    private static final String BG_URL = "https://i.ytimg.com/vi/%s/maxresdefault.jpg";
    private static final String USER_AGENT = "SmartHackerTube";
    private static HackerTubeFeed sInstance;
    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Map<String, List<Video>> mRows = new LinkedHashMap<>();
    private final String mSeed = String.valueOf((int) (Math.random() * 1_000_000_000));
    private long mLastUpdateMs;
    private long mLastAttemptMs;
    private boolean mLoading;

    private HackerTubeFeed(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized HackerTubeFeed instance(Context context) {
        if (sInstance == null) {
            sInstance = new HackerTubeFeed(context);
        }

        return sInstance;
    }

    /**
     * Row title to videos, ranking preserved.
     */
    public Map<String, List<Video>> getRows() {
        return Collections.unmodifiableMap(mRows);
    }

    /**
     * Fetch the feed when it is missing or older than an hour. Callback runs on the main thread.
     */
    public void refreshIfStale(Runnable onLoaded) {
        long now = System.currentTimeMillis();

        if (!mRows.isEmpty() && now - mLastUpdateMs < STALE_MS) {
            return;
        }

        // Offline retry guard: don't hammer the server on every home focus.
        if (now - mLastAttemptMs < RETRY_MS) {
            return;
        }

        refresh(onLoaded);
    }

    public void refresh(Runnable onLoaded) {
        if (mLoading) {
            return;
        }

        mLoading = true;
        mLastAttemptMs = System.currentTimeMillis();

        new Thread(() -> {
            String response = null;

            try {
                response = read(buildUrl());
            } catch (Exception e) {
                Log.e(TAG, "Cannot load the feed: %s", e.getMessage());
            }

            String finalResponse = response;
            mHandler.post(() -> apply(finalResponse, onLoaded));
        }).start();
    }

    /**
     * Make the next {@link #refreshIfStale} hit the server, e.g. after a like.
     */
    public void invalidate() {
        mLastUpdateMs = 0;
        mLastAttemptMs = 0;
    }

    private void apply(String response, Runnable onLoaded) {
        mLoading = false;

        if (response != null) {
            Map<String, List<Video>> rows = parse(response, mContext.getString(R.string.ht_views),
                    mContext.getString(R.string.ht_for_you));

            // A broken answer must not wipe the rows the user is looking at.
            if (!rows.isEmpty()) {
                mRows.clear();
                mRows.putAll(rows);
                mLastUpdateMs = System.currentTimeMillis();
            }
        }

        if (onLoaded != null) {
            onLoaded.run();
        }
    }

    private String buildUrl() throws IOException {
        HackerTubeData data = HackerTubeData.instance(mContext);

        return API_URL
                + "?like=" + encode(data.getLikes())
                + "&dislike=" + encode(data.getDislikes())
                + "&seen=" + encode(data.getSeen())
                + "&seed=" + mSeed
                + "&offset=0&n=" + FEED_SIZE;
    }

    private static String encode(List<String> ids) throws IOException {
        return URLEncoder.encode(TextUtils.join(",", ids), "UTF-8");
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

    /**
     * Parse a feed answer into TV rows: ranking preserved, one row per reason.
     */
    static Map<String, List<Video>> parse(String response, String viewsLabel, String fallbackTitle) {
        List<Item> items = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(response);

            for (int i = 0; i < array.length(); i++) {
                Item item = Item.from(array.getJSONObject(i), viewsLabel);

                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot parse the feed: %s", e.getMessage());
        }

        return toRows(items, fallbackTitle);
    }

    /**
     * Group the ranked feed by reason. Reasons with only a couple of picks (the "Explore: ..." ones)
     * are pooled into one row, so no row looks empty on a TV screen.
     */
    private static Map<String, List<Video>> toRows(List<Item> items, String fallbackTitle) {
        Map<String, List<Video>> grouped = new LinkedHashMap<>();

        for (Item item : items) {
            String reason = TextUtils.isEmpty(item.reason) ? fallbackTitle : item.reason;
            List<Video> videos = grouped.get(reason);

            if (videos == null) {
                videos = new ArrayList<>();
                grouped.put(reason, videos);
            }

            videos.add(item.video);
        }

        Map<String, List<Video>> rows = new LinkedHashMap<>();
        List<Video> leftovers = new ArrayList<>();

        for (Map.Entry<String, List<Video>> group : grouped.entrySet()) {
            if (group.getValue().size() >= MIN_ROW_SIZE) {
                rows.put(shorten(group.getKey()), group.getValue());
            } else {
                leftovers.addAll(group.getValue());
            }
        }

        if (!leftovers.isEmpty()) {
            rows.put(fallbackTitle, leftovers);
        }

        return rows;
    }

    private static String shorten(String title) {
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH - 1) + "…" : title;
    }

    private static final class Item {
        private final Video video;
        private final String reason;

        private Item(Video video, String reason) {
            this.video = video;
            this.reason = reason;
        }

        private static Item from(JSONObject json, String viewsLabel) {
            String videoId = json.optString("vid", null);

            if (TextUtils.isEmpty(videoId)) {
                return null;
            }

            Video video = new Video();
            video.videoId = videoId;
            video.title = json.optString("title", "");
            video.author = json.optString("channel", "");
            video.cardImageUrl = json.optString("thumbnail", null);
            video.bgImageUrl = String.format(BG_URL, videoId);
            video.secondTitle = buildSecondTitle(json, viewsLabel);
            video.description = buildDescription(json);
            video.setDurationMs(json.optInt("duration_s", 0) * 1_000L);

            if (video.getDurationMs() > 0) {
                video.badge = ServiceHelper.millisToTimeText(video.getDurationMs());
            }

            return new Item(video, json.optString("reason", null));
        }
    }

    private static String buildSecondTitle(JSONObject item, String viewsLabel) {
        String channel = item.optString("channel", "");
        int views = item.optInt("views", 0);

        if (views <= 0) {
            return channel;
        }

        return channel + " • " + shortCount(views) + " " + viewsLabel;
    }

    private static String buildDescription(JSONObject item) {
        List<String> lines = new ArrayList<>();

        addLine(lines, item.optString("summary", null));
        addLine(lines, item.optString("why_hn", null));
        addLine(lines, item.optString("reason", null));

        return TextUtils.join("\n\n", lines);
    }

    private static void addLine(List<String> lines, String line) {
        if (!TextUtils.isEmpty(line)) {
            lines.add(line);
        }
    }

    private static String shortCount(int count) {
        if (count >= 1_000_000) {
            return String.format(Locale.US, "%.0fM", count / 1_000_000f);
        }

        if (count >= 1_000) {
            return String.format(Locale.US, "%.0fK", count / 1_000f);
        }

        return String.valueOf(count);
    }

}
