package cgeo.geocaching.enumerations;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Enum listing waypoint types
 */
public enum WaypointType {
    FINAL("flag", "Final Location", R.string.wp_final, R.drawable.waypoint_flag, 3, R.drawable.dot_waypoint_flag),
    OWN("own", "Own", R.string.wp_waypoint, R.drawable.waypoint_waypoint, 5, R.drawable.dot_waypoint),
    PARKING("pkg", "Parking Area", R.string.wp_pkg, R.drawable.waypoint_pkg, -1, R.drawable.dot_waypoint_pkg),
    PUZZLE("puzzle", "Virtual Stage", R.string.wp_puzzle, R.drawable.waypoint_puzzle, 2, R.drawable.dot_waypoint),
    STAGE("stage", "Physical Stage", R.string.wp_stage, R.drawable.waypoint_stage, 2, R.drawable.dot_waypoint),
    TRAILHEAD("trailhead", "Trailhead", R.string.wp_trailhead, R.drawable.waypoint_trailhead, 1, R.drawable.dot_waypoint),
    WAYPOINT("waypoint", "Reference Point", R.string.wp_waypoint, R.drawable.waypoint_waypoint, 2, R.drawable.dot_waypoint_reference),
    ORIGINAL("original", "Original Coordinates", R.string.wp_original, R.drawable.waypoint_waypoint, 4, R.drawable.dot_waypoint_reference);

    @NonNull
    public final String id;

    @NonNull public final String gpx;
    public final int stringId;
    public final int markerId;

    public final int order;
    public final int dotMarkerId;

    WaypointType(@NonNull final String id, @NonNull final String gpx, final int stringId, final int markerId, final int order, final int dotMarkerId) {
        this.id = id;
        this.gpx = gpx;
        this.stringId = stringId;
        this.markerId = markerId;
        this.order = order;
        this.dotMarkerId = dotMarkerId;
    }

    /**
     * inverse lookup of waypoint IDs<br/>
     * non public so that {@code null} handling can be handled centrally in the enum type itself
     */
    private static final Map<String, WaypointType> FIND_BY_ID = new HashMap<>();
    static {
        for (final WaypointType wt : values()) {
            FIND_BY_ID.put(wt.id, wt);
        }
    }
    @NonNull
    public static final List<WaypointType> ALL_TYPES_EXCEPT_OWN_AND_ORIGINAL = orderedWaypointTypes();

    private static List<WaypointType> orderedWaypointTypes() {
        // enforce an order for these types
        final Set<WaypointType> waypointTypes = new LinkedHashSet<>();
        waypointTypes.addAll(Arrays.asList(PARKING, TRAILHEAD, PUZZLE, STAGE, FINAL));
        // then add all remaining except "internal" types
        waypointTypes.addAll(EnumSet.complementOf(EnumSet.of(OWN, ORIGINAL)));
        return Collections.unmodifiableList(new ArrayList<>(waypointTypes));
    }

    /**
     * inverse lookup of waypoint IDs<br/>
     * here the {@code null} handling shall be done
     */
    @NonNull
    public static WaypointType findById(final String id) {
        if (id == null) {
            return WAYPOINT;
        }
        final WaypointType waypointType = FIND_BY_ID.get(id);
        if (waypointType == null) {
            return WAYPOINT;
        }
        return waypointType;
    }

    @NonNull
    public final String getL10n() {
        return CgeoApplication.getInstance().getBaseContext().getString(stringId);
    }

    @Override
    public final String toString() {
        return getL10n();
    }

    public boolean applyDistanceRule() {
        return this == FINAL || this == STAGE;
    }

    public static WaypointType fromGPXString(@NonNull final String sym) {
        return fromGPXString(sym, null);
    }

    public static WaypointType fromGPXString(@NonNull final String sym, final String subtype) {
        // first check the somewhat "official" sym types
        for (final WaypointType waypointType : WaypointType.values()) {
            if (waypointType.gpx.equalsIgnoreCase(sym)) {
                return waypointType;
            }
            // Maybe the <sym> element was used for a Garmin symbol (e.g. Opencaching GPX).
            // Try the subtype instead if defined
            if (waypointType.gpx.equalsIgnoreCase(subtype)) {
                return waypointType;
            }
        }

        // old names of multi cache stages
        if ("stages of a multicache".equalsIgnoreCase(sym)) {
            return WaypointType.STAGE;
        }
        if ("stage of a multicache".equalsIgnoreCase(sym)) {
            return WaypointType.STAGE;
        }
        if ("question to answer".equalsIgnoreCase(sym)) {
            return WaypointType.PUZZLE;
        }

        // this is not fully correct, but lets also look for localized waypoint types
        for (final WaypointType waypointType : WaypointType.ALL_TYPES_EXCEPT_OWN_AND_ORIGINAL) {
            final String localized = waypointType.getL10n();
            if (StringUtils.isNotEmpty(localized) && localized.equalsIgnoreCase(sym)) {
                return waypointType;
            }
        }
        return WaypointType.WAYPOINT;
    }
}
