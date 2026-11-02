package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HackerTube's catalog (https://hackertube.org/data), bundled as a JSON Lines asset.<br/>
 * Records are YouTube ids, so playback still goes through the regular media service.<br/>
 * Rebuild the asset with {@code tools/hackertube/build_assets.py}.
 */
public final class HackerTubeCatalog {
    private static final String TAG = HackerTubeCatalog.class.getSimpleName();
    private static final String ASSET = "hackertube/videos.jsonl";
    private static final String THUMB_URL = "https://i.ytimg.com/vi/%s/hqdefault.jpg";
    private static final String BG_URL = "https://i.ytimg.com/vi/%s/maxresdefault.jpg";
    private static final int HOME_ROW_LIMIT = 50;
    /**
     * Section ids live next to {@link com.liskovsoft.mediaserviceinterfaces.data.MediaGroup} types (0..22),
     * so they get their own range.
     */
    public static final int SECTION_ID_BASE = 30;
    /**
     * Area ids and titles, in the order the data was exported. They match {@code area} in videos.jsonl.gz.
     */
    public static final String[] AREA_IDS = {
            "software", "systems-security", "ai-data", "math-theory", "hardware", "computing-history-culture",
            "design-graphics-media", "business-economics", "politics-history-society", "physics-space",
            "life-sciences-health", "earth-energy-environment", "engineering-making", "games",
            "arts-entertainment", "ideas-education"
    };
    public static final int[] AREA_TITLES = {
            R.string.ht_area_software, R.string.ht_area_systems_security, R.string.ht_area_ai_data,
            R.string.ht_area_math_theory, R.string.ht_area_hardware, R.string.ht_area_computing_history_culture,
            R.string.ht_area_design_graphics_media, R.string.ht_area_business_economics,
            R.string.ht_area_politics_history_society, R.string.ht_area_physics_space,
            R.string.ht_area_life_sciences_health, R.string.ht_area_earth_energy_environment,
            R.string.ht_area_engineering_making, R.string.ht_area_games, R.string.ht_area_arts_entertainment,
            R.string.ht_area_ideas_education
    };
    private static final Comparator<Entry> BY_SCORE = (first, second) -> {
        int result = Float.compare(second.score, first.score);
        return result != 0 ? result : first.title.compareTo(second.title);
    };
    private static final Comparator<Entry> BY_RECOMMENDERS = (first, second) -> {
        int result = Integer.compare(second.recommenders, first.recommenders);
        return result != 0 ? result : BY_SCORE.compare(first, second);
    };
    private static final Comparator<Entry> BY_LAST_YEAR = (first, second) -> {
        int result = Integer.compare(second.lastYear, first.lastYear);
        return result != 0 ? result : BY_SCORE.compare(first, second);
    };
    private static HackerTubeCatalog sInstance;
    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> mCallbacks = new ArrayList<>();
    private final Map<String, List<Entry>> mByArea = new LinkedHashMap<>();
    private final Set<String> mVideoIds = new HashSet<>();
    private List<Entry> mByScore = Collections.emptyList();
    private List<Entry> mByRecommenders = Collections.emptyList();
    private List<Entry> mByLastYear = Collections.emptyList();
    private List<Entry> mEvergreen = Collections.emptyList();
    private boolean mLoading;
    private boolean mLoaded;

