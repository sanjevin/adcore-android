package zonely.ams.adcore.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeviceDataDelta {
    public final String dateKey;
    public final long windowStartMs;
    public final long windowEndMs;
    public final int totalPlayTimeInSec;
    public final int totalSyncTimeInSec;
    public final int totalAppUpTimeInSec;
    public final int totalDeviceUpTimeInSec;
    public final int currentTotalSyncTimeInSec;
    public final int currentTotalAppUpTimeInSec;
    public final int currentTotalDeviceUpTimeInSec;
    public final Map<String, Integer> resourceIdPlayCountMap;
    public final Map<String, PlaybackSnapshot> playbackSnapshots;

    public DeviceDataDelta(String dateKey,
                           long windowStartMs,
                           long windowEndMs,
                           int totalPlayTimeInSec,
                           int totalSyncTimeInSec,
                           int totalAppUpTimeInSec,
                           int totalDeviceUpTimeInSec,
                           int currentTotalSyncTimeInSec,
                           int currentTotalAppUpTimeInSec,
                           int currentTotalDeviceUpTimeInSec,
                           Map<String, Integer> resourceIdPlayCountMap,
                           Map<String, PlaybackSnapshot> playbackSnapshots) {
        this.dateKey = dateKey;
        this.windowStartMs = windowStartMs;
        this.windowEndMs = windowEndMs;
        this.totalPlayTimeInSec = totalPlayTimeInSec;
        this.totalSyncTimeInSec = totalSyncTimeInSec;
        this.totalAppUpTimeInSec = totalAppUpTimeInSec;
        this.totalDeviceUpTimeInSec = totalDeviceUpTimeInSec;
        this.currentTotalSyncTimeInSec = currentTotalSyncTimeInSec;
        this.currentTotalAppUpTimeInSec = currentTotalAppUpTimeInSec;
        this.currentTotalDeviceUpTimeInSec = currentTotalDeviceUpTimeInSec;
        this.resourceIdPlayCountMap = immutableCopy(resourceIdPlayCountMap);
        this.playbackSnapshots = immutableCopy(playbackSnapshots);
    }

    public boolean hasData() {
        return totalPlayTimeInSec > 0
                || totalSyncTimeInSec > 0
                || totalAppUpTimeInSec > 0
                || totalDeviceUpTimeInSec > 0
                || !resourceIdPlayCountMap.isEmpty();
    }

    private static <T> Map<String, T> immutableCopy(Map<String, T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static class PlaybackSnapshot {
        public final int playCount;
        public final int totalPlaySeconds;

        public PlaybackSnapshot(int playCount, int totalPlaySeconds) {
            this.playCount = playCount;
            this.totalPlaySeconds = totalPlaySeconds;
        }
    }
}
