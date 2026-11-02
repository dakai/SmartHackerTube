package com.liskovsoft.smartyoutubetv2.common.hackertube;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * The signals hackertube.org keeps in the browser's localStorage: liked, disliked and seen videos.
 * Watched videos come from the app history, likes and dislikes are local to the app.
 */
public class HackerTubeData {
    private static final String HACKER_TUBE_DATA = "hacker_tube_data";
    private static final int MAX_SIGNAL_SIZE = 200;
    @SuppressLint("StaticFieldLeak")
    private static HackerTubeData sInstance;
    private final AppPrefs mPrefs;
    private final List<String> mLikes = new ArrayList<>();
    private final List<String> mDislikes = new ArrayList<>();

    private HackerTubeData(Context context) {
        mPrefs = AppPrefs.instance(context.getApplicationContext());
        restoreState();
    }

    public static HackerTubeData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HackerTubeData(context);
        }

        return sInstance;
    }

    public List<String> getLikes() {
        return mLikes;
    }

    public List<String> getDislikes() {
        return mDislikes;
    }

    public boolean isLiked(String videoId) {
        return mLikes.contains(videoId);
    }

    public boolean isDisliked(String videoId) {
        return mDislikes.contains(videoId);
    }

    public boolean toggleLike(String videoId) {
        return toggle(mLikes, mDislikes, videoId);
    }

    public boolean toggleDislike(String videoId) {
        return toggle(mDislikes, mLikes, videoId);
    }

    /**
     * @return true when the signal was added
     */
    private boolean toggle(List<String> addTo, List<String> removeFrom, String videoId) {
        if (videoId == null) {
            return false;
        }

        removeFrom.remove(videoId);

        boolean added = !addTo.remove(videoId);

        if (added) {
            addTo.add(videoId);
            trim(addTo);
        }

        persistState();

        return added;
    }

    /**
     * Same as the site: newest last, old signals fall off.
     */
    private static void trim(List<String> signals) {
        while (signals.size() > MAX_SIGNAL_SIZE) {
            signals.remove(0);
        }
    }

    /**
     * Videos the user opened, newest last.
     */
    public List<String> getSeen() {
        List<String> seen = new ArrayList<>();
        VideoStateService stateService = VideoStateService.instance(null);

        if (stateService == null) {
            return seen;
        }

        for (VideoStateService.State state : stateService.getStates()) {
            if (state.video != null && state.video.videoId != null) {
                seen.add(state.video.videoId);
            }
        }

        trim(seen);

        return seen;
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(HACKER_TUBE_DATA));

        mLikes.clear();
        mDislikes.clear();
        mLikes.addAll(Helpers.parseList(split, 0, key -> Helpers.parseStr(key)));
        mDislikes.addAll(Helpers.parseList(split, 1, key -> Helpers.parseStr(key)));
    }

    private void persistState() {
        mPrefs.setProfileData(HACKER_TUBE_DATA, Helpers.mergeData(mLikes, mDislikes));
    }
}