    private HackerTubeCatalog(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized HackerTubeCatalog instance(Context context) {
        if (sInstance == null) {
            sInstance = new HackerTubeCatalog(context);
        }

        return sInstance;
    }

    public static boolean isAreaSection(int sectionId) {
        return sectionId >= SECTION_ID_BASE && sectionId < SECTION_ID_BASE + AREA_IDS.length;
    }

    public static int sectionId(int areaIndex) {
        return SECTION_ID_BASE + areaIndex;
    }

    /**
     * Parse the bundled asset once. Callback runs on the main thread.
     */
    public void load(Runnable onLoaded) {
        if (mLoaded) {
            if (onLoaded != null) {
                onLoaded.run();
            }
            return;
        }

        if (onLoaded != null) {
            mCallbacks.add(onLoaded);
        }

        if (mLoading) {
            return;
        }

        mLoading = true;

        new Thread(this::loadSync).start();
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    private void loadSync() {
        List<Entry> entries = parse();

        mHandler.post(() -> apply(entries));
    }

    private void apply(List<Entry> entries) {
        List<Entry> byScore = new ArrayList<>(entries);
        Collections.sort(byScore, BY_SCORE);

        List<Entry> evergreen = new ArrayList<>();
        for (Entry entry : byScore) {
            if (entry.evergreen) {
                evergreen.add(entry);
            }

            mVideoIds.add(entry.vid);

            for (String area : entry.areas) {
                List<Entry> areaEntries = mByArea.get(area);
                if (areaEntries == null) {
                    areaEntries = new ArrayList<>();
                    mByArea.put(area, areaEntries);
                }
                areaEntries.add(entry);
            }
        }

        List<Entry> byRecommenders = new ArrayList<>(byScore);
        Collections.sort(byRecommenders, BY_RECOMMENDERS);

        List<Entry> byLastYear = new ArrayList<>(byScore);
        Collections.sort(byLastYear, BY_LAST_YEAR);

        mByScore = byScore;
        mByRecommenders = byRecommenders;
        mByLastYear = byLastYear;
        mEvergreen = evergreen;
        mLoaded = true;

        List<Runnable> callbacks = new ArrayList<>(mCallbacks);
        mCallbacks.clear();

        for (Runnable callback : callbacks) {
            callback.run();
        }
    }

    private List<Entry> parse() {
        List<Entry> entries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                mContext.getAssets().open(ASSET), "UTF-8"))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (TextUtils.isEmpty(line)) {
                    continue;
                }

                Entry entry = Entry.from(new JSONObject(line));

                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot read %s: %s", ASSET, e.getMessage());
        }

        return entries;
    }

    public List<Video> topPicks() {
        return toVideos(mByScore, HOME_ROW_LIMIT);
    }

    public List<Video> mostRecommended() {
        return toVideos(mByRecommenders, HOME_ROW_LIMIT);
    }

    public List<Video> recent() {
        return toVideos(mByLastYear, HOME_ROW_LIMIT);
    }

    public List<Video> evergreen() {
        return toVideos(mEvergreen, HOME_ROW_LIMIT);
    }

    public List<Video> byArea(int areaIndex) {
        return toVideos(mByArea.get(AREA_IDS[areaIndex]), Integer.MAX_VALUE);
    }

    /**
     * HackerTube has HN comments for its own records only.
     */
    public boolean hasVideo(String videoId) {
        return videoId != null && mVideoIds.contains(videoId);
    }

    /**
     * Fresh {@link Video} objects: the same record can sit in several rows and rows own their items.
     */
    private List<Video> toVideos(List<Entry> entries, int limit) {
        List<Video> videos = new ArrayList<>();

        if (entries == null) {
            return videos;
        }

        for (Entry entry : entries.subList(0, Math.min(limit, entries.size()))) {
            videos.add(entry.toVideo());
        }

        return videos;
    }

    private static final class Entry {
        private final String vid;
        private final String title;
        private final String channel;
        private final String summary;
        private final String why;
        private final float score;
        private final int recommenders;
        private final int firstYear;
        private final int lastYear;
        private final boolean evergreen;
        private final String[] areas;

        private Entry(JSONObject json) {
            vid = json.optString("vid", null);
            title = json.optString("title", "");
            channel = json.optString("channel", "");
            summary = json.optString("summary", "");
            why = json.optString("why", "");
            score = (float) json.optDouble("score", 0);
            recommenders = json.optInt("rec", 0);
            firstYear = json.optInt("first", 0);
            lastYear = json.optInt("last", 0);
            evergreen = "evergreen".equals(json.optString("shelf", null));
            areas = toStrings(json.optJSONArray("areas"));
        }

        private static Entry from(JSONObject json) {
            Entry entry = new Entry(json);

            return TextUtils.isEmpty(entry.vid) ? null : entry;
        }

        private static String[] toStrings(JSONArray array) {
            if (array == null) {
                return new String[0];
            }

            String[] result = new String[array.length()];

            for (int i = 0; i < array.length(); i++) {
                result[i] = array.optString(i);
            }

            return result;
        }

        private Video toVideo() {
            Video video = new Video();

            video.videoId = vid;
            video.title = title;
            video.author = channel;
            video.secondTitle = buildSecondTitle();
            video.description = buildDescription();
            video.cardImageUrl = String.format(THUMB_URL, vid);
            video.bgImageUrl = String.format(BG_URL, vid);

            return video;
        }

        private String buildSecondTitle() {
            if (firstYear == 0 && lastYear == 0) {
                return channel;
            }

            return firstYear != lastYear ? channel + " • " + firstYear + "–" + lastYear : channel + " • " + lastYear;
        }

        private String buildDescription() {
            if (TextUtils.isEmpty(why)) {
                return summary;
            }

            return summary + "\n\n" + why;
        }
    }
}
